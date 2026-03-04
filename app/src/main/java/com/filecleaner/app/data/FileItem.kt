package com.filecleaner.app.data

import android.os.Parcelable
import androidx.annotation.StringRes
import com.filecleaner.app.R
import com.filecleaner.app.utils.UndoHelper
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.io.File

enum class FileCategory(@StringRes val displayNameRes: Int, val emoji: String) {
    IMAGE(R.string.cat_images, "\uD83D\uDDBC\uFE0F"),
    VIDEO(R.string.cat_videos, "\uD83C\uDFAC"),
    AUDIO(R.string.cat_audio, "\uD83C\uDFB5"),
    DOCUMENT(R.string.cat_documents, "\uD83D\uDCC4"),
    APK(R.string.cat_apks, "\uD83D\uDCE6"),
    ARCHIVE(R.string.cat_archives, "\uD83D\uDDDC\uFE0F"),
    DOWNLOAD(R.string.cat_downloads, "\u2B07\uFE0F"),
    OTHER(R.string.cat_other, "\uD83D\uDCC1");

    companion object {
        private val extMap = mapOf(
            IMAGE to setOf("jpg","jpeg","png","gif","bmp","webp","heic","heif","tiff","svg","raw","cr2","nef","ico","avif","jxl","jp2"),
            VIDEO to setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mpeg","mpg","mts","m2ts","ogv","rmvb"),
            AUDIO to setOf("mp3","aac","flac","wav","ogg","m4a","wma","opus","aiff","mid","amr","ape","wv","m4b","dsf"),
            DOCUMENT to setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","odt","ods","odp","epub","mobi","rtf","md","html","htm","xml","json","yaml","yml","azw","azw3","key","numbers"),
            APK to setOf("apk","xapk","apks"),
            ARCHIVE to setOf("zip","rar","7z","tar","gz","bz2","xz","cab","iso","tgz","zst")
        )

        // Flat O(1) lookup map: extension → category (pre-computed from extMap)
        private val flatLookup: Map<String, FileCategory> = buildMap {
            for ((cat, exts) in extMap) {
                for (ext in exts) put(ext, cat)
            }
        }

        /** Combined media extensions (image + video + audio) — single source of truth. */
        val MEDIA_EXTENSIONS: Set<String> =
            (extMap[IMAGE] ?: emptySet()) + (extMap[VIDEO] ?: emptySet()) + (extMap[AUDIO] ?: emptySet())

        val DOCUMENT_EXTENSIONS: Set<String> = extMap[DOCUMENT] ?: emptySet()
        val ARCHIVE_APK_EXTENSIONS: Set<String> = (extMap[ARCHIVE] ?: emptySet()) + (extMap[APK] ?: emptySet())

        fun fromExtension(ext: String): FileCategory =
            flatLookup[ext.lowercase()] ?: OTHER
    }
}

@Parcelize
data class FileItem(
    val path: String,
    val name: String,
    val size: Long,          // bytes
    val lastModified: Long,  // epoch ms
    val category: FileCategory,
    val duplicateGroup: Int = -1  // -1 = not a duplicate
) : Parcelable {

    val file: File get() = File(path)

    val sizeReadable: String get() = UndoHelper.formatBytes(size)

    @IgnoredOnParcel
    val extension: String = name.substringAfterLast('.', "").lowercase()
}
