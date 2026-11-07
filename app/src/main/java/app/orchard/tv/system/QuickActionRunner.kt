package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.orchard.tv.R
import app.orchard.tv.settings.QuickAction
import org.json.JSONObject

/** Only explicit, exported app entry points; no shell, service starts, URI grants or user flags. */
object QuickActionRunner {
    enum class Result(val message: Int) {
        Sent(R.string.qc_sent), Unavailable(R.string.qc_unavailable), Invalid(R.string.qc_invalid), Failed(R.string.qc_failed),
    }

    fun run(context: Context, control: QuickAction): Result {
        if (!control.valid() || !control.enabled) return Result.Invalid
        if (control.kind == "clash") return when (ClashShortcut.toggle(context)) {
            ClashShortcut.Result.Sent -> Result.Sent
            ClashShortcut.Result.Unavailable -> Result.Unavailable
            ClashShortcut.Result.Failed -> Result.Failed
        }
        if (control.kind == "system") return runCatching {
            when (control.action) {
                "mute" -> SystemProbe.toggleMute(context)
                "network" -> {
                    val pages = SystemProbe.networkSettingsIntents(context, SystemProbe.network(context).kind == NetKind.Ethernet)
                    LaunchFallback.arm(pages.drop(1))
                    SystemProbe.startSettingsIntent(context, pages.first())
                }
                "bluetooth" -> context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "settings" -> {
                    LaunchFallback.arm(SystemProbe.settingsFallbacks(context))
                    SystemProbe.startSettingsIntent(context, SystemProbe.settingsIntent(context))
                }
                "inputs" -> if (!SystemProbe.openInputPicker(context)) return Result.Unavailable
            }
            Result.Sent
        }.getOrDefault(Result.Failed)
        return try {
            val pm = context.packageManager
            val intent = if (control.kind == "app") {
                pm.getLeanbackLaunchIntentForPackage(control.pkg) ?: pm.getLaunchIntentForPackage(control.pkg)
                    ?: return Result.Unavailable
            } else {
                val name = if (control.component.startsWith('.')) control.pkg + control.component else control.component
                Intent().setComponent(ComponentName(control.pkg, name)).apply {
                    if (control.action.isNotBlank()) action = control.action
                    if (control.uri.isNotBlank()) data = Uri.parse(control.uri)
                    val extras = JSONObject(control.extras)
                    for (key in extras.keys()) {
                        when (val v = extras.get(key)) {
                            is String -> putExtra(key, v)
                            is Boolean -> putExtra(key, v)
                            is Int -> putExtra(key, v)
                            is Long -> putExtra(key, v)
                            is Number -> putExtra(key, v.toDouble())
                        }
                    }
                }
            }
            val target = intent.component ?: return Result.Unavailable
            @Suppress("DEPRECATION")
            val info = if (control.kind == "broadcast") pm.getReceiverInfo(target, 0) else pm.getActivityInfo(target, 0)
            if (!info.exported || !info.enabled || !info.applicationInfo.enabled ||
                (!info.permission.isNullOrBlank() && pm.checkPermission(info.permission, context.packageName) != PackageManager.PERMISSION_GRANTED)
            ) return Result.Unavailable
            if (control.kind == "broadcast") context.sendBroadcast(intent)
            else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Result.Sent
        } catch (_: PackageManager.NameNotFoundException) {
            Result.Unavailable
        } catch (_: android.content.ActivityNotFoundException) {
            Result.Unavailable
        } catch (_: RuntimeException) {
            Result.Failed
        }
    }
}
