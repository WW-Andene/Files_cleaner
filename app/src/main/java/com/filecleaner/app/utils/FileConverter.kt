package com.filecleaner.app.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Format conversion utilities using only Android SDK APIs (no external dependencies).
 *
 * Supported conversions:
 * - Image -> Image (PNG, JPG, WEBP, BMP)
 * - Image(s) -> PDF
 * - PDF -> Images (PNG or JPG per page)
 * - Text/Code/CSV/Markdown -> PDF
 * - Video -> Thumbnail image (PNG/JPG)
 * - Video -> Frame series extraction
 * - Audio -> Extract album art image
 */
object FileConverter {

    data class ConvertResult(val success: Boolean, val outputPath: String, val message: String)

    /** Supported image output formats. */
    enum class ImageFormat(val extension: String, val compressFormat: Bitmap.CompressFormat, val label: String) {
        PNG("png", Bitmap.CompressFormat.PNG, "PNG"),
        JPG("jpg", Bitmap.CompressFormat.JPEG, "JPG"),
        WEBP("webp", Bitmap.CompressFormat.WEBP_LOSSY, "WEBP (Lossy)"),
        WEBP_LOSSLESS("webp_ll", Bitmap.CompressFormat.WEBP_LOSSLESS, "WEBP (Lossless)"),
        BMP("bmp", Bitmap.CompressFormat.PNG, "BMP") // BMP uses custom writer
    }

    /** PDF extraction output format. */
    enum class PdfImageFormat(val extension: String, val compressFormat: Bitmap.CompressFormat, val label: String) {
        PNG("png", Bitmap.CompressFormat.PNG, "PNG images"),
        JPG("jpg", Bitmap.CompressFormat.JPEG, "JPG images")
    }

    // =========================================================================
    // IMAGE CONVERSIONS
    // =========================================================================

