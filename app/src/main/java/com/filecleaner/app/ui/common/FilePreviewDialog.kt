package com.filecleaner.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.navigation.findNavController
import com.bumptech.glide.Glide
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.viewer.FileViewerFragment
import com.filecleaner.app.utils.FileOpener

/**
 * Quick preview dialog for files.
 * - Images: full-size preview via Glide
 * - Text/code files: first 10KB in monospace (60+ extensions)
 * - PDF/Audio/Video/HTML/Markdown: opens fullscreen viewer directly
 * - Others: fallback to external open
 *
 * All previews include a "View fullscreen" button to open FileViewerFragment.
 */
object FilePreviewDialog {

    private val TEXT_EXTENSIONS = setOf(
        // Plain text & config
        "txt", "csv", "log", "ini", "cfg", "conf", "properties",
        "yml", "yaml", "toml", "env", "gitignore", "dockerignore",
        "editorconfig", "htaccess", "npmrc", "nvmrc",
        // Markup (non-rendered)
        "xml", "svg", "plist",
        // Code: JVM
        "kt", "kts", "java", "groovy", "gradle", "scala",
        // Code: Web
        "js", "jsx", "ts", "tsx", "css", "scss", "sass", "less",
        "vue", "svelte",
        // Code: Systems
        "c", "cpp", "cc", "cxx", "h", "hpp", "hxx",
        "rs", "go", "zig",
        // Code: Scripting
        "py", "rb", "php", "pl", "pm", "lua", "r",
        "sh", "bash", "zsh", "fish", "bat", "ps1", "cmd",
        // Code: Mobile / other
        "swift", "m", "mm", "dart",
        // Code: Functional / ML
        "hs", "ml", "ex", "exs", "erl", "clj",
        // Data & query
        "sql", "graphql", "gql", "proto",
        // Build & CI
        "makefile", "cmake", "dockerfile",
        "tf", "hcl", "nix",
        // Docs
        "tex", "latex", "bib", "srt", "sub", "ass", "vtt",
        "diff", "patch",
        // Other
        "json", "json5", "jsonc", "jsonl"
    )

    // Types that open directly in the fullscreen viewer
    private val VIEWER_DIRECT_EXTENSIONS = setOf(
        "pdf", "html", "htm", "md", "markdown", "mdown", "mkd",
        "zip", "jar", "apk"
    )

    private const val MAX_TEXT_BYTES = 10 * 1024 // 10 KB

    fun show(context: Context, item: FileItem) {
        when {
            item.extension in VIEWER_DIRECT_EXTENSIONS -> navigateToViewer(context, item)
            item.category == FileCategory.AUDIO -> navigateToViewer(context, item)
            item.category == FileCategory.VIDEO -> navigateToViewer(context, item)
            item.category == FileCategory.IMAGE -> showImagePreview(context, item)
            item.extension in TEXT_EXTENSIONS -> showTextPreview(context, item)
            else -> FileOpener.open(context, item.file)
        }
    }

    private fun navigateToViewer(context: Context, item: FileItem) {
        try {
            val activity = context as? android.app.Activity ?: return
            val navController = activity.findNavController(R.id.nav_host_fragment)
            val args = Bundle().apply { putString(FileViewerFragment.ARG_FILE_PATH, item.path) }
            navController.navigate(R.id.fileViewerFragment, args)
        } catch (_: Exception) {
            FileOpener.open(context, item.file)
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
            .setNeutralButton(context.getString(R.string.viewer_open_fullscreen)) { _, _ ->
                navigateToViewer(context, item)
            }
            .setNegativeButton(context.getString(R.string.ctx_open)) { _, _ ->
                FileOpener.open(context, item.file)
            }
            .show()
    }

    private fun showTextPreview(context: Context, item: FileItem) {
        val content = try {
            val bytes = item.file.inputStream().use { it.readNBytes(MAX_TEXT_BYTES) }
            val text = String(bytes, Charsets.UTF_8)
            if (item.file.length() > MAX_TEXT_BYTES) {
                text + "\n\n\u2026 [truncated at 10 KB]"
            } else {
                text
            }
        } catch (e: Exception) {
            context.getString(R.string.preview_error, e.localizedMessage ?: "")
        }

        val padding = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val textView = TextView(context).apply {
            text = content
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_body_small))
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
            .setNeutralButton(context.getString(R.string.viewer_open_fullscreen)) { _, _ ->
                navigateToViewer(context, item)
            }
            .setNegativeButton(context.getString(R.string.ctx_open)) { _, _ ->
                FileOpener.open(context, item.file)
            }
            .show()
    }
}
