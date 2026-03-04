package com.filecleaner.app.utils

import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryParserTest {

    private fun item(
        name: String = "test.txt",
        size: Long = 1024,
        lastModified: Long = System.currentTimeMillis()
    ) = FileItem(
        path = "/storage/emulated/0/$name",
        name = name,
        size = size,
        lastModified = lastModified,
        category = FileCategory.DOCUMENT
    )

    @Test
    fun `parse empty query`() {
        val result = SearchQueryParser.parse("")
        assertTrue(result.nameTerms.isEmpty())
        assertNull(result.minSizeBytes)
        assertNull(result.maxSizeBytes)
        assertNull(result.extensions)
    }

    @Test
    fun `parse plain text query`() {
        val result = SearchQueryParser.parse("report final")
        assertEquals(listOf("report", "final"), result.nameTerms)
    }

    @Test
    fun `parse size operator greater than`() {
        val result = SearchQueryParser.parse(">50mb")
        assertEquals(50 * 1024 * 1024L, result.minSizeBytes)
        assertNull(result.maxSizeBytes)
    }

    @Test
    fun `parse size operator less than`() {
        val result = SearchQueryParser.parse("<10kb")
        assertEquals(10 * 1024L, result.maxSizeBytes)
        assertNull(result.minSizeBytes)
    }

    @Test
    fun `parse size operator with gb`() {
        val result = SearchQueryParser.parse(">1gb")
        assertEquals(1L * 1024 * 1024 * 1024, result.minSizeBytes)
    }

    @Test
    fun `parse extension operator single`() {
        val result = SearchQueryParser.parse("ext:pdf")
        assertEquals(setOf("pdf"), result.extensions)
    }

    @Test
    fun `parse extension operator multiple`() {
        val result = SearchQueryParser.parse("ext:pdf,jpg,png")
        assertEquals(setOf("pdf", "jpg", "png"), result.extensions)
    }

    @Test
    fun `parse date operators`() {
        val result = SearchQueryParser.parse("after:2025-01-01 before:2025-06-01")
        assertTrue(result.afterMs != null)
        assertTrue(result.beforeMs != null)
    }

    @Test
    fun `parse combined operators`() {
        val result = SearchQueryParser.parse(">10mb ext:pdf report")
        assertEquals(10 * 1024 * 1024L, result.minSizeBytes)
        assertEquals(setOf("pdf"), result.extensions)
        assertTrue(result.nameTerms.contains("report"))
    }

    @Test
    fun `matches size filter`() {
        val parsed = SearchQueryParser.parse(">1mb")
        val smallFile = item(size = 500 * 1024L) // 500 KB
        val largeFile = item(size = 2 * 1024 * 1024L) // 2 MB

        assertFalse(SearchQueryParser.matches(smallFile, parsed))
        assertTrue(SearchQueryParser.matches(largeFile, parsed))
    }

    @Test
    fun `matches extension filter`() {
        val parsed = SearchQueryParser.parse("ext:pdf")
        val pdfFile = item(name = "report.pdf")
        val txtFile = item(name = "notes.txt")

        assertTrue(SearchQueryParser.matches(pdfFile, parsed))
        assertFalse(SearchQueryParser.matches(txtFile, parsed))
    }

    @Test
    fun `matches name terms all must match`() {
        val parsed = SearchQueryParser.parse("final report")
        val matching = item(name = "final_report_2025.pdf")
        val partial = item(name = "final_notes.txt")

        assertTrue(SearchQueryParser.matches(matching, parsed))
        assertFalse(SearchQueryParser.matches(partial, parsed))
    }

    @Test
    fun `hasOperators detects operators`() {
        assertTrue(SearchQueryParser.hasOperators(">50mb"))
        assertTrue(SearchQueryParser.hasOperators("ext:pdf"))
        assertTrue(SearchQueryParser.hasOperators("after:2025-01-01"))
        assertFalse(SearchQueryParser.hasOperators("plain text search"))
        assertFalse(SearchQueryParser.hasOperators(""))
    }
}
