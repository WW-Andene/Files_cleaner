package com.filecleaner.app.ui.adapters

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import java.io.File
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
        ColorMode.JUNK_CATEGORY   -> null   // background color only, consistent with duplicates
        ColorMode.SIZE_SEVERITY   -> null   // background color only, consistent with duplicates
    }

    // ── Thumbnail / icon loading ─────────────────────────────────────────

    /**
     * Loads a thumbnail or category icon into [imageView].
     * For images and videos: loads actual file thumbnail via Glide.
     * For other types: sets a tinted vector icon.
     */
    fun loadThumbnail(imageView: ImageView, item: FileItem, isGrid: Boolean) {
        val ctx = imageView.context
        val file = File(item.path)
        val cornerRadius = (8 * ctx.resources.displayMetrics.density).toInt()

        // Load real thumbnails for images and videos
        if ((item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) && file.exists()) {
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            // §G1: Describe the thumbnail for screen readers
            imageView.contentDescription = ctx.getString(R.string.a11y_file_icon,
                ctx.getString(item.category.displayNameRes))
            Glide.with(ctx)
                .load(file)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .placeholder(if (item.category == FileCategory.IMAGE) R.drawable.ic_image else R.drawable.ic_video)
                .error(if (item.category == FileCategory.IMAGE) R.drawable.ic_image else R.drawable.ic_video)
                .into(imageView)
            return
        }

        // Load audio album art (Glide can extract embedded cover art from audio files)
        if (item.category == FileCategory.AUDIO && file.exists() && isGrid) {
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(ctx)
                .load(android.net.Uri.fromFile(file))
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .placeholder(R.drawable.ic_audio)
                .error(R.drawable.ic_audio)
                .into(imageView)
            return
        }

        // Load APK icon asynchronously via Glide (avoids blocking UI thread with getPackageArchiveInfo)
        if (item.category == FileCategory.APK && file.exists() && isGrid) {
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            Glide.with(ctx)
                .load(android.net.Uri.fromFile(file))
                .placeholder(R.drawable.ic_apk)
                .error(R.drawable.ic_apk)
                .into(imageView)
            return
        }

        // Fallback: category icon for all other file types
        Glide.with(ctx).clear(imageView)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
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

        // §G1: Set contentDescription on file icon for accessibility
        imageView.contentDescription = ctx.getString(R.string.a11y_file_icon,
            ctx.getString(item.category.displayNameRes))
    }

    // ── Junk background colors ──────────────────────────────────────────

    private val JUNK_BG_COLOR_RES = mapOf(
        JunkType.CACHE to R.color.junkCacheBg,
        JunkType.TEMP to R.color.junkTempBg,
        JunkType.THUMBNAIL to R.color.junkThumbnailBg,
        JunkType.OLD_DOWNLOAD to R.color.junkOldDownloadBg,
        JunkType.LOG to R.color.junkLogBg
    )

    /** Background tint for junk items (light version of their category color). */
    fun junkBgColor(ctx: Context, item: FileItem): Int =
        ContextCompat.getColor(ctx, JUNK_BG_COLOR_RES[classifyJunk(item)] ?: R.color.junkOldDownloadBg)

    // ── Size severity background colors ─────────────────────────────────

    private val SIZE_BG_COLOR_RES = mapOf(
        SizeSeverity.HUGE to R.color.sizeHugeBg,
        SizeSeverity.LARGE to R.color.sizeLargeBg,
        SizeSeverity.MEDIUM to R.color.sizeMediumBg
    )

    /** Background tint for large file items (light version of their severity color). */
    fun sizeBgColor(ctx: Context, item: FileItem): Int =
        ContextCompat.getColor(ctx, SIZE_BG_COLOR_RES[sizeSeverity(item)] ?: R.color.sizeMediumBg)

    // ── Meta text builder ────────────────────────────────────────────────

    // D1: ThreadLocal date format to avoid per-call allocation while remaining thread-safe
    private val dateFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
    }

    fun buildMeta(textView: TextView, item: FileItem) {
        val dateStr = dateFormat.get()!!.format(Date(item.lastModified))
        textView.text = "${item.sizeReadable}  \u2022  $dateStr"
    }
}
