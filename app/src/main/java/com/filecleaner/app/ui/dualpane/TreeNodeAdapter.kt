package com.filecleaner.app.ui.dualpane

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.utils.UndoHelper

/**
 * Adapter that renders a directory tree (arborescence) as a flat list
 * with indentation based on depth. Nodes can be expanded/collapsed.
 */
class TreeNodeAdapter : ListAdapter<TreeNodeAdapter.FlatNode, TreeNodeAdapter.ViewHolder>(DIFF) {

    data class FlatNode(
        val node: DirectoryNode,
        val depth: Int,
        val expanded: Boolean = false
    )

    companion object {
        private const val PAYLOAD_EXPAND = "expand"

        private val DIFF = object : DiffUtil.ItemCallback<FlatNode>() {
            override fun areItemsTheSame(a: FlatNode, b: FlatNode) =
                a.node.path == b.node.path

            override fun areContentsTheSame(a: FlatNode, b: FlatNode) =
                a == b

            override fun getChangePayload(a: FlatNode, b: FlatNode): Any? {
                if (a.copy(expanded = b.expanded) == b) return PAYLOAD_EXPAND
                return null
            }
        }
    }

    private val expandedPaths = mutableSetOf<String>()
    private var rootNode: DirectoryNode? = null

    /** Called when user taps a directory node — navigate the pane to that path. */
    var onDirectorySelected: ((String) -> Unit)? = null

    fun setRootNode(root: DirectoryNode) {
        rootNode = root
        expandedPaths.clear()
        // Auto-expand root
        expandedPaths.add(root.path)
        submitList(buildFlatList(root, 0))
    }

    private fun buildFlatList(node: DirectoryNode, depth: Int): List<FlatNode> {
        val result = mutableListOf<FlatNode>()
        val isExpanded = node.path in expandedPaths
        result.add(FlatNode(node, depth, isExpanded))
        if (isExpanded) {
            for (child in node.children.sortedBy { it.name.lowercase() }) {
                result.addAll(buildFlatList(child, depth + 1))
            }
        }
        return result
    }

    private fun refreshList() {
        val root = rootNode ?: return
        submitList(buildFlatList(root, 0))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dual_pane_tree_node, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_EXPAND)) {
            val item = getItem(position)
            // Partial rebind: only update expand/collapse arrow rotation
            if (item.node.children.isNotEmpty()) {
                holder.expandIcon.rotation = if (item.expanded) 90f else 0f
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
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
                toggleExpand(holder.bindingAdapterPosition)
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
                toggleExpand(holder.bindingAdapterPosition)
            }
            true
        }
    }

    private fun toggleExpand(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val item = getItem(position)
        if (item.expanded) {
            expandedPaths.remove(item.node.path)
        } else {
            expandedPaths.add(item.node.path)
        }
        refreshList()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expandIcon: ImageView = view.findViewById(R.id.iv_expand)
        val folderIcon: ImageView = view.findViewById(R.id.iv_folder_icon)
        val name: TextView = view.findViewById(R.id.tv_node_name)
        val info: TextView = view.findViewById(R.id.tv_node_info)
    }
}
