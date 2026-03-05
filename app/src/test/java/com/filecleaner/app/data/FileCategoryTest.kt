package com.filecleaner.app.data

import org.junit.Assert.*
import org.junit.Test

class FileCategoryTest {

    // ── fromExtension tests ──

    @Test
    fun `fromExtension maps jpg to IMAGE`() {
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("jpg"))
    }

    @Test
    fun `fromExtension maps jpeg to IMAGE`() {
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("jpeg"))
    }

    @Test
    fun `fromExtension maps png to IMAGE`() {
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("png"))
    }

    @Test
    fun `fromExtension maps webp to IMAGE`() {
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("webp"))
    }

    @Test
    fun `fromExtension maps mp4 to VIDEO`() {
        assertEquals(FileCategory.VIDEO, FileCategory.fromExtension("mp4"))
    }

    @Test
    fun `fromExtension maps mkv to VIDEO`() {
        assertEquals(FileCategory.VIDEO, FileCategory.fromExtension("mkv"))
    }

    @Test
    fun `fromExtension maps mp3 to AUDIO`() {
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension("mp3"))
    }

    @Test
    fun `fromExtension maps flac to AUDIO`() {
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension("flac"))
    }

    @Test
    fun `fromExtension maps pdf to DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.fromExtension("pdf"))
    }

    @Test
    fun `fromExtension maps docx to DOCUMENT`() {
        assertEquals(FileCategory.DOCUMENT, FileCategory.fromExtension("docx"))
    }

    @Test
    fun `fromExtension maps apk to APK`() {
        assertEquals(FileCategory.APK, FileCategory.fromExtension("apk"))
    }

    @Test
    fun `fromExtension maps zip to ARCHIVE`() {
        assertEquals(FileCategory.ARCHIVE, FileCategory.fromExtension("zip"))
    }

    @Test
    fun `fromExtension maps unknown to OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.fromExtension("xyz123"))
    }

    @Test
    fun `fromExtension maps empty to OTHER`() {
        assertEquals(FileCategory.OTHER, FileCategory.fromExtension(""))
    }

    @Test
    fun `fromExtension is case insensitive`() {
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("JPG"))
        assertEquals(FileCategory.IMAGE, FileCategory.fromExtension("Png"))
        assertEquals(FileCategory.VIDEO, FileCategory.fromExtension("MP4"))
    }

    // ── Extension set tests ──

    @Test
    fun `MEDIA_EXTENSIONS contains image extensions`() {
        assertTrue("jpg" in FileCategory.MEDIA_EXTENSIONS)
        assertTrue("png" in FileCategory.MEDIA_EXTENSIONS)
        assertTrue("webp" in FileCategory.MEDIA_EXTENSIONS)
    }

    @Test
    fun `MEDIA_EXTENSIONS contains video extensions`() {
        assertTrue("mp4" in FileCategory.MEDIA_EXTENSIONS)
        assertTrue("mkv" in FileCategory.MEDIA_EXTENSIONS)
    }

    @Test
    fun `MEDIA_EXTENSIONS contains audio extensions`() {
        assertTrue("mp3" in FileCategory.MEDIA_EXTENSIONS)
        assertTrue("flac" in FileCategory.MEDIA_EXTENSIONS)
    }

    @Test
    fun `MEDIA_EXTENSIONS excludes documents`() {
        assertFalse("pdf" in FileCategory.MEDIA_EXTENSIONS)
        assertFalse("docx" in FileCategory.MEDIA_EXTENSIONS)
    }

    @Test
    fun `DOCUMENT_EXTENSIONS contains common docs`() {
        assertTrue("pdf" in FileCategory.DOCUMENT_EXTENSIONS)
        assertTrue("docx" in FileCategory.DOCUMENT_EXTENSIONS)
        assertTrue("txt" in FileCategory.DOCUMENT_EXTENSIONS)
        assertTrue("csv" in FileCategory.DOCUMENT_EXTENSIONS)
    }

    @Test
    fun `ARCHIVE_APK_EXTENSIONS contains archives and apks`() {
        assertTrue("zip" in FileCategory.ARCHIVE_APK_EXTENSIONS)
        assertTrue("rar" in FileCategory.ARCHIVE_APK_EXTENSIONS)
        assertTrue("apk" in FileCategory.ARCHIVE_APK_EXTENSIONS)
    }

    // ── FileItem tests ──

    @Test
    fun `FileItem extension derived from name`() {
        val item = FileItem("/path/photo.jpg", "photo.jpg", 1024, 0, FileCategory.IMAGE)
        assertEquals("jpg", item.extension)
    }

    @Test
    fun `FileItem extension is lowercased`() {
        val item = FileItem("/path/photo.JPG", "photo.JPG", 1024, 0, FileCategory.IMAGE)
        assertEquals("jpg", item.extension)
    }

    @Test
    fun `FileItem extension empty for no extension`() {
        val item = FileItem("/path/Makefile", "Makefile", 1024, 0, FileCategory.OTHER)
        assertEquals("", item.extension)
    }

    @Test
    fun `FileItem extension handles multiple dots`() {
        val item = FileItem("/path/archive.tar.gz", "archive.tar.gz", 1024, 0, FileCategory.ARCHIVE)
        assertEquals("gz", item.extension)
    }

    @Test
    fun `FileItem extension handles dot file`() {
        val item = FileItem("/path/.gitignore", ".gitignore", 100, 0, FileCategory.OTHER)
        assertEquals("gitignore", item.extension)
    }

    @Test
    fun `FileItem default duplicateGroup is negative one`() {
        val item = FileItem("/path/test.txt", "test.txt", 100, 0, FileCategory.DOCUMENT)
        assertEquals(-1, item.duplicateGroup)
    }

    @Test
    fun `FileItem copy with duplicateGroup`() {
        val item = FileItem("/path/test.txt", "test.txt", 100, 0, FileCategory.DOCUMENT)
        val dup = item.copy(duplicateGroup = 5)
        assertEquals(5, dup.duplicateGroup)
        assertEquals(item.path, dup.path)
    }
}
