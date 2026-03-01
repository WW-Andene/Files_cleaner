package com.filecleaner.app.ui.arborescence

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.MotionUtil
import kotlin.math.max
import kotlin.math.min

class ArborescenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.a11y_tree_view_default)
    }

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ── Layout constants ──
    private val blockWidth = 260f
    private val blockMinHeight = 80f
    private val fileLineHeight = 28f
    private val hGap = 100f  // horizontal gap between depth levels
    private val vGap = 32f   // vertical gap between sibling blocks
    private val cornerRadius = 16f
    private val headerHeight = 48f

    // ── Theme colors (resolved once — View is recreated on config change) ──
    private val colorPrimary by lazy { ContextCompat.getColor(context, R.color.colorPrimary) }
    private val colorAccent by lazy { ContextCompat.getColor(context, R.color.colorAccent) }
    private val colorSurface by lazy { ContextCompat.getColor(context, R.color.surfaceColor) }
    private val colorSurfaceDim by lazy { ContextCompat.getColor(context, R.color.surfaceDim) }
    private val colorBorder by lazy { ContextCompat.getColor(context, R.color.borderDefault) }
    private val colorTextPrimary by lazy { ContextCompat.getColor(context, R.color.textPrimary) }
    private val colorTextSecondary by lazy { ContextCompat.getColor(context, R.color.textSecondary) }
    private val colorTextTertiary by lazy { ContextCompat.getColor(context, R.color.textTertiary) }


    // ── Paints ──
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blockStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_subtitle)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color = Color.WHITE
        letterSpacing = -0.01f
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_body_small)
        color = ContextCompat.getColor(context, R.color.textOnColor)
    }
    private val filePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_body)
    }
    private val fileSizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_body_small)
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val expandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_title)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var highlightAnimator: ValueAnimator? = null
    private var highlightAlpha = 1f

    // ── Category colors (resolved once — View is recreated on config change) ──
    // Maps each FileCategory to its corresponding color resource; auto-populated
    // from enum entries so new categories can't be silently missed.
    private val categoryColorRes = mapOf(
        FileCategory.IMAGE to R.color.catImage,
        FileCategory.VIDEO to R.color.catVideo,
        FileCategory.AUDIO to R.color.catAudio,
        FileCategory.DOCUMENT to R.color.catDocument,
        FileCategory.APK to R.color.catApk,
        FileCategory.ARCHIVE to R.color.catArchive,
        FileCategory.DOWNLOAD to R.color.catDownload,
        FileCategory.OTHER to R.color.catOther
    ).also { map ->
        // Fail-fast if a FileCategory is added without a color mapping
        require(map.keys == FileCategory.entries.toSet()) {
            "categoryColorRes missing entries: ${FileCategory.entries.toSet() - map.keys}"
        }
    }
    private val categoryColors: Map<FileCategory, Int> by lazy {
        categoryColorRes.mapValues { (_, resId) -> ContextCompat.getColor(context, resId) }
    }

    // ── Reusable draw objects (avoid allocations in onDraw) ──
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val tempPath = Path()

    // ── Node layout data ──
    data class NodeLayout(
        val node: DirectoryNode,
        var x: Float = 0f,
        var y: Float = 0f,
        var w: Float = 0f,
        var h: Float = 0f,
        var expanded: Boolean = false,
        var cachedFiles: List<FileItem> = emptyList(),
        var dominantCategory: FileCategory = FileCategory.OTHER
    )

    private var rootNode: DirectoryNode? = null
    private val layouts = mutableMapOf<String, NodeLayout>()
    private var selectedPath: String? = null

    // ── Filter state ──
    private var filterCategory: FileCategory? = null
    private var filterExtensions: Set<String> = emptySet()

    // ── Transform ──
    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var scaleFactor = 1f
    private val minScale = 0.15f
    private val maxScale = 3f

    // ── Drag state for file move ──
    var dragFilePath: String? = null
        private set
    var dragFileName: String? = null
        private set
    private var dragX = 0f
    private var dragY = 0f
    private var isDragging = false
    private var dragSourceBlock: String? = null
    var dropTargetPath: String? = null
        private set

    // ── Callbacks ──
    var onFileMoveRequested: ((filePath: String, targetDirPath: String) -> Unit)? = null
    var onStatsUpdate: ((totalFiles: Int, totalSize: Long, visibleNodes: Int, zoom: Float) -> Unit)? = null
    var onNodeSelected: ((DirectoryNode?) -> Unit)? = null
    var onFileLongPress: ((filePath: String, fileName: String) -> Unit)? = null

    // ── Gesture detectors ──
    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val oldScale = scaleFactor
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
                val ratio = scaleFactor / oldScale
                viewMatrix.postScale(ratio, ratio, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                if (!isDragging) {
                    viewMatrix.postTranslate(-dx, -dy)
                    invalidate()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val pt = screenToWorld(e.x, e.y)
                val hit = hitTest(pt[0], pt[1])
                if (hit != null) {
                    toggleExpand(hit)
                } else {
                    selectedPath = null
                    onNodeSelected?.invoke(null)
                    invalidate()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val pt = screenToWorld(e.x, e.y)
                val hit = hitTest(pt[0], pt[1])
                if (hit != null) {
                    zoomToFit(hit)
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val pt = screenToWorld(e.x, e.y)
                val hit = hitTestFile(pt[0], pt[1])
                if (hit != null) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    // If context menu callback is set, use that; otherwise drag
                    if (onFileLongPress != null) {
                        onFileLongPress?.invoke(hit.first, hit.second)
                    } else {
                        isDragging = true
                        dragFilePath = hit.first
                        dragFileName = hit.second
                        dragSourceBlock = hit.third
                        dragX = e.x
                        dragY = e.y
                        invalidate()
                    }
                }
            }
        })

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        highlightAnimator?.cancel()
        highlightAnimator = null
    }

    fun getExpandedPaths(): Set<String> =
        layouts.filter { it.value.expanded }.keys.toSet()

    fun setTree(root: DirectoryNode) {
        rootNode = root
        layouts.clear()
        selectedPath = null
        // Start collapsed — user expands as they explore
        buildLayout(root, expanded = false, expandChildren = false)
        computePositions()
        // Center on root
        viewMatrix.reset()
        scaleFactor = 1f
        viewMatrix.postTranslate(60f, 60f)
        contentDescription = context.getString(
            R.string.a11y_tree_view, root.totalFileCount, formatSize(root.totalSize))
        invalidate()
        notifyStats()
    }

    fun setTreeWithState(root: DirectoryNode, expandedPaths: Set<String>) {
        rootNode = root
        layouts.clear()
        selectedPath = null
        rebuildWithState(root, expandedPaths)
        computePositions()
        if (expandedPaths.isEmpty()) {
            viewMatrix.reset()
            scaleFactor = 1f
            viewMatrix.postTranslate(60f, 60f)
        }
        contentDescription = context.getString(
            R.string.a11y_tree_view, root.totalFileCount, formatSize(root.totalSize))
        invalidate()
        notifyStats()
    }

    fun setFilter(category: FileCategory?, extensions: Set<String>) {
        filterCategory = category
        filterExtensions = extensions

        val root = rootNode ?: return

        // Preserve manually expanded paths
        val expandedPaths = layouts.filter { it.value.expanded }.keys.toMutableSet()

        // Auto-expand nodes with matching files when a filter is active
        if (category != null || extensions.isNotEmpty()) {
            collectMatchingPaths(root, expandedPaths)
        }

        layouts.clear()
        rebuildWithState(root, expandedPaths)
        computePositions()
        invalidate()
        notifyStats()
    }

    /** Iterative post-order traversal — marks nodes with matching files + their ancestors. */
    private fun collectMatchingPaths(root: DirectoryNode, paths: MutableSet<String>) {
        // Phase 1: collect all nodes in pre-order (parents before children)
        val ordered = mutableListOf<DirectoryNode>()
        val stack = ArrayDeque<DirectoryNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            ordered.add(node)
            for (child in node.children.asReversed()) stack.addLast(child)
        }
        // Phase 2: iterate in reverse (post-order) to propagate matches upward
        val matchSet = mutableSetOf<String>()
        for (node in ordered.asReversed()) {
            val hasOwnMatch = filteredFiles(node).isNotEmpty()
            val childMatch = node.children.any { it.path in matchSet }
            if (hasOwnMatch || childMatch) {
                matchSet.add(node.path)
                paths.add(node.path)
            }
        }
    }

    /** Iterative rebuild — avoids stack overflow on deeply nested trees. */
    private fun rebuildWithState(root: DirectoryNode, expandedPaths: Set<String>) {
        val stack = ArrayDeque<DirectoryNode>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val expanded = node.path in expandedPaths
            buildLayout(node, expanded = expanded, expandChildren = false)
            if (expanded) {
                for (child in node.children.asReversed()) stack.addLast(child)
            }
        }
    }

    private fun filteredFiles(node: DirectoryNode): List<FileItem> {
        var files = node.files
        if (filterCategory != null) {
            files = files.filter { it.category == filterCategory }
        }
        if (filterExtensions.isNotEmpty()) {
            files = files.filter { file ->
                file.extension in filterExtensions
            }
        }
        return files
    }

    private fun buildLayout(node: DirectoryNode, expanded: Boolean, expandChildren: Boolean) {
        val filtered = filteredFiles(node)
        val maxFiles = 5
        val displayFiles = min(filtered.size, maxFiles)
        val h = headerHeight + displayFiles * fileLineHeight +
            (if (filtered.size > maxFiles) fileLineHeight else 0f) + 12f
        val dominant = node.files.groupBy { it.category }
            .maxByOrNull { it.value.size }?.key ?: FileCategory.OTHER
        layouts[node.path] = NodeLayout(
            node = node,
            w = blockWidth,
            h = max(blockMinHeight, h),
            expanded = expanded,
            cachedFiles = filtered,
            dominantCategory = dominant
        )
        if (expanded) {
            for (child in node.children) {
                buildLayout(child, expandChildren, expandChildren = false)
            }
        }
    }

    private fun computePositions() {
        val root = rootNode ?: return
        var yOffset = 0f
        positionSubtree(root, 0f, yOffset)
    }

    /** Lay out node and its visible children; returns total height consumed. */
    private fun positionSubtree(node: DirectoryNode, x: Float, y: Float): Float {
        val layout = layouts[node.path] ?: return 0f
        layout.x = x

        if (!layout.expanded || node.children.isEmpty()) {
            layout.y = y
            return layout.h + vGap
        }

        // Position children first to compute total child height
        val childX = x + blockWidth + hGap
        var childY = y
        var totalChildHeight = 0f
        for (child in node.children) {
            val childLayout = layouts[child.path] ?: continue
            val consumed = positionSubtree(child, childX, childY)
            totalChildHeight += consumed
            childY += consumed
        }

        // Center parent vertically among its children
        val parentTop = y + (totalChildHeight - layout.h) / 2f
        layout.y = max(y, parentTop)

        return max(totalChildHeight, layout.h + vGap)
    }

    private fun toggleExpand(path: String) {
        val layout = layouts[path] ?: return
        val node = layout.node

        selectedPath = path
        onNodeSelected?.invoke(node)

        if (node.children.isEmpty()) {
            invalidate()
            return
        }

        layout.expanded = !layout.expanded

        if (layout.expanded) {
            // Add child layouts
            for (child in node.children) {
                if (child.path !in layouts) {
                    buildLayout(child, expanded = false, expandChildren = false)
                }
            }
            announceForAccessibility(context.getString(
                R.string.a11y_node_expanded, node.name, node.children.size))
        } else {
            // Remove all descendant layouts
            removeDescendantLayouts(node)
            announceForAccessibility(context.getString(
                R.string.a11y_node_collapsed, node.name))
        }

        computePositions()
        invalidate()
        notifyStats()
    }

    private fun removeDescendantLayouts(node: DirectoryNode) {
        for (child in node.children) {
            layouts.remove(child.path)
            removeDescendantLayouts(child)
        }
    }

    private fun hitTest(wx: Float, wy: Float): String? {
        for ((path, layout) in layouts) {
            if (wx >= layout.x && wx <= layout.x + layout.w &&
                wy >= layout.y && wy <= layout.y + layout.h
            ) {
                return path
            }
        }
        return null
    }

    /** Returns (filePath, fileName, blockPath) if a file row was hit. */
    private fun hitTestFile(wx: Float, wy: Float): Triple<String, String, String>? {
        for ((blockPath, layout) in layouts) {
            if (wx < layout.x || wx > layout.x + layout.w) continue
            val fileStartY = layout.y + headerHeight
            val maxFiles = 5
            val files = layout.cachedFiles.take(maxFiles)
            for ((i, file) in files.withIndex()) {
                val rowTop = fileStartY + i * fileLineHeight
                if (wy >= rowTop && wy <= rowTop + fileLineHeight) {
                    return Triple(file.path, file.name, blockPath)
                }
            }
        }
        return null
    }

    private fun zoomToFit(path: String) {
        val layout = layouts[path] ?: return
        val cw = width.toFloat()
        val ch = height.toFloat()
        val targetScale = min(cw / (layout.w + 40f), ch / (layout.h + 40f)).coerceIn(minScale, maxScale)
        scaleFactor = targetScale
        viewMatrix.reset()
        viewMatrix.postScale(scaleFactor, scaleFactor)
        viewMatrix.postTranslate(
            cw / 2f - (layout.x + layout.w / 2f) * scaleFactor,
            ch / 2f - (layout.y + layout.h / 2f) * scaleFactor
        )
        invalidate()
    }

    private fun screenToWorld(sx: Float, sy: Float): FloatArray {
        viewMatrix.invert(inverseMatrix)
        val pts = floatArrayOf(sx, sy)
        inverseMatrix.mapPoints(pts)
        return pts
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        if (isDragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    dragX = event.x
                    dragY = event.y
                    val pt = screenToWorld(dragX, dragY)
                    dropTargetPath = hitTest(pt[0], pt[1])?.takeIf { it != dragSourceBlock }
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragFilePath != null && dropTargetPath != null) {
                        onFileMoveRequested?.invoke(dragFilePath!!, dropTargetPath!!)
                    }
                    isDragging = false
                    dragFilePath = null
                    dragFileName = null
                    dragSourceBlock = null
                    dropTargetPath = null
                    invalidate()
                }
            }
            return true
        }

        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rootNode == null) return

        // Set theme-aware paint colors once per frame (not per block)
        filePaint.color = colorTextPrimary
        fileSizePaint.color = colorTextSecondary
        blockStrokePaint.color = colorBorder
        linePaint.color = (colorPrimary and 0x00FFFFFF) or 0x66000000
        expandPaint.color = colorPrimary
        highlightPaint.color = colorAccent
        highlightFillPaint.color = (colorAccent and 0x00FFFFFF) or 0x44000000

        canvas.save()
        canvas.concat(viewMatrix)

        // Draw connections first (below blocks)
        for ((_, layout) in layouts) {
            if (!layout.expanded) continue
            for (child in layout.node.children) {
                val childLayout = layouts[child.path] ?: continue
                drawConnection(canvas, layout, childLayout)
            }
        }

        // Draw blocks
        for ((path, layout) in layouts) {
            drawBlock(canvas, layout, path == selectedPath, path == dropTargetPath)
        }

        canvas.restore()

        // Draw drag ghost
        if (isDragging && dragFileName != null) {
            drawDragGhost(canvas)
        }
    }

    private fun drawConnection(canvas: Canvas, parent: NodeLayout, child: NodeLayout) {
        val startX = parent.x + parent.w
        val startY = parent.y + parent.h / 2f
        val endX = child.x
        val endY = child.y + child.h / 2f
        val midX = (startX + endX) / 2f

        tempPath.rewind()
        tempPath.moveTo(startX, startY)
        tempPath.cubicTo(midX, startY, midX, endY, endX, endY)
        canvas.drawPath(tempPath, linePaint)
    }

    private fun drawBlock(canvas: Canvas, layout: NodeLayout, isSelected: Boolean, isDropTarget: Boolean) {
        val node = layout.node
        val rect = RectF(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h)

        // Block background, semi-transparent if no matching files
        val hasMatchingFiles = layout.cachedFiles.isNotEmpty() ||
            (filterCategory == null && filterExtensions.isEmpty())
        val blockAlpha = if (hasMatchingFiles) 255 else 80
        blockPaint.color = colorSurface
        blockPaint.alpha = blockAlpha
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockPaint)
        blockPaint.alpha = 255

        // Clip to rounded block bounds to prevent text overflow / superposition
        canvas.save()
        tempPath.rewind()
        tempPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(tempPath)

        // Header bar with category-based color (dominant cached at layout build time)
        headerPaint.color = categoryColors[layout.dominantCategory] ?: categoryColors[FileCategory.OTHER]!!
        val headerRect = RectF(layout.x, layout.y, layout.x + layout.w, layout.y + headerHeight)
        canvas.drawRoundRect(headerRect, cornerRadius, cornerRadius, headerPaint)
        // Square off bottom corners of header
        canvas.drawRect(layout.x, layout.y + headerHeight - cornerRadius,
            layout.x + layout.w, layout.y + headerHeight, headerPaint)

        // Title (folder name) — pixel-based truncation to fit within block
        val titleMaxWidth = layout.w - 24f - (if (node.children.isNotEmpty()) 28f else 0f)
        val displayName = ellipsizeText(node.name, titlePaint, titleMaxWidth)
        canvas.drawText(displayName, layout.x + 12f, layout.y + 22f, titlePaint)

        // Subtitle (file count + size) — also truncated to block width
        val sizeStr = formatSize(node.totalSize)
        val subtitleText = context.resources.getQuantityString(
            R.plurals.tree_node_subtitle, node.totalFileCount, node.totalFileCount, sizeStr)
        val subtitleMaxWidth = layout.w - 24f
        canvas.drawText(ellipsizeText(subtitleText, subtitlePaint, subtitleMaxWidth),
            layout.x + 12f, layout.y + 40f, subtitlePaint)

        // Expand/collapse indicator
        if (node.children.isNotEmpty()) {
            val indicator = if (layout.expanded) "\u25BC" else "\u25B6"
            canvas.drawText(indicator, layout.x + layout.w - 28f, layout.y + 30f, expandPaint)
        }

        // File list (filtered) — clip to file area within block
        val filtered = layout.cachedFiles
        val maxFiles = 5
        val files = filtered.take(maxFiles)
        var highlightFileRowTop = -1f // Track for drawing arrow outside clip

        canvas.save()
        canvas.clipRect(layout.x, layout.y + headerHeight, layout.x + layout.w, layout.y + layout.h)
        for ((i, file) in files.withIndex()) {
            val fy = layout.y + headerHeight + i * fileLineHeight + 20f

            // Category dot
            dotPaint.color = categoryColors[file.category] ?: categoryColors[FileCategory.OTHER]!!
            canvas.drawCircle(layout.x + 16f, fy - 5f, 4f, dotPaint)

            // File name (truncated to fit before file size)
            val fsz = formatSize(file.size)
            val fszWidth = fileSizePaint.measureText(fsz)
            val maxNameWidth = layout.w - 28f - fszWidth - 16f
            val fname = ellipsizeText(file.name, filePaint, maxNameWidth)
            canvas.drawText(fname, layout.x + 28f, fy, filePaint)

            // File size
            canvas.drawText(fsz, layout.x + layout.w - fszWidth - 8f, fy, fileSizePaint)

            // Highlight matched file row fill + border
            if (file.path == highlightedFilePath) {
                val fileRowTop = layout.y + headerHeight + i * fileLineHeight
                highlightFileRowTop = fileRowTop
                val highlightRect = RectF(
                    layout.x + 2f, fileRowTop,
                    layout.x + layout.w - 2f, fileRowTop + fileLineHeight
                )

                // Strong fill + border on the file row
                highlightFillPaint.color = colorAccent
                highlightFillPaint.setAlpha((180 * highlightAlpha).toInt())
                val savedStrokeWidth = highlightPaint.strokeWidth
                highlightPaint.strokeWidth = 3f
                highlightPaint.setAlpha((255 * highlightAlpha).toInt())
                canvas.drawRoundRect(highlightRect, 6f, 6f, highlightFillPaint)
                canvas.drawRoundRect(highlightRect, 6f, 6f, highlightPaint)
                highlightPaint.strokeWidth = savedStrokeWidth
            }
        }

        // "and N more..." label
        if (filtered.size > maxFiles) {
            val moreY = layout.y + headerHeight + maxFiles * fileLineHeight + 20f
            val remaining = filtered.size - maxFiles
            val moreText = context.resources.getQuantityString(
                R.plurals.tree_more_files, remaining, remaining)
            val savedColor = fileSizePaint.color
            fileSizePaint.color = colorTextTertiary
            canvas.drawText(moreText, layout.x + 28f, moreY, fileSizePaint)
            fileSizePaint.color = savedColor
        }

        canvas.restore() // Restore from file list clip
        canvas.restore() // Restore from block-level clip

        // --- Draw elements outside clip (borders, highlights, arrows) ---

        // Border
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockStrokePaint)

        // Drop target highlight
        if (isDropTarget) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)
        }

        // Selection highlight (reuse pre-allocated selectionPaint)
        if (isSelected) {
            selectionPaint.color = colorPrimary
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selectionPaint)
        }

        // Highlight glow + arrow (drawn outside clip so arrow extends left of block)
        if (highlightFileRowTop >= 0f) {
            // Glow outline around the entire containing block (reuse pre-allocated glowPaint)
            glowPaint.color = colorAccent
            glowPaint.setAlpha((160 * highlightAlpha).toInt())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint)

            // Large arrow indicator on the left edge
            highlightArrowPaint.color = colorAccent
            highlightArrowPaint.alpha = (255 * highlightAlpha).toInt()
            tempPath.rewind()
            val cy = highlightFileRowTop + fileLineHeight / 2f
            tempPath.moveTo(layout.x - 20f, cy - 12f)
            tempPath.lineTo(layout.x - 4f, cy)
            tempPath.lineTo(layout.x - 20f, cy + 12f)
            tempPath.close()
            canvas.drawPath(tempPath, highlightArrowPaint)
        }
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return "\u2026"
        if (paint.measureText(text) <= maxWidth) return text
        // Binary search for the longest prefix that fits (O(log n) measureText calls)
        var lo = 1
        var hi = text.length - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (paint.measureText(text, 0, mid) + paint.measureText("\u2026") <= maxWidth) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        return if (lo > 0) text.substring(0, lo) + "\u2026" else "\u2026"
    }

    private fun drawDragGhost(canvas: Canvas) {
        val name = dragFileName ?: return
        val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (colorAccent and 0x00FFFFFF) or 0xDD000000.toInt()
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextPrimary
            textSize = 16f
        }
        val tw = textPaint.measureText(name) + 24f
        canvas.drawRoundRect(
            RectF(dragX - tw / 2, dragY - 24f, dragX + tw / 2, dragY + 8f),
            12f, 12f, ghostPaint
        )
        canvas.drawText(name, dragX - tw / 2 + 12f, dragY - 4f, textPaint)
    }

    // ── Search + highlight ──
    var highlightedFilePath: String? = null
        private set

    fun searchAndHighlight(query: String): Boolean {
        if (query.isBlank()) {
            clearHighlight()
            return false
        }
        val root = rootNode ?: return false
        val lowerQuery = query.lowercase()

        // BFS to find first matching file or folder (visited set guards against symlink cycles)
        val queue = ArrayDeque<DirectoryNode>()
        val visited = mutableSetOf<String>()
        queue.add(root)
        visited.add(root.path)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            // Check folder name
            if (node.name.lowercase().contains(lowerQuery)) {
                expandToNode(node)
                highlightedFilePath = null
                selectedPath = node.path
                zoomToFit(node.path)
                onNodeSelected?.invoke(node)
                invalidate()
                return true
            }
            // Check files in this folder
            for (file in node.files) {
                if (file.name.lowercase().contains(lowerQuery)) {
                    expandToNode(node)
                    highlightedFilePath = file.path
                    selectedPath = node.path
                    zoomToFit(node.path)
                    invalidate()
                    return true
                }
            }
            for (child in node.children) {
                if (child.path !in visited) {
                    visited.add(child.path)
                    queue.add(child)
                }
            }
        }
        return false
    }

    fun highlightFilePath(filePath: String) {
        val root = rootNode ?: return
        val node = findNodeContainingFile(root, filePath) ?: return
        expandToNode(node)
        highlightedFilePath = filePath
        selectedPath = node.path

        // Defer zoom if view hasn't been laid out yet (e.g. just navigated to tab)
        if (width == 0 || height == 0) {
            post {
                zoomToFileRow(node.path, filePath)
                startHighlightAnimation()
                invalidate()
            }
        } else {
            zoomToFileRow(node.path, filePath)
            startHighlightAnimation()
            invalidate()
        }
    }

    /** Zoom and center so the specific file row is in the middle of the screen. */
    private fun zoomToFileRow(blockPath: String, filePath: String) {
        val layout = layouts[blockPath] ?: return
        val cw = width.toFloat()
        val ch = height.toFloat()
        if (cw <= 0f || ch <= 0f) return

        // Find the Y position of the specific file row
        val files = layout.cachedFiles.take(5)
        val fileIndex = files.indexOfFirst { it.path == filePath }
        val fileRowCenterY = if (fileIndex >= 0) {
            layout.y + headerHeight + fileIndex * fileLineHeight + fileLineHeight / 2f
        } else {
            layout.y + layout.h / 2f
        }

        // Use a scale that shows the block comfortably — zoomed out enough to see context
        val targetScale = min(cw / (layout.w + 200f), ch / (layout.h + 200f))
            .coerceIn(0.5f, 1.2f)
        scaleFactor = targetScale
        viewMatrix.reset()
        viewMatrix.postScale(scaleFactor, scaleFactor)
        viewMatrix.postTranslate(
            cw / 2f - (layout.x + layout.w / 2f) * scaleFactor,
            ch / 2f - fileRowCenterY * scaleFactor
        )
    }

    fun clearHighlight() {
        highlightAnimator?.cancel()
        highlightAnimator = null
        highlightedFilePath = null
        highlightAlpha = 1f
        highlightPaint.alpha = 255
        highlightFillPaint.alpha = 64
        invalidate()
    }

    private fun startHighlightAnimation() {
        highlightAnimator?.cancel()
        highlightAlpha = 1f

        if (MotionUtil.isReducedMotion(context)) {
            // Skip pulsing — just show highlight then fade out after delay
            invalidate()
            postDelayed({ fadeOutHighlight() }, 3000)
            return
        }

        val emphasisDuration = resources.getInteger(R.integer.motion_emphasis).toLong()
        highlightAnimator = ValueAnimator.ofFloat(1f, 0.2f, 1f).apply {
            duration = emphasisDuration
            repeatCount = 4
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                highlightAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    postDelayed({ fadeOutHighlight() }, 3000)
                }
            })
            start()
        }
    }

    private fun fadeOutHighlight() {
        if (highlightedFilePath == null) return
        highlightAnimator?.cancel()

        if (MotionUtil.isReducedMotion(context)) {
            highlightedFilePath = null
            highlightAlpha = 1f
            highlightPaint.alpha = 255
            highlightFillPaint.alpha = 64
            invalidate()
            return
        }

        val enterDuration = resources.getInteger(R.integer.motion_enter).toLong()
        highlightAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = enterDuration
            addUpdateListener {
                highlightAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    highlightedFilePath = null
                    highlightAlpha = 1f
                    highlightPaint.alpha = 255
                    highlightFillPaint.alpha = 64
                    invalidate()
                }
            })
            start()
        }
    }

    private fun expandToNode(target: DirectoryNode) {
        val root = rootNode ?: return
        val path = mutableListOf<DirectoryNode>()
        if (!findPath(root, target, path)) return

        // Expand each ancestor
        for (ancestor in path) {
            val layout = layouts[ancestor.path]
            if (layout != null && !layout.expanded && ancestor.children.isNotEmpty()) {
                layout.expanded = true
                for (child in ancestor.children) {
                    if (child.path !in layouts) {
                        buildLayout(child, expanded = false, expandChildren = false)
                    }
                }
            }
        }
        computePositions()
        notifyStats()
    }

    /** Iterative DFS path-finding — avoids stack overflow on deeply nested trees. */
    private fun findPath(root: DirectoryNode, target: DirectoryNode, path: MutableList<DirectoryNode>): Boolean {
        // DFS with explicit stack; each frame tracks (node, childIndex)
        val stack = ArrayDeque<Pair<DirectoryNode, Int>>()
        stack.addLast(root to 0)
        path.add(root)
        if (root.path == target.path) return true

        while (stack.isNotEmpty()) {
            val (node, idx) = stack.last()
            if (idx < node.children.size) {
                stack[stack.lastIndex] = node to (idx + 1)
                val child = node.children[idx]
                path.add(child)
                if (child.path == target.path) return true
                stack.addLast(child to 0)
            } else {
                stack.removeLast()
                path.removeAt(path.lastIndex)
            }
        }
        return false
    }

    /** Iterative BFS search — avoids stack overflow on deeply nested trees. */
    private fun findNodeContainingFile(root: DirectoryNode, filePath: String): DirectoryNode? {
        val queue = ArrayDeque<DirectoryNode>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.files.any { it.path == filePath }) return node
            queue.addAll(node.children)
        }
        return null
    }

    private fun notifyStats() {
        val root = rootNode ?: return
        onStatsUpdate?.invoke(root.totalFileCount, root.totalSize, layouts.size, scaleFactor)
    }

    private fun formatSize(bytes: Long): String =
        com.filecleaner.app.utils.UndoHelper.formatBytes(bytes)
}
