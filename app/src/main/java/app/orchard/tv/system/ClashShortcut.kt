package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Only controls an already installed client. No SDK, shell commands or network access. */
internal object ClashShortcut {
    // The Alpha build uses a different package but the same documented action namespace.
    private val PACKAGES = listOf("com.github.metacubex.clash.meta", "com.github.metacubex.clash.alpha")
    private const val ACTIVITY = "com.github.kr328.clash.ExternalControlActivity"
    private const val TOGGLE = "com.github.metacubex.clash.meta.action.TOGGLE_CLASH"

    enum class Result { Sent, Unavailable, Failed }

    // The client owns both its running state and VPN permission. A system VPN badge may belong
    // to another app, so it must never determine whether we send START or STOP here.
    fun toggle(context: Context): Result {
        val intent = PACKAGES.asSequence().map { pkg ->
            Intent(TOGGLE).setComponent(ComponentName(pkg, ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.firstOrNull { candidate ->
            val target = context.packageManager.resolveActivity(candidate, 0)?.activityInfo
            target != null && target.exported && target.enabled && target.applicationInfo.enabled
        } ?: return Result.Unavailable
        return try {
            // VpnService.prepare(context) checks the caller, not the remote client. Initial VPN
            // approval must happen inside Clash; dispatch alone does not prove it connected.
            context.startActivity(intent)
            Result.Sent
        } catch (_: android.content.ActivityNotFoundException) {
            Result.Unavailable
        } catch (_: SecurityException) {
            Result.Failed
        }
    }
}
