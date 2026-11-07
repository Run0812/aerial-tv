package app.orchard.tv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Immutable, shared by the TV editor and the authenticated phone page. */
data class QuickAction(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: String = "clash",
    val enabled: Boolean = true,
    val pkg: String = "",
    val component: String = "",
    val action: String = "",
    val uri: String = "",
    val extras: String = "{}",
    val shortcutId: String = "",
) {
    val catalogKey: String get() = when (kind) {
        "clash" -> "clash"
        "app" -> "app:$pkg"
        "system" -> "system:$action"
        "shortcut" -> "shortcut:$pkg:$shortcutId"
        else -> "legacy:$id"
    }
    fun json() = JSONObject().put("id", id).put("title", title).put("kind", kind).put("enabled", enabled)
        .put("key", catalogKey).put("pkg", pkg).put("component", component).put("action", action).put("uri", uri).put("extras", extras)
        .put("shortcutId", shortcutId)

    fun valid(): Boolean {
        if (id.length !in 1..80 || !id.matches(Regex("[A-Za-z0-9_-]+")) || title.isBlank() || title.length > 64) return false
        if (kind !in listOf("clash", "app", "system", "activity", "broadcast", "shortcut")) return false
        if (kind == "clash") return true
        if (kind == "system") return action in listOf("network", "bluetooth", "mute", "inputs", "settings")
        if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) || pkg.length > 200) return false
        if (kind == "app") return true
        if (kind == "shortcut") return shortcutId.isNotBlank() && shortcutId.length <= 1024
        if (component.length > 250 || !component.matches(Regex("\\.?[A-Za-z_$][A-Za-z0-9_.$]*"))) return false
        if (action.length > 250 || uri.length > 2048 || extras.length > 8192) return false
        if (uri.isNotBlank()) {
            val scheme = uri.substringBefore(':').lowercase()
            if (':' !in uri || !scheme.matches(Regex("[a-z][a-z0-9+.-]*")) || scheme in listOf("file", "intent", "javascript")) return false
        }
        return runCatching {
            val objectValue = JSONObject(extras)
            objectValue.length() <= 20 && objectValue.keys().asSequence().all { key ->
                val value = objectValue.get(key)
                key.length in 1..100 && (value is Boolean || value is Number || (value is String && value.length <= 2048))
            }
        }.getOrDefault(false)
    }

    companion object {
        fun from(json: JSONObject) = QuickAction(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            title = json.optString("title").trim(), kind = json.optString("kind", "clash"),
            enabled = json.optBoolean("enabled", true), pkg = json.optString("pkg").trim(),
            component = json.optString("component").trim(), action = json.optString("action").trim(),
            uri = json.optString("uri").trim(), extras = json.optString("extras", "{}").ifBlank { "{}" },
            shortcutId = json.optString("shortcutId"),
        )
    }
}

/** Main-thread mutations with a revision guard, so a stale phone form cannot overwrite a TV edit. */
class QuickControlStore(context: Context) {
    private data class Snapshot(val enabled: Boolean, val items: List<QuickAction>, val revision: Long)

    private val prefs = context.getSharedPreferences("quick_controls", Context.MODE_PRIVATE)
    private var snapshot by mutableStateOf(load(context))
    val enabled get() = snapshot.enabled
    val items get() = snapshot.items
    val revision get() = snapshot.revision

    private fun load(context: Context): Snapshot {
        if (!prefs.contains("items")) {
            val legacy = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("clash_shortcut", false)
            return Snapshot(legacy, if (legacy) listOf(QuickAction(title = "Clash Meta")) else emptyList(), 0L).also(::persist)
        }
        val items = runCatching {
            val array = JSONArray(prefs.getString("items", "[]"))
            (0 until array.length()).map { QuickAction.from(array.getJSONObject(it)) }
                .filter { it.valid() }.distinctBy { it.id }.take(MAX_ITEMS)
        }.getOrDefault(emptyList())
        return Snapshot(prefs.getBoolean("enabled", false), items, prefs.getLong("revision", 0L))
    }

    fun json(): JSONObject = snapshot.let { current ->
        JSONObject().put("enabled", current.enabled).put("revision", current.revision)
            .put("items", JSONArray().apply { current.items.forEach { put(it.json()) } })
    }

    /** Returns null on success, otherwise a stable error key for both interfaces. */
    fun change(op: String, id: String = "", value: QuickAction? = null, on: Boolean = false, delta: Int = 0,
               expected: Long = revision): String? {
        val current = snapshot
        if (expected != current.revision) return "conflict"
        val items = current.items
        val index = items.indexOfFirst { it.id == id }
        val next = when (op) {
            "enabled" -> current.copy(enabled = on)
            "add" -> {
                if (value == null || !value.valid() || items.any { it.id == value.id }) return "invalid"
                if (items.any { it.catalogKey == value.catalogKey }) return "exists"
                if (items.size >= MAX_ITEMS) return "limit"
                current.copy(enabled = current.enabled || items.isEmpty(), items = items + value)
            }
            "update" -> {
                if (index < 0) return "missing"
                if (value == null || value.id != id || !value.valid()) return "invalid"
                current.copy(items = items.map { if (it.id == id) value else it })
            }
            "toggle", "delete", "move", "place" -> {
                if (index < 0) return "missing"
                current.copy(items = when (op) {
                    "toggle" -> items.map { if (it.id == id) it.copy(enabled = on) else it }
                    "delete" -> items.filterNot { it.id == id }
                    else -> {
                        if (op == "move" && delta != -1 && delta != 1) return "invalid"
                        if (op == "place" && delta !in items.indices) return "invalid"
                        val destination = if (op == "place") delta else (index + delta).coerceIn(0, items.lastIndex)
                        items.toMutableList().apply { add(destination, removeAt(index)) }
                    }
                })
            }
            else -> return "invalid"
        }
        snapshot = next.copy(revision = current.revision + 1)
        persist(snapshot)
        return null
    }

    private fun persist(value: Snapshot) = prefs.edit {
        putBoolean("enabled", value.enabled); putLong("revision", value.revision)
        putString("items", JSONArray().apply { value.items.forEach { put(it.json()) } }.toString())
    }

    companion object { const val MAX_ITEMS = 12 }
}

object QuickControls {
    lateinit var store: QuickControlStore; private set
    fun init(context: Context) { if (!::store.isInitialized) store = QuickControlStore(context.applicationContext) }
}
