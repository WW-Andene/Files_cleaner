package com.filecleaner.app.utils

import com.filecleaner.app.data.FileItem
import com.filecleaner.app.data.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Stateless helper for file I/O operations.
 * All methods are suspend functions that run on Dispatchers.IO.
 * The ViewModel delegates file I/O here and handles state management separately.
 */
object FileOperationHelper {

    suspend fun moveFile(filePath: String, targetDirPath: String): OperationResult =
        withContext(Dispatchers.IO) {
            val src = File(filePath)
            val dst = File(targetDirPath, src.name)
            if (!src.exists()) return@withContext OperationResult(false, "Source file not found")
            if (dst.exists()) return@withContext OperationResult(false, "File already exists in target")
            if (src.renameTo(dst)) OperationResult(true, "Moved ${src.name}")
            else OperationResult(false, "Failed to move file")
        }

    suspend fun renameFile(oldPath: String, newName: String): OperationResult =
        withContext(Dispatchers.IO) {
            val src = File(oldPath)
            if (!src.exists()) return@withContext OperationResult(false, "File not found")
            val dst = File(src.parent, newName)
            if (dst.exists()) return@withContext OperationResult(false, "File with that name already exists")
            if (src.renameTo(dst)) OperationResult(true, "Renamed to $newName")
            else OperationResult(false, "Rename failed")
        }

    suspend fun compressFile(filePath: String): OperationResult =
        withContext(Dispatchers.IO) {
            try {
                val src = File(filePath)
                if (!src.exists()) return@withContext OperationResult(false, "File not found")
                val zipFile = File(src.parent, "${src.nameWithoutExtension}.zip")
                ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                    zos.putNextEntry(ZipEntry(src.name))
                    src.inputStream().buffered().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                OperationResult(true, "Compressed to ${zipFile.name}")
            } catch (e: Exception) {
                OperationResult(false, "Compression failed: ${e.message}")
            }
        }

    suspend fun extractArchive(filePath: String): OperationResult =
        withContext(Dispatchers.IO) {
            try {
                val src = File(filePath)
                if (!src.exists()) return@withContext OperationResult(false, "File not found")
                if (!src.extension.equals("zip", ignoreCase = true)) {
                    return@withContext OperationResult(false, "Only ZIP archives can be extracted")
                }
                val outDir = File(src.parent, src.nameWithoutExtension)
                outDir.mkdirs()
                ZipInputStream(src.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(outDir, entry.name)
                        // Prevent zip slip
                        if (!outFile.canonicalPath.startsWith(outDir.canonicalPath)) {
                            entry = zis.nextEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().buffered().use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }
                OperationResult(true, "Extracted to ${outDir.name}/")
            } catch (e: Exception) {
                OperationResult(false, "Extraction failed: ${e.message}")
            }
        }

    /** Move files to trash directory. Returns map of (originalPath -> trashPath) and total freed bytes. */
    suspend fun softDeleteFiles(
        toDelete: List<FileItem>,
        trashDir: File
    ): Pair<Map<String, String>, Long> = withContext(Dispatchers.IO) {
        trashDir.mkdirs()
        var freed = 0L
        val moved = mutableMapOf<String, String>()
        for (item in toDelete) {
            val src = File(item.path)
            val dst = File(trashDir, "${System.nanoTime()}_${src.name}")
            if (src.renameTo(dst)) {
                moved[item.path] = dst.absolutePath
                freed += item.size
            }
        }
        moved to freed
    }

    /** Undo delete by restoring files from trash to their original locations. */
    suspend fun undoDelete(pendingTrash: Map<String, String>): List<FileItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FileItem>()
            for ((origPath, trashPath) in pendingTrash) {
                val trashFile = File(trashPath)
                val origFile = File(origPath)
                if (trashFile.renameTo(origFile)) {
                    items.add(FileScanner.fileToItem(origFile))
                }
            }
            items
        }

    /** Permanently delete trashed files. */
    suspend fun commitTrash(pendingTrash: Map<String, String>) =
        withContext(Dispatchers.IO) {
            for ((_, trashPath) in pendingTrash) {
                File(trashPath).delete()
            }
        }
}
