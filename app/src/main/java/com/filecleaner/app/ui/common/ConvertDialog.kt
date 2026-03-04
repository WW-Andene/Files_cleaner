package com.filecleaner.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.FileConverter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Material Design conversion dialog for files.
 *
 * Shows contextually appropriate conversion options based on the source file type,
 * with clear descriptions explaining what each option does. For video files, provides
 * a seek bar to choose the exact timestamp for frame extraction instead of the
 * misleading "10s/20s frames" options.
 *
 * Supported conversions:
 * - Images: convert to PNG/JPG/WEBP/BMP, resize, export to PDF
 * - PDFs: extract pages as PNG or JPG images
 * - Videos: extract a single frame at a chosen timestamp, extract evenly-spaced key frames
 * - Audio: extract embedded album art as PNG or JPG
 * - Text/Code/Markdown/HTML/CSV: convert to PDF, CSV to formatted text table
 */
object ConvertDialog {

    /** Extensions that can be converted to PDF via text rendering. */
    private val TEXT_CONVERTIBLE = setOf(
        "txt", "log", "ini", "cfg", "conf", "properties",
        "yml", "yaml", "toml", "env",
        "kt", "kts", "java", "py", "js", "ts", "c", "cpp", "h",
        "rs", "go", "rb", "php", "swift", "dart", "lua", "r",
        "sh", "bash", "bat", "ps1",
        "sql", "graphql", "proto",
        "makefile", "cmake", "dockerfile",
        "tex", "latex", "diff", "patch",
        "xml", "json", "json5", "jsonc", "jsonl",
        "css", "scss", "sass", "less",
        "gradle", "groovy", "scala"
    )

