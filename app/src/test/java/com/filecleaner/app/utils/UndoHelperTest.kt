package com.filecleaner.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoHelperTest {

    @Test
    fun `formatBytes handles zero`() {
        assertEquals("0 B", UndoHelper.formatBytes(0))
    }

    @Test
    fun `formatBytes handles bytes`() {
        assertEquals("512 B", UndoHelper.formatBytes(512))
    }

    @Test
    fun `formatBytes handles kilobytes`() {
        assertEquals("10.0 KB", UndoHelper.formatBytes(10 * 1024L))
    }

    @Test
    fun `formatBytes handles megabytes`() {
        assertEquals("5.0 MB", UndoHelper.formatBytes(5 * 1024 * 1024L))
    }

    @Test
    fun `formatBytes handles gigabytes`() {
        assertEquals("2.0 GB", UndoHelper.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `formatBytes handles fractional megabytes`() {
        assertEquals("1.5 MB", UndoHelper.formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatBytes handles 1 byte`() {
        assertEquals("1 B", UndoHelper.formatBytes(1))
    }

    @Test
    fun `formatBytes handles exactly 1 KB`() {
        assertEquals("1.0 KB", UndoHelper.formatBytes(1024))
    }

    @Test
    fun `formatBytes handles exactly 1 MB`() {
        assertEquals("1.0 MB", UndoHelper.formatBytes(1024 * 1024L))
    }

    @Test
    fun `formatBytes handles exactly 1 GB`() {
        assertEquals("1.0 GB", UndoHelper.formatBytes(1024L * 1024 * 1024))
    }
}
