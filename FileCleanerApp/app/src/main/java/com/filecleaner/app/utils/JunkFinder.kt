package com.filecleaner.app.utils

import android.os.Environment
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object JunkFinder {

    private val JUNK_EXTENSIONS = setOf(
        "tmp", "temp", "log", "bak", "old", "dmp", "crdownload", "part", "partial"
    )

    private val JUNK_DIR_KEYWORDS = listOf(
        ".cache", "cache", "temp", "tmp", "thumbnail", ".thumbnails", "lost+found"
    )

    /**
     * Returns files that are considered "junk":
     * - Known junk extensions (.tmp, .log, .bak, etc.)
     * - Files in cache/temp directories
     * - Old downloads (> 90 days, not media)
     */
    suspend fun findJunk(files: List<FileItem>): List<FileItem> = withContext(Dispatchers.IO) {
        val cutoff90Days = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
        val downloadPath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        files.filter { item ->
            val ext = item.name.substringAfterLast('.', "").lowercase()
            val path = item.path.lowercase()

            when {
                // Known junk extension
                ext in JUNK_EXTENSIONS -> true

                // In a cache/temp directory
                JUNK_DIR_KEYWORDS.any { path.contains("/$it/") } -> true

                // Old download (> 90 days old, not a media file)
                item.path.startsWith(downloadPath) &&
                        item.lastModified < cutoff90Days &&
                        !isMedia(ext) -> true

                else -> false
            }
        }.sortedByDescending { it.size }
    }

    /**
     * Returns the top N largest files on the device.
     */
    suspend fun findLargeFiles(
        files: List<FileItem>,
        minSizeBytes: Long = 50 * 1024 * 1024L, // 50 MB default
        maxResults: Int = 200
    ): List<FileItem> = withContext(Dispatchers.IO) {
        files.filter { it.size >= minSizeBytes }
            .sortedByDescending { it.size }
            .take(maxResults)
    }

    private fun isMedia(ext: String) = ext in setOf(
        "jpg","jpeg","png","gif","mp4","mkv","avi","mov","mp3","aac","flac","wav"
    )
}
