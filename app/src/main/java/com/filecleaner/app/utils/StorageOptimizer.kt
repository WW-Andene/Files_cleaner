package com.filecleaner.app.utils

import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Storage optimization rule engine.
 * Analyzes scanned files and generates move suggestions to organize storage.
 *
 * Default rules:
 * - Photos/videos → Pictures/{YYYY-MM} or Videos/{YYYY-MM}
 * - Audio → Music/
 * - Documents → Documents/
 * - APKs → APKs/
 * - Downloads older than 90 days → OldDownloads/
 */
object StorageOptimizer {

    data class Suggestion(
        val file: FileItem,
        val currentPath: String,
        val suggestedPath: String,
        val reason: String,
        var accepted: Boolean = true
    )

    /**
     * Analyze files and generate optimization suggestions.
     * [storagePath] is the root external storage path.
     */
    fun analyze(files: List<FileItem>, storagePath: String): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val usedPaths = mutableSetOf<String>()
        val dateFmt = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        for (file in files) {
            val parentDir = File(file.path).parent ?: continue
            val relative = parentDir.removePrefix(storagePath).trimStart(File.separatorChar)

            val suggestion = when (file.category) {
                FileCategory.IMAGE -> {
                    val monthFolder = dateFmt.format(Date(file.lastModified))
                    val targetDir = "$storagePath/Pictures/$monthFolder"
                    if (parentDir != targetDir && !parentDir.startsWith("$storagePath/Pictures/")) {
                        Suggestion(file, file.path, "$targetDir/${file.name}",
                            "Organize photo by date")
                    } else null
                }
                FileCategory.VIDEO -> {
                    val monthFolder = dateFmt.format(Date(file.lastModified))
                    val targetDir = "$storagePath/Videos/$monthFolder"
                    if (parentDir != targetDir && !parentDir.startsWith("$storagePath/Videos/")) {
                        Suggestion(file, file.path, "$targetDir/${file.name}",
                            "Organize video by date")
                    } else null
                }
                FileCategory.AUDIO -> {
                    val targetDir = "$storagePath/Music"
                    if (!parentDir.startsWith(targetDir)) {
                        Suggestion(file, file.path, "$targetDir/${file.name}",
                            "Move audio to Music")
                    } else null
                }
                FileCategory.DOCUMENT -> {
                    val targetDir = "$storagePath/Documents"
                    if (!parentDir.startsWith(targetDir)) {
                        Suggestion(file, file.path, "$targetDir/${file.name}",
                            "Move document to Documents")
                    } else null
                }
                FileCategory.APK -> {
                    val targetDir = "$storagePath/APKs"
                    if (!parentDir.startsWith(targetDir)) {
                        Suggestion(file, file.path, "$targetDir/${file.name}",
                            "Move APK to APKs folder")
                    } else null
                }
                FileCategory.DOWNLOAD -> {
                    if (file.lastModified <= 0L) {
                        null // P2-A1-08: Skip files with unknown modification time
                    } else {
                        val ageMs = System.currentTimeMillis() - file.lastModified
                        val ageDays = ageMs / (24 * 60 * 60 * 1000L)
                        if (ageDays > 90) {
                            val targetDir = "$storagePath/OldDownloads"
                            Suggestion(file, file.path, "$targetDir/${file.name}",
                                "Old download (${ageDays}d)")
                        } else null
                    }
                }
                else -> null
            }

            if (suggestion != null) {
                // P2-A1-09: Avoid duplicate target paths by appending a suffix
                var finalPath = suggestion.suggestedPath
                if (finalPath in usedPaths || File(finalPath).exists()) {
                    val targetFile = File(finalPath)
                    val parent = targetFile.parent ?: ""
                    val nameNoExt = targetFile.nameWithoutExtension
                    val ext = targetFile.extension
                    var counter = 1
                    while (finalPath in usedPaths || File(finalPath).exists()) {
                        val suffix = if (ext.isNotEmpty()) "${nameNoExt}_($counter).$ext" else "${nameNoExt}_($counter)"
                        finalPath = "$parent/$suffix"
                        counter++
                    }
                    suggestions.add(suggestion.copy(suggestedPath = finalPath))
                } else {
                    suggestions.add(suggestion)
                }
                usedPaths.add(finalPath)
            }
        }

        return suggestions
    }
}
