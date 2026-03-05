package com.filecleaner.app.ui.adapters

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.UndoHelper

class BrowseAdapter : ListAdapter<BrowseAdapter.Item, RecyclerView.ViewHolder>(DIFF) {

    sealed class Item {
        data class Header(val folderPath: String, val displayName: String, val fileCount: Int, val totalSize: Long = 0L) : Item()
        data class File(val fileItem: FileItem) : Item()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
        private const val TYPE_FILE_GRID = 11
        private const val PAYLOAD_SELECTION = "selection"

        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item): Boolean = when {
                a is Item.Header && b is Item.Header -> a.folderPath == b.folderPath
                a is Item.File && b is Item.File -> a.fileItem.path == b.fileItem.path
                else -> false
            }
            override fun areContentsTheSame(a: Item, b: Item): Boolean = a == b
        }
    }

    var viewMode: ViewMode = ViewMode.LIST_MD
        set(value) {
            if (field != value) {
                field = value
                // Must use notifyDataSetChanged when view types change (list <-> grid)
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileItem) -> Unit)? = null
    var onItemLongClick: ((FileItem, View) -> Unit)? = null
    var onHeaderClick: ((String) -> Unit)? = null
    var onSelectionChanged: ((List<FileItem>) -> Unit)? = null

    // I3: Use shared color resolution from FileItemUtils
    private var colors: FileItemUtils.AdapterColors? = null

    // ── Selection state ─────────────────────────────────────────────────
    val selectedPaths = mutableSetOf<String>()
    var selectionMode = false
        private set

    fun enterSelectionMode(initialPath: String? = null) {
        if (!selectionMode) {
            selectionMode = true
            selectedPaths.clear()
        }
        if (initialPath != null) {
            selectedPaths.add(initialPath)
        }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedPaths.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged?.invoke(emptyList())
    }

    fun toggleSelection(path: String) {
        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
        val position = currentList.indexOfFirst { it is Item.File && it.fileItem.path == path }
        if (position >= 0) {
            notifyItemChanged(position, PAYLOAD_SELECTION)
        }
        notifySelectionChanged()
    }

    /** Select item at a given adapter position (for drag-to-select). */
    fun selectAtPosition(position: Int) {
        val item = currentList.getOrNull(position) as? Item.File ?: return
        val path = item.fileItem.path
        if (path !in selectedPaths) {
            selectedPaths.add(path)
            notifyItemChanged(position)
            notifySelectionChanged()
        }
    }

    private fun notifySelectionChanged() {
        val selected = currentList.filterIsInstance<Item.File>().filter { it.fileItem.path in selectedPaths }.map { it.fileItem }
        onSelectionChanged?.invoke(selected)
        if (selected.isEmpty() && selectionMode) exitSelectionMode()
    }

    fun selectAll() {
        enterSelectionMode()
        currentList.filterIsInstance<Item.File>().forEach { selectedPaths.add(it.fileItem.path) }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    fun selectAllFiles() {
        enterSelectionMode()
        currentList.filterIsInstance<Item.File>()
            .filter { !java.io.File(it.fileItem.path).isDirectory }
            .forEach { selectedPaths.add(it.fileItem.path) }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    fun selectAllFolders() {
        enterSelectionMode()
        currentList.filterIsInstance<Item.File>()
            .filter { java.io.File(it.fileItem.path).isDirectory }
            .forEach { selectedPaths.add(it.fileItem.path) }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    fun deselectAll() {
        exitSelectionMode()
    }

    fun getSelectedItems(): List<FileItem> =
        currentList.filterIsInstance<Item.File>().filter { it.fileItem.path in selectedPaths }.map { it.fileItem }

    fun getSelectedCount(): Int = selectedPaths.size

    /** Set of folder paths that are currently collapsed. */
    val collapsedFolders: MutableSet<String> = mutableSetOf()

    /** The full unfiltered list of items, before collapse filtering. */
    private var fullList: List<Item> = emptyList()

    fun getFileCount(): Int = currentList.count { it is Item.File }

    /**
     * Accepts the full list of items (headers + files) and submits only
     * the visible items (hiding files under collapsed folders) to the
     * RecyclerView differ.
     */
    fun submitFullList(items: List<Item>, commitCallback: Runnable? = null) {
        fullList = items
        val visible = computeVisibleList()
        if (commitCallback != null) {
            submitList(visible, commitCallback)
        } else {
            submitList(visible)
        }
    }

    /** Toggle a folder between collapsed and expanded. */
    fun toggleFolder(folderPath: String) {
        if (folderPath in collapsedFolders) {
            collapsedFolders.remove(folderPath)
        } else {
            collapsedFolders.add(folderPath)
        }
        submitList(computeVisibleList())
    }

    /** Expand all folders. */
    fun expandAll() {
        collapsedFolders.clear()
        submitList(computeVisibleList())
    }

    /** Collapse all folders. */
    fun collapseAll() {
        collapsedFolders.clear()
        for (item in fullList) {
            if (item is Item.Header) {
                collapsedFolders.add(item.folderPath)
            }
        }
        submitList(computeVisibleList())
    }

    /** Returns true if any folder is currently expanded. */
    fun hasExpandedFolders(): Boolean {
        val allFolders = fullList.filterIsInstance<Item.Header>().map { it.folderPath }
        return allFolders.any { it !in collapsedFolders }
    }

    /** Filters the full list, keeping headers but removing files under collapsed folders. */
    private fun computeVisibleList(): List<Item> {
        val result = mutableListOf<Item>()
        var currentFolderCollapsed = false
        for (item in fullList) {
            when (item) {
                is Item.Header -> {
                    currentFolderCollapsed = item.folderPath in collapsedFolders
                    result.add(item)
                }
                is Item.File -> {
                    if (!currentFolderCollapsed) {
                        result.add(item)
                    }
                }
            }
        }
        return result
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Item.Header -> TYPE_HEADER
        is Item.File -> when {
            viewMode.usesGridLayout -> TYPE_FILE_GRID
            else -> TYPE_FILE
        }
    }

    fun isHeader(position: Int): Boolean = position in currentList.indices && getItem(position) is Item.Header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_folder_header, parent, false))
            else -> {
                val layoutRes = when (viewType) {
                    TYPE_FILE_GRID -> R.layout.item_file_grid
                    else -> R.layout.item_file
                }
                FileViewHolder(inflater.inflate(layoutRes, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION) && holder is FileViewHolder) {
            val item = getItem(position) as? Item.File ?: return
            val fileItem = item.fileItem
            val isSelected = fileItem.path in selectedPaths
            val ctx = holder.itemView.context
            val c = colors ?: FileItemUtils.resolveColorsWithSelection(ctx).also { colors = it }

            // Partial rebind: only update selection visual state (skip icon, text, thumbnail)
            val card = holder.itemView as? com.google.android.material.card.MaterialCardView
            if (isSelected) {
                card?.setCardBackgroundColor(c.selectedBg)
                card?.strokeColor = c.selectedBorder
            } else {
                card?.setCardBackgroundColor(c.surface)
                card?.strokeColor = c.border
            }

            val cb = holder.check as? CheckBox
            if (selectionMode && cb != null) {
                cb.visibility = View.VISIBLE
                cb.isChecked = isSelected
                cb.isClickable = false
                cb.contentDescription = ctx.getString(
                    if (isSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, fileItem.name)
            } else {
                cb?.visibility = View.GONE
            }

            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_info,
                fileItem.name, holder.meta?.text ?: fileItem.sizeReadable)
            // §G1: Set stateDescription so TalkBack announces selection state
            ViewCompat.setStateDescription(holder.itemView,
                ctx.getString(if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_info,
                    fileItem.name, holder.meta?.text ?: fileItem.sizeReadable))
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Header -> bindHeader(holder as HeaderViewHolder, item)
            is Item.File -> bindFile(holder as FileViewHolder, item.fileItem)
        }
    }

    private fun bindHeader(holder: HeaderViewHolder, header: Item.Header) {
        holder.folderName.text = header.displayName
        holder.folderSize.text = UndoHelper.formatBytes(header.totalSize)
        holder.folderCount.text = holder.itemView.context.resources.getQuantityString(R.plurals.n_files, header.fileCount, header.fileCount)

        val isCollapsed = header.folderPath in collapsedFolders
        holder.chevron.setImageResource(
            if (isCollapsed) R.drawable.ic_arrow_down else R.drawable.ic_chevron_up
        )
        // Rotate chevron: 0 degrees when expanded (arrow pointing down), 180 when collapsed
        holder.chevron.rotation = 0f

        val ctx = holder.itemView.context
        // §G1: Rich folder header description with file count and size
        val folderDesc = ctx.getString(R.string.a11y_folder_header,
            header.displayName, header.fileCount, UndoHelper.formatBytes(header.totalSize))
        holder.itemView.contentDescription = if (isCollapsed) {
            "$folderDesc, ${ctx.getString(R.string.a11y_expand_folder, header.displayName)}"
        } else {
            "$folderDesc, ${ctx.getString(R.string.a11y_collapse_folder, header.displayName)}"
        }
        // §G1: State description for expand/collapse state
        ViewCompat.setStateDescription(holder.itemView,
            if (isCollapsed) ctx.getString(R.string.collapse_all) else ctx.getString(R.string.expand_all))

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val h = getItem(pos) as? Item.Header ?: return@setOnClickListener
                onHeaderClick?.invoke(h.folderPath)
            }
        }
    }

    private fun bindFile(holder: FileViewHolder, item: FileItem) {
        holder.name.text = item.name
        val ctx = holder.itemView.context
        val c = colors ?: FileItemUtils.resolveColorsWithSelection(ctx).also { colors = it }
        val isSelected = item.path in selectedPaths

        // Resize icon/container based on mode + size
        if (!viewMode.usesGridLayout) {
            val sizePx = (viewMode.iconSizeDp * ctx.resources.displayMetrics.density).toInt()
            val container = holder.icon.parent as? android.view.View
            if (container != null) {
                val clp = container.layoutParams
                clp.width = sizePx
                clp.height = sizePx
                container.layoutParams = clp
            }
        }

        // Load thumbnail or icon
        FileItemUtils.loadThumbnail(holder.icon, item, viewMode.showsRichThumbnails)

        // Card colors: selection highlight or default
        val card = holder.itemView as? com.google.android.material.card.MaterialCardView
        if (isSelected) {
            card?.setCardBackgroundColor(c.selectedBg)
            card?.strokeColor = c.selectedBorder
        } else {
            card?.setCardBackgroundColor(c.surface)
            card?.strokeColor = c.border
        }

        // Meta line
        holder.meta?.let { FileItemUtils.buildMeta(it, item) }

        // Checkbox visibility based on selection mode
        val cb = holder.check as? CheckBox
        if (selectionMode && cb != null) {
            cb.visibility = View.VISIBLE
            cb.isChecked = isSelected
            cb.isClickable = false // click handled by itemView
            cb.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, item.name)
        } else {
            cb?.visibility = View.GONE
        }

        // Click handlers
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val current = getItem(pos)
                if (current is Item.File) {
                    if (selectionMode) {
                        toggleSelection(current.fileItem.path)
                    } else {
                        onItemClick?.invoke(current.fileItem)
                    }
                }
            }
        }
        holder.itemView.setOnLongClickListener { v ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val current = getItem(pos)
                if (current is Item.File) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (!selectionMode) {
                        enterSelectionMode(current.fileItem.path)
                    } else {
                        onItemLongClick?.invoke(current.fileItem, v)
                    }
                }
            }
            true
        }

        holder.itemView.contentDescription = ctx.getString(
            if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_info,
            item.name, holder.meta?.text ?: item.sizeReadable)
        // §G1: Set stateDescription so TalkBack announces selection state
        ViewCompat.setStateDescription(holder.itemView,
            ctx.getString(if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_info,
                item.name, holder.meta?.text ?: item.sizeReadable))
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.tv_folder_name)
        val folderSize: TextView = view.findViewById(R.id.tv_folder_size)
        val folderCount: TextView = view.findViewById(R.id.tv_folder_count)
        val chevron: ImageView = view.findViewById(R.id.iv_chevron)
    }

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
        val name: TextView = view.findViewById(R.id.tv_file_name)
        val meta: TextView? = view.findViewById(R.id.tv_file_meta)
        val check: View? = view.findViewById(R.id.cb_select)
    }
}
