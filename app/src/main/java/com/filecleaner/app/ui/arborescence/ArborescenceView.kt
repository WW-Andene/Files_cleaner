package com.filecleaner.app.ui.arborescence

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
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
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

    // ── Paints ──
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blockStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF455A64.toInt()
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f
        color = 0xCCFFFFFF.toInt()
    }
    private val filePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = 0xFF212121.toInt()
    }
    private val fileSizePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        color = 0xFF757575.toInt()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x6600897B.toInt()
    }
    private val expandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 22f
        color = 0xFF00897B.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val statsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC000000.toInt()
        style = Paint.Style.FILL
    }
    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = Color.WHITE
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFFB300.toInt()
    }

    // ── Category colors ──
    private val categoryColors = mapOf(
        FileCategory.IMAGE to 0xFF43A047.toInt(),
        FileCategory.VIDEO to 0xFF1E88E5.toInt(),
        FileCategory.AUDIO to 0xFFE53935.toInt(),
        FileCategory.DOCUMENT to 0xFF8E24AA.toInt(),
        FileCategory.APK to 0xFFFF6F00.toInt(),
        FileCategory.ARCHIVE to 0xFF6D4C41.toInt(),
        FileCategory.DOWNLOAD to 0xFF00ACC1.toInt(),
        FileCategory.OTHER to 0xFF78909C.toInt()
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
                    isDragging = true
                    dragFilePath = hit.first
                    dragFileName = hit.second
                    dragSourceBlock = hit.third
                    dragX = e.x
                    dragY = e.y
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    invalidate()
                }
            }
        })

    fun setTree(root: DirectoryNode) {
        rootNode = root
        layouts.clear()
        selectedPath = null
        // Expand root + first level by default (2-level display)
        buildLayout(root, expanded = true, expandChildren = true)
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

    private fun buildLayout(node: DirectoryNode, expanded: Boolean, expandChildren: Boolean) {
        val maxFiles = 5
        val displayFiles = min(node.files.size, maxFiles)
        val h = headerHeight + displayFiles * fileLineHeight +
            (if (node.files.size > maxFiles) fileLineHeight else 0f) + 12f
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
            val files = layout.node.files.take(maxFiles)
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

        // Draw stats overlay in screen space
        drawStatsOverlay(canvas)

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

        // Theme-aware text colors
        filePaint.color = if (isDarkMode) 0xFFE0E0E0.toInt() else 0xFF212121.toInt()
        fileSizePaint.color = if (isDarkMode) 0xFFB0B0B0.toInt() else 0xFF757575.toInt()
        blockStrokePaint.color = if (isDarkMode) 0xFF616161.toInt() else 0xFF455A64.toInt()
        linePaint.color = if (isDarkMode) 0x664DB6AC.toInt() else 0x6600897B.toInt()

        // Block background (theme-aware)
        blockPaint.color = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFFAFAFA.toInt()
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockPaint)

        // Drop target highlight
        if (isDropTarget) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)
        }

        // Selection highlight
        if (isSelected) {
            val selPaint = Paint(highlightPaint).apply { color = 0xFF00897B.toInt() }
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

        // File list
        val maxFiles = 5
        val files = node.files.take(maxFiles)
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
        }

        // "and N more..." label
        if (node.files.size > maxFiles) {
            val moreY = layout.y + headerHeight + maxFiles * fileLineHeight + 20f
            val moreText = "+${node.files.size - maxFiles} more\u2026"
            fileSizePaint.color = 0xFF9E9E9E.toInt()
            canvas.drawText(moreText, layout.x + 28f, moreY, fileSizePaint)
            fileSizePaint.color = 0xFF757575.toInt()
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
            color = 0xDDFFB300.toInt()
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF212121.toInt()
            textSize = 16f
        }
        val tw = textPaint.measureText(name) + 24f
        canvas.drawRoundRect(
            RectF(dragX - tw / 2, dragY - 24f, dragX + tw / 2, dragY + 8f),
            12f, 12f, ghostPaint
        )
        canvas.drawText(name, dragX - tw / 2 + 12f, dragY - 4f, textPaint)
    }

    private fun notifyStats() {
        val root = rootNode ?: return
        onStatsUpdate?.invoke(root.totalFileCount, root.totalSize, layouts.size, scaleFactor)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
