package com.filecleaner.app.utils.antivirus

import android.content.Context
import android.content.SharedPreferences
import com.filecleaner.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages scan history persistence.
 * Stores scan summaries (not full results) in SharedPreferences.
 * Keeps the last 20 scan records.
 */
object ScanHistoryManager {

    private const val PREFS_NAME = "av_scan_history"
    private const val KEY_HISTORY = "scan_records"
    private const val KEY_LAST_SCAN = "last_scan_time"
    private const val MAX_RECORDS = 20
    // F-021: Time-based expiry matching ScanCache (30 days)
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveResult(context: Context, results: List<ThreatResult>) {
        val p = prefs(context)
        val now = System.currentTimeMillis()

        // Build scan record
        val record = JSONObject().apply {
            put("timestamp", now)
            put("total", results.size)
            put("critical", results.count { it.severity == ThreatResult.Severity.CRITICAL })
            put("high", results.count { it.severity == ThreatResult.Severity.HIGH })
            put("medium", results.count { it.severity == ThreatResult.Severity.MEDIUM })
            put("low", results.count { it.severity == ThreatResult.Severity.LOW })
            put("info", results.count { it.severity == ThreatResult.Severity.INFO })
            put("threats", results.count { it.severity >= ThreatResult.Severity.MEDIUM })

            // Breakdown by source
            val bySource = JSONObject()
            for (source in ThreatResult.ScannerSource.entries) {
                bySource.put(source.name, results.count { it.source == source })
            }
            put("by_source", bySource)

            // Top threats (names only, up to 5)
            val topThreats = JSONArray()
            results.filter { it.severity >= ThreatResult.Severity.MEDIUM }
                .sortedByDescending { it.severity.ordinal }
                .take(5)
                .forEach { topThreats.put(it.name) }
            put("top_threats", topThreats)
        }

        // Load existing history
        val historyJson = p.getString(KEY_HISTORY, "[]") ?: "[]"
        val history = try {
            JSONArray(historyJson)
        } catch (_: Exception) {
            JSONArray()
        }

        // Prepend new record, pruning expired entries (F-021)
        val updated = JSONArray()
        updated.put(record)
        for (i in 0 until history.length().coerceAtMost(MAX_RECORDS - 1)) {
            val entry = history.getJSONObject(i)
            val age = now - entry.optLong("timestamp", 0L)
            if (age <= MAX_AGE_MS) {
                updated.put(entry)
            }
        }

        // Atomic write: save both last scan time and history in a single edit
        p.edit()
            .putLong(KEY_LAST_SCAN, now)
            .putString(KEY_HISTORY, updated.toString())
            .apply()
    }

    fun getLastScanTime(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SCAN, 0L)
    }

    fun getLastScanTimeFormatted(context: Context): String? {
        val ts = getLastScanTime(context)
        if (ts == 0L) return null
        return formatRelativeTime(context, ts)
    }

    fun getHistory(context: Context): List<ScanRecord> {
        val historyJson = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val history = try {
            JSONArray(historyJson)
        } catch (_: Exception) {
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val records = mutableListOf<ScanRecord>()
        for (i in 0 until history.length()) {
            try {
                val obj = history.getJSONObject(i)
                val timestamp = obj.getLong("timestamp")
                // F-021: Skip records older than 30 days
                if (now - timestamp > MAX_AGE_MS) continue
                records.add(
                    ScanRecord(
                        timestamp = timestamp,
                        totalFindings = obj.getInt("total"),
                        threatCount = obj.getInt("threats"),
                        critical = obj.getInt("critical"),
                        high = obj.getInt("high"),
                        medium = obj.getInt("medium"),
                        low = obj.getInt("low"),
                        info = obj.getInt("info")
                    )
                )
            } catch (_: Exception) {
                // Skip malformed records
            }
        }

        return records
    }

    fun getLastScanRecord(context: Context): ScanRecord? {
        return getHistory(context).firstOrNull()
    }

    fun clearHistory(context: Context) {
        prefs(context).edit()
            .remove(KEY_HISTORY)
            .remove(KEY_LAST_SCAN)
            .apply()
    }

    internal fun formatRelativeTime(context: Context, timestampMs: Long): String {
        val diff = System.currentTimeMillis() - timestampMs

        return when {
            // F-081: Use centralized date formatting
            diff < 0 -> com.filecleaner.app.utils.DateFormatUtils.formatDateTime(timestampMs)
            diff < 60_000 -> context.getString(R.string.time_just_now)
            diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, (diff / 60_000).toInt())
            diff < 86_400_000 -> context.getString(R.string.time_hours_ago, (diff / 3_600_000).toInt())
            diff < 172_800_000 -> context.getString(R.string.time_yesterday)
            // F-081: Use centralized date formatting
            else -> com.filecleaner.app.utils.DateFormatUtils.formatDate(timestampMs)
        }
    }

    data class ScanRecord(
        val timestamp: Long,
        val totalFindings: Int,
        val threatCount: Int,
        val critical: Int,
        val high: Int,
        val medium: Int,
        val low: Int,
        val info: Int
    ) {
        fun formattedTime(context: Context): String = ScanHistoryManager.formatRelativeTime(context, timestamp)
    }
}
