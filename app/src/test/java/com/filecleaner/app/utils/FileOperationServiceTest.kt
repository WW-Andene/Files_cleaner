package com.filecleaner.app.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tests for FileOperationService validation logic.
 * Since the class requires an Application context, we mirror the validation
 * logic here to verify correctness of the rules.
 */
class FileOperationServiceTest {

    // Mirror the invalid chars from FileOperationService companion
    private val INVALID_FILENAME_CHARS = charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|')

    /** Mirror of FileOperationService.hasInvalidFilenameChars */
    private fun hasInvalidFilenameChars(name: String): Boolean {
        return INVALID_FILENAME_CHARS.any { it in name } || name.isBlank() || name.trim() != name
    }

    /** Mirror of FileOperationService.isPathWithinStorage */
    private fun isPathWithinStorage(path: String, storagePath: String): Boolean {
        val canonical = File(path).canonicalPath
        return canonical.startsWith(storagePath)
    }

    // ── Filename validation tests ──

    @Test
    fun `valid filename accepted`() {
        assertFalse(hasInvalidFilenameChars("document.pdf"))
    }

    @Test
    fun `filename with spaces accepted`() {
        assertFalse(hasInvalidFilenameChars("my document.pdf"))
    }

    @Test
    fun `filename with dash and underscore accepted`() {
        assertFalse(hasInvalidFilenameChars("my-file_v2.txt"))
    }

    @Test
    fun `filename with parentheses accepted`() {
        assertFalse(hasInvalidFilenameChars("photo (1).jpg"))
    }

    @Test
    fun `filename with unicode accepted`() {
        assertFalse(hasInvalidFilenameChars("文档.pdf"))
    }

    @Test
    fun `filename with slash rejected`() {
        assertTrue(hasInvalidFilenameChars("path/file.txt"))
    }

    @Test
    fun `filename with null char rejected`() {
        assertTrue(hasInvalidFilenameChars("file\u0000.txt"))
    }

    @Test
    fun `filename with colon rejected`() {
        assertTrue(hasInvalidFilenameChars("file:name.txt"))
    }

    @Test
    fun `filename with asterisk rejected`() {
        assertTrue(hasInvalidFilenameChars("file*.txt"))
    }

    @Test
    fun `filename with question mark rejected`() {
        assertTrue(hasInvalidFilenameChars("file?.txt"))
    }

    @Test
    fun `filename with double quote rejected`() {
        assertTrue(hasInvalidFilenameChars("file\"name.txt"))
    }

    @Test
    fun `filename with angle brackets rejected`() {
        assertTrue(hasInvalidFilenameChars("file<name>.txt"))
    }

    @Test
    fun `filename with pipe rejected`() {
        assertTrue(hasInvalidFilenameChars("file|name.txt"))
    }

    @Test
    fun `blank filename rejected`() {
        assertTrue(hasInvalidFilenameChars(""))
        assertTrue(hasInvalidFilenameChars("   "))
    }

    @Test
    fun `filename with leading space rejected`() {
        assertTrue(hasInvalidFilenameChars(" file.txt"))
    }

    @Test
    fun `filename with trailing space rejected`() {
        assertTrue(hasInvalidFilenameChars("file.txt "))
    }

    // ── Path validation tests ──

    @Test
    fun `path within storage accepted`() {
        val storagePath = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val testPath = "$storagePath/test/file.txt"
        assertTrue(isPathWithinStorage(testPath, storagePath))
    }

    @Test
    fun `path outside storage rejected`() {
        val storagePath = "/storage/emulated/0"
        assertFalse(isPathWithinStorage("/etc/passwd", storagePath))
    }

    @Test
    fun `path traversal rejected`() {
        val storagePath = "/storage/emulated/0"
        // Canonical path resolves ".." so this should be caught
        assertFalse(isPathWithinStorage("/storage/emulated/0/../../../etc/passwd", storagePath))
    }
}
