package app.orchard.tv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A catalog capability. Saved controls keep its identity, never an executable Intent. */
internal sealed interface QuickTarget {
    val key: String
    val icon: String

    enum class BuiltIn(override val key: String, override val icon: String) : QuickTarget {
        Clash("clash", "vpn"), Network("system:network", "network"), Bluetooth("system:bluetooth", "bluetooth"),
        Mute("system:mute", "mute"), Inputs("system:inputs", "inputs"), Settings("system:settings", "settings"),
    }

    data class Shortcut(val pkg: String, val shortcutId: String) : QuickTarget {
        override val key get() = "shortcut:$pkg:$shortcutId"
        override val icon get() = "shortcut"
    }

    companion object {
        private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

        fun parse(key: String): QuickTarget? {
            BuiltIn.entries.firstOrNull { it.key == key }?.let { return it }
            val parts = key.split(':', limit = 3)
            val pkg = parts.getOrNull(1)?.takeIf { it.length <= 200 && packagePattern.matches(it) } ?: return null
            return if (parts[0] == "shortcut") {
                parts.getOrNull(2)?.takeIf { it.isNotBlank() && it.length <= 1024 }?.let { Shortcut(pkg, it) }
            } else null
        }
    }
}

internal data class QuickAction(
    val title: String,
    val target: QuickTarget,
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
) {
    fun json() = JSONObject().put("id", id).put("title", title).put("enabled", enabled).put("key", target.key)

    fun valid(): Boolean = validTitle(title) && id.length in 1..80 && idPattern.matches(id) && QuickTarget.parse(target.key) == target

    companion object {
        private val idPattern = Regex("[A-Za-z0-9_-]+")
        fun validTitle(title: String) = title.isNotBlank() && title.length <= 64

        fun from(json: JSONObject): QuickAction? {
            val target = QuickTarget.parse(json.optString("key")) ?: return null
            return QuickAction(json.optString("title").trim(), target, json.optString("id"), json.optBoolean("enabled", true))
                .takeIf { it.valid() }
        }
    }
}

internal sealed interface QuickEdit {
    data class EnableArea(val enabled: Boolean) : QuickEdit
    data class Add(val item: QuickAction) : QuickEdit
    data class Rename(val id: String, val title: String) : QuickEdit
    data class Show(val id: String, val visible: Boolean) : QuickEdit
    data class Remove(val id: String) : QuickEdit
    data class Move(val id: String, val position: Int) : QuickEdit
}

/** Main-thread mutations shared by both editors, with a revision guard for concurrent edits. */
internal class QuickControlStore(context: Context) {
    private data class Snapshot(val enabled: Boolean, val items: List<QuickAction>, val revision: Long)
    private val prefs = context.getSharedPreferences("quick_controls", Context.MODE_PRIVATE)
    private var snapshot by mutableStateOf(load())
    val enabled get() = snapshot.enabled
    val items get() = snapshot.items
    val revision get() = snapshot.revision

    private fun load(): Snapshot {
        val array = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrElse { JSONArray() }
        val items = (0 until array.length()).asSequence().mapNotNull { array.optJSONObject(it)?.let(QuickAction::from) }
            .distinctBy { it.id }.distinctBy { it.target.key }.take(MAX_ITEMS).toList()
        return Snapshot(prefs.getBoolean("enabled", false), items, prefs.getLong("revision", 0L))
    }

    fun json(encode: (QuickAction) -> JSONObject): JSONObject = snapshot.let { current ->
        JSONObject().put("enabled", current.enabled).put("revision", current.revision)
            .put("items", JSONArray().apply { current.items.forEach { put(encode(it)) } })
    }

    fun enabledAction(id: String): QuickAction? = snapshot.let { current ->
        current.items.firstOrNull { current.enabled && it.enabled && it.id == id }
    }

    fun change(edit: QuickEdit, expected: Long = revision): String? {
        val current = snapshot
        if (expected != current.revision) return "conflict"
        val items = current.items
        val next = when (edit) {
            is QuickEdit.EnableArea -> current.copy(enabled = edit.enabled)
            is QuickEdit.Add -> {
                if (!edit.item.valid() || items.any { it.id == edit.item.id }) return "invalid"
                if (items.any { it.target.key == edit.item.target.key }) return "exists"
                if (items.size >= MAX_ITEMS) return "limit"
                current.copy(enabled = current.enabled || items.isEmpty(), items = items + edit.item)
            }
            is QuickEdit.Rename -> {
                if (items.none { it.id == edit.id }) return "missing"
                if (!QuickAction.validTitle(edit.title)) return "invalid"
                current.copy(items = items.map { if (it.id == edit.id) it.copy(title = edit.title) else it })
            }
            is QuickEdit.Show -> {
                if (items.none { it.id == edit.id }) return "missing"
                current.copy(items = items.map { if (it.id == edit.id) it.copy(enabled = edit.visible) else it })
            }
            is QuickEdit.Remove -> {
                if (items.none { it.id == edit.id }) return "missing"
                current.copy(items = items.filterNot { it.id == edit.id })
            }
            is QuickEdit.Move -> {
                val index = items.indexOfFirst { it.id == edit.id }
                if (index < 0) return "missing"
                if (edit.position !in items.indices) return "invalid"
                current.copy(items = items.toMutableList().apply { add(edit.position, removeAt(index)) })
            }
        }
        if (next != current) {
            snapshot = next.copy(revision = current.revision + 1)
            persist(snapshot)
        }
        return null
    }

    private fun persist(value: Snapshot) = prefs.edit {
        putBoolean("enabled", value.enabled)
        putLong("revision", value.revision)
        putString("items", JSONArray().apply { value.items.forEach { put(it.json()) } }.toString())
    }

    companion object { const val MAX_ITEMS = 12 }
}

internal object QuickControls {
    lateinit var store: QuickControlStore; private set
    fun init(context: Context) { if (!::store.isInitialized) store = QuickControlStore(context.applicationContext) }
}
