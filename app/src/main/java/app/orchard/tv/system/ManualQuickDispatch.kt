package app.orchard.tv.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.orchard.tv.settings.ManualQuickTarget
import app.orchard.tv.settings.ManualQuickTarget.Type
import app.orchard.tv.system.QuickActionRunner.Result

internal object ManualQuickDispatch {
    fun send(context: Context, target: ManualQuickTarget): Result = try {
        val component = ComponentName(target.pkg, target.component)
        val pm = context.packageManager
        val receiver = target.type == Type.Broadcast
        val info = if (receiver) pm.getReceiverInfo(component, 0) else pm.getActivityInfo(component, 0)
        // A manually configured control can call only public entry points, even if Aerial
        // itself holds extra privileges on a particular TV.
        if (!info.exported || !info.enabled || !info.applicationInfo.enabled || !info.permission.isNullOrEmpty()) {
            Result.Unavailable
        } else {
            val intent = Intent().setComponent(component).apply {
                if (target.action.isNotEmpty()) action = target.action
                if (target.uri.isNotEmpty()) data = Uri.parse(target.uri)
                for ((key, value) in target.extras) when (value) {
                    is String -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Double -> putExtra(key, value)
                }
            }
            if (receiver) context.sendBroadcast(intent)
            else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.Sent
        }
    } catch (_: PackageManager.NameNotFoundException) {
        Result.Unavailable
    } catch (_: ActivityNotFoundException) {
        Result.Unavailable
    } catch (_: SecurityException) {
        Result.Unavailable
    } catch (_: RuntimeException) {
        Result.Failed
    }
}
