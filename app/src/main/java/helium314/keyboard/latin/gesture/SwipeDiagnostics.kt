// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent log for swipe typing. It is written to a file (so it survives the keyboard process dying) and can be
 * shown or shared from Settings > Gesture typing, together with Android's record of why the process last exited.
 * Only events worth reading are logged: engine loading, failures, slow decodes and finished swipes.
 */
object SwipeDiagnostics {
    private const val TAG = "SwipeDiagnostics"
    private const val MAX_BYTES = 200_000L
    private var file: File? = null
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "swipe_log.txt")
    }

    @Synchronized
    fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d("LafzSwipe", message)
        val f = file ?: return
        try {
            if (f.length() > MAX_BYTES) {
                val old = File(f.path + ".1")
                old.delete()
                f.renameTo(old)
            }
            f.appendText("${format.format(Date())} [${Thread.currentThread().name}] $message\n")
        } catch (_: Exception) { }
    }

    fun logError(message: String, t: Throwable) = log("$message: ${t.stackTraceToString().take(1500)}")

    /** Why the process of this app last died (crash, native crash, low memory, ANR ...), newest first. API 30+. */
    fun exitReasons(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "(process exit reasons need Android 11 or newer)"
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            if (exits.isEmpty()) return "(no recorded process exits)"
            exits.joinToString("\n") { e ->
                val trace = if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
                    runCatching { e.traceInputStream?.bufferedReader()?.use { it.readText().take(1200) } }.getOrNull() else null
                "${format.format(Date(e.timestamp))} reason=${reasonName(e.reason)} importance=${e.importance} " +
                    "rss=${e.rss / 1024}MB pss=${e.pss / 1024}MB desc=${e.description}" + (trace?.let { "\n  trace: $it" } ?: "")
            }
        } catch (e: Exception) {
            "(could not read process exits: $e)"
        }
    }

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> reason.toString()
    }

    /** Everything needed to understand a swipe problem from a phone, as plain text. */
    @Synchronized
    fun report(context: Context, status: String): String {
        val runtime = Runtime.getRuntime()
        val log = file?.takeIf { it.isFile }?.readText()?.takeLast(60_000) ?: "(empty)"
        return buildString {
            appendLine("Lafz ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${Build.MANUFACTURER} ${Build.MODEL} Android ${Build.VERSION.RELEASE} abi=${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("heap used=${(runtime.totalMemory() - runtime.freeMemory()) / 1048576}MB max=${runtime.maxMemory() / 1048576}MB native=${android.os.Debug.getNativeHeapAllocatedSize() / 1048576}MB")
            appendLine(status)
            appendLine()
            appendLine("== Process exits ==")
            appendLine(exitReasons(context))
            appendLine()
            appendLine("== Swipe log ==")
            append(log)
        }
    }

    @Synchronized
    fun clear() {
        file?.delete()
        file?.let { File(it.path + ".1").delete() }
    }
}
