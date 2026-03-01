package com.filecleaner.app.ui.common

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem

/**
 * Batch rename dialog (P5).
 *
 * Supports pattern-based renaming with placeholders:
 * - {name} — original filename without extension
 * - {ext} — original extension
 * - {n} — sequential number (zero-padded)
 *
 * Example: "photo_{n}.{ext}" renames to "photo_001.jpg", "photo_002.jpg", etc.
 */
object BatchRenameDialog {

    fun show(
        context: Context,
        files: List<FileItem>,
        onConfirm: (List<Pair<FileItem, String>>) -> Unit
    ) {
        val padding = (16 * context.resources.displayMetrics.density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }

        val hintText = TextView(context).apply {
            text = context.getString(R.string.batch_rename_hint)
            textSize = 12f
        }
        container.addView(hintText)

        val patternInput = EditText(context).apply {
            hint = context.getString(R.string.batch_rename_pattern_hint)
            setText("{name}_{n}.{ext}")
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(patternInput)

        val startNumInput = EditText(context).apply {
            hint = context.getString(R.string.batch_rename_start_hint)
            setText("1")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(startNumInput)

        val previewText = TextView(context).apply {
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.ITALIC)
            setPadding(0, padding / 2, 0, 0)
        }
        container.addView(previewText)

        fun updatePreview() {
            val pattern = patternInput.text.toString()
            val startNum = startNumInput.text.toString().toIntOrNull() ?: 1
            val padWidth = files.size.toString().length.coerceAtLeast(3)
            val preview = buildString {
                val previewCount = files.take(3).size
                for ((index, file) in files.take(3).withIndex()) {
                    val newName = applyPattern(file, pattern, startNum + index, padWidth)
                    appendLine("${file.name} → $newName")
                }
                if (files.size > 3) {
                    appendLine("… and ${files.size - 3} more")
                }
            }
            previewText.text = preview
        }

        updatePreview()
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        patternInput.addTextChangedListener(watcher)
        startNumInput.addTextChangedListener(watcher)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.batch_rename_title, files.size))
            .setView(container)
            .setPositiveButton(context.getString(R.string.ctx_rename)) { _, _ ->
                val pattern = patternInput.text.toString()
                val startNum = startNumInput.text.toString().toIntOrNull() ?: 1
                val padWidth = files.size.toString().length.coerceAtLeast(3)
                val renames = files.mapIndexed { index, file ->
                    file to applyPattern(file, pattern, startNum + index, padWidth)
                }
                onConfirm(renames)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun applyPattern(file: FileItem, pattern: String, num: Int, padWidth: Int): String {
        val nameNoExt = file.name.substringBeforeLast('.', file.name)
        val ext = file.extension
        return pattern
            .replace("{name}", nameNoExt)
            .replace("{ext}", ext)
            .replace("{n}", num.toString().padStart(padWidth, '0'))
    }
}
