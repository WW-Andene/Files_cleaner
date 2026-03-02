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
    private const val MILLIS_PER_DAY = 86_400_000L
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
                if (op == "after") afterMs = ms else beforeMs = ms + MILLIS_PER_DAY // end of day
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

    /**
     * Applies search query filtering to a list of files.
     * Supports both plain text search and operator-based advanced search.
     * I2: Extracted from BrowseFragment and BaseFileListFragment to eliminate duplication.
     */
    fun filterItems(items: List<FileItem>, query: String): List<FileItem> {
        if (query.isEmpty()) return items
        return if (hasOperators(query)) {
            val parsed = parse(query)
            items.filter { matches(it, parsed) }
        } else {
            val lowerQuery = query.lowercase()
            items.filter { it.name.lowercase().contains(lowerQuery) }
        }
    }

    /**
     * Sorts a list of files based on a spinner position index.
     * 0=name asc, 1=name desc, 2=size asc, 3=size desc, 4=date asc, 5=date desc.
     * I2: Extracted from BrowseFragment and BaseFileListFragment to eliminate duplication.
     */
    fun sortItems(items: List<FileItem>, sortIndex: Int): List<FileItem> = when (sortIndex) {
        0 -> items.sortedBy { it.name.lowercase() }
        1 -> items.sortedByDescending { it.name.lowercase() }
        2 -> items.sortedBy { it.size }
        3 -> items.sortedByDescending { it.size }
        4 -> items.sortedBy { it.lastModified }
        5 -> items.sortedByDescending { it.lastModified }
        else -> items
    }
}
