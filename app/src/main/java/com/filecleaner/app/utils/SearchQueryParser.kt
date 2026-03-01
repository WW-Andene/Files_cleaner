package com.filecleaner.app.utils

import com.filecleaner.app.data.FileItem
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses search queries with operators for advanced file filtering (P10).
 *
 * Supported operators:
 * - `>50mb` or `>100kb` — minimum file size
 * - `<10mb` or `<500kb` — maximum file size
 * - `ext:pdf` or `ext:jpg,png` — filter by extension(s)
 * - `after:2025-01-01` — modified after date
 * - `before:2025-06-01` — modified before date
 * - Plain text — name contains (case-insensitive)
 *
 * Multiple operators can be combined: `>10mb ext:pdf report`
 */
object SearchQueryParser {

    data class ParsedQuery(
        val nameTerms: List<String> = emptyList(),
        val minSizeBytes: Long? = null,
        val maxSizeBytes: Long? = null,
        val extensions: Set<String>? = null,
        val afterMs: Long? = null,
        val beforeMs: Long? = null
    )

    private val SIZE_PATTERN = Regex("""([<>])(\d+(?:\.\d+)?)(kb|mb|gb)""", RegexOption.IGNORE_CASE)
    private val EXT_PATTERN = Regex("""ext:([a-zA-Z0-9,]+)""", RegexOption.IGNORE_CASE)
    private val DATE_PATTERN = Regex("""(after|before):(\d{4}-\d{2}-\d{2})""", RegexOption.IGNORE_CASE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun parse(query: String): ParsedQuery {
        if (query.isBlank()) return ParsedQuery()

        var minSize: Long? = null
        var maxSize: Long? = null
        var extensions: Set<String>? = null
        var afterMs: Long? = null
        var beforeMs: Long? = null

        // Extract and remove operators from query
        var remaining = query

        // Size operators
        SIZE_PATTERN.findAll(query).forEach { match ->
            val op = match.groupValues[1]
            val num = match.groupValues[2].toDoubleOrNull() ?: return@forEach
            val unit = match.groupValues[3].lowercase()
            val bytes = when (unit) {
                "kb" -> (num * 1024).toLong()
                "mb" -> (num * 1024 * 1024).toLong()
                "gb" -> (num * 1024 * 1024 * 1024).toLong()
                else -> return@forEach
            }
            if (op == ">") minSize = bytes else maxSize = bytes
            remaining = remaining.replace(match.value, "")
        }

        // Extension operator
        EXT_PATTERN.find(query)?.let { match ->
            extensions = match.groupValues[1].split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            remaining = remaining.replace(match.value, "")
        }

        // Date operators
        DATE_PATTERN.findAll(query).forEach { match ->
            val op = match.groupValues[1].lowercase()
            val dateStr = match.groupValues[2]
            try {
                val ms = dateFormat.parse(dateStr)?.time ?: return@forEach
                if (op == "after") afterMs = ms else beforeMs = ms + 86_400_000L // end of day
            } catch (_: Exception) { /* ignore bad dates */ }
            remaining = remaining.replace(match.value, "")
        }

        val nameTerms = remaining.trim().split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }

        return ParsedQuery(nameTerms, minSize, maxSize, extensions, afterMs, beforeMs)
    }

    fun matches(item: FileItem, parsed: ParsedQuery): Boolean {
        // Name terms — all must match
        if (parsed.nameTerms.isNotEmpty()) {
            val lowerName = item.name.lowercase()
            if (!parsed.nameTerms.all { lowerName.contains(it) }) return false
        }

        // Size range
        if (parsed.minSizeBytes != null && item.size < parsed.minSizeBytes) return false
        if (parsed.maxSizeBytes != null && item.size > parsed.maxSizeBytes) return false

        // Extension filter
        if (parsed.extensions != null && item.extension !in parsed.extensions) return false

        // Date range
        if (parsed.afterMs != null && item.lastModified < parsed.afterMs) return false
        if (parsed.beforeMs != null && item.lastModified > parsed.beforeMs) return false

        return true
    }

    /** Returns true if the query contains any operators (not just plain text). */
    fun hasOperators(query: String): Boolean {
        return SIZE_PATTERN.containsMatchIn(query) ||
                EXT_PATTERN.containsMatchIn(query) ||
                DATE_PATTERN.containsMatchIn(query)
    }
}
