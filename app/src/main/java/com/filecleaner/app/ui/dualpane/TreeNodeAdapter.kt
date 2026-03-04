package com.filecleaner.app.ui.dualpane

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.utils.UndoHelper

/**
 * Adapter that renders a directory tree (arborescence) as a flat list
 * with indentation based on depth. Nodes can be expanded/collapsed.
 */
class TreeNodeAdapter : RecyclerView.Adapter<TreeNodeAdapter.ViewHolder>() {

    data class FlatNode(
        val node: DirectoryNode,
        val depth: Int,
        var expanded: Boolean = false
    )

    private val flatList = mutableListOf<FlatNode>()
    private val expandedPaths = mutableSetOf<String>()

    /** Called when user taps a directory node — navigate the pane to that path. */
    var onDirectorySelected: ((String) -> Unit)? = null

    fun setRootNode(root: DirectoryNode) {
        flatList.clear()
        expandedPaths.clear()
        // Auto-expand root
        expandedPaths.add(root.path)
        rebuildFlatList(root, 0)
        notifyDataSetChanged()
    }

    private fun rebuildFlatList(node: DirectoryNode, depth: Int) {
        val isExpanded = node.path in expandedPaths
        flatList.add(FlatNode(node, depth, isExpanded))
        if (isExpanded) {
            for (child in node.children.sortedBy { it.name.lowercase() }) {
                rebuildFlatList(child, depth + 1)
            }
        }
    }

    private fun refreshList(root: DirectoryNode) {
        flatList.clear()
        rebuildFlatList(root, 0)
        notifyDataSetChanged()
    }

    private fun findRoot(): DirectoryNode? {
        return flatList.firstOrNull()?.node?.let { first ->
            // Walk up to depth 0
            flatList.find { it.depth == 0 }?.node
        }
    }

    override fun getItemCount(): Int = flatList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dual_pane_tree_node, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = flatList[position]
        val node = item.node
        val ctx = holder.itemView.context

        // Indentation via left padding
        val basePad = ctx.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val indentPx = ctx.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        holder.itemView.setPadding(
            basePad + (item.depth * indentPx),
            holder.itemView.paddingTop,
            holder.itemView.paddingRight,
            holder.itemView.paddingBottom
        )

        holder.name.text = node.name

        // Info: file count + size
        val info = "${node.totalFileCount} files \u2022 ${UndoHelper.formatBytes(node.totalSize)}"
        holder.info.text = info

        // Expand/collapse arrow
        if (node.children.isNotEmpty()) {
            holder.expandIcon.visibility = View.VISIBLE
            holder.expandIcon.rotation = if (item.expanded) 90f else 0f
            holder.expandIcon.setOnClickListener {
                toggleExpand(position)
            }
        } else {
            holder.expandIcon.visibility = View.INVISIBLE
        }

        // Tap the row to navigate to that directory in the pane
        holder.itemView.setOnClickListener {
            onDirectorySelected?.invoke(node.path)
        }

        // Long-press to expand/collapse
        holder.itemView.setOnLongClickListener {
            if (node.children.isNotEmpty()) {
                toggleExpand(position)
            }
            true
        }
    }

    private fun toggleExpand(position: Int) {
        val item = flatList[position]
        if (item.expanded) {
            expandedPaths.remove(item.node.path)
        } else {
            expandedPaths.add(item.node.path)
        }
        val root = findRoot() ?: return
        refreshList(root)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expandIcon: ImageView = view.findViewById(R.id.iv_expand)
        val folderIcon: ImageView = view.findViewById(R.id.iv_folder_icon)
        val name: TextView = view.findViewById(R.id.tv_node_name)
        val info: TextView = view.findViewById(R.id.tv_node_info)
    }
}
