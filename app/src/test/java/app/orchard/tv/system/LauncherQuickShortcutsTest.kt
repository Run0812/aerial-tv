package app.orchard.tv.system

import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import app.orchard.tv.settings.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class LauncherQuickShortcutsTest {
    @Test fun discoversNewlyPublishedShortcutsAndStopsOfferingThemWhenHostAccessIsLost() {
        val context = RuntimeEnvironment.getApplication()
        val host = shadowOf(requireNotNull(context.getSystemService(LauncherApps::class.java)))
        host.setHasShortcutHostPermission(true)
        assertTrue(LauncherQuickShortcuts.available(context).isEmpty())

        host.addDynamicShortcut(ShortcutInfo.Builder(context, "publisher-shortcut")
            .setShortLabel("Published action").setIntent(Intent(Intent.ACTION_VIEW)).build())
        val entry = QuickCatalog.available(context).single { it.group == "shortcuts" }
        assertEquals("Published action", entry.title)
        assertEquals("shortcut:${context.packageName}:publisher-shortcut", entry.key)
        assertEquals(entry.control, QuickAction.from(entry.control.json()))
        assertEquals("publisher-shortcut", entry.json().getString("shortcutId"))

        val missing = entry.control.copy(shortcutId = "removed")
        assertEquals(QuickActionRunner.Result.Unavailable, QuickActionRunner.run(context, missing))
        host.setHasShortcutHostPermission(false)
        assertFalse(QuickCatalog.available(context).any { it.group == "shortcuts" })
        assertEquals(QuickActionRunner.Result.Unavailable, QuickActionRunner.run(context, entry.control))
    }
}
