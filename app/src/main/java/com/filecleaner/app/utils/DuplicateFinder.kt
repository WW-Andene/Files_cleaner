package com.filecleaner.app.utils

import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object DuplicateFinder {

    private const val PARTIAL_HASH_BYTES = 4096L // 4 KB from head + tail

    /**
     * Multi-stage duplicate detection (F-017):
     *   Stage 1 — group by file size (free, eliminates most files)
     *   Stage 2 — partial hash: first 4 KB + last 4 KB (avoids reading whole file)
     *   Stage 3 — full MD5 only for files that still collide after partial hash
     */
    suspend fun findDuplicates(
        files: List<FileItem>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<FileItem> = withContext(Dispatchers.IO) {

        // Stage 1: Group by size — skip unique sizes immediately
        val bySizeGroups = files
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filter { it.value.size > 1 }

        val sizeCollisions = bySizeGroups.values.flatten()
        val total = sizeCollisions.size
        var done = 0

        // Stage 2: Partial hash — first 4 KB + last 4 KB
        val byPartial = mutableMapOf<String, MutableList<FileItem>>()
        for (item in sizeCollisions) {
            onProgress(done++, total)
            val key = partialHash(File(item.path)) ?: continue
            byPartial.getOrPut(key) { mutableListOf() }.add(item)
        }

        // Stage 3: Full hash only groups that still collide after partial hash
        val result = mutableListOf<FileItem>()
        var groupId = 0
        for ((_, partialGroup) in byPartial) {
            if (partialGroup.size < 2) continue

            val byFull = mutableMapOf<String, MutableList<FileItem>>()
            for (item in partialGroup) {
                val hash = fullMd5(File(item.path)) ?: continue
                byFull.getOrPut(hash) { mutableListOf() }.add(item)
            }

            for ((_, fullGroup) in byFull) {
                if (fullGroup.size > 1) {
                    result.addAll(fullGroup.map { it.copy(duplicateGroup = groupId) })
                    groupId++
                }
            }
        }

        result.sortWith(compareBy({ it.duplicateGroup }, { -it.size }))
        result
    }

    /** Hash first 4 KB + last 4 KB of a file (fast pre-filter). */
    private fun partialHash(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        val length = file.length()
        if (length <= PARTIAL_HASH_BYTES * 2) {
            // Small file — just hash the whole thing
            return@runCatching fullMd5(file)
        }
        java.io.RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(PARTIAL_HASH_BYTES.toInt())
            // Head
            raf.readFully(buf)
            md.update(buf)
            // Tail
            raf.seek(length - PARTIAL_HASH_BYTES)
            raf.readFully(buf)
            md.update(buf)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Full MD5 of the entire file content. */
    private fun fullMd5(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
