package com.filecleaner.app.utils

import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object DuplicateFinder {

    /**
     * Groups files by size first (cheap), then by MD5 hash of content (only for same-size groups).
     * Returns a flat list of FileItems that have at least one duplicate.
     * duplicateGroup is set to a unique group index so the UI can colour-code them.
     */
    suspend fun findDuplicates(
        files: List<FileItem>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<FileItem> = withContext(Dispatchers.IO) {

        // Step 1: Group by size — skip unique sizes immediately
        val bySizeGroups = files
            .filter { it.size > 0 }
            .groupBy { it.size }
            .filter { it.value.size > 1 }

        val candidates = bySizeGroups.values.flatten()
        val total = candidates.size
        var done = 0

        // Step 2: Hash only the candidates
        val byHash = mutableMapOf<String, MutableList<FileItem>>()
        for (item in candidates) {
            onProgress(done++, total)
            val hash = md5(File(item.path)) ?: continue
            byHash.getOrPut(hash) { mutableListOf() }.add(item)
        }

        // Step 3: Keep only actual duplicates (2+ files with same hash)
        val result = mutableListOf<FileItem>()
        var groupId = 0
        for ((_, group) in byHash) {
            if (group.size > 1) {
                group.forEach { it.duplicateGroup = groupId }
                result.addAll(group)
                groupId++
            }
        }

        result.sortWith(compareBy({ it.duplicateGroup }, { -it.size }))
        result
    }

    private fun md5(file: File): String? = runCatching {
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
