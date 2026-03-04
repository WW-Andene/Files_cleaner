package com.filecleaner.app.ui.adapters

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.data.UserPreferences

/** Shared adapter utilities — single source of truth for file display logic. */
object FileItemUtils {

    private const val THUMB_SIZE_LIST = 128
    private const val THUMB_SIZE_GRID = 256
    private const val DATE_SKELETON = "dd MMM yyyy"

    fun categoryDrawable(cat: FileCategory): Int = when (cat) {
        FileCategory.IMAGE    -> R.drawable.ic_image
        FileCategory.VIDEO    -> R.drawable.ic_video
        FileCategory.AUDIO    -> R.drawable.ic_audio
        FileCategory.DOCUMENT -> R.drawable.ic_document
        FileCategory.APK      -> R.drawable.ic_apk
        FileCategory.ARCHIVE  -> R.drawable.ic_archive
        FileCategory.DOWNLOAD -> R.drawable.ic_download
        else                  -> R.drawable.ic_file
    }

    /** Loads a thumbnail for images/videos or sets a category icon for other file types. */
    fun loadThumbnail(icon: ImageView, item: FileItem, isGridMode: Boolean) {
        if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
            val thumbSize = if (isGridMode) THUMB_SIZE_GRID else THUMB_SIZE_LIST
            val fallback = categoryDrawable(item.category)
            Glide.with(icon)
                .load(item.file)
                .override(thumbSize, thumbSize)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .placeholder(fallback)
                .error(fallback)
                .centerCrop()
                .into(icon)
        } else {
            Glide.with(icon).clear(icon)
            icon.setImageResource(categoryDrawable(item.category))
            icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    /** Builds and sets the "size  ·  date" meta text. Returns the formatted string. */
    fun buildMeta(metaView: TextView, item: FileItem): String {
        val date = if (item.lastModified > 0L) {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(
                metaView.resources.configuration.locales[0], DATE_SKELETON)
            android.text.format.DateFormat.format(pattern, item.lastModified)
        } else {
            metaView.context.getString(R.string.unknown_date)
        }
        val prefix = buildString {
            try {
                if (UserPreferences.isFavorite(item.path)) append("\u2B50 ")
                if (UserPreferences.isProtected(item.path)) append("\uD83D\uDEE1\uFE0F ")
            } catch (_: Exception) { /* prefs not initialized */ }
        }
        val text = prefix + metaView.context.getString(R.string.file_meta_format, item.sizeReadable, date)
        metaView.text = text
        return text
    }

    fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()

    /**
     * Cached adapter color palette — resolves theme colors once per context.
     * I3: Extracted from FileAdapter and BrowseAdapter to eliminate duplication.
     */
    data class AdapterColors(
        val surface: Int,
        val border: Int,
        val selectedBg: Int = 0,
        val selectedBorder: Int = 0
    )

    private var cachedColors: AdapterColors? = null
    private var cachedColorsWithSelection: AdapterColors? = null

    fun resolveColors(ctx: android.content.Context): AdapterColors {
        cachedColors?.let { return it }
        val colors = AdapterColors(
            surface = androidx.core.content.ContextCompat.getColor(ctx, R.color.surfaceColor),
            border = androidx.core.content.ContextCompat.getColor(ctx, R.color.borderDefault)
        )
        cachedColors = colors
        return colors
    }

    fun resolveColorsWithSelection(ctx: android.content.Context): AdapterColors {
        cachedColorsWithSelection?.let { return it }
        val colors = AdapterColors(
            surface = androidx.core.content.ContextCompat.getColor(ctx, R.color.surfaceColor),
            border = androidx.core.content.ContextCompat.getColor(ctx, R.color.borderDefault),
            selectedBg = androidx.core.content.ContextCompat.getColor(ctx, R.color.selectedBackground),
            selectedBorder = androidx.core.content.ContextCompat.getColor(ctx, R.color.selectedBorder)
        )
        cachedColorsWithSelection = colors
        return colors
    }
}
