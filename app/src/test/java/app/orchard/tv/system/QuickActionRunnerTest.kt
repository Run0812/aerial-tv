package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
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
class QuickActionRunnerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val pkg = "org.example.control"
    private val activity = ComponentName(pkg, "$pkg.Panel")
    private val receiver = ComponentName(pkg, "$pkg.Receiver")
    private val activities = mutableListOf<Intent>()
    private val broadcasts = mutableListOf<Intent>()
    private val caller = object : ContextWrapper(context) {
        override fun startActivity(intent: Intent) { activities += intent }
        override fun sendBroadcast(intent: Intent) { broadcasts += intent }
    }

    @Test fun explicitActivityReceivesItsUriAndTypedExtras() {
        shadowOf(context.packageManager).addActivityIfNotPresent(activity).apply {
            exported = true; enabled = true; applicationInfo.enabled = true
        }
        val action = QuickAction(title = "Panel", kind = "activity", pkg = pkg, component = ".Panel",
            action = "$pkg.OPEN", uri = "example://panel/page", extras = "{\"index\":3,\"enabled\":true,\"label\":\"TV\"}")
        assertEquals(QuickActionRunner.Result.Sent, QuickActionRunner.run(caller, action))
        val intent = activities.single()
        assertEquals(activity, intent.component)
        assertEquals("$pkg.OPEN", intent.action)
        assertEquals("example://panel/page", intent.dataString)
        assertEquals(3, intent.getIntExtra("index", -1))
        assertTrue(intent.getBooleanExtra("enabled", false))
        assertEquals("TV", intent.getStringExtra("label"))
        assertFalse(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(broadcasts.isEmpty())
    }

    @Test fun rejectsPrivateOrProtectedTargetsBeforeDispatch() {
        val info = shadowOf(context.packageManager).addActivityIfNotPresent(activity).apply {
            exported = false; enabled = true; applicationInfo.enabled = true
        }
        val action = QuickAction(title = "Panel", kind = "activity", pkg = pkg, component = activity.className)
        assertEquals(QuickActionRunner.Result.Unavailable, QuickActionRunner.run(caller, action))
        info.exported = true
        info.permission = "org.example.permission.PRIVATE"
        assertEquals(QuickActionRunner.Result.Unavailable, QuickActionRunner.run(caller, action))
        assertTrue(activities.isEmpty())
    }

    @Test fun broadcastTargetsOnlyTheNamedExportedReceiver() {
        val appInfo = ApplicationInfo().apply { packageName = pkg; enabled = true }
        val info = ActivityInfo().apply {
            packageName = pkg; name = receiver.className; exported = true; enabled = true; applicationInfo = appInfo
        }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg; applicationInfo = appInfo; receivers = arrayOf(info)
        })
        val action = QuickAction(title = "Signal", kind = "broadcast", pkg = pkg, component = ".Receiver", action = "$pkg.SIGNAL")
        assertEquals(QuickActionRunner.Result.Sent, QuickActionRunner.run(caller, action))
        assertEquals(receiver, broadcasts.single().component)
        assertEquals("$pkg.SIGNAL", broadcasts.single().action)
        assertTrue(activities.isEmpty())
    }
}
