package com.filecleaner.app.utils

import android.app.Application
import com.filecleaner.app.R
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Encapsulates pure file I/O operations (move, copy, rename, compress, extract).
 * I4: Extracted from MainViewModel to separate business logic from UI coordination.
 *
 * All public methods run synchronously on the caller's thread — the ViewModel is
 * responsible for dispatching to Dispatchers.IO and coordinating state updates.
 */
class FileOperationService(private val app: Application, private val storagePath: String) {

    companion object {
        private const val MAX_EXTRACT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
        private const val MAX_EXTRACT_ENTRIES = 10_000
        private const val IO_BUFFER_SIZE = 8192

        // Characters invalid on FAT32/exFAT or that can cause shell injection issues
        private val INVALID_FILENAME_CHARS = charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|')
    }

    data class OpResult(val success: Boolean, val message: String)

    /** Validates that a file path is within external storage. */
    fun isPathWithinStorage(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return canonical.startsWith(storagePath)
    }

    /** Returns true if the filename contains characters invalid on common filesystems. */
    fun hasInvalidFilenameChars(name: String): Boolean {
        return INVALID_FILENAME_CHARS.any { it in name } || name.isBlank() || name.trim() != name
    }

    private fun str(id: Int): String = app.getString(id)
    private fun str(id: Int, vararg args: Any): String = app.getString(id, *args)

    /** Move a file to a different directory. Must be called on IO thread. */
    fun moveFile(filePath: String, targetDirPath: String): OpResult {
        val src = File(filePath)
        val dst = File(targetDirPath, src.name)
        if (!isPathWithinStorage(filePath) || !isPathWithinStorage(targetDirPath)) {
            return OpResult(false, str(R.string.op_invalid_path))
        }
        if (!src.exists()) return OpResult(false, str(R.string.op_source_not_found))
        if (dst.exists()) return OpResult(false, str(R.string.op_file_exists_in_target))
        return if (src.renameTo(dst)) OpResult(true, str(R.string.op_moved, src.name))
        else OpResult(false, str(R.string.op_move_failed))
    }

    /** Copy a file to a target directory. Must be called on IO thread. */
    fun copyFile(filePath: String, targetDirPath: String): OpResult {
        val src = File(filePath)
        val dst = File(targetDirPath, src.name)
        if (!isPathWithinStorage(filePath) || !isPathWithinStorage(targetDirPath)) {
            return OpResult(false, str(R.string.op_invalid_path))
        }
        if (!src.exists()) return OpResult(false, str(R.string.op_source_not_found))
        if (dst.exists()) return OpResult(false, str(R.string.op_file_exists_in_target))
        return try {
            src.copyTo(dst)
            OpResult(true, str(R.string.op_copied, src.name))
        } catch (e: Exception) {
            OpResult(false, str(R.string.op_copy_failed, e.localizedMessage ?: ""))
        }
    }

    /** Rename a file. Must be called on IO thread. */
    fun renameFile(oldPath: String, newName: String): OpResult {
        if (hasInvalidFilenameChars(newName)) {
            return OpResult(false, str(R.string.op_invalid_name))
        }
        val src = File(oldPath)
        if (!src.exists()) return OpResult(false, str(R.string.op_file_not_found))
        val parentDir = src.parent
            ?: return OpResult(false, str(R.string.op_no_parent_dir))
        val dst = File(parentDir, newName)
        if (dst.exists()) return OpResult(false, str(R.string.op_name_exists))
        return if (src.renameTo(dst)) OpResult(true, str(R.string.op_renamed, newName))
        else OpResult(false, str(R.string.op_rename_failed))
    }

    /** Compress a file into a ZIP. Must be called on IO thread. */
    fun compressFile(filePath: String): OpResult {
        return try {
            if (!isPathWithinStorage(filePath)) {
                return OpResult(false, str(R.string.op_invalid_path))
            }
            val src = File(filePath)
            if (!src.exists()) return OpResult(false, str(R.string.op_file_not_found))
            val parentDir = src.parent
                ?: return OpResult(false, str(R.string.op_no_parent_dir))
            val zipFile = File(parentDir, "${src.nameWithoutExtension}.zip")
            ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                zos.putNextEntry(ZipEntry(src.name))
                src.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            OpResult(true, str(R.string.op_compressed, zipFile.name))
        } catch (e: Exception) {
            OpResult(false, str(R.string.op_compress_failed, e.localizedMessage ?: ""))
        }
    }

    /** Extract a ZIP archive. Must be called on IO thread. */
    fun extractArchive(filePath: String): OpResult {
        return try {
            if (!isPathWithinStorage(filePath)) {
                return OpResult(false, str(R.string.op_invalid_path))
            }
            val src = File(filePath)
            if (!src.exists()) return OpResult(false, str(R.string.op_file_not_found))
            if (!src.extension.equals("zip", ignoreCase = true)) {
                return OpResult(false, str(R.string.op_zip_only))
            }
            val parentDir = src.parent
                ?: return OpResult(false, str(R.string.op_no_parent_dir))
            val outDir = File(parentDir, src.nameWithoutExtension)
            outDir.mkdirs()
            var totalExtracted = 0L
            var entryCount = 0
            ZipInputStream(src.inputStream().buffered()).use { zis ->
                val outDirCanonical = outDir.canonicalPath + File.separator
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.contains("..")) {
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(outDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(outDirCanonical) &&
                        outFile.canonicalPath != outDir.canonicalPath) {
                        entry = zis.nextEntry
                        continue
                    }
                    entryCount++
                    if (entryCount > MAX_EXTRACT_ENTRIES) {
                        return OpResult(false, str(R.string.op_too_many_entries, MAX_EXTRACT_ENTRIES))
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().buffered().use { out ->
                            val buf = ByteArray(IO_BUFFER_SIZE)
                            var len: Int
                            while (zis.read(buf).also { len = it } > 0) {
                                totalExtracted += len
                                if (totalExtracted > MAX_EXTRACT_BYTES) {
                                    out.close()
                                    outFile.delete()
                                    return OpResult(false, str(R.string.op_archive_too_large))
                                }
                                out.write(buf, 0, len)
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            OpResult(true, str(R.string.op_extracted, "${outDir.name}/"))
        } catch (e: Exception) {
            OpResult(false, str(R.string.op_extract_failed, e.localizedMessage ?: ""))
        }
    }
}
