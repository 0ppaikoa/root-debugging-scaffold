package app.aoki.example.module

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class RootDebugModule : XposedModule() {

    @Volatile
    private var hookStatus: String = "not installed"

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "hello from module, process " + param.processName)
        hookStatus = NativeBridge.installBaselineHooksOnce()
        log(Log.INFO, TAG, "baseline hooks: " + hookStatus)
        val ping = try {
            NativeBridge.diagnosticPing()
        } catch (t: Throwable) {
            "failed: " + (t.message ?: t.javaClass.simpleName)
        }
        log(Log.INFO, TAG, "diagnostic ping: " + ping)
        installNotificationHook(param.processName)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log(Log.INFO, TAG, "package ready: " + param.packageName)
    }

    /**
     * Hooks Application.onCreate so that, once the process has an
     * application context, the module can post a notification reporting
     * the baseline hook installation. This is also the scaffold's
     * example of a Java method hook.
     */
    private fun installNotificationHook(processName: String) {
        try {
            val onCreate = Application::class.java.getDeclaredMethod("onCreate")
            hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) {
                    notifyHookInstalled(app, processName)
                }
                result
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "notification hook failed: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    private fun notifyHookInstalled(context: Context, processName: String) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Module events",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Root Debug Module")
                .setContentText("Baseline hooks in " + processName + ": " + hookStatus)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "notify failed: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    private companion object {
        const val TAG = "RootDebugModule"
        const val CHANNEL_ID = "module-events"
        const val NOTIFICATION_ID = 1
    }
}
