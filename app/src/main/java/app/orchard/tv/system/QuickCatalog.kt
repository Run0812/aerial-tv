package app.orchard.tv.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.orchard.tv.R
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.AppNames
import app.orchard.tv.settings.QuickAction
import app.orchard.tv.settings.QuickTarget
import app.orchard.tv.settings.QuickTarget.BuiltIn

/** Discover the same capabilities for the TV and phone editors. */
internal object QuickCatalog {
    data class Entry(val control: QuickAction, val group: String) {
        val key get() = control.target.key
        fun json() = displayJson(control).put("group", group)
    }

    fun displayJson(control: QuickAction) = control.json().put("icon", control.target.icon).apply {
        when (val target = control.target) {
            is QuickTarget.App -> put("app", target.pkg)
            is QuickTarget.Shortcut -> put("app", target.pkg).put("shortcutId", target.shortcutId)
            is BuiltIn -> Unit
        }
    }

    fun app(pkg: String): AppEntry? = AppCatalog.loaded.firstOrNull { it.key == pkg && it.canOpenFromQuickControls() }

    fun available(context: Context): List<Entry> = buildList {
        if (ClashShortcut.available(context)) add(Entry(QuickAction("Clash Meta", BuiltIn.Clash), "system"))
        fun system(target: BuiltIn, label: Int) {
            add(Entry(QuickAction(context.getString(label), target), "system"))
        }
        system(BuiltIn.Network, R.string.qc_network_settings)
        if (context.packageManager.resolveActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), 0) != null) {
            system(BuiltIn.Bluetooth, R.string.qc_bluetooth_settings)
        }
        system(BuiltIn.Mute, R.string.qc_mute)
        system(BuiltIn.Inputs, R.string.cc_inputs)
        system(BuiltIn.Settings, R.string.cc_settings)

        val apps = AppCatalog.loaded.filter { it.canOpenFromQuickControls() }.distinctBy { it.packageName }
        val packages = apps.mapTo(HashSet()) { it.packageName }
        LauncherQuickShortcuts.available(context).forEach { control ->
            val target = control.target as? QuickTarget.Shortcut ?: return@forEach
            if (target.pkg in packages) add(Entry(control, "shortcuts"))
        }
        apps.sortedBy { (AppNames.of(it.key) ?: it.label).lowercase() }.forEach { app ->
            val control = QuickAction((AppNames.of(app.key) ?: app.label).take(64), QuickTarget.App(app.packageName))
            if (control.valid()) add(Entry(control, "apps"))
        }
    }

    private fun AppEntry.canOpenFromQuickControls() = source == null && folder == null && shortcut == null
}
