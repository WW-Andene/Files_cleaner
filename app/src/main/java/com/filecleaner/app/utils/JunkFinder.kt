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

                // Old download (> 90 days old, only truly disposable types)
                // Excludes documents, media, archives, APKs — only flags
                // files with no recognized extension (F-002)
                item.path.startsWith(downloadPath) &&
                        item.lastModified < cutoff90Days &&
                        !isMedia(ext) && !isDocument(ext) &&
                        !isArchiveOrApk(ext) -> true

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

    private val MEDIA_EXTENSIONS = setOf(
        // Images — matches FileScanner.CATEGORY_MAP
        "jpg","jpeg","png","gif","bmp","webp","heic","heif","tiff","svg","raw","cr2","nef",
        // Videos
        "mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mpeg","mpg",
        // Audio
        "mp3","aac","flac","wav","ogg","m4a","wma","opus","aiff","mid"
    )

    private fun isMedia(ext: String) = ext in MEDIA_EXTENSIONS

    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv",
        "odt","ods","odp","epub","mobi","rtf","md"
    )

    private val ARCHIVE_APK_EXTENSIONS = setOf(
        "apk","xapk","apks","zip","rar","7z","tar","gz","bz2","xz","cab","iso","tgz"
    )

    private fun isDocument(ext: String) = ext in DOCUMENT_EXTENSIONS
    private fun isArchiveOrApk(ext: String) = ext in ARCHIVE_APK_EXTENSIONS
}
