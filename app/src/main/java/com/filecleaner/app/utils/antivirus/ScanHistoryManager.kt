package com.filecleaner.app.utils.antivirus

import android.content.Context
import android.content.SharedPreferences
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

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveResult(context: Context, results: List<ThreatResult>) {
        val p = prefs(context)
        val now = System.currentTimeMillis()

        // Save last scan time
        p.edit().putLong(KEY_LAST_SCAN, now).apply()

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

        // Prepend new record
        val updated = JSONArray()
        updated.put(record)
        for (i in 0 until history.length().coerceAtMost(MAX_RECORDS - 1)) {
            updated.put(history.getJSONObject(i))
        }

        p.edit().putString(KEY_HISTORY, updated.toString()).apply()
    }

    fun getLastScanTime(context: Context): Long {
        return prefs(context).getLong(KEY_LAST_SCAN, 0L)
    }

    fun getLastScanTimeFormatted(context: Context): String? {
        val ts = getLastScanTime(context)
        if (ts == 0L) return null
        return formatTimestamp(ts)
    }

    fun getHistory(context: Context): List<ScanRecord> {
        val historyJson = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val history = try {
            JSONArray(historyJson)
        } catch (_: Exception) {
            return emptyList()
        }

        val records = mutableListOf<ScanRecord>()
        for (i in 0 until history.length()) {
            try {
                val obj = history.getJSONObject(i)
                records.add(
                    ScanRecord(
                        timestamp = obj.getLong("timestamp"),
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

    private fun formatTimestamp(ts: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - ts

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 172_800_000 -> "Yesterday"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
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
        val formattedTime: String get() = formatTimestamp(timestamp)

        private fun formatTimestamp(ts: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - ts
            return when {
                diff < 60_000 -> "Just now"
                diff < 3_600_000 -> "${diff / 60_000}m ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h ago"
                diff < 172_800_000 -> "Yesterday"
                else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
            }
        }
    }
}
