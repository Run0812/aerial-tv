package app.orchard.tv.system

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import app.orchard.tv.settings.QuickAction

/** Use the platform shortcut host contract. Publishers keep ownership of their action intents. */
internal object LauncherQuickShortcuts {
    fun hasAccess(context: Context): Boolean = launcher(context) != null

    fun available(context: Context): List<QuickAction> {
        val launcher = launcher(context) ?: return emptyList()
        return query(launcher).mapNotNull { shortcut ->
            val title = shortcut.shortLabel?.toString()?.trim().orEmpty().take(64)
            QuickAction(title = title, kind = "shortcut", pkg = shortcut.`package`, shortcutId = shortcut.id)
                .takeIf { it.valid() }
        }.distinctBy { it.catalogKey }.sortedBy { it.title.lowercase() }
    }

    fun start(context: Context, control: QuickAction): QuickActionRunner.Result {
        val launcher = launcher(context) ?: return QuickActionRunner.Result.Unavailable
        if (query(launcher, control.pkg, control.shortcutId).isEmpty()) return QuickActionRunner.Result.Unavailable
        return try {
            launcher.startShortcut(control.pkg, control.shortcutId, null, null, Process.myUserHandle())
            QuickActionRunner.Result.Sent
        } catch (_: RuntimeException) {
            // An app may remove a shortcut or the launcher may lose access after discovery.
            QuickActionRunner.Result.Unavailable
        }
    }

    fun icon(context: Context, pkg: String, id: String): Bitmap? {
        if (!QuickAction(title = "Icon", kind = "shortcut", pkg = pkg, shortcutId = id).valid()) return null
        val launcher = launcher(context) ?: return null
        val shortcut = query(launcher, pkg, id).firstOrNull() ?: return null
        return runCatching { launcher.getShortcutIconDrawable(shortcut, 0)?.toBitmap(96, 96, Bitmap.Config.ARGB_8888) }.getOrNull()
    }

    private fun launcher(context: Context): LauncherApps? {
        if (Build.VERSION.SDK_INT < 25) return null
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
        return launcher.takeIf { runCatching { it.hasShortcutHostPermission() }.getOrDefault(false) }
    }

    @android.annotation.TargetApi(25)
    private fun query(launcher: LauncherApps, pkg: String? = null, id: String? = null): List<ShortcutInfo> {
        val request = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        if (pkg != null) request.setPackage(pkg)
        if (id != null) request.setShortcutIds(listOf(id))
        return runCatching { launcher.getShortcuts(request, Process.myUserHandle()).orEmpty().filter { it.isEnabled } }
            .getOrDefault(emptyList())
    }
}