    fun show(context: Context, item: FileItem, onResult: (FileConverter.ConvertResult) -> Unit) {
        val ext = item.extension
        val category = item.category

        // For video files, show the special video conversion dialog with seek bar
        if (category == FileCategory.VIDEO) {
            showVideoConvertDialog(context, item, onResult)
            return
        }

        // For images, show the image conversion dialog with resize option
        if (category == FileCategory.IMAGE) {
            showImageConvertDialog(context, item, onResult)
            return
        }

        // For all other types, build a list of conversion options
        val options = buildOptionsForNonMediaFile(context, item)
        if (options.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.convert_title))
                .setMessage(context.getString(R.string.convert_unsupported))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        showOptionsList(context, options, onResult)
    }

    // =========================================================================
    // IMAGE CONVERSION DIALOG
    // =========================================================================

    private fun showImageConvertDialog(context: Context, item: FileItem, onResult: (FileConverter.ConvertResult) -> Unit) {
        val options = mutableListOf<ConvertOption>()

        // Image format conversions
        for (fmt in FileConverter.ImageFormat.entries) {
            val targetExt = if (fmt == FileConverter.ImageFormat.WEBP_LOSSLESS) "webp" else fmt.extension
            if (targetExt != item.extension || fmt == FileConverter.ImageFormat.WEBP_LOSSLESS) {
                if (fmt == FileConverter.ImageFormat.WEBP && item.extension == "webp") continue
                if (fmt == FileConverter.ImageFormat.WEBP_LOSSLESS && item.extension == "webp") continue
                options.add(ConvertOption(
                    title = context.getString(R.string.convert_to_format, fmt.label),
                    description = context.getString(R.string.convert_image_desc, fmt.label, fmt.extension),
                    action = { FileConverter.convertImage(item.path, fmt) }
                ))
            }
        }

        // Image to PDF
        options.add(ConvertOption(
            title = context.getString(R.string.convert_to_pdf),
            description = context.getString(R.string.convert_image_to_pdf_desc),
            action = {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                FileConverter.imagesToPdf(listOf(item.path), outputPath)
            }
        ))

        // Resize option -- handled specially with a sub-dialog
        options.add(ConvertOption(
            title = context.getString(R.string.convert_resize),
            description = context.getString(R.string.convert_resize_desc),
            action = null // Handled by custom click
        ))

        showOptionsListWithResize(context, item, options, onResult)
    }

    /**
     * Shows the options list for images, with special handling for the resize option
     * which opens a sub-dialog to choose dimensions.
     */
    private fun showOptionsListWithResize(
        context: Context,
        item: FileItem,
        options: List<ConvertOption>,
        onResult: (FileConverter.ConvertResult) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * dp).toInt()
            setPadding(0, pad / 2, 0, pad / 2)
        }
        scrollView.addView(container)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.convert_title))
            .setView(scrollView)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        for (option in options) {
            val row = buildOptionRow(context, option)
            row.setOnClickListener {
                dialog.dismiss()
                if (option.action != null) {
                    runConversion(context, option.action, onResult)
                } else {
                    // This is the resize option -- show resize sub-dialog
                    showResizeDialog(context, item, onResult)
                }
            }
            container.addView(row)
        }

        dialog.show()
    }

    private fun showResizeDialog(context: Context, item: FileItem, onResult: (FileConverter.ConvertResult) -> Unit) {
        val dp = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * dp).toInt()
            setPadding(pad, (16 * dp).toInt(), pad, (8 * dp).toInt())
        }

        val descText = TextView(context).apply {
            text = context.getString(R.string.convert_resize_instruction)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
        }
        container.addView(descText)

        // Width input
        val widthLabel = TextView(context).apply {
            text = context.getString(R.string.convert_max_width)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (16 * dp).toInt()
            layoutParams = lp
        }
        container.addView(widthLabel)

        val widthInput = EditText(context).apply {
            hint = "1920"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        container.addView(widthInput)

        // Height input
        val heightLabel = TextView(context).apply {
            text = context.getString(R.string.convert_max_height)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (8 * dp).toInt()
            layoutParams = lp
        }
        container.addView(heightLabel)

        val heightInput = EditText(context).apply {
            hint = "1080"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        container.addView(heightInput)

        // Format selector note
        val noteText = TextView(context).apply {
            text = context.getString(R.string.convert_resize_format_note)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(ContextCompat.getColor(context, R.color.textTertiary))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }
        container.addView(noteText)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.convert_resize))
            .setView(container)
            .setPositiveButton(context.getString(R.string.convert_action)) { _, _ ->
                val maxW = widthInput.text.toString().toIntOrNull() ?: 1920
                val maxH = heightInput.text.toString().toIntOrNull() ?: 1080
                if (maxW <= 0 || maxH <= 0) return@setPositiveButton
                // Keep the same format as the original file
                val fmt = when (item.extension) {
                    "png" -> FileConverter.ImageFormat.PNG
                    "webp" -> FileConverter.ImageFormat.WEBP
                    "bmp" -> FileConverter.ImageFormat.BMP
                    else -> FileConverter.ImageFormat.JPG
                }
                runConversion(context, { FileConverter.resizeImage(item.path, maxW, maxH, fmt) }, onResult)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    // =========================================================================
    // VIDEO CONVERSION DIALOG
    // =========================================================================

    /**
     * Shows a video-specific conversion dialog with:
     * 1. "Extract frame at timestamp" with a seek bar to choose the exact position
     * 2. "Extract key frames" to get evenly-spaced frames across the entire video
     */
    private fun showVideoConvertDialog(context: Context, item: FileItem, onResult: (FileConverter.ConvertResult) -> Unit) {
        val dp = context.resources.displayMetrics.density
        val durationMs = FileConverter.getVideoDurationMs(item.path)

        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * dp).toInt()
            setPadding(pad, (16 * dp).toInt(), pad, (8 * dp).toInt())
        }
        scrollView.addView(container)

        // ---- Section 1: Extract single frame ----
        val sectionTitle1 = TextView(context).apply {
            text = context.getString(R.string.convert_video_section_single_frame)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(sectionTitle1)

        val sectionDesc1 = TextView(context).apply {
            text = context.getString(R.string.convert_video_single_frame_desc)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        container.addView(sectionDesc1)

        // Timestamp display
        val timeDisplay = TextView(context).apply {
            text = context.getString(R.string.convert_video_timestamp, "0:00",
                if (durationMs > 0) FileConverter.formatTimeDisplay(durationMs) else "?:??")
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }
        container.addView(timeDisplay)

        // Seek bar for timestamp selection
        var selectedTimeMs = 0L
        val seekBar = SeekBar(context).apply {
            max = if (durationMs > 0) durationMs.toInt() else 100
            progress = 0
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedTimeMs = progress.toLong()
                    timeDisplay.text = context.getString(
                        R.string.convert_video_timestamp,
                        FileConverter.formatTimeDisplay(selectedTimeMs),
                        if (durationMs > 0) FileConverter.formatTimeDisplay(durationMs) else "?:??"
                    )
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        if (durationMs <= 0) {
            seekBar.isEnabled = false
        }
        container.addView(seekBar)

        // Format choice row for single frame
        val formatRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }

        val btnExtractPng = buildActionButton(context, context.getString(R.string.convert_extract_as_png))
        val btnExtractJpg = buildActionButton(context, context.getString(R.string.convert_extract_as_jpg))
        formatRow1.addView(btnExtractPng)
        formatRow1.addView(btnExtractJpg)
        container.addView(formatRow1)

        // ---- Divider ----
        val divider = View(context).apply {
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt()
            )
            lp.topMargin = (20 * dp).toInt()
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
            setBackgroundColor(ContextCompat.getColor(context, R.color.borderDefault))
        }
        container.addView(divider)

        // ---- Section 2: Extract key frames ----
        val sectionTitle2 = TextView(context).apply {
            text = context.getString(R.string.convert_video_section_key_frames)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(sectionTitle2)

        val sectionDesc2 = TextView(context).apply {
            text = context.getString(R.string.convert_video_key_frames_desc)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (4 * dp).toInt()
            layoutParams = lp
        }
        container.addView(sectionDesc2)

        // Frame count input
        val countRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }

        val countLabel = TextView(context).apply {
            text = context.getString(R.string.convert_video_frame_count)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        countRow.addView(countLabel)

        val countInput = EditText(context).apply {
            hint = "10"
            setText("10")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((80 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        countRow.addView(countInput)
        container.addView(countRow)

        // Format choice row for key frames
        val formatRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (12 * dp).toInt()
            layoutParams = lp
        }

        val btnKeyPng = buildActionButton(context, context.getString(R.string.convert_extract_key_png))
        val btnKeyJpg = buildActionButton(context, context.getString(R.string.convert_extract_key_jpg))
        formatRow2.addView(btnKeyPng)
        formatRow2.addView(btnKeyJpg)
        container.addView(formatRow2)

        // Show the dialog
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.convert_title))
            .setView(scrollView)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        // Wire button actions
        btnExtractPng.setOnClickListener {
            dialog.dismiss()
            runConversion(context, {
                FileConverter.extractFrameAtTime(item.path, selectedTimeMs, FileConverter.ImageFormat.PNG)
            }, onResult)
        }
        btnExtractJpg.setOnClickListener {
            dialog.dismiss()
            runConversion(context, {
                FileConverter.extractFrameAtTime(item.path, selectedTimeMs, FileConverter.ImageFormat.JPG, quality = 90)
            }, onResult)
        }
        btnKeyPng.setOnClickListener {
            dialog.dismiss()
            val count = countInput.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 10
            val outDir = "${item.file.parent}/${item.file.nameWithoutExtension}_frames"
            runConversion(context, {
                FileConverter.extractKeyFrames(item.path, outDir, count, FileConverter.ImageFormat.PNG)
            }, onResult)
        }
        btnKeyJpg.setOnClickListener {
            dialog.dismiss()
            val count = countInput.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 10
            val outDir = "${item.file.parent}/${item.file.nameWithoutExtension}_frames"
            runConversion(context, {
                FileConverter.extractKeyFrames(item.path, outDir, count, FileConverter.ImageFormat.JPG, quality = 85)
            }, onResult)
        }

        dialog.show()
    }

    // =========================================================================
    // NON-MEDIA FILE OPTIONS
    // =========================================================================

    private fun buildOptionsForNonMediaFile(context: Context, item: FileItem): List<ConvertOption> {
        val options = mutableListOf<ConvertOption>()
        val ext = item.extension

        // PDF -> Images
        if (ext == "pdf") {
            for (fmt in FileConverter.PdfImageFormat.entries) {
                options.add(ConvertOption(
                    title = context.getString(R.string.convert_pdf_to_images, fmt.label),
                    description = context.getString(R.string.convert_pdf_to_images_desc, fmt.label),
                    action = {
                        val outputDir = "${item.file.parent}/${item.file.nameWithoutExtension}_pages"
                        FileConverter.pdfToImages(item.path, outputDir, fmt)
                    }
                ))
            }
        }

        // Audio -> Album art
        if (item.category == FileCategory.AUDIO) {
            options.add(ConvertOption(
                title = context.getString(R.string.convert_extract_album_art_png),
                description = context.getString(R.string.convert_extract_album_art_desc),
                action = { FileConverter.audioToAlbumArt(item.path, FileConverter.ImageFormat.PNG) }
            ))
            options.add(ConvertOption(
                title = context.getString(R.string.convert_extract_album_art_jpg),
                description = context.getString(R.string.convert_extract_album_art_desc),
                action = { FileConverter.audioToAlbumArt(item.path, FileConverter.ImageFormat.JPG, quality = 90) }
            ))
        }

        // Text/Code -> PDF
        if (ext in TEXT_CONVERTIBLE) {
            options.add(ConvertOption(
                title = context.getString(R.string.convert_to_pdf),
                description = context.getString(R.string.convert_text_to_pdf_desc),
                action = {
                    val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                    FileConverter.textToPdf(item.path, outputPath)
                }
            ))
        }

        // HTML -> PDF
        if (ext == "html" || ext == "htm") {
            options.add(ConvertOption(
                title = context.getString(R.string.convert_to_pdf),
                description = context.getString(R.string.convert_html_to_pdf_desc),
                action = {
                    val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                    FileConverter.textToPdf(item.path, outputPath)
                }
            ))
        }

        // Markdown -> PDF
        if (ext == "md" || ext == "markdown" || ext == "mdown" || ext == "mkd") {
            options.add(ConvertOption(
                title = context.getString(R.string.convert_to_pdf),
                description = context.getString(R.string.convert_markdown_to_pdf_desc),
                action = {
                    val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                    FileConverter.textToPdf(item.path, outputPath)
                }
            ))
        }

        // CSV -> Formatted table
        if (ext == "csv") {
            options.add(ConvertOption(
                title = context.getString(R.string.convert_csv_to_table),
                description = context.getString(R.string.convert_csv_to_table_desc),
                action = {
                    val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}_table.txt"
                    FileConverter.csvToText(item.path, outputPath)
                }
            ))
        }

        return options
    }

    // =========================================================================
    // SHARED UI HELPERS
    // =========================================================================

    /** A single conversion option with title, description, and conversion action. */
    private data class ConvertOption(
        val title: String,
        val description: String,
        val action: (() -> FileConverter.ConvertResult)?
    )

    /**
     * Shows a simple list of conversion options (used for non-media files and audio).
     */
    private fun showOptionsList(
        context: Context,
        options: List<ConvertOption>,
        onResult: (FileConverter.ConvertResult) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * dp).toInt()
            setPadding(0, pad / 2, 0, pad / 2)
        }
        scrollView.addView(container)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.convert_title))
            .setView(scrollView)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        for (option in options) {
            val row = buildOptionRow(context, option)
            row.setOnClickListener {
                dialog.dismiss()
                if (option.action != null) {
                    runConversion(context, option.action, onResult)
                }
            }
            container.addView(row)
        }

        dialog.show()
    }

    /**
     * Builds a tappable row with a title and description for a conversion option.
     */
    private fun buildOptionRow(context: Context, option: ConvertOption): LinearLayout {
        val dp = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (24 * dp).toInt()
            val padV = (12 * dp).toInt()
            setPadding(padH, padV, padH, padV)

            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            contentDescription = option.title

            val titleView = TextView(context).apply {
                text = option.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            addView(titleView)

            val descView = TextView(context).apply {
                text = option.description
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (2 * dp).toInt()
                layoutParams = lp
            }
            addView(descView)
        }
    }

    /**
     * Builds a Material-styled outlined action button.
     */
    private fun buildActionButton(context: Context, label: String): com.google.android.material.button.MaterialButton {
        val dp = context.resources.displayMetrics.density
        return com.google.android.material.button.MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isAllCaps = false
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = (8 * dp).toInt()
            layoutParams = lp
        }
    }

    /**
     * Runs a conversion action on a background thread with a progress dialog.
     */
    private fun runConversion(
        context: Context,
        action: () -> FileConverter.ConvertResult,
        onResult: (FileConverter.ConvertResult) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val progressContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * dp).toInt()
            setPadding(pad, (16 * dp).toInt(), pad, (16 * dp).toInt())

            addView(TextView(context).apply {
                text = context.getString(R.string.convert_progress)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
            })

            addView(ProgressBar(context).apply {
                isIndeterminate = true
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (16 * dp).toInt()
                layoutParams = lp
            })
        }

        val progressDialog = MaterialAlertDialogBuilder(context)
            .setView(progressContainer)
            .setCancelable(false)
            .show()

        // Use SupervisorJob so the coroutine is scoped to the dialog lifecycle
        val job = kotlinx.coroutines.SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Main + job)
        progressDialog.setOnDismissListener { job.cancel() }
        scope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            try {
                if (progressDialog.isShowing) progressDialog.dismiss()
            } catch (_: Exception) { /* Window already destroyed */ }
            onResult(result)
        }
    }
}
