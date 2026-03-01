package com.filecleaner.app.ui.adapters

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem

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
        val text = "${item.sizeReadable}  \u2022  $date"
        metaView.text = text
        return text
    }

    fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()
}
