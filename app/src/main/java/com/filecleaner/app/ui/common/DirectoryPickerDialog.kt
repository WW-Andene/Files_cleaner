package com.filecleaner.app.ui.common

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import android.os.Environment
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode

object DirectoryPickerDialog {

    // File manager needs broad storage path; MANAGE_EXTERNAL_STORAGE grants access
    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    fun show(
        context: Context,
        rootNode: DirectoryNode,
        excludePath: String? = null,
        onSelected: (String) -> Unit
    ) {
        var currentNode = rootNode
        val dp = context.resources.displayMetrics.density

        fun buildDialog() {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            }

            // Current path display
            val pathLabel = TextView(context).apply {
                text = currentNode.path.removePrefix(storagePath)
                    .ifEmpty { "/" }
                setTextColor(ContextCompat.getColor(context, R.color.textSecondary))
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, (8 * dp).toInt())
            }
            container.addView(pathLabel)

            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (300 * dp).toInt()
                )
            }

            val listLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Back / parent entry (if not root)
            if (currentNode != rootNode) {
                val backRow = createRow(context, dp, context.getString(R.string.dir_picker_back), isBack = true)
                backRow.setOnClickListener {
                    val parentPath = currentNode.path.substringBeforeLast('/')
                    val parent = findNode(rootNode, parentPath)
                    if (parent != null) {
                        currentNode = parent
                        // Dismiss and rebuild
                        buildDialog()
                    }
                }
                listLayout.addView(backRow)
            }

            // Child directories
            val children = currentNode.children.sortedBy { it.name.lowercase() }
            if (children.isEmpty()) {
                val emptyLabel = TextView(context).apply {
                    text = context.getString(R.string.no_subdirectories)
                    setTextColor(ContextCompat.getColor(context, R.color.textTertiary))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, (24 * dp).toInt(), 0, (24 * dp).toInt())
                }
                listLayout.addView(emptyLabel)
            } else {
                for (child in children) {
                    if (child.path == excludePath) continue
                    val row = createRow(context, dp, child.name, isBack = false)
                    row.setOnClickListener {
                        currentNode = child
                        buildDialog()
                    }
                    listLayout.addView(row)
                }
            }

            scrollView.addView(listLayout)
            container.addView(scrollView)

            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.move_to_title))
                .setView(container)
                .setPositiveButton(context.getString(R.string.select_directory)) { _, _ ->
                    onSelected(currentNode.path)
                }
                .setNegativeButton(context.getString(R.string.cancel), null)
                .show()
        }

        buildDialog()
    }

    private fun createRow(context: Context, dp: Float, label: String, isBack: Boolean): TextView {
        return TextView(context).apply {
            text = if (isBack) label else context.getString(R.string.dir_picker_folder, label)
            textSize = 15f
            setTextColor(ContextCompat.getColor(context,
                if (isBack) R.color.colorPrimary else R.color.textPrimary))
            if (isBack) typeface = Typeface.DEFAULT_BOLD
            setPadding((8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt())
            setBackgroundResource(android.R.attr.selectableItemBackground.let {
                val attrs = intArrayOf(it)
                val ta = context.obtainStyledAttributes(attrs)
                val resId = ta.getResourceId(0, 0)
                ta.recycle()
                resId
            })
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun findNode(root: DirectoryNode, path: String): DirectoryNode? {
        if (root.path == path) return root
        for (child in root.children) {
            val found = findNode(child, path)
            if (found != null) return found
        }
        return null
    }
}
