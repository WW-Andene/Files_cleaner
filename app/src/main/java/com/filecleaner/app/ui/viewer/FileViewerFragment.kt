package com.filecleaner.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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

/**
 * Full-screen in-app file viewer supporting:
 * - Images (Glide with pinch-to-zoom and pan, including GIF animation)
 * - PDFs (PdfRenderer with page navigation)
 * - Text/code files (monospace scrollable view with syntax highlighting for common languages)
 * - Video (native VideoView with play/pause/seek controls)
 * - Audio (MediaPlayer with play/pause/seek and album art)
 * - HTML/Markdown (WebView rendering)
 * - Fallback: offer to open with external app
 */
class FileViewerFragment : Fragment() {

    companion object {
        const val ARG_FILE_PATH = "file_path"
        private const val MAX_TEXT_BYTES = 50 * 1024 // 50 KB

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
            // Docs & text formats
            "tex", "latex", "bib", "srt", "sub", "ass", "vtt",
            "diff", "patch",
            // Other
            "json", "json5", "jsonc", "jsonl"
        )

        private val HTML_EXTENSIONS = setOf("html", "htm")
        private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "wma", "opus",
            "aiff", "mid", "amr", "ape", "wv", "m4b", "dsf"
        )

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
            "m4v", "3gp", "ts", "mpeg", "mpg"
        )

        // Extensions that get syntax highlighting
        private val CODE_EXTENSIONS = setOf(
            "kt", "kts", "java", "groovy", "gradle", "scala",
            "js", "jsx", "ts", "tsx", "css", "scss", "sass", "less",
            "vue", "svelte",
            "c", "cpp", "cc", "cxx", "h", "hpp", "hxx",
            "rs", "go", "zig",
            "py", "rb", "php", "pl", "pm", "lua", "r",
            "sh", "bash", "zsh", "fish", "bat", "ps1", "cmd",
            "swift", "m", "mm", "dart",
            "hs", "ml", "ex", "exs", "erl", "clj",
            "sql", "graphql", "gql", "proto",
            "xml", "svg", "plist", "json", "json5", "jsonc", "jsonl",
            "html", "htm", "css", "scss"
        )

        // Syntax highlighting colors
        private const val COLOR_KEYWORD = 0xFF7B68EE.toInt()   // Medium slate blue
        private const val COLOR_STRING = 0xFF2E8B57.toInt()    // Sea green
        private const val COLOR_COMMENT = 0xFF808080.toInt()   // Gray
        private const val COLOR_NUMBER = 0xFFCD853F.toInt()    // Peru / brown
        private const val COLOR_TYPE = 0xFF4682B4.toInt()      // Steel blue

        // Common keywords across many languages
        private val COMMON_KEYWORDS = setOf(
            "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "return", "try", "catch", "finally", "throw", "throws",
            "class", "interface", "object", "enum", "struct", "trait",
            "fun", "function", "def", "fn", "func", "val", "var", "let", "const",
            "import", "package", "from", "export", "module", "require",
            "public", "private", "protected", "internal", "static", "final",
            "abstract", "override", "open", "sealed", "data",
            "new", "this", "self", "super", "null", "nil", "None",
            "true", "false", "True", "False",
            "void", "int", "long", "float", "double", "boolean", "bool",
            "string", "String", "char", "byte", "short",
            "async", "await", "yield", "suspend",
            "when", "is", "as", "in", "not", "and", "or",
            "type", "typealias", "typedef", "impl", "use", "mod",
            "lambda", "inline", "extern", "unsafe", "where",
            "select", "from", "where", "insert", "update", "delete", "create",
            "table", "index", "view", "alter", "drop"
        )
    }

    private var _binding: FragmentFileViewerBinding? = null
    private val binding get() = _binding!!

    // PDF state
    private var pdfRenderer: PdfRenderer? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var currentPdfPage = 0
    private var currentPdfBitmap: Bitmap? = null

    // Audio state
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isAudioPlaying = false

    // Video state
    private var isVideoPlaying = false
    private var isVideoInitialized = false

    // Image zoom/pan state
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

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
            category == FileCategory.VIDEO || ext in VIDEO_EXTENSIONS -> showVideo(file)
            ext in TEXT_EXTENSIONS -> showText(file, ext)
            else -> showUnsupported(file)
        }
    }

    // ── Image Viewer with Zoom/Pan ──────────────────────────────────────────

    private fun showImage(file: File) {
        binding.ivImage.visibility = View.VISIBLE
        Glide.with(this)
            .load(file)
            .into(binding.ivImage)
        binding.ivImage.contentDescription = getString(R.string.a11y_image_preview, file.name)

        // Set up pinch-to-zoom and pan
        setupImageZoom()
    }

    private fun setupImageZoom() {
        val imageView = binding.ivImage
        val scaleDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
                    imageView.scaleX = scaleFactor
                    imageView.scaleY = scaleFactor
                    return true
                }
            })

        imageView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    activePointerId = event.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (scaleFactor > 1.0f) {
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex >= 0) {
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)
                            val dx = x - lastTouchX
                            val dy = y - lastTouchY

                            translateX += dx
                            translateY += dy
                            imageView.translationX = translateX
                            imageView.translationY = translateY

                            lastTouchX = x
                            lastTouchY = y
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        if (newPointerIndex < event.pointerCount) {
                            lastTouchX = event.getX(newPointerIndex)
                            lastTouchY = event.getY(newPointerIndex)
                            activePointerId = event.getPointerId(newPointerIndex)
                        }
                    }
                }
            }

            // Double-tap to reset zoom
            v.performClick()
            true
        }

        // Double-tap to reset
        imageView.setOnClickListener {
            if (scaleFactor != 1.0f) {
                scaleFactor = 1.0f
                translateX = 0f
                translateY = 0f
                imageView.scaleX = 1.0f
                imageView.scaleY = 1.0f
                imageView.translationX = 0f
                imageView.translationY = 0f
            }
        }
    }

    // ── PDF Viewer ──────────────────────────────────────────────────────────

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
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        currentPdfBitmap?.recycle()
        currentPdfBitmap = bitmap
        binding.ivPdfPage.setImageBitmap(bitmap)

        val pageCount = renderer.pageCount
        binding.tvPdfPageInfo.text = getString(R.string.viewer_pdf_page, currentPdfPage + 1, pageCount)
        binding.btnPdfPrev.isEnabled = currentPdfPage > 0
        binding.btnPdfNext.isEnabled = currentPdfPage < pageCount - 1
    }

    // ── Text/Code Viewer with Syntax Highlighting ───────────────────────────

    private fun showText(file: File, ext: String) {
        binding.scrollText.visibility = View.VISIBLE
        val rawContent = try {
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

        if (ext in CODE_EXTENSIONS) {
            binding.tvTextContent.text = applySyntaxHighlighting(rawContent, ext)
        } else {
            binding.tvTextContent.text = rawContent
        }
    }

    /**
     * Applies basic syntax highlighting to code text. Highlights:
     * - Single-line comments (// and #)
     * - Multi-line comments
     * - String literals (double and single quotes)
     * - Keywords
     * - Numbers
     */
    private fun applySyntaxHighlighting(code: String, ext: String): SpannableStringBuilder {
        val spannable = SpannableStringBuilder(code)
        val length = code.length

        // Determine comment style based on language
        val lineCommentPrefix = when (ext) {
            "py", "rb", "r", "sh", "bash", "zsh", "fish", "pl", "pm",
            "yml", "yaml", "toml", "conf", "cfg", "ini", "dockerfile",
            "makefile", "cmake", "tf", "hcl", "nix" -> "#"
            "lua", "hs", "sql" -> "--"
            "html", "htm", "xml", "svg", "plist" -> null // no line comments
            else -> "//"
        }
        val hasBlockComments = ext !in setOf(
            "py", "rb", "sh", "bash", "zsh", "fish", "yml", "yaml",
            "toml", "conf", "cfg", "ini", "makefile"
        )

        // Track which character positions are already highlighted
        val highlighted = BooleanArray(length)

        var i = 0
        while (i < length) {
            // Block comments: /* ... */
            if (hasBlockComments && i + 1 < length && code[i] == '/' && code[i + 1] == '*') {
                val start = i
                i += 2
                while (i + 1 < length && !(code[i] == '*' && code[i + 1] == '/')) i++
                if (i + 1 < length) i += 2
                val end = i.coerceAtMost(length)
                spannable.setSpan(
                    ForegroundColorSpan(COLOR_COMMENT), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (j in start until end) highlighted[j] = true
                continue
            }

            // XML/HTML comments: <!-- ... -->
            if (ext in setOf("html", "htm", "xml", "svg", "plist") &&
                i + 3 < length && code.substring(i).startsWith("<!--")) {
                val start = i
                val endIdx = code.indexOf("-->", i + 4)
                i = if (endIdx >= 0) endIdx + 3 else length
                spannable.setSpan(
                    ForegroundColorSpan(COLOR_COMMENT), start, i,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (j in start until i) highlighted[j] = true
                continue
            }

            // Line comments
            if (lineCommentPrefix != null && code.startsWith(lineCommentPrefix, i)) {
                val start = i
                while (i < length && code[i] != '\n') i++
                spannable.setSpan(
                    ForegroundColorSpan(COLOR_COMMENT), start, i,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (j in start until i) highlighted[j] = true
                continue
            }

            // String literals (double-quoted)
            if (code[i] == '"') {
                val start = i
                i++
                while (i < length && code[i] != '"') {
                    if (code[i] == '\\' && i + 1 < length) i++ // skip escaped char
                    i++
                }
                if (i < length) i++ // closing quote
                spannable.setSpan(
                    ForegroundColorSpan(COLOR_STRING), start, i,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (j in start until i) highlighted[j] = true
                continue
            }

            // String literals (single-quoted)
            if (code[i] == '\'') {
                val start = i
                i++
                while (i < length && code[i] != '\'') {
                    if (code[i] == '\\' && i + 1 < length) i++
                    i++
                }
                if (i < length) i++
                spannable.setSpan(
                    ForegroundColorSpan(COLOR_STRING), start, i,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                for (j in start until i) highlighted[j] = true
                continue
            }

            // Numbers
            if (code[i].isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit())) {
                val start = i
                while (i < length && (code[i].isDigit() || code[i] == '.' ||
                            code[i] == 'x' || code[i] == 'f' || code[i] == 'L' ||
                            (code[i] in 'a'..'f') || (code[i] in 'A'..'F'))) i++
                if (!highlighted.sliceArray(start until i.coerceAtMost(length)).any { it }) {
                    spannable.setSpan(
                        ForegroundColorSpan(COLOR_NUMBER), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    for (j in start until i) highlighted[j] = true
                }
                continue
            }

            // Keywords and identifiers
            if (code[i].isLetter() || code[i] == '_') {
                val start = i
                while (i < length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val word = code.substring(start, i)
                if (word in COMMON_KEYWORDS) {
                    spannable.setSpan(
                        ForegroundColorSpan(COLOR_KEYWORD), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    for (j in start until i) highlighted[j] = true
                } else if (word.first().isUpperCase() && word.length > 1) {
                    // Type names (PascalCase)
                    spannable.setSpan(
                        ForegroundColorSpan(COLOR_TYPE), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    for (j in start until i) highlighted[j] = true
                }
                continue
            }

            i++
        }

        return spannable
    }

    // ── Video Player ────────────────────────────────────────────────────────

    private fun showVideo(file: File) {
        binding.videoContainer.visibility = View.VISIBLE
        val videoView = binding.videoView
        val playOverlay = binding.ivVideoPlayOverlay
        val controls = binding.videoControls

        videoView.setVideoURI(Uri.fromFile(file))

        // Show thumbnail first via Glide on the play overlay background
        videoView.setOnPreparedListener { mp ->
            isVideoInitialized = true
            binding.seekVideo.max = mp.duration
            binding.tvVideoDuration.text = formatTime(mp.duration)
            binding.tvVideoCurrent.text = formatTime(0)

            mp.setOnCompletionListener {
                isVideoPlaying = false
                _binding?.btnVideoPlay?.setImageResource(android.R.drawable.ic_media_play)
                _binding?.ivVideoPlayOverlay?.visibility = View.VISIBLE
                _binding?.videoControls?.visibility = View.GONE
                _binding?.seekVideo?.progress = 0
                _binding?.tvVideoCurrent?.text = formatTime(0)
            }
        }

        videoView.setOnErrorListener { _, _, _ ->
            _binding?.videoContainer?.visibility = View.GONE
            showUnsupported(file)
            true
        }

        // Tap play overlay to start video
        playOverlay.setOnClickListener {
            playOverlay.visibility = View.GONE
            controls.visibility = View.VISIBLE
            videoView.start()
            isVideoPlaying = true
            binding.btnVideoPlay.setImageResource(android.R.drawable.ic_media_pause)
            updateVideoSeekBar()
        }

        // Tap video to toggle controls visibility
        videoView.setOnClickListener {
            if (isVideoInitialized) {
                controls.visibility = if (controls.visibility == View.VISIBLE)
                    View.GONE else View.VISIBLE
            }
        }

        // Play/pause button
        binding.btnVideoPlay.setOnClickListener {
            if (isVideoPlaying) {
                videoView.pause()
                isVideoPlaying = false
                binding.btnVideoPlay.setImageResource(android.R.drawable.ic_media_play)
            } else {
                videoView.start()
                isVideoPlaying = true
                binding.btnVideoPlay.setImageResource(android.R.drawable.ic_media_pause)
                updateVideoSeekBar()
            }
        }

        // Seek bar
        binding.seekVideo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                    _binding?.tvVideoCurrent?.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateVideoSeekBar() {
        if (isVideoPlaying && isVideoInitialized) {
            try {
                val pos = binding.videoView.currentPosition
                _binding?.seekVideo?.progress = pos
                _binding?.tvVideoCurrent?.text = formatTime(pos)
            } catch (_: Exception) { }
            handler.postDelayed({ updateVideoSeekBar() }, 500)
        }
    }

    // ── Audio Player ────────────────────────────────────────────────────────

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
                    updateAudioSeekBar()
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

    private fun updateAudioSeekBar() {
        val mp = mediaPlayer ?: return
        if (isAudioPlaying && mp.isPlaying) {
            _binding?.seekAudio?.progress = mp.currentPosition
            _binding?.tvAudioCurrent?.text = formatTime(mp.currentPosition)
            handler.postDelayed({ updateAudioSeekBar() }, 500)
        }
    }

    // ── HTML Viewer ─────────────────────────────────────────────────────────

    private fun showHtml(file: File) {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        }
        // Load file content as data URL to avoid granting file:// access
        val htmlContent = file.readText()
        binding.webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        binding.webView.contentDescription = getString(R.string.a11y_webview_content, file.name)
    }

    // ── Markdown Viewer ─────────────────────────────────────────────────────

    private fun showMarkdown(file: File) {
        binding.webView.visibility = View.VISIBLE
        binding.webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
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
        binding.webView.contentDescription = getString(R.string.a11y_webview_content, file.name)
    }

    // ── Unsupported Fallback ────────────────────────────────────────────────

    private fun showUnsupported(file: File) {
        binding.unsupportedContainer.visibility = View.VISIBLE
        binding.tvUnsupported.text = getString(R.string.viewer_unsupported, file.name.substringAfterLast('.', ""))
        binding.btnOpenFallback.setOnClickListener { FileOpener.open(requireContext(), file) }
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pdf_page", currentPdfPage)
    }

    override fun onPause() {
        super.onPause()
        // Pause audio if playing
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isAudioPlaying = false
                _binding?.btnAudioPlay?.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        // Pause video if playing
        if (isVideoPlaying) {
            _binding?.videoView?.pause()
            isVideoPlaying = false
            _binding?.btnVideoPlay?.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        // Release audio
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.release()
        mediaPlayer = null
        isAudioPlaying = false
        // Release video
        _binding?.videoView?.stopPlayback()
        isVideoPlaying = false
        isVideoInitialized = false
        // Release PDF
        currentPdfBitmap?.recycle()
        currentPdfBitmap = null
        pdfRenderer?.close()
        pdfFd?.close()
        pdfRenderer = null
        pdfFd = null
        // WebView cleanup
        _binding?.webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        super.onDestroyView()
        _binding = null
    }
}
