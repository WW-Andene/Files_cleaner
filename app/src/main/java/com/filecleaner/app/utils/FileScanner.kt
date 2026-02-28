package com.filecleaner.app.utils

import android.content.Context
import android.os.Environment
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

object FileScanner {

    // Flat extension → category map for O(1) lookup (F-019)
    private val EXT_TO_CATEGORY: Map<String, FileCategory> = buildMap {
        val groups = mapOf(
            FileCategory.IMAGE    to setOf("jpg","jpeg","png","gif","bmp","webp","heic","heif","tiff","svg","raw","cr2","nef"),
            FileCategory.VIDEO    to setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mpeg","mpg"),
            FileCategory.AUDIO    to setOf("mp3","aac","flac","wav","ogg","m4a","wma","opus","aiff","mid"),
            FileCategory.DOCUMENT to setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","odt","ods","odp","epub","mobi","rtf","md"),
            FileCategory.APK      to setOf("apk","xapk","apks"),
            FileCategory.ARCHIVE  to setOf("zip","rar","7z","tar","gz","bz2","xz","cab","iso","tgz")
        )
        for ((cat, exts) in groups) {
            for (ext in exts) put(ext, cat)
        }
    }

    private val SKIP_DIRS = setOf(
        "Android/data", "Android/obb", ".thumbnails", ".cache",
        "lost+found", "proc", "sys", "dev"
    )

    suspend fun scanAll(context: Context, onProgress: (Int) -> Unit = {}): List<FileItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            val root = Environment.getExternalStorageDirectory()
            val rootPath = root.absolutePath

            // Iterative walk using explicit stack (F-018)
            val stack = ArrayDeque<File>()
            stack.push(root)
            var scanned = 0

            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.pop()
                val children = dir.listFiles() ?: continue

                for (child in children) {
                    if (child.isDirectory) {
                        val relative = child.absolutePath.substringAfter("$rootPath/")
                        if (SKIP_DIRS.any { relative.startsWith(it) } || child.name.startsWith(".")) continue
                        stack.push(child)
                    } else {
                        scanned++
                        if (scanned % 100 == 0) onProgress(scanned)
                        results.add(child.toFileItem())
                    }
                }
            }
            results
        }

    fun fileToItem(file: File): FileItem = file.toFileItem()

    fun File.toFileItem(): FileItem {
        val ext = extension.lowercase()
        val category = EXT_TO_CATEGORY[ext]
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
