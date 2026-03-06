package com.filecleaner.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * F-081: Centralized user-facing date formatting.
 * Standardizes on two formats across the app:
 * - Date only: "dd MMM yyyy" (e.g., "15 Jan 2025")
 * - Date + time: "dd MMM yyyy HH:mm" (e.g., "15 Jan 2025 14:30")
 *
 * Internal/API formats (ISO 8601, RFC 2822, batch rename tokens) are NOT
 * handled here — those remain at their call sites with fixed Locale.US.
 */
object DateFormatUtils {

    // ThreadLocal for thread safety (SimpleDateFormat is not thread-safe)
    private val dateOnly: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }
    private val dateTime: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
    }

    /** Format a timestamp as date only: "15 Jan 2025" */
    fun formatDate(timestampMs: Long): String = dateOnly.get()!!.format(Date(timestampMs))

    /** Format a timestamp as date + time: "15 Jan 2025 14:30" */
    fun formatDateTime(timestampMs: Long): String = dateTime.get()!!.format(Date(timestampMs))
}
