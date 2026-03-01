package com.filecleaner.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.FileOpener

/**
 * Quick preview dialog for files (P8).
 * - Images: full-size preview via Glide
 * - Text files: first 10KB in monospace
 * - Others: fallback to external open
 */
object FilePreviewDialog {

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "csv", "json", "xml", "html", "htm", "log",
        "yml", "yaml", "toml", "ini", "cfg", "conf", "properties",
        "sh", "bat", "py", "js", "ts", "kt", "java", "c", "cpp",
        "h", "css", "scss", "sql", "gradle", "gitignore"
    )

    private const val MAX_TEXT_BYTES = 10 * 1024 // 10 KB

    fun show(context: Context, item: FileItem) {
        when {
            item.category == FileCategory.IMAGE -> showImagePreview(context, item)
            item.extension in TEXT_EXTENSIONS -> showTextPreview(context, item)
            else -> {
                // Fallback: open with external app
                FileOpener.open(context, item.file)
            }
        }
    }

    private fun showImagePreview(context: Context, item: FileItem) {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.5).toInt()
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        Glide.with(context)
            .load(item.file)
            .into(imageView)

        AlertDialog.Builder(context)
            .setTitle(item.name)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(context.getString(R.string.ctx_open)) { _, _ ->
                FileOpener.open(context, item.file)
            }
            .show()
    }

    private fun showTextPreview(context: Context, item: FileItem) {
        val content = try {
            val bytes = item.file.inputStream().use { it.readNBytes(MAX_TEXT_BYTES) }
            val text = String(bytes, Charsets.UTF_8)
            if (item.file.length() > MAX_TEXT_BYTES) {
                text + "\n\n… [truncated at 10 KB]"
            } else {
                text
            }
        } catch (e: Exception) {
            context.getString(R.string.preview_error, e.localizedMessage ?: "")
        }

        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val textView = TextView(context).apply {
            text = content
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(padding, padding, padding, padding)
            setTextIsSelectable(true)
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.5).toInt()
            )
            addView(textView)
        }

        AlertDialog.Builder(context)
            .setTitle(item.name)
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(context.getString(R.string.ctx_open)) { _, _ ->
                FileOpener.open(context, item.file)
            }
            .show()
    }
}
