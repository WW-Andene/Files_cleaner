package com.filecleaner.app.utils

import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object DuplicateFinder {

    // 4 KB from head + tail — balances collision avoidance vs. I/O cost.
    // Reducing below 1 KB raises false-positive rate significantly on media files.
    private const val PARTIAL_HASH_BYTES = 4096L
    // Standard I/O buffer — matches Android's default BufferedInputStream size.
    private const val HASH_BUFFER_SIZE = 8192
    // Skip full-hashing files larger than this (200 MB) — too slow on mobile storage.
    private const val MAX_FULL_HASH_SIZE = 200L * 1024L * 1024L

    // D1: Pre-allocated lookup table for hex encoding (avoids per-byte String.format allocation)
    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private fun bytesToHex(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[v ushr 4]
            result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(result)
    }

    /**
     * Multi-stage duplicate detection (F-017):
     *   Stage 1 — group by file size (free, eliminates most files)
     *   Stage 2 — partial hash: first 4 KB + last 4 KB (avoids reading whole file)
     *   Stage 3 — full MD5 only for files that still collide after partial hash
     *
     * @param onProgress (done, total) — covers both Stage 2 and Stage 3 combined
     */
    suspend fun findDuplicates(
        files: List<FileItem>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<FileItem> = withContext(Dispatchers.IO) {

        // Stage 1: Group by size — skip unique sizes immediately.
        // Also verify files are readable to avoid silent failures later.
        val readable = files.filter { item ->
            item.size > 0 && File(item.path).let { it.exists() && it.canRead() }
        }
        val bySizeGroups = readable
            .groupBy { it.size }
            .filter { it.value.size > 1 }

        val sizeCollisions = bySizeGroups.values.flatten()

        // Stage 2: Partial hash — first 4 KB + last 4 KB
        val byPartial = mutableMapOf<String, MutableList<FileItem>>()
        var stage2Done = 0
        val stage2Total = sizeCollisions.size
        for (item in sizeCollisions) {
            ensureActive()
            stage2Done++
            // Report stage 2 as first half of total progress
            onProgress(stage2Done, stage2Total * 2)
            val file = File(item.path)
            if (!file.exists() || !file.canRead()) continue
            val key = partialHash(file) ?: continue
            byPartial.getOrPut(key) { mutableListOf() }.add(item)
        }

        // Stage 3: Full hash only groups that still collide after partial hash
        // Count total files needing full hash for progress reporting
        val stage3Items = byPartial.values.filter { it.size >= 2 }.flatten()
        val stage3Total = stage3Items.size
        var stage3Done = 0

        val result = mutableListOf<FileItem>()
        var groupId = 0
        for ((_, partialGroup) in byPartial) {
            if (partialGroup.size < 2) continue

            val byFull = mutableMapOf<String, MutableList<FileItem>>()
            for (item in partialGroup) {
                ensureActive()
                stage3Done++
                // Report stage 3 as second half of total progress
                onProgress(stage2Total + stage3Done, stage2Total + stage3Total)
                val file = File(item.path)
                if (!file.exists() || !file.canRead()) continue
                // Skip files too large for full hashing on mobile
                if (file.length() > MAX_FULL_HASH_SIZE) continue
                val hash = fullMd5(file) ?: continue
                byFull.getOrPut(hash) { mutableListOf() }.add(item)
            }

            for ((_, fullGroup) in byFull) {
                if (fullGroup.size > 1) {
                    result.addAll(fullGroup.map { it.copy(duplicateGroup = groupId) })
                    groupId++
                }
            }
        }

        result.sortWith(compareBy({ it.duplicateGroup }, { it.name.lowercase() }))
        result
    }

    /** Hash first 4 KB + last 4 KB of a file (fast pre-filter). */
    private suspend fun partialHash(file: File): String? = runCatching {
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
        bytesToHex(md.digest())
    }.getOrNull()

    /** Full MD5 of the entire file content — yields periodically for cancellation. */
    private suspend fun fullMd5(file: File): String? = runCatching {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            var read: Int
            var bytesRead = 0L
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
                bytesRead += read
                // Check cancellation every ~512 KB to stay responsive
                if (bytesRead % (512 * 1024) < HASH_BUFFER_SIZE) {
                    coroutineContext.ensureActive()
                }
            }
        }
        bytesToHex(md.digest())
    }.getOrNull()
}
