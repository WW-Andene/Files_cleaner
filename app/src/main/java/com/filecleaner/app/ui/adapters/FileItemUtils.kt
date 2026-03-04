package com.filecleaner.app.ui.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import java.util.Date

/**
 * Shared helpers for [FileAdapter] — thumbnail loading, meta text, color
 * resolution.  Extracted so the adapter stays focused on view-binding logic.
 */
object FileItemUtils {

    // ── Adapter-wide resolved color cache ──────────────────────────────────

    data class AdapterColors(
        val surface: Int,
        val border: Int,
        val selectedBg: Int,
        val selectedBorder: Int
    )

    fun resolveColorsWithSelection(ctx: Context): AdapterColors = AdapterColors(
        surface        = ContextCompat.getColor(ctx, R.color.surfaceColor),
        border         = ContextCompat.getColor(ctx, R.color.borderDefault),
        selectedBg     = ContextCompat.getColor(ctx, R.color.selectedBackground),
        selectedBorder = ContextCompat.getColor(ctx, R.color.selectedBorder)
    )

    // ── Category → color res mapping ──────────────────────────────────────

    private val CATEGORY_COLOR_RES = mapOf(
        FileCategory.IMAGE    to R.color.catImage,
        FileCategory.VIDEO    to R.color.catVideo,
        FileCategory.AUDIO    to R.color.catAudio,
        FileCategory.DOCUMENT to R.color.catDocument,
        FileCategory.APK      to R.color.catApk,
        FileCategory.ARCHIVE  to R.color.catArchive,
        FileCategory.DOWNLOAD to R.color.catDownload,
        FileCategory.OTHER    to R.color.catOther
    )

    /** Resolve the accent color for a file's [FileCategory]. */
    fun categoryColor(ctx: Context, category: FileCategory): Int =
        ContextCompat.getColor(ctx, CATEGORY_COLOR_RES[category] ?: R.color.catOther)

    // ── Junk sub-category classification and colors ──────────────────────

    enum class JunkType(val colorRes: Int, val labelRes: Int) {
        CACHE(R.color.junkCache, R.string.junk_cache),
        TEMP(R.color.junkTemp, R.string.junk_temp),
        THUMBNAIL(R.color.junkThumbnail, R.string.junk_thumbnails),
        OLD_DOWNLOAD(R.color.junkOldDownload, R.string.junk_old_downloads),
        LOG(R.color.junkLog, R.string.junk_logs)
    }

    private val JUNK_EXTENSIONS_LOG = setOf("log", "bak", "old", "dmp")
    private val JUNK_EXTENSIONS_TEMP = setOf("tmp", "temp", "crdownload", "part", "partial")

    /** Classify a junk [FileItem] into a sub-category for color-coding. */
    fun classifyJunk(item: FileItem): JunkType {
        val path = item.path.lowercase()
        val ext = item.extension

        return when {
            path.contains("/.thumbnails/") || path.contains("/thumbnail") -> JunkType.THUMBNAIL
            path.contains("/.cache/") || path.contains("/cache/")         -> JunkType.CACHE
            path.contains("/temp/") || path.contains("/tmp/")             -> JunkType.TEMP
            ext in JUNK_EXTENSIONS_TEMP                                   -> JunkType.TEMP
            ext in JUNK_EXTENSIONS_LOG                                    -> JunkType.LOG
            else                                                          -> JunkType.OLD_DOWNLOAD
        }
    }

    fun junkColor(ctx: Context, item: FileItem): Int =
        ContextCompat.getColor(ctx, classifyJunk(item).colorRes)

    // ── Size severity ────────────────────────────────────────────────────

    enum class SizeSeverity(val colorRes: Int, val labelRes: Int) {
        HUGE(R.color.sizeHuge, R.string.size_huge),       // > 500 MB
        LARGE(R.color.sizeLarge, R.string.size_large),    // > 100 MB
        MEDIUM(R.color.sizeMedium, R.string.size_medium)  // > 50 MB (minimum for large-file tab)
    }

    private const val MB_500 = 500L * 1024 * 1024
    private const val MB_100 = 100L * 1024 * 1024

    fun sizeSeverity(item: FileItem): SizeSeverity = when {
        item.size >= MB_500 -> SizeSeverity.HUGE
        item.size >= MB_100 -> SizeSeverity.LARGE
        else                -> SizeSeverity.MEDIUM
    }

    fun sizeColor(ctx: Context, item: FileItem): Int =
        ContextCompat.getColor(ctx, sizeSeverity(item).colorRes)

    // ── Accent stripe color dispatch ─────────────────────────────────────

    /**
     * Returns the accent-stripe color for [item] under the given [mode],
     * or `null` when no stripe should be shown.
     */
    fun accentColor(ctx: Context, item: FileItem, mode: ColorMode): Int? = when (mode) {
        ColorMode.NONE            -> null
        ColorMode.DUPLICATE_GROUP -> null   // handled via background colors
        ColorMode.CATEGORY        -> categoryColor(ctx, item.category)
        ColorMode.JUNK_CATEGORY   -> junkColor(ctx, item)
        ColorMode.SIZE_SEVERITY   -> sizeColor(ctx, item)
    }

    // ── Thumbnail / icon loading ─────────────────────────────────────────

    /**
     * Loads a category icon into [imageView].  Image/Video thumbnails are
     * loaded via Glide when available; this fallback sets a tinted vector.
     */
    fun loadThumbnail(imageView: ImageView, item: FileItem, isGrid: Boolean) {
        val ctx = imageView.context
        val iconRes = when (item.category) {
            FileCategory.IMAGE    -> R.drawable.ic_image
            FileCategory.VIDEO    -> R.drawable.ic_video
            FileCategory.AUDIO    -> R.drawable.ic_audio
            FileCategory.DOCUMENT -> R.drawable.ic_document
            FileCategory.APK      -> R.drawable.ic_apk
            FileCategory.ARCHIVE  -> R.drawable.ic_archive
            FileCategory.DOWNLOAD -> R.drawable.ic_download
            FileCategory.OTHER    -> R.drawable.ic_file
        }
        imageView.setImageResource(iconRes)
        val tint = categoryColor(ctx, item.category)
        imageView.setColorFilter(tint)
    }

    // ── Meta text builder ────────────────────────────────────────────────

    fun buildMeta(textView: TextView, item: FileItem) {
        val ctx = textView.context
        val dateStr = DateFormat.format(
            ctx.getString(R.string.date_format_short), Date(item.lastModified)
        )
        textView.text = "${item.sizeReadable}  \u2022  $dateStr"
    }
}
