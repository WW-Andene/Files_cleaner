package com.filecleaner.app.utils

import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRenameTest {

    private fun item(name: String) = FileItem(
        path = "/storage/emulated/0/$name",
        name = name,
        size = 1024,
        lastModified = System.currentTimeMillis(),
        category = FileCategory.IMAGE
    )

    @Test
    fun `applyPattern replaces name placeholder`() {
        val result = applyPattern(item("photo.jpg"), "{name}.{ext}", 1, 3)
        assertEquals("photo.jpg", result)
    }

    @Test
    fun `applyPattern replaces number placeholder with padding`() {
        val result = applyPattern(item("photo.jpg"), "img_{n}.{ext}", 5, 3)
        assertEquals("img_005.jpg", result)
    }

    @Test
    fun `applyPattern combines all placeholders`() {
        val result = applyPattern(item("photo.jpg"), "{name}_{n}.{ext}", 12, 3)
        assertEquals("photo_012.jpg", result)
    }

    @Test
    fun `applyPattern handles file without extension`() {
        val result = applyPattern(item("Makefile"), "{name}_{n}.{ext}", 1, 3)
        // name is "Makefile", ext is "" (no dot)
        assertEquals("Makefile_001.", result)
    }

    // Mirror the logic from BatchRenameDialog
    private fun applyPattern(file: FileItem, pattern: String, num: Int, padWidth: Int): String {
        val nameNoExt = file.name.substringBeforeLast('.', file.name)
        val ext = file.extension
        return pattern
            .replace("{name}", nameNoExt)
            .replace("{ext}", ext)
            .replace("{n}", num.toString().padStart(padWidth, '0'))
    }
}
