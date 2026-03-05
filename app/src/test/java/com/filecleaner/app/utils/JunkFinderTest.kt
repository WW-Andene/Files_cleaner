package com.filecleaner.app.utils

import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JunkFinderTest {

    private fun item(
        name: String = "test.txt",
        size: Long = 1024,
        path: String = "/storage/emulated/0/$name"
    ) = FileItem(
        path = path,
        name = name,
        size = size,
        lastModified = System.currentTimeMillis(),
        category = FileCategory.fromExtension(name.substringAfterLast('.', ""))
    )

    // ── findLargeFiles tests ──

    @Test
    fun `findLargeFiles returns empty for empty list`() = runBlocking {
        val result = JunkFinder.findLargeFiles(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findLargeFiles returns empty when no files exceed threshold`() = runBlocking {
        val files = listOf(
            item("small.txt", size = 1024),
            item("medium.txt", size = 10 * 1024 * 1024L) // 10 MB
        )
        // Default threshold is 50 MB
        val result = JunkFinder.findLargeFiles(files)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findLargeFiles returns files above threshold`() = runBlocking {
        val files = listOf(
            item("small.txt", size = 1024),
            item("big.mp4", size = 100 * 1024 * 1024L), // 100 MB
            item("huge.zip", size = 500 * 1024 * 1024L)  // 500 MB
        )
        val result = JunkFinder.findLargeFiles(files)
        assertEquals(2, result.size)
    }

    @Test
    fun `findLargeFiles respects custom threshold`() = runBlocking {
        val files = listOf(
            item("a.txt", size = 5 * 1024 * 1024L),  // 5 MB
            item("b.txt", size = 15 * 1024 * 1024L), // 15 MB
            item("c.txt", size = 25 * 1024 * 1024L)  // 25 MB
        )
        val result = JunkFinder.findLargeFiles(files, minSizeBytes = 10 * 1024 * 1024L)
        assertEquals(2, result.size)
    }

    @Test
    fun `findLargeFiles sorted by size descending`() = runBlocking {
        val files = listOf(
            item("medium.bin", size = 100 * 1024 * 1024L),
            item("largest.bin", size = 500 * 1024 * 1024L),
            item("large.bin", size = 200 * 1024 * 1024L)
        )
        val result = JunkFinder.findLargeFiles(files)
        assertEquals("largest.bin", result[0].name)
        assertEquals("large.bin", result[1].name)
        assertEquals("medium.bin", result[2].name)
    }

    @Test
    fun `findLargeFiles respects maxResults`() = runBlocking {
        val files = (1..10).map {
            item("file$it.bin", size = (it * 100L) * 1024 * 1024)
        }
        val result = JunkFinder.findLargeFiles(files, minSizeBytes = 50 * 1024 * 1024L, maxResults = 3)
        assertEquals(3, result.size)
        // Should be the 3 largest
        assertTrue(result[0].size >= result[1].size)
        assertTrue(result[1].size >= result[2].size)
    }

    @Test
    fun `findLargeFiles with exact threshold`() = runBlocking {
        val threshold = 50 * 1024 * 1024L
        val files = listOf(
            item("exact.bin", size = threshold),
            item("below.bin", size = threshold - 1)
        )
        val result = JunkFinder.findLargeFiles(files, minSizeBytes = threshold)
        assertEquals(1, result.size)
        assertEquals("exact.bin", result[0].name)
    }

    @Test
    fun `findLargeFiles preserves file metadata`() = runBlocking {
        val file = item("movie.mp4", size = 200 * 1024 * 1024L)
        val result = JunkFinder.findLargeFiles(listOf(file))
        assertEquals(1, result.size)
        assertEquals(file.path, result[0].path)
        assertEquals(file.name, result[0].name)
        assertEquals(file.size, result[0].size)
        assertEquals(file.category, result[0].category)
    }
}
