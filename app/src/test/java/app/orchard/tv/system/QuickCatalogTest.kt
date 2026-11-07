package app.orchard.tv.system

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
class QuickCatalogTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun exposesClashOnlyWhenAnEnabledSupportedAppIsInstalled() {
        assertFalse(QuickCatalog.available(context).any { it.key == "clash" })
        val info = ApplicationInfo().apply { packageName = "com.github.metacubex.clash.alpha"; enabled = true }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = info.packageName; applicationInfo = info })
        val entry = QuickCatalog.available(context).single { it.key == "clash" }
        assertEquals("vpn", entry.icon)
        assertEquals("clash", entry.control.kind)
        assertTrue(entry.control.valid())
        assertFalse(entry.json().has("component"))
        assertFalse(entry.json().has("extras"))
    }

    @Test fun capabilityKeysStayStableAcrossRenameAndAvailableControlsAreValid() {
        val control = QuickAction(title = "Mute", kind = "system", action = "mute")
        assertEquals("system:mute", QuickCatalog.key(control.copy(title = "Quiet")))
        val entries = QuickCatalog.available(context)
        assertEquals(entries.size, entries.map { it.key }.distinct().size)
        assertTrue(entries.all { it.control.valid() })
        assertTrue(entries.any { it.key == "system:mute" })
    }
}
