package com.filecleaner.app.ui.common

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem

/**
 * Dialog for configuring compression of selected files.
 * Allows setting a custom archive name before compressing.
 */
object CompressDialog {

    fun show(
        context: Context,
        files: List<FileItem>,
        onConfirm: (archiveName: String, filePaths: List<String>) -> Unit
    ) {
        val padding = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }

        // Summary
        val summary = TextView(context).apply {
            text = context.resources.getQuantityString(R.plurals.compress_summary, files.size, files.size)
        }
        container.addView(summary)

        // Archive name input
        val defaultName = if (files.size == 1) {
            "${files.first().file.nameWithoutExtension}.zip"
        } else {
            "archive.zip"
        }
        val nameInput = EditText(context).apply {
            hint = context.getString(R.string.compress_name_hint)
            setText(defaultName)
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(nameInput)

        // File list preview
        val previewText = TextView(context).apply {
            val preview = buildString {
                for (file in files.take(5)) {
                    appendLine("\u2022 ${file.name}")
                }
                if (files.size > 5) {
                    appendLine("\u2026 and ${files.size - 5} more")
                }
            }
            text = preview
            setPadding(0, context.resources.getDimensionPixelSize(R.dimen.spacing_sm), 0, 0)
        }
        container.addView(previewText)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.compress_title))
            .setView(container)
            .setPositiveButton(context.getString(R.string.ctx_compress)) { _, _ ->
                var name = nameInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (!name.endsWith(".zip", ignoreCase = true)) name += ".zip"
                    onConfirm(name, files.map { it.path })
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
}
