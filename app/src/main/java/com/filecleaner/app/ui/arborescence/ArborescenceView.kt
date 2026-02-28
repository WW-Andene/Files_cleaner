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
import kotlin.math.max
import kotlin.math.min

class ArborescenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val isDarkMode: Boolean
        get() = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ── Layout constants ──
    private val blockWidth = 260f
    private val blockMinHeight = 80f
    private val fileLineHeight = 28f
    private val hGap = 100f  // horizontal gap between depth levels
    private val vGap = 24f   // vertical gap between sibling blocks
    private val cornerRadius = 16f
    private val headerHeight = 48f

    // ── Theme colors (resolved from resources) ──
    private val colorPrimary get() = ContextCompat.getColor(context, R.color.colorPrimary)
    private val colorAccent get() = ContextCompat.getColor(context, R.color.colorAccent)
    private val colorSurface get() = ContextCompat.getColor(context, R.color.surfaceColor)
    private val colorSurfaceDim get() = ContextCompat.getColor(context, R.color.surfaceDim)
    private val colorBorder get() = ContextCompat.getColor(context, R.color.borderDefault)
    private val colorTextPrimary get() = ContextCompat.getColor(context, R.color.textPrimary)
    private val colorTextSecondary get() = ContextCompat.getColor(context, R.color.textSecondary)
    private val colorTextTertiary get() = ContextCompat.getColor(context, R.color.textTertiary)
    private val colorPrimaryDark get() = ContextCompat.getColor(context, R.color.colorPrimaryDark)

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
        color = 0xCCFFFFFF.toInt()
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
    private val statsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.text_body)
        color = Color.WHITE
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var highlightAnimator: ValueAnimator? = null
    private var highlightAlpha = 1f

    // ── Category colors (resolved from resources for theme support) ──
    private val categoryColors: Map<FileCategory, Int> get() = mapOf(
        FileCategory.IMAGE to ContextCompat.getColor(context, R.color.catImage),
        FileCategory.VIDEO to ContextCompat.getColor(context, R.color.catVideo),
        FileCategory.AUDIO to ContextCompat.getColor(context, R.color.catAudio),
        FileCategory.DOCUMENT to ContextCompat.getColor(context, R.color.catDocument),
        FileCategory.APK to ContextCompat.getColor(context, R.color.catApk),
        FileCategory.ARCHIVE to ContextCompat.getColor(context, R.color.catArchive),
        FileCategory.DOWNLOAD to ContextCompat.getColor(context, R.color.catDownload),
        FileCategory.OTHER to ContextCompat.getColor(context, R.color.catOther)
    )

    // ── Node layout data ──
    data class NodeLayout(
        val node: DirectoryNode,
        var x: Float = 0f,
        var y: Float = 0f,
        var w: Float = 0f,
        var h: Float = 0f,
        var expanded: Boolean = false
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

    /** Recursively finds nodes with matching filtered files and adds their paths + ancestors. */
    private fun collectMatchingPaths(node: DirectoryNode, paths: MutableSet<String>): Boolean {
        var hasMatch = filteredFiles(node).isNotEmpty()
        for (child in node.children) {
            if (collectMatchingPaths(child, paths)) {
                hasMatch = true
            }
        }
        if (hasMatch) {
            paths.add(node.path)
        }
        return hasMatch
    }

    private fun rebuildWithState(node: DirectoryNode, expandedPaths: Set<String>) {
        val expanded = node.path in expandedPaths
        buildLayout(node, expanded = expanded, expandChildren = false)
        if (expanded) {
            for (child in node.children) {
                rebuildWithState(child, expandedPaths)
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
                file.name.substringAfterLast('.', "").lowercase() in filterExtensions
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
        layouts[node.path] = NodeLayout(
            node = node,
            w = blockWidth,
            h = max(blockMinHeight, h),
            expanded = expanded
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
        } else {
            // Remove all descendant layouts
            removeDescendantLayouts(node)
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
            // Use filteredFiles() to match what drawBlock() actually renders
            val files = filteredFiles(layout.node).take(maxFiles)
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

        val path = Path().apply {
            moveTo(startX, startY)
            cubicTo(midX, startY, midX, endY, endX, endY)
        }
        canvas.drawPath(path, linePaint)
    }

    private fun drawBlock(canvas: Canvas, layout: NodeLayout, isSelected: Boolean, isDropTarget: Boolean) {
        val node = layout.node
        val rect = RectF(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h)

        // Theme-aware colors from resources
        filePaint.color = colorTextPrimary
        fileSizePaint.color = colorTextSecondary
        blockStrokePaint.color = colorBorder
        linePaint.color = (colorPrimary and 0x00FFFFFF) or 0x66000000
        expandPaint.color = colorPrimary
        highlightPaint.color = colorAccent
        highlightFillPaint.color = (colorAccent and 0x00FFFFFF) or 0x44000000
        statsBgPaint.color = colorPrimaryDark

        // Block background, semi-transparent if no matching files
        val hasMatchingFiles = filteredFiles(node).isNotEmpty() ||
            (filterCategory == null && filterExtensions.isEmpty())
        val alpha = if (hasMatchingFiles) 255 else 80
        blockPaint.color = colorSurface
        blockPaint.alpha = alpha
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockPaint)
        blockPaint.alpha = 255

        // Drop target highlight
        if (isDropTarget) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)
        }

        // Selection highlight
        if (isSelected) {
            val selPaint = Paint(highlightPaint).apply { color = colorPrimary }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, selPaint)
        }

        // Header bar with category-based color
        val dominant = node.files.groupBy { it.category }
            .maxByOrNull { it.value.size }?.key ?: FileCategory.OTHER
        headerPaint.color = categoryColors[dominant] ?: 0xFF78909C.toInt()
        val headerRect = RectF(layout.x, layout.y, layout.x + layout.w, layout.y + headerHeight)
        canvas.drawRoundRect(headerRect, cornerRadius, cornerRadius, headerPaint)
        // Square off bottom corners of header
        canvas.drawRect(layout.x, layout.y + headerHeight - cornerRadius,
            layout.x + layout.w, layout.y + headerHeight, headerPaint)

        // Title (folder name)
        val displayName = if (node.name.length > 18) node.name.take(16) + "\u2026" else node.name
        canvas.drawText(displayName, layout.x + 12f, layout.y + 22f, titlePaint)

        // Subtitle (file count + size)
        val sizeStr = formatSize(node.totalSize)
        canvas.drawText("${node.totalFileCount} files \u2022 $sizeStr",
            layout.x + 12f, layout.y + 40f, subtitlePaint)

        // Expand/collapse indicator
        if (node.children.isNotEmpty()) {
            val indicator = if (layout.expanded) "\u25BC" else "\u25B6"
            canvas.drawText(indicator, layout.x + layout.w - 28f, layout.y + 30f, expandPaint)
        }

        // File list (filtered)
        val filtered = filteredFiles(node)
        val maxFiles = 5
        val files = filtered.take(maxFiles)
        for ((i, file) in files.withIndex()) {
            val fy = layout.y + headerHeight + i * fileLineHeight + 20f

            // Category dot
            val dotColor = categoryColors[file.category] ?: 0xFF78909C.toInt()
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
            canvas.drawCircle(layout.x + 16f, fy - 5f, 4f, dotPaint)

            // File name (truncated)
            val fname = if (file.name.length > 22) file.name.take(20) + "\u2026" else file.name
            canvas.drawText(fname, layout.x + 28f, fy, filePaint)

            // File size
            val fsz = formatSize(file.size)
            canvas.drawText(fsz, layout.x + layout.w - fileSizePaint.measureText(fsz) - 8f, fy, fileSizePaint)

            // Highlight matched file
            if (file.path == highlightedFilePath) {
                val fileRowTop = layout.y + headerHeight + i * fileLineHeight
                val highlightRect = RectF(
                    layout.x + 4f, fileRowTop,
                    layout.x + layout.w - 4f, fileRowTop + fileLineHeight
                )
                highlightFillPaint.alpha = (64 * highlightAlpha).toInt()
                highlightPaint.alpha = (255 * highlightAlpha).toInt()
                canvas.drawRoundRect(highlightRect, 4f, 4f, highlightFillPaint)
                canvas.drawRoundRect(highlightRect, 4f, 4f, highlightPaint)
            }
        }

        // "and N more..." label
        if (filtered.size > maxFiles) {
            val moreY = layout.y + headerHeight + maxFiles * fileLineHeight + 20f
            val moreText = "+${filtered.size - maxFiles} more\u2026"
            val savedColor = fileSizePaint.color
            fileSizePaint.color = colorTextTertiary
            canvas.drawText(moreText, layout.x + 28f, moreY, fileSizePaint)
            fileSizePaint.color = savedColor
        }

        // Border
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockStrokePaint)
    }

    private fun drawStatsOverlay(canvas: Canvas) {
        val root = rootNode ?: return
        val padding = 12f
        val lineH = 20f

        // Top-left: total stats
        val totalText = "${root.totalFileCount} files \u2022 ${formatSize(root.totalSize)}"
        val tw = statsTextPaint.measureText(totalText) + padding * 2
        canvas.drawRoundRect(RectF(8f, 8f, 8f + tw, 8f + lineH + padding * 2),
            8f, 8f, statsBgPaint)
        canvas.drawText(totalText, 8f + padding, 8f + padding + 14f, statsTextPaint)

        // Top-right: zoom + visible nodes
        val zoomText = "%.0f%%  \u2022  ${layouts.size} nodes".format(scaleFactor * 100)
        val zw = statsTextPaint.measureText(zoomText) + padding * 2
        val zx = width - 8f - zw
        canvas.drawRoundRect(RectF(zx, 8f, zx + zw, 8f + lineH + padding * 2),
            8f, 8f, statsBgPaint)
        canvas.drawText(zoomText, zx + padding, 8f + padding + 14f, statsTextPaint)
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

        // BFS to find first matching file or folder
        val queue = ArrayDeque<DirectoryNode>()
        queue.add(root)
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
            queue.addAll(node.children)
        }
        return false
    }

    fun highlightFilePath(filePath: String) {
        val root = rootNode ?: return
        val node = findNodeContainingFile(root, filePath) ?: return
        expandToNode(node)
        highlightedFilePath = filePath
        selectedPath = node.path
        zoomToFit(node.path)
        startHighlightAnimation()
        invalidate()
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

        // Pulse 3 times over 3 seconds
        highlightAnimator = ValueAnimator.ofFloat(1f, 0.3f, 1f).apply {
            duration = 1000
            repeatCount = 2
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                highlightAlpha = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Fade out after 2 more seconds
                    postDelayed({ fadeOutHighlight() }, 2000)
                }
            })
            start()
        }
    }

    private fun fadeOutHighlight() {
        if (highlightedFilePath == null) return
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500
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

    private fun findPath(current: DirectoryNode, target: DirectoryNode, path: MutableList<DirectoryNode>): Boolean {
        path.add(current)
        if (current.path == target.path) return true
        for (child in current.children) {
            if (findPath(child, target, path)) return true
        }
        path.removeAt(path.lastIndex)
        return false
    }

    private fun findNodeContainingFile(node: DirectoryNode, filePath: String): DirectoryNode? {
        for (file in node.files) {
            if (file.path == filePath) return node
        }
        for (child in node.children) {
            val result = findNodeContainingFile(child, filePath)
            if (result != null) return result
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
