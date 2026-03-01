package com.filecleaner.app.utils

import android.os.Environment
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object JunkFinder {

    private val JUNK_EXTENSIONS = setOf(
        "tmp", "temp", "log", "bak", "old", "dmp", "crdownload", "part", "partial"
    )

    private const val STALE_DOWNLOAD_DAYS = 90L

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
        val cutoff90Days = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(STALE_DOWNLOAD_DAYS)
        // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
        @Suppress("DEPRECATION")
        val downloadPath = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        val result = mutableListOf<FileItem>()
        for ((index, item) in files.withIndex()) {
            if (index % 500 == 0) ensureActive()
            val ext = item.extension
            val path = item.path.lowercase()

            val isJunk = when {
                // Known junk extension
                ext in JUNK_EXTENSIONS -> true

                // In a cache/temp directory
                JUNK_DIR_KEYWORDS.any { path.contains("/$it/") } -> true

                // Old download (> 90 days old, only truly disposable types)
                // Excludes documents, media, archives, APKs — only flags
                // files with no recognized extension (F-002)
                item.path.startsWith(downloadPath) &&
                        item.lastModified < cutoff90Days &&
                        !isMedia(ext) && !isDocument(ext) &&
                        !isArchiveOrApk(ext) -> true

                else -> false
            }
            if (isJunk) result.add(item)
        }
        result.sortedByDescending { it.size }
    }

    /**
     * Returns the top N largest files on the device.
     */
    suspend fun findLargeFiles(
        files: List<FileItem>,
        minSizeBytes: Long = 50 * 1024 * 1024L, // 50 MB default
        maxResults: Int = 200
    ): List<FileItem> = withContext(Dispatchers.IO) {
        ensureActive()
        files.filter { it.size >= minSizeBytes }
            .sortedByDescending { it.size }
            .take(maxResults)
    }

    // Derive from FileCategory — single source of truth for extension mappings
    private fun isMedia(ext: String) = ext in FileCategory.MEDIA_EXTENSIONS
    private fun isDocument(ext: String) = ext in FileCategory.DOCUMENT_EXTENSIONS
    private fun isArchiveOrApk(ext: String) = ext in FileCategory.ARCHIVE_APK_EXTENSIONS
}
