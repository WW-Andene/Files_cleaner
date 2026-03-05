package com.filecleaner.app.utils

import android.content.Context
import android.os.Build
import com.filecleaner.app.BuildConfig
import com.filecleaner.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporter that persists crash logs to disk and creates GitHub Issues
 * on the next app launch.
 *
 * Flow:
 * 1. [install] sets a global UncaughtExceptionHandler
 * 2. On crash: writes crash info to a file in internal storage (synchronous, before process dies)
 * 3. On next launch: [uploadPendingCrashReports] checks for pending files and POSTs them
 *    as GitHub Issues via the GitHub REST API
 */
object CrashReporter {

    private const val CRASH_DIR = "crash_reports"
    private const val MAX_STACK_TRACE_LENGTH = 8000 // GitHub issue body limit is ~65k, keep it reasonable

    private lateinit var crashDir: File
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Installs the crash handler. Call once from Application.onCreate().
     */
    fun install(context: Context) {
        crashDir = File(context.filesDir, CRASH_DIR)
        crashDir.mkdirs()

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashFile(thread, throwable)
            } catch (_: Exception) {
                // Writing failed — don't make things worse
            }
            // Chain to the default handler (which kills the process)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Checks for pending crash reports and uploads them as GitHub Issues.
     * Call from Application.onCreate() or MainActivity.onCreate() after init.
     */
    fun uploadPendingCrashReports() {
        if (!UserPreferences.crashReportingEnabled) return
        val token = UserPreferences.crashReportGithubToken
        val repo = UserPreferences.crashReportRepo
        if (token.isBlank() || repo.isBlank()) return

        scope.launch {
            val files = crashDir.listFiles { f -> f.extension == "txt" } ?: return@launch
            for (file in files) {
                try {
                    val content = file.readText()
                    val title = extractTitle(content)
                    if (createGitHubIssue(token, repo, title, content)) {
                        file.delete()
                    }
                } catch (_: Exception) {
                    // Will retry on next launch
                }
            }
        }
    }

    /**
     * Returns the number of pending (unsent) crash reports.
     */
    fun pendingReportCount(): Int {
        if (!::crashDir.isInitialized) return 0
        return crashDir.listFiles { f -> f.extension == "txt" }?.size ?: 0
    }

    /**
     * Deletes all pending crash reports without uploading.
     */
    fun clearPendingReports() {
        if (!::crashDir.isInitialized) return
        crashDir.listFiles { f -> f.extension == "txt" }?.forEach { it.delete() }
    }

    // ── Crash file writing (synchronous, runs during crash) ──

    private fun writeCrashFile(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(crashDir, "crash_$timestamp.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        var stackTrace = sw.toString()
        if (stackTrace.length > MAX_STACK_TRACE_LENGTH) {
            stackTrace = stackTrace.take(MAX_STACK_TRACE_LENGTH) + "\n... (truncated)"
        }

        val report = buildString {
            appendLine("## Crash Report")
            appendLine()
            appendLine("**App Version:** ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("**Device:** ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("**Android:** ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("**Time:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("**Thread:** ${thread.name}")
            appendLine()
            appendLine("### Exception")
            appendLine("```")
            appendLine("${throwable.javaClass.name}: ${throwable.message}")
            appendLine("```")
            appendLine()
            appendLine("### Stack Trace")
            appendLine("```java")
            appendLine(stackTrace)
            appendLine("```")
        }

        // Write synchronously — process is about to die
        file.writeText(report)
    }

    // ── GitHub API ──

    private fun extractTitle(content: String): String {
        // Extract the exception line for the issue title
        val exceptionLine = content.lineSequence()
            .firstOrNull { it.startsWith("```") && it.length == 3 }
            ?.let { null } // skip the ``` markers
        // Find the line after "### Exception" and "```"
        val lines = content.lines()
        val exIdx = lines.indexOfFirst { it.startsWith("```") && it.length > 3 }
        val raw = if (exIdx >= 0) lines.getOrNull(exIdx + 1)?.removePrefix("```")?.trim()
        else null

        // Find exception class:message from the crash report
        val exLine = lines.firstOrNull { it.contains("Exception:") || it.contains("Error:") }
            ?.removePrefix("```")?.trim()

        val title = (exLine ?: raw ?: "Unknown crash").take(120)
        return "Crash: $title"
    }

    private fun createGitHubIssue(token: String, repo: String, title: String, body: String): Boolean {
        val url = URL("https://api.github.com/repos/$repo/issues")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "token $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("title", title)
                put("body", body)
                put("labels", org.json.JSONArray().put("crash-report"))
            }

            conn.outputStream.bufferedWriter().use { it.write(json.toString()) }

            val code = conn.responseCode
            return code in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
