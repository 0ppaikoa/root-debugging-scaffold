package app.aoki.example.module

object NativeBridge {

    @Volatile
    private var loadAttempted = false

    @Volatile
    var loadStatus: String = "not attempted"
        private set

    @Volatile
    var hooksInstallStatus: String = "not installed"
        private set

    fun load() {
        if (loadAttempted) return
        loadAttempted = true
        loadStatus = try {
            System.loadLibrary("rootdebug")
            "loaded"
        } catch (t: Throwable) {
            "failed: " + (t.message ?: t.javaClass.simpleName)
        }
    }

    @Synchronized
    fun installBaselineHooksOnce(): String {
        if (hooksInstallStatus != "not installed") return hooksInstallStatus
        load()
        if (loadStatus != "loaded") {
            hooksInstallStatus = "library not loaded"
            return hooksInstallStatus
        }
        hooksInstallStatus = try {
            installBaselineHooks()
        } catch (t: Throwable) {
            "failed: " + (t.message ?: t.javaClass.simpleName)
        }
        return hooksInstallStatus
    }

    fun hookCounts(): LongArray {
        load()
        if (loadStatus != "loaded") return longArrayOf(0, 0)
        return try {
            baselineHookCounts()
        } catch (t: Throwable) {
            longArrayOf(0, 0)
        }
    }

    @JvmStatic
    external fun diagnosticPing(): String

    @JvmStatic
    external fun installBaselineHooks(): String

    @JvmStatic
    external fun baselineHookCounts(): LongArray
}
