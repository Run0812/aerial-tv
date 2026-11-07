package app.orchard.tv.system

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import app.orchard.tv.settings.QuickAction
import app.orchard.tv.settings.QuickTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLauncherApps

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], shadows = [LauncherQuickShortcutsTest.RecordingLauncherApps::class])
class LauncherQuickShortcutsTest {
    @Implements(LauncherApps::class)
    class RecordingLauncherApps : ShadowLauncherApps() {
        var started: Triple<String, String, UserHandle>? = null

        @Implementation
        override fun startShortcut(pkg: String, id: String, bounds: Rect?, options: Bundle?, user: UserHandle) {
            started = Triple(pkg, id, user)
        }
    }

    @Test fun discoversAndDispatchesPublishedShortcutsOnlyWithHostAccess() {
        val context = RuntimeEnvironment.getApplication()
        val host = Shadow.extract<RecordingLauncherApps>(requireNotNull(context.getSystemService(LauncherApps::class.java)))
        host.setHasShortcutHostPermission(true)
        assertTrue(LauncherQuickShortcuts.available(context).isEmpty())

        host.addDynamicShortcut(ShortcutInfo.Builder(context, "publisher-shortcut")
            .setShortLabel("Published action").setIntent(Intent(Intent.ACTION_VIEW)).build())
        val control = LauncherQuickShortcuts.available(context).single()
        val target = control.target as QuickTarget.Shortcut
        assertEquals("Published action", control.title)
        assertEquals("shortcut:${context.packageName}:publisher-shortcut", target.key)
        assertEquals(control, QuickAction.from(control.json()))
        assertEquals("publisher-shortcut", QuickCatalog.displayJson(control).getString("shortcutId"))
        assertEquals(QuickActionRunner.Result.Sent, LauncherQuickShortcuts.start(context, target))
        assertEquals(Triple(context.packageName, "publisher-shortcut", Process.myUserHandle()), host.started)

        val missing = target.copy(shortcutId = "removed")
        assertEquals(QuickActionRunner.Result.Unavailable, LauncherQuickShortcuts.start(context, missing))
        host.setHasShortcutHostPermission(false)
        assertFalse(LauncherQuickShortcuts.hasAccess(context))
        assertTrue(LauncherQuickShortcuts.available(context).isEmpty())
        assertEquals(QuickActionRunner.Result.Unavailable, LauncherQuickShortcuts.start(context, target))
    }
}
