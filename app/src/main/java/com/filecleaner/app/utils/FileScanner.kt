package com.filecleaner.app.utils

import android.content.Context
import android.os.Environment
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileScanner {

    // Extensions mapped to categories
    private val CATEGORY_MAP = mapOf(
        FileCategory.IMAGE    to setOf("jpg","jpeg","png","gif","bmp","webp","heic","heif","tiff","svg","raw","cr2","nef"),
        FileCategory.VIDEO    to setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mpeg","mpg"),
        FileCategory.AUDIO    to setOf("mp3","aac","flac","wav","ogg","m4a","wma","opus","aiff","mid"),
        FileCategory.DOCUMENT to setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","odt","ods","odp","epub","mobi","rtf","md"),
        FileCategory.APK      to setOf("apk","xapk","apks"),
        FileCategory.ARCHIVE  to setOf("zip","rar","7z","tar","gz","bz2","xz","cab","iso","tgz")
    )

    // Directories to skip (system / sensitive)
    private val SKIP_DIRS = setOf(
        "Android/data", "Android/obb", ".thumbnails", ".cache",
        "lost+found", "proc", "sys", "dev"
    )

    suspend fun scanAll(context: Context, onProgress: (Int) -> Unit = {}): List<FileItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            val root = Environment.getExternalStorageDirectory()

            var scanned = 0
            walkDir(root, results) {
                scanned++
                if (scanned % 100 == 0) onProgress(scanned)
            }
            results
        }

    private fun walkDir(dir: File, out: MutableList<FileItem>, tick: () -> Unit) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                // Skip hidden and system dirs
                val relative = child.absolutePath.substringAfter(
                    Environment.getExternalStorageDirectory().absolutePath + "/"
                )
                if (SKIP_DIRS.any { relative.startsWith(it) } || child.name.startsWith(".")) continue
                walkDir(child, out, tick)
            } else {
                tick()
                out.add(child.toFileItem())
            }
        }
    }

    /** Public helper for creating a FileItem from a File (used by undo restore). */
    fun fileToItem(file: File): FileItem = file.toFileItem()

    fun File.toFileItem(): FileItem {
        val ext = extension.lowercase()
        val category = CATEGORY_MAP.entries.firstOrNull { ext in it.value }?.key
            ?: if (absolutePath.contains("/Download/", ignoreCase = true)) FileCategory.DOWNLOAD
            else FileCategory.OTHER

        return FileItem(
            path         = absolutePath,
            name         = name,
            size         = length(),
            lastModified = lastModified(),
            category     = category
        )
    }
}
