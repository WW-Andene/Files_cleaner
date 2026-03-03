package com.filecleaner.app.ui.common

import android.content.Context
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.FileConverter

/**
 * Dialog to choose a target format for file conversion.
 * Shows applicable formats based on the source file type.
 *
 * Supports conversions for:
 * - Images: to other image formats (PNG, JPG, WEBP, WEBP lossless, BMP) + PDF
 * - PDFs: to PNG or JPG images
 * - Videos: extract thumbnail (PNG/JPG), extract frame series
 * - Audio: extract album art
 * - Text/Code/Markdown/HTML/CSV: to PDF
 */
object ConvertDialog {

    data class ConvertAction(val label: String, val action: () -> FileConverter.ConvertResult)

    fun show(context: Context, item: FileItem, onResult: (FileConverter.ConvertResult) -> Unit) {
        val actions = buildActions(item)
        if (actions.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.convert_title))
                .setMessage(context.getString(R.string.convert_unsupported))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val labels = actions.map { it.label }
        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, labels)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.convert_title))
            .setAdapter(adapter) { _, which ->
                val result = actions[which].action()
                onResult(result)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

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

    private fun buildActions(item: FileItem): List<ConvertAction> {
        val actions = mutableListOf<ConvertAction>()
        val ext = item.extension

        // =====================================================================
        // IMAGE conversions
        // =====================================================================
        if (item.category == FileCategory.IMAGE) {
            // Image -> other image formats
            for (fmt in FileConverter.ImageFormat.entries) {
                val targetExt = if (fmt == FileConverter.ImageFormat.WEBP_LOSSLESS) "webp" else fmt.extension
                if (targetExt != ext || fmt == FileConverter.ImageFormat.WEBP_LOSSLESS) {
                    // Don't show duplicate webp entries
                    if (fmt == FileConverter.ImageFormat.WEBP && ext == "webp") continue
                    if (fmt == FileConverter.ImageFormat.WEBP_LOSSLESS && ext == "webp") continue
                    actions.add(ConvertAction("${fmt.label} (.${fmt.extension})") {
                        FileConverter.convertImage(item.path, fmt)
                    })
                }
            }
            // Image -> PDF
            actions.add(ConvertAction("PDF (.pdf)") {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                FileConverter.imagesToPdf(listOf(item.path), outputPath)
            })
        }

        // =====================================================================
        // PDF conversions
        // =====================================================================
        if (ext == "pdf") {
            for (fmt in FileConverter.PdfImageFormat.entries) {
                actions.add(ConvertAction("${fmt.label} (one per page)") {
                    val outputDir = "${item.file.parent}/${item.file.nameWithoutExtension}_pages"
                    FileConverter.pdfToImages(item.path, outputDir, fmt)
                })
            }
        }

        // =====================================================================
        // VIDEO conversions
        // =====================================================================
        if (item.category == FileCategory.VIDEO) {
            // Thumbnail extraction
            actions.add(ConvertAction("PNG thumbnail") {
                FileConverter.videoToThumbnail(item.path, FileConverter.ImageFormat.PNG)
            })
            actions.add(ConvertAction("JPG thumbnail") {
                FileConverter.videoToThumbnail(item.path, FileConverter.ImageFormat.JPG, quality = 85)
            })
            // Frame series
            actions.add(ConvertAction("Extract 10 frames (JPG)") {
                val outputDir = "${item.file.parent}/${item.file.nameWithoutExtension}_frames"
                FileConverter.videoToFrames(item.path, outputDir, 10, FileConverter.ImageFormat.JPG)
            })
            actions.add(ConvertAction("Extract 20 frames (PNG)") {
                val outputDir = "${item.file.parent}/${item.file.nameWithoutExtension}_frames"
                FileConverter.videoToFrames(item.path, outputDir, 20, FileConverter.ImageFormat.PNG)
            })
        }

        // =====================================================================
        // AUDIO conversions
        // =====================================================================
        if (item.category == FileCategory.AUDIO) {
            // Extract album art
            actions.add(ConvertAction("Extract album art (PNG)") {
                FileConverter.audioToAlbumArt(item.path, FileConverter.ImageFormat.PNG)
            })
            actions.add(ConvertAction("Extract album art (JPG)") {
                FileConverter.audioToAlbumArt(item.path, FileConverter.ImageFormat.JPG, quality = 90)
            })
        }

        // =====================================================================
        // TEXT / CODE / DOCUMENT -> PDF
        // =====================================================================
        if (ext in TEXT_CONVERTIBLE) {
            actions.add(ConvertAction("PDF (.pdf)") {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                FileConverter.textToPdf(item.path, outputPath)
            })
        }

        // HTML -> PDF
        if (ext == "html" || ext == "htm") {
            actions.add(ConvertAction("PDF (.pdf)") {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                FileConverter.textToPdf(item.path, outputPath)
            })
        }

        // Markdown -> PDF
        if (ext == "md" || ext == "markdown" || ext == "mdown") {
            actions.add(ConvertAction("PDF (.pdf)") {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}.pdf"
                FileConverter.textToPdf(item.path, outputPath)
            })
        }

        // CSV -> Formatted text table
        if (ext == "csv") {
            actions.add(ConvertAction("Formatted table (.txt)") {
                val outputPath = "${item.file.parent}/${item.file.nameWithoutExtension}_table.txt"
                FileConverter.csvToText(item.path, outputPath)
            })
        }

        return actions
    }
}
