package com.filecleaner.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.databinding.FragmentFileViewerBinding
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Full-screen file viewer supporting:
 * - Images (Glide, including GIF animation and SVG)
 * - PDFs (PdfRenderer with page navigation)
 * - Text/code files (monospace scrollable view, 60+ extensions)
 * - Audio (MediaPlayer with play/pause/seek)
 * - Video (thumbnail + open externally)
 * - HTML/Markdown (WebView rendering)
 * - Archives (ZIP/JAR content listing)
 */
class FileViewerFragment : Fragment() {

    companion object {
        const val ARG_FILE_PATH = "file_path"
        private const val MAX_TEXT_BYTES = 50 * 1024 // 50 KB for full viewer
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
            "py", "rb", "php", "pl", "pm", "lua", "r", "R",
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
            // Docs & text formats
            "tex", "latex", "bib", "srt", "sub", "ass", "vtt",
            "diff", "patch",
            // Other
            "json5", "jsonc", "jsonl"
        )

        private val HTML_EXTENSIONS = setOf("html", "htm")
        private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "jar", "apk")
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "wma", "opus",
            "aiff", "mid", "amr", "ape", "wv", "m4b", "dsf"
        )
    }

    private var _binding: FragmentFileViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfRenderer: PdfRenderer? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var currentPdfPage = 0
    private var currentPdfBitmap: Bitmap? = null

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isAudioPlaying = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentFileViewerBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filePath = arguments?.getString(ARG_FILE_PATH) ?: run {
            findNavController().popBackStack()
            return
        }
        val file = File(filePath)
        if (!file.exists()) {
            findNavController().popBackStack()
            return
        }

        val ext = file.name.substringAfterLast('.', "").lowercase()
        val category = FileCategory.fromExtension(ext)

        // Toolbar
        binding.tvFilename.text = file.name
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnOpenExternal.setOnClickListener { FileOpener.open(requireContext(), file) }
        binding.btnShare.setOnClickListener { FileOpener.share(requireContext(), file) }

        // File info bar
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        binding.tvFileInfo.text = getString(
            R.string.viewer_file_info,
            UndoHelper.formatBytes(file.length()),
            dateFmt.format(Date(file.lastModified()))
        )

        // Display content based on type
        when {
            category == FileCategory.AUDIO || ext in AUDIO_EXTENSIONS -> showAudio(file)
            ext in HTML_EXTENSIONS -> showHtml(file)
            ext in MARKDOWN_EXTENSIONS -> showMarkdown(file)
            category == FileCategory.IMAGE -> showImage(file)
            ext == "pdf" -> showPdf(file, savedInstanceState)
            ext in ARCHIVE_EXTENSIONS -> showArchiveContents(file)
            ext in TEXT_EXTENSIONS || ext == "json" -> showText(file)
            category == FileCategory.VIDEO -> showVideoFallback(file)
            else -> showUnsupported(file)
        }
    }

    private fun showImage(file: File) {
        binding.ivImage.visibility = View.VISIBLE
        Glide.with(this)
            .load(file)
            .into(binding.ivImage)
    }

    private fun showPdf(file: File, savedInstanceState: Bundle?) {
        binding.pdfContainer.visibility = View.VISIBLE
        try {
            pdfFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pdfFd!!)

            currentPdfPage = savedInstanceState?.getInt("pdf_page", 0) ?: 0
            renderPdfPage()

            binding.btnPdfPrev.setOnClickListener {
                if (currentPdfPage > 0) {
                    currentPdfPage--
                    renderPdfPage()
                }
            }
            binding.btnPdfNext.setOnClickListener {
                val pageCount = pdfRenderer?.pageCount ?: 0
                if (currentPdfPage < pageCount - 1) {
                    currentPdfPage++
                    renderPdfPage()
                }
            }
        } catch (e: Exception) {
            binding.pdfContainer.visibility = View.GONE
            showUnsupported(file)
        }
    }

    private fun renderPdfPage() {
        val renderer = pdfRenderer ?: return
        val page = renderer.openPage(currentPdfPage)
        val scale = 2 // Render at 2x for readability
        val bitmap = Bitmap.createBitmap(
            page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        // Recycle previous page bitmap to prevent memory accumulation
        currentPdfBitmap?.recycle()
        currentPdfBitmap = bitmap
        binding.ivPdfPage.setImageBitmap(bitmap)

        val pageCount = renderer.pageCount
        binding.tvPdfPageInfo.text = getString(R.string.viewer_pdf_page, currentPdfPage + 1, pageCount)
        binding.btnPdfPrev.isEnabled = currentPdfPage > 0
        binding.btnPdfNext.isEnabled = currentPdfPage < pageCount - 1
    }

    private fun showText(file: File) {
        binding.scrollText.visibility = View.VISIBLE
        val content = try {
            val bytes = file.inputStream().use { it.readNBytes(MAX_TEXT_BYTES) }
            val text = String(bytes, Charsets.UTF_8)
            if (file.length() > MAX_TEXT_BYTES) {
                text + "\n\n\u2026 [truncated at 50 KB]"
            } else {
                text
            }
        } catch (e: Exception) {
            getString(R.string.preview_error, e.localizedMessage ?: "")
        }
        binding.tvTextContent.text = content
    }

    private fun showAudio(file: File) {
        binding.audioContainer.visibility = View.VISIBLE

        // Try to extract album art
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                Glide.with(this).load(art).into(binding.ivAudioArt)
            }
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            binding.tvAudioTitle.text = title ?: file.nameWithoutExtension
        } catch (e: Exception) {
            binding.tvAudioTitle.text = file.nameWithoutExtension
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
            val mp = mediaPlayer!!
            binding.seekAudio.max = mp.duration
            binding.tvAudioDuration.text = formatTime(mp.duration)
            binding.tvAudioCurrent.text = formatTime(0)

            binding.btnAudioPlay.setOnClickListener {
                if (isAudioPlaying) {
                    mp.pause()
                    isAudioPlaying = false
                    binding.btnAudioPlay.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    mp.start()
                    isAudioPlaying = true
                    binding.btnAudioPlay.setImageResource(android.R.drawable.ic_media_pause)
                    updateSeekBar()
                }
            }

            binding.seekAudio.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mp.seekTo(progress)
                        _binding?.tvAudioCurrent?.text = formatTime(progress)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            mp.setOnCompletionListener {
                isAudioPlaying = false
                _binding?.btnAudioPlay?.setImageResource(android.R.drawable.ic_media_play)
                _binding?.seekAudio?.progress = 0
                _binding?.tvAudioCurrent?.text = formatTime(0)
            }
        } catch (e: Exception) {
            _binding?.tvAudioTitle?.text = getString(R.string.viewer_audio_error)
        }
    }

    private fun updateSeekBar() {
        val mp = mediaPlayer ?: return
        if (isAudioPlaying && mp.isPlaying) {
            _binding?.seekAudio?.progress = mp.currentPosition
            _binding?.tvAudioCurrent?.text = formatTime(mp.currentPosition)
            handler.postDelayed({ updateSeekBar() }, 500)
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    private fun showHtml(file: File) {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        binding.webView.loadUrl("file://${file.absolutePath}")
    }

    private fun showMarkdown(file: File) {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        val content = try {
            val bytes = file.inputStream().use { it.readNBytes(MAX_TEXT_BYTES) }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Error reading file: ${e.localizedMessage}"
        }

        // Simple Markdown to HTML conversion (handles common patterns)
        val html = buildString {
            append("<!DOCTYPE html><html><head>")
            append("<meta charset='utf-8'>")
            append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
            append("<style>")
            append("body{font-family:sans-serif;padding:16px;line-height:1.6;color:#333;max-width:100%;word-wrap:break-word;}")
            append("pre,code{background:#f4f4f4;padding:2px 6px;border-radius:4px;font-family:monospace;font-size:14px;}")
            append("pre{padding:12px;overflow-x:auto;}")
            append("pre code{background:none;padding:0;}")
            append("blockquote{border-left:4px solid #ddd;margin:0;padding:0 16px;color:#666;}")
            append("h1,h2,h3{margin-top:24px;}")
            append("hr{border:none;border-top:1px solid #ddd;margin:24px 0;}")
            append("img{max-width:100%;}")
            append("table{border-collapse:collapse;width:100%;}")
            append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}")
            append("th{background:#f4f4f4;}")
            append("</style></head><body>")

            // Process markdown line by line
            var inCodeBlock = false
            for (line in content.lines()) {
                if (line.trimStart().startsWith("```")) {
                    if (inCodeBlock) {
                        append("</code></pre>")
                        inCodeBlock = false
                    } else {
                        append("<pre><code>")
                        inCodeBlock = true
                    }
                    continue
                }
                if (inCodeBlock) {
                    append(line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    append("\n")
                    continue
                }

                var processed = line
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

                // Headers
                processed = processed
                    .replace(Regex("^######\\s+(.*)"), "<h6>$1</h6>")
                    .replace(Regex("^#####\\s+(.*)"), "<h5>$1</h5>")
                    .replace(Regex("^####\\s+(.*)"), "<h4>$1</h4>")
                    .replace(Regex("^###\\s+(.*)"), "<h3>$1</h3>")
                    .replace(Regex("^##\\s+(.*)"), "<h2>$1</h2>")
                    .replace(Regex("^#\\s+(.*)"), "<h1>$1</h1>")

                // Bold & italic
                processed = processed
                    .replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "<b><i>$1</i></b>")
                    .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
                    .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")

                // Inline code
                processed = processed.replace(Regex("`(.+?)`"), "<code>$1</code>")

                // Horizontal rule
                if (processed.matches(Regex("^\\s*[-*_]{3,}\\s*$"))) {
                    append("<hr>")
                    continue
                }

                // Blockquote
                if (processed.startsWith("&gt; ")) {
                    processed = "<blockquote>${processed.removePrefix("&gt; ")}</blockquote>"
                }

                // Unordered list
                if (processed.matches(Regex("^\\s*[-*+]\\s+.*"))) {
                    processed = "<li>${processed.replace(Regex("^\\s*[-*+]\\s+"), "")}</li>"
                }

                // Ordered list
                if (processed.matches(Regex("^\\s*\\d+\\.\\s+.*"))) {
                    processed = "<li>${processed.replace(Regex("^\\s*\\d+\\.\\s+"), "")}</li>"
                }

                if (processed.isBlank()) {
                    append("<br>")
                } else if (!processed.startsWith("<h") && !processed.startsWith("<li") &&
                    !processed.startsWith("<blockquote") && !processed.startsWith("<hr")) {
                    append("<p>$processed</p>")
                } else {
                    append(processed)
                }
            }
            if (inCodeBlock) append("</code></pre>")
            append("</body></html>")
        }

        binding.webView.loadDataWithBaseURL(
            "file://${file.parent}/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun showArchiveContents(file: File) {
        binding.scrollText.visibility = View.VISIBLE
        val content = try {
            ZipFile(file).use { zf ->
                val entries = zf.entries().toList().sortedBy { it.name }
                val sb = StringBuilder()
                sb.appendLine("Archive: ${file.name}")
                sb.appendLine("Entries: ${entries.size}")
                sb.appendLine("─".repeat(50))
                sb.appendLine()
                for (entry in entries) {
                    val size = if (entry.size >= 0) UndoHelper.formatBytes(entry.size) else "?"
                    val dir = if (entry.isDirectory) "/" else ""
                    sb.appendLine("  $size  ${entry.name}$dir")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            getString(R.string.viewer_archive_error, e.localizedMessage ?: "")
        }
        binding.tvTextContent.text = content
    }

    private fun showVideoFallback(file: File) {
        // Show video thumbnail with a prompt to open externally
        binding.ivImage.visibility = View.VISIBLE
        Glide.with(this)
            .load(file)
            .into(binding.ivImage)
        binding.ivImage.setOnClickListener { FileOpener.open(requireContext(), file) }
    }

    private fun showUnsupported(file: File) {
        binding.unsupportedContainer.visibility = View.VISIBLE
        binding.tvUnsupported.text = getString(R.string.viewer_unsupported, file.name.substringAfterLast('.', ""))
        binding.btnOpenFallback.setOnClickListener { FileOpener.open(requireContext(), file) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pdf_page", currentPdfPage)
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isAudioPlaying = false
                _binding?.btnAudioPlay?.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.release()
        mediaPlayer = null
        isAudioPlaying = false
        currentPdfBitmap?.recycle()
        currentPdfBitmap = null
        pdfRenderer?.close()
        pdfFd?.close()
        pdfRenderer = null
        pdfFd = null
        // WebView must be removed from parent before destroy()
        _binding?.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        super.onDestroyView()
        _binding = null
    }
}
