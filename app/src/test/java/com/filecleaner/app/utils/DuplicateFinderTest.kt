package com.filecleaner.app.utils

import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DuplicateFinderTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun createTempFile(name: String, content: String): FileItem {
        val file = tempDir.newFile(name)
        file.writeText(content)
        return FileItem(
            path = file.absolutePath,
            name = name,
            size = file.length(),
            lastModified = file.lastModified(),
            category = FileCategory.DOCUMENT
        )
    }

    private fun createLargeTempFile(name: String, content: ByteArray): FileItem {
        val file = tempDir.newFile(name)
        file.writeBytes(content)
        return FileItem(
            path = file.absolutePath,
            name = name,
            size = file.length(),
            lastModified = file.lastModified(),
            category = FileCategory.DOCUMENT
        )
    }

    @Test
    fun `empty file list returns empty`() = runBlocking {
        val result = DuplicateFinder.findDuplicates(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single file returns empty`() = runBlocking {
        val item = createTempFile("a.txt", "hello world")
        val result = DuplicateFinder.findDuplicates(listOf(item))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unique files return empty`() = runBlocking {
        val a = createTempFile("a.txt", "content A")
        val b = createTempFile("b.txt", "content B different length")
        val result = DuplicateFinder.findDuplicates(listOf(a, b))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `identical files detected as duplicates`() = runBlocking {
        val a = createTempFile("a.txt", "identical content here")
        val b = createTempFile("b.txt", "identical content here")
        val result = DuplicateFinder.findDuplicates(listOf(a, b))
        assertEquals(2, result.size)
        assertTrue(result.all { it.duplicateGroup >= 0 })
        assertEquals(result[0].duplicateGroup, result[1].duplicateGroup)
    }

    @Test
    fun `same size different content not duplicates`() = runBlocking {
        // Same length strings, different content
        val a = createTempFile("a.txt", "AAAAAAAAAA")
        val b = createTempFile("b.txt", "BBBBBBBBBB")
        val result = DuplicateFinder.findDuplicates(listOf(a, b))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `zero size files excluded`() = runBlocking {
        val a = createTempFile("a.txt", "")
        val b = createTempFile("b.txt", "")
        val result = DuplicateFinder.findDuplicates(listOf(a, b))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `multiple duplicate groups`() = runBlocking {
        val a1 = createTempFile("a1.txt", "group A content")
        val a2 = createTempFile("a2.txt", "group A content")
        val b1 = createTempFile("b1.txt", "group B different")
        val b2 = createTempFile("b2.txt", "group B different")
        val result = DuplicateFinder.findDuplicates(listOf(a1, a2, b1, b2))
        assertEquals(4, result.size)
        val groups = result.groupBy { it.duplicateGroup }
        assertEquals(2, groups.size)
    }

    @Test
    fun `three identical files in same group`() = runBlocking {
        val a = createTempFile("a.txt", "triple content")
        val b = createTempFile("b.txt", "triple content")
        val c = createTempFile("c.txt", "triple content")
        val result = DuplicateFinder.findDuplicates(listOf(a, b, c))
        assertEquals(3, result.size)
        assertTrue(result.map { it.duplicateGroup }.distinct().size == 1)
    }

    @Test
    fun `large files use partial hash optimization`() = runBlocking {
        // Create files > 8KB (PARTIAL_HASH_BYTES * 2) to trigger partial hash path
        val content = ByteArray(16384) { (it % 256).toByte() }
        val a = createLargeTempFile("large1.bin", content)
        val b = createLargeTempFile("large2.bin", content)
        val result = DuplicateFinder.findDuplicates(listOf(a, b))
        assertEquals(2, result.size)
    }

    @Test
    fun `progress callback invoked`() = runBlocking {
        val a = createTempFile("a.txt", "same same same!")
        val b = createTempFile("b.txt", "same same same!")
        var callbackCalled = false
        DuplicateFinder.findDuplicates(listOf(a, b)) { _, _ -> callbackCalled = true }
        assertTrue(callbackCalled)
    }

    @Test
    fun `results sorted by group then name`() = runBlocking {
        val z = createTempFile("z.txt", "same content!")
        val a = createTempFile("a.txt", "same content!")
        val m = createTempFile("m.txt", "same content!")
        val result = DuplicateFinder.findDuplicates(listOf(z, a, m))
        assertEquals("a.txt", result[0].name)
        assertEquals("m.txt", result[1].name)
        assertEquals("z.txt", result[2].name)
    }
}
