package app.orchard.tv.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.orchard.tv.R
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppNames
import app.orchard.tv.settings.QuickAction
import org.json.JSONObject

/** Human-facing capabilities. Protocol details belong to adapters, never to the add-control UI. */
object QuickCatalog {
    data class Entry(val key: String, val title: String, val icon: String, val group: String, val control: QuickAction) {
        fun json() = JSONObject().put("key", key).put("title", title).put("icon", icon).put("group", group)
            .put("app", if (control.kind == "app") control.pkg else "")
    }

    fun key(control: QuickAction): String = control.catalogKey

    fun available(context: Context): List<Entry> = buildList {
        val clash = listOf("com.github.metacubex.clash.meta", "com.github.metacubex.clash.alpha").any { pkg ->
            runCatching { context.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
        }
        if (clash) add(Entry("clash", "Clash Meta", "vpn", "system", QuickAction(title = "Clash Meta")))
        fun system(action: String, label: Int, icon: String) {
            val title = context.getString(label)
            add(Entry("system:$action", title, icon, "system", QuickAction(title = title, kind = "system", action = action)))
        }
        system("network", R.string.qc_network_settings, "network")
        if (context.packageManager.resolveActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 0) != null) {
            system("bluetooth", R.string.qc_bluetooth_settings, "bluetooth")
        }
        system("mute", R.string.cc_muted, "mute")
        system("inputs", R.string.cc_inputs, "inputs")
        system("settings", R.string.cc_settings, "settings")
        AppCatalog.loaded.filter { it.source == null && it.folder == null && it.shortcut == null }
            .distinctBy { it.packageName }.sortedBy { (AppNames.of(it.key) ?: it.label).lowercase() }.forEach { app ->
                val title = (AppNames.of(app.key) ?: app.label).take(64)
                add(Entry("app:${app.packageName}", title, "app", "apps", QuickAction(title = title, kind = "app", pkg = app.packageName)))
            }
    }
}
