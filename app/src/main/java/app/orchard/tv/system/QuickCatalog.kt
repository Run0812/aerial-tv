package app.orchard.tv.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import app.orchard.tv.R
import app.orchard.tv.apps.AppCatalog
import app.orchard.tv.apps.AppEntry
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
            is QuickTarget.Shortcut -> put("app", target.pkg).put("shortcutId", target.shortcutId)
            is BuiltIn -> Unit
        }
    }

    fun hasPublisher(pkg: String): Boolean = AppCatalog.loaded.any { it.key == pkg && it.isShortcutPublisher() }

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

        val packages = AppCatalog.loaded.filter { it.isShortcutPublisher() }.mapTo(HashSet()) { it.packageName }
        LauncherQuickShortcuts.available(context).forEach { control ->
            val target = control.target as? QuickTarget.Shortcut ?: return@forEach
            if (target.pkg in packages) add(Entry(control, "shortcuts"))
        }
    }

    private fun AppEntry.isShortcutPublisher() = source == null && folder == null && shortcut == null
}