    /** Convert an image file to another image format. */
    fun convertImage(inputPath: String, outputFormat: ImageFormat, quality: Int = 90): ConvertResult {
        return try {
            val src = File(inputPath)
            if (!src.exists()) return ConvertResult(false, "", "Source file not found")

            val bitmap = BitmapFactory.decodeFile(inputPath)
                ?: return ConvertResult(false, "", "Cannot decode image")

            try {
                val ext = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "webp" else outputFormat.extension
                val suffix = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "_lossless" else ""
                val outputName = "${src.nameWithoutExtension}$suffix.$ext"
                val outputFile = File(src.parent, outputName)
                val finalFile = if (outputFile.absolutePath == src.absolutePath) {
                    File(src.parent, "${src.nameWithoutExtension}_converted.$ext")
                } else outputFile

                if (outputFormat == ImageFormat.BMP) {
                    writeBmp(bitmap, finalFile)
                } else {
                    finalFile.outputStream().buffered().use { out ->
                        bitmap.compress(outputFormat.compressFormat, quality, out)
                    }
                }
                ConvertResult(true, finalFile.absolutePath, "Converted to ${finalFile.name}")
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ConvertResult(false, "", "Conversion failed: ${e.localizedMessage}")
        }
    }

    /** Write a bitmap as BMP format (uncompressed 24-bit). */
    private fun writeBmp(bitmap: Bitmap, outputFile: File) {
        val w = bitmap.width
        val h = bitmap.height
        val rowSize = ((24 * w + 31) / 32) * 4
        val imageSize = rowSize * h
        val fileSize = 54 + imageSize

        outputFile.outputStream().buffered().use { out ->
            // BMP header
            out.write(byteArrayOf('B'.code.toByte(), 'M'.code.toByte()))
            out.write(intToBytes(fileSize))
            out.write(intToBytes(0)) // reserved
            out.write(intToBytes(54)) // offset

            // DIB header
            out.write(intToBytes(40)) // header size
            out.write(intToBytes(w))
            out.write(intToBytes(h))
            out.write(shortToBytes(1)) // planes
            out.write(shortToBytes(24)) // bits per pixel
            out.write(intToBytes(0)) // compression
            out.write(intToBytes(imageSize))
            out.write(intToBytes(2835)) // h resolution
            out.write(intToBytes(2835)) // v resolution
            out.write(intToBytes(0)) // colors
            out.write(intToBytes(0)) // important colors

            // Pixel data (bottom-up)
            val row = ByteArray(rowSize)
            for (y in h - 1 downTo 0) {
                row.fill(0)
                for (x in 0 until w) {
                    val pixel = bitmap.getPixel(x, y)
                    row[x * 3] = (pixel and 0xFF).toByte()         // B
                    row[x * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()  // G
                    row[x * 3 + 2] = ((pixel shr 16) and 0xFF).toByte() // R
                }
                out.write(row)
            }
        }
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )

    /** Resize an image to specified dimensions. */
    fun resizeImage(inputPath: String, maxWidth: Int, maxHeight: Int, outputFormat: ImageFormat, quality: Int = 90): ConvertResult {
        return try {
            val src = File(inputPath)
            if (!src.exists()) return ConvertResult(false, "", "Source file not found")

            val original = BitmapFactory.decodeFile(inputPath)
                ?: return ConvertResult(false, "", "Cannot decode image")

            var resized: Bitmap? = null
            try {
                val ratioW = maxWidth.toFloat() / original.width
                val ratioH = maxHeight.toFloat() / original.height
                val ratio = minOf(ratioW, ratioH, 1f)
                val newW = (original.width * ratio).toInt()
                val newH = (original.height * ratio).toInt()

                resized = Bitmap.createScaledBitmap(original, newW, newH, true)

                val ext = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "webp" else outputFormat.extension
                val outputFile = File(src.parent, "${src.nameWithoutExtension}_${newW}x${newH}.$ext")
                outputFile.outputStream().buffered().use { out ->
                    resized.compress(outputFormat.compressFormat, quality, out)
                }

                ConvertResult(true, outputFile.absolutePath, "Resized to ${newW}x${newH}")
            } finally {
                // Only recycle resized if it's a different object than original
                if (resized != null && resized !== original) resized.recycle()
                original.recycle()
            }
        } catch (e: Exception) {
            ConvertResult(false, "", "Resize failed: ${e.localizedMessage}")
        }
    }

    // =========================================================================
    // PDF CONVERSIONS
    // =========================================================================

    /** Convert one or more images to a multi-page PDF. */
    fun imagesToPdf(imagePaths: List<String>, outputPath: String): ConvertResult {
        if (imagePaths.isEmpty()) return ConvertResult(false, "", "No images provided")
        val doc = PdfDocument()
        return try {
            var pagesAdded = 0
            for ((index, path) in imagePaths.withIndex()) {
                val bitmap = BitmapFactory.decodeFile(path) ?: continue
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(page)
                    pagesAdded++
                } finally {
                    bitmap.recycle()
                }
            }
            if (pagesAdded == 0) return ConvertResult(false, "", "No valid images to convert")
            File(outputPath).outputStream().buffered().use { out ->
                doc.writeTo(out)
            }
            ConvertResult(true, outputPath, "Created ${File(outputPath).name}")
        } catch (e: Exception) {
            ConvertResult(false, "", "PDF creation failed: ${e.localizedMessage}")
        } finally {
            doc.close()
        }
    }

    /** Extract all pages of a PDF to individual images. */
    fun pdfToImages(pdfPath: String, outputDir: String, format: PdfImageFormat = PdfImageFormat.PNG, quality: Int = 90): ConvertResult {
        val pdfFile = File(pdfPath)
        if (!pdfFile.exists()) return ConvertResult(false, "", "PDF not found")

        val outDir = File(outputDir)
        outDir.mkdirs()

        var fd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd)
            val pageCount = renderer.pageCount

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val scale = 2
                val bitmap = Bitmap.createBitmap(
                    page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
                )
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageFile = File(outDir, "${pdfFile.nameWithoutExtension}_page_${i + 1}.${format.extension}")
                    pageFile.outputStream().buffered().use { out ->
                        bitmap.compress(format.compressFormat, quality, out)
                    }
                } finally {
                    bitmap.recycle()
                }
            }

            ConvertResult(true, outDir.absolutePath, "Extracted $pageCount pages to ${outDir.name}/")
        } catch (e: Exception) {
            ConvertResult(false, "", "PDF extraction failed: ${e.localizedMessage}")
        } finally {
            renderer?.close()
            fd?.close()
        }
    }

    // =========================================================================
    // TEXT / DOCUMENT -> PDF
    // =========================================================================

    /** Convert a text file to PDF (monospace rendering). */
    fun textToPdf(inputPath: String, outputPath: String, fontSize: Float = 10f): ConvertResult {
        val src = File(inputPath)
        if (!src.exists()) return ConvertResult(false, "", "Source file not found")

        val doc = PdfDocument()
        return try {
            val lines = BufferedReader(InputStreamReader(src.inputStream(), Charsets.UTF_8)).use {
                it.readLines()
            }

            val paint = Paint().apply {
                typeface = Typeface.MONOSPACE
                textSize = fontSize
                color = Color.BLACK
                isAntiAlias = true
            }

            val pageWidth = 595 // A4 at 72 DPI
            val pageHeight = 842
            val margin = 40f
            val lineHeight = fontSize * 1.4f
            val usableHeight = pageHeight - margin * 2
            val linesPerPage = (usableHeight / lineHeight).toInt()

            var pageNum = 1
            var lineIndex = 0

            while (lineIndex < lines.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = doc.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawColor(Color.WHITE)
                var y = margin + fontSize
                for (i in 0 until linesPerPage) {
                    if (lineIndex + i >= lines.size) break
                    canvas.drawText(lines[lineIndex + i], margin, y, paint)
                    y += lineHeight
                }

                doc.finishPage(page)
                lineIndex += linesPerPage
                pageNum++
            }

            File(outputPath).outputStream().buffered().use { doc.writeTo(it) }

            ConvertResult(true, outputPath, "Created ${File(outputPath).name} ($pageNum pages)")
        } catch (e: Exception) {
            ConvertResult(false, "", "Text to PDF failed: ${e.localizedMessage}")
        } finally {
            doc.close()
        }
    }

    /** Convert CSV to formatted text table. */
    fun csvToText(inputPath: String, outputPath: String, delimiter: Char = ','): ConvertResult {
        return try {
            val src = File(inputPath)
            if (!src.exists()) return ConvertResult(false, "", "Source file not found")

            val rows = src.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLines().map { line ->
                    parseCsvLine(line, delimiter)
                }
            }

            if (rows.isEmpty()) return ConvertResult(false, "", "CSV file is empty")

            // Calculate column widths
            val colCount = rows.maxOf { it.size }
            val colWidths = IntArray(colCount)
            for (row in rows) {
                for ((i, cell) in row.withIndex()) {
                    colWidths[i] = maxOf(colWidths[i], cell.length)
                }
            }

            val sb = StringBuilder()
            for ((rowIndex, row) in rows.withIndex()) {
                for (i in 0 until colCount) {
                    val cell = row.getOrElse(i) { "" }
                    sb.append(cell.padEnd(colWidths[i] + 2))
                }
                sb.appendLine()
                // Separator after header
                if (rowIndex == 0) {
                    for (i in 0 until colCount) {
                        sb.append("─".repeat(colWidths[i] + 2))
                    }
                    sb.appendLine()
                }
            }

            File(outputPath).writeText(sb.toString(), Charsets.UTF_8)
            ConvertResult(true, outputPath, "Converted to ${File(outputPath).name}")
        } catch (e: Exception) {
            ConvertResult(false, "", "CSV conversion failed: ${e.localizedMessage}")
        }
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val cells = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    // =========================================================================
    // VIDEO CONVERSIONS
    // =========================================================================

    /** Extract a thumbnail frame from a video file. */
    fun videoToThumbnail(inputPath: String, outputFormat: ImageFormat = ImageFormat.PNG, quality: Int = 90, timeUs: Long = 0): ConvertResult {
        val src = File(inputPath)
        if (!src.exists()) return ConvertResult(false, "", "Source file not found")

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(inputPath)
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0)
                ?: return ConvertResult(false, "", "Cannot extract frame from video")

            try {
                val ext = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "webp" else outputFormat.extension
                val outputFile = File(src.parent, "${src.nameWithoutExtension}_thumb.$ext")
                outputFile.outputStream().buffered().use { out ->
                    bitmap.compress(outputFormat.compressFormat, quality, out)
                }
                ConvertResult(true, outputFile.absolutePath, "Thumbnail: ${outputFile.name}")
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ConvertResult(false, "", "Thumbnail extraction failed: ${e.localizedMessage}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Extract multiple frames from video as a series of images. */
    fun videoToFrames(inputPath: String, outputDir: String, frameCount: Int = 10, outputFormat: ImageFormat = ImageFormat.JPG, quality: Int = 85): ConvertResult {
        val src = File(inputPath)
        if (!src.exists()) return ConvertResult(false, "", "Source file not found")

        val outDir = File(outputDir)
        outDir.mkdirs()

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(inputPath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return ConvertResult(false, "", "Cannot determine video duration")
            val durationUs = durationMs * 1000
            val interval = durationUs / frameCount.coerceAtLeast(1)

            var extracted = 0
            for (i in 0 until frameCount) {
                val timeUs = interval * i
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                try {
                    val ext = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "webp" else outputFormat.extension
                    val frameFile = File(outDir, "${src.nameWithoutExtension}_frame_${i + 1}.$ext")
                    frameFile.outputStream().buffered().use { out ->
                        bitmap.compress(outputFormat.compressFormat, quality, out)
                    }
                    extracted++
                } finally {
                    bitmap.recycle()
                }
            }

            ConvertResult(true, outDir.absolutePath, "Extracted $extracted frames to ${outDir.name}/")
        } catch (e: Exception) {
            ConvertResult(false, "", "Frame extraction failed: ${e.localizedMessage}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // AUDIO CONVERSIONS
    // =========================================================================

    /** Extract album art from an audio file. */
    fun audioToAlbumArt(inputPath: String, outputFormat: ImageFormat = ImageFormat.PNG, quality: Int = 90): ConvertResult {
        val src = File(inputPath)
        if (!src.exists()) return ConvertResult(false, "", "Source file not found")

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(inputPath)
            val art = retriever.embeddedPicture
                ?: return ConvertResult(false, "", "No album art found in this file")

            val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                ?: return ConvertResult(false, "", "Cannot decode album art")

            try {
                val ext = if (outputFormat == ImageFormat.WEBP_LOSSLESS) "webp" else outputFormat.extension
                val outputFile = File(src.parent, "${src.nameWithoutExtension}_cover.$ext")
                outputFile.outputStream().buffered().use { out ->
                    bitmap.compress(outputFormat.compressFormat, quality, out)
                }
                ConvertResult(true, outputFile.absolutePath, "Album art: ${outputFile.name}")
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            ConvertResult(false, "", "Album art extraction failed: ${e.localizedMessage}")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
