package com.filecleaner.app.ui.common

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enhanced batch rename dialog (P5).
 *
 * Modes:
 * 0 — Pattern: {name}, {ext}, {n}, {date}, {time}, {datetime}
 * 1 — Prefix / Suffix
 * 2 — Find & Replace (text or regex)
 * 3 — Change case (lowercase, UPPERCASE, Title Case)
 */
object BatchRenameDialog {

    private const val MODE_PATTERN = 0
    private const val MODE_PREFIX_SUFFIX = 1
    private const val MODE_FIND_REPLACE = 2
    private const val MODE_CHANGE_CASE = 3

    fun show(
        context: Context,
        files: List<FileItem>,
        onConfirm: (List<Pair<FileItem, String>>) -> Unit
    ) {
        val padding = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val smallPadding = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }

        // Mode selector spinner
        val modeLabels = listOf(
            context.getString(R.string.batch_mode_pattern),
            context.getString(R.string.batch_mode_prefix_suffix),
            context.getString(R.string.batch_mode_find_replace),
            context.getString(R.string.batch_mode_change_case)
        )
        val modeSpinner = Spinner(context)
        modeSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        root.addView(modeSpinner)

        // Dynamic fields container (swapped per mode)
        val fieldsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, smallPadding, 0, 0)
        }
        root.addView(fieldsContainer)

        // Preview text (always shown)
        val previewText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_label))
            setTypeface(null, android.graphics.Typeface.ITALIC)
            setPadding(0, smallPadding, 0, 0)
        }
        root.addView(previewText)

        // --- Mode 0: Pattern fields ---
        val patternInput = EditText(context).apply {
            hint = context.getString(R.string.batch_rename_pattern_hint_extended)
            setText("{name}_{n}.{ext}")
            layoutParams = wrapLp()
        }
        val startNumInput = EditText(context).apply {
            hint = context.getString(R.string.batch_rename_start_hint)
            setText("1")
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = wrapLp()
        }

        // --- Mode 1: Prefix/Suffix fields ---
        val prefixInput = EditText(context).apply {
            hint = context.getString(R.string.batch_prefix_hint)
            layoutParams = wrapLp()
        }
        val suffixInput = EditText(context).apply {
            hint = context.getString(R.string.batch_suffix_hint)
            layoutParams = wrapLp()
        }

        // --- Mode 2: Find/Replace fields ---
        val findInput = EditText(context).apply {
            hint = context.getString(R.string.batch_find_hint)
            layoutParams = wrapLp()
        }
        val replaceInput = EditText(context).apply {
            hint = context.getString(R.string.batch_replace_hint)
            layoutParams = wrapLp()
        }
        val regexCheck = CheckBox(context).apply {
            text = context.getString(R.string.batch_use_regex)
        }

        // --- Mode 3: Case fields ---
        val caseLabels = listOf(
            context.getString(R.string.batch_case_lower),
            context.getString(R.string.batch_case_upper),
            context.getString(R.string.batch_case_title)
        )
        val caseSpinner = Spinner(context)
        caseSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, caseLabels)

        // Compute rename for one file based on current mode
        fun computeRename(file: FileItem, index: Int): String {
            return when (modeSpinner.selectedItemPosition) {
                MODE_PATTERN -> {
                    val pattern = patternInput.text.toString()
                    val startNum = startNumInput.text.toString().toIntOrNull() ?: 1
                    val padWidth = files.size.toString().length.coerceAtLeast(3)
                    applyPattern(file, pattern, startNum + index, padWidth)
                }
                MODE_PREFIX_SUFFIX -> {
                    val prefix = prefixInput.text.toString()
                    val suffix = suffixInput.text.toString()
                    val nameNoExt = file.name.substringBeforeLast('.', file.name)
                    val ext = file.extension
                    if (ext.isNotEmpty()) "$prefix$nameNoExt$suffix.$ext" else "$prefix$nameNoExt$suffix"
                }
                MODE_FIND_REPLACE -> {
                    val find = findInput.text.toString()
                    val replace = replaceInput.text.toString()
                    if (find.isEmpty()) return file.name
                    val nameNoExt = file.name.substringBeforeLast('.', file.name)
                    val ext = file.extension
                    val newName = if (regexCheck.isChecked) {
                        try { nameNoExt.replace(Regex(find), replace) } catch (_: Exception) { nameNoExt }
                    } else {
                        nameNoExt.replace(find, replace)
                    }
                    if (ext.isNotEmpty()) "$newName.$ext" else newName
                }
                MODE_CHANGE_CASE -> {
                    val nameNoExt = file.name.substringBeforeLast('.', file.name)
                    val ext = file.extension
                    val transformed = when (caseSpinner.selectedItemPosition) {
                        0 -> nameNoExt.lowercase()
                        1 -> nameNoExt.uppercase()
                        2 -> nameNoExt.split(Regex("[\\s_\\-]+")).joinToString(" ") { word ->
                            word.replaceFirstChar { it.titlecase() }
                        }
                        else -> nameNoExt
                    }
                    if (ext.isNotEmpty()) "$transformed.$ext" else transformed
                }
                else -> file.name
            }
        }

        fun updatePreview() {
            val preview = buildString {
                for ((index, file) in files.take(3).withIndex()) {
                    val newName = computeRename(file, index)
                    appendLine("${file.name} \u2192 $newName")
                }
                if (files.size > 3) {
                    appendLine("\u2026 and ${files.size - 3} more")
                }
            }
            previewText.text = preview
        }

        fun buildModeFields(mode: Int) {
            fieldsContainer.removeAllViews()
            when (mode) {
                MODE_PATTERN -> {
                    fieldsContainer.addView(patternInput)
                    fieldsContainer.addView(startNumInput)
                }
                MODE_PREFIX_SUFFIX -> {
                    fieldsContainer.addView(prefixInput)
                    fieldsContainer.addView(suffixInput)
                }
                MODE_FIND_REPLACE -> {
                    fieldsContainer.addView(findInput)
                    fieldsContainer.addView(replaceInput)
                    fieldsContainer.addView(regexCheck)
                }
                MODE_CHANGE_CASE -> {
                    fieldsContainer.addView(caseSpinner)
                }
            }
            updatePreview()
        }

        // Mode change listener
        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                buildModeFields(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Live preview watcher
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        }
        patternInput.addTextChangedListener(watcher)
        startNumInput.addTextChangedListener(watcher)
        prefixInput.addTextChangedListener(watcher)
        suffixInput.addTextChangedListener(watcher)
        findInput.addTextChangedListener(watcher)
        replaceInput.addTextChangedListener(watcher)
        regexCheck.setOnCheckedChangeListener { _, _ -> updatePreview() }
        caseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) = updatePreview()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Initial state
        buildModeFields(MODE_PATTERN)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.batch_rename_title, files.size))
            .setView(root)
            .setPositiveButton(context.getString(R.string.ctx_rename)) { _, _ ->
                val renames = files.mapIndexed { index, file ->
                    file to computeRename(file, index)
                }.filter { (original, newName) ->
                    // C2: Skip renames that produce invalid filenames
                    newName.isNotBlank() && newName != original.name && newName.none { c ->
                        c in charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|')
                    }
                }
                if (renames.isNotEmpty()) onConfirm(renames)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun applyPattern(file: FileItem, pattern: String, num: Int, padWidth: Int): String {
        val nameNoExt = file.name.substringBeforeLast('.', file.name)
        val ext = file.extension
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HHmmss", Locale.getDefault())
        val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        val date = Date(file.lastModified)
        return pattern
            .replace("{name}", nameNoExt)
            .replace("{ext}", ext)
            .replace("{n}", num.toString().padStart(padWidth, '0'))
            .replace("{date}", dateFmt.format(date))
            .replace("{time}", timeFmt.format(date))
            .replace("{datetime}", dateTimeFmt.format(date))
    }

    private fun wrapLp() = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
