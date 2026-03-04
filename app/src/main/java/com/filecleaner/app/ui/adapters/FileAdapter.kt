package com.filecleaner.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem

class FileAdapter(
    private val selectable: Boolean = true,
    private val onSelectionChanged: (List<FileItem>) -> Unit = {}
) : ListAdapter<FileItem, FileAdapter.FileViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(a: FileItem, b: FileItem) = a.path == b.path
            override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
        }
        private const val TYPE_COMPACT = 2
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
        private const val PAYLOAD_SELECTION = "selection"
    }

    // Selection tracked separately from FileItem (F-001)
    private val selectedPaths = mutableSetOf<String>()

    private val DUPLICATE_GROUP_COLOR_RES = listOf(
        R.color.dupGroup0, R.color.dupGroup1, R.color.dupGroup2,
        R.color.dupGroup3, R.color.dupGroup4, R.color.dupGroup5
    )

    // I3: Use shared base color resolution; duplicate group colors resolved locally
    private var colors: FileItemUtils.AdapterColors? = null
    private var resolvedDupColors: IntArray? = null

    private fun resolveColors(ctx: android.content.Context) {
        if (colors == null) {
            colors = FileItemUtils.resolveColorsWithSelection(ctx)
        }
        if (resolvedDupColors == null) {
            resolvedDupColors = IntArray(DUPLICATE_GROUP_COLOR_RES.size) { ContextCompat.getColor(ctx, DUPLICATE_GROUP_COLOR_RES[it]) }
        }
    }

    var viewMode: ViewMode = ViewMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    /** Color-coding mode — set by the hosting fragment to match its screen context. */
    var colorMode: ColorMode = ColorMode.NONE
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileItem) -> Unit)? = null
    var onItemLongClick: ((FileItem, View) -> Unit)? = null

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
        val name: TextView = view.findViewById(R.id.tv_file_name)
        val meta: TextView? = view.findViewById(R.id.tv_file_meta)
        val check: CheckBox? = view.findViewById(R.id.cb_select)
        val accentStripe: View? = view.findViewById(R.id.view_accent_stripe)
    }

    override fun getItemViewType(position: Int): Int = when (viewMode) {
        ViewMode.LIST_COMPACT -> TYPE_COMPACT
        ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> TYPE_LIST
        ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layoutRes = when (viewType) {
            TYPE_COMPACT -> R.layout.item_file_compact
            TYPE_GRID -> R.layout.item_file_grid
            else -> R.layout.item_file
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val item = getItem(position)
            val isSelected = item.path in selectedPaths
            val ctx = holder.itemView.context
            resolveColors(ctx)
            val c = colors!!
            val card = holder.itemView as? MaterialCardView

            // Partial rebind: only update selection visual state (skip icon, text, thumbnail)
            if (item.duplicateGroup < 0) {
                if (isSelected) {
                    card?.setCardBackgroundColor(c.selectedBg) ?: holder.itemView.setBackgroundColor(c.selectedBg)
                    card?.strokeColor = c.selectedBorder
                } else {
                    card?.setCardBackgroundColor(c.surface) ?: run { holder.itemView.background = null }
                    card?.strokeColor = c.border
                }
            }
            if (selectable && holder.check != null) {
                holder.check.isChecked = isSelected
                holder.check.contentDescription = ctx.getString(
                    if (isSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, item.name)
            }
            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_not_selected,
                item.name, holder.meta?.text ?: "")
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = item.path in selectedPaths
        val ctx = holder.itemView.context
        resolveColors(ctx)

        holder.name.text = item.name

        // Reset icon size for recycled views; enlarge only for thumbnail mode
        if (viewMode == ViewMode.LIST_WITH_THUMBNAILS) {
            val thumbSize = ctx.resources.getDimensionPixelSize(R.dimen.icon_file_list_large)
            val lp = holder.icon.layoutParams
            lp.width = thumbSize
            lp.height = thumbSize
            holder.icon.layoutParams = lp
        } else {
            val defaultSize = ctx.resources.getDimensionPixelSize(R.dimen.icon_file_list_default)
            val lp = holder.icon.layoutParams
            lp.width = defaultSize
            lp.height = defaultSize
            holder.icon.layoutParams = lp
        }

        // Load thumbnail for images/videos, category icon for everything else
        val isGrid = viewMode != ViewMode.LIST && viewMode != ViewMode.LIST_WITH_THUMBNAILS && viewMode != ViewMode.LIST_COMPACT
        FileItemUtils.loadThumbnail(holder.icon, item, isGrid)

        // Accent stripe (color-coded indicator)
        bindAccentStripe(holder, item)

        // Visual state: duplicate group colouring → junk/size bg → selection highlight → default
        val c = colors!!
        val card = holder.itemView as? MaterialCardView
        val dupColors = resolvedDupColors
        if (item.duplicateGroup >= 0 && dupColors != null) {
            val color = dupColors[item.duplicateGroup % dupColors.size]
            card?.setCardBackgroundColor(color) ?: holder.itemView.setBackgroundColor(color)
            card?.strokeColor = c.border
        } else if (isSelected) {
            card?.setCardBackgroundColor(c.selectedBg) ?: holder.itemView.setBackgroundColor(c.selectedBg)
            card?.strokeColor = c.selectedBorder
        } else if (colorMode == ColorMode.JUNK_CATEGORY) {
            val bgColor = FileItemUtils.junkBgColor(ctx, item)
            card?.setCardBackgroundColor(bgColor) ?: holder.itemView.setBackgroundColor(bgColor)
            card?.strokeColor = c.border
        } else if (colorMode == ColorMode.SIZE_SEVERITY) {
            val bgColor = FileItemUtils.sizeBgColor(ctx, item)
            card?.setCardBackgroundColor(bgColor) ?: holder.itemView.setBackgroundColor(bgColor)
            card?.strokeColor = c.border
        } else {
            card?.setCardBackgroundColor(c.surface) ?: run { holder.itemView.background = null }
            card?.strokeColor = c.border
        }

        // Meta line (only in list layouts that have it)
        holder.meta?.let { FileItemUtils.buildMeta(it, item) }

        // Checkbox + accessibility (F-033)
        if (selectable && holder.check != null) {
            holder.check.visibility = View.VISIBLE
            holder.check.isChecked = isSelected
            holder.check.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, item.name)
            // Use bindingAdapterPosition to avoid stale item capture from ViewHolder recycling
            val toggle = {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(pos)
                    toggleSelection(currentItem.path)
                    val nowSelected = currentItem.path in selectedPaths
                    holder.check.isChecked = nowSelected
                    holder.check.contentDescription = ctx.getString(
                        if (nowSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, currentItem.name)
                    // Immediate card visual feedback for selection (§DP3)
                    if (currentItem.duplicateGroup < 0) {
                        if (nowSelected) {
                            card?.setCardBackgroundColor(c.selectedBg)
                            card?.strokeColor = c.selectedBorder
                        } else {
                            card?.setCardBackgroundColor(c.surface)
                            card?.strokeColor = c.border
                        }
                    }
                    notifySelectionChanged()
                }
            }
            // Only wire click on itemView, not on checkbox separately (avoids double-toggle)
            holder.check.isClickable = false
            holder.itemView.setOnClickListener { toggle() }
            holder.itemView.setOnLongClickListener { v ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemLongClick?.invoke(getItem(pos), v)
                }
                true
            }
            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_not_selected,
                item.name, holder.meta?.text ?: "")
        } else if (selectable) {
            // G2-3: Grid mode with selection — no checkbox, use tap to toggle + stateDescription
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val currentItem = getItem(pos)
                    toggleSelection(currentItem.path)
                    notifyItemChanged(pos, PAYLOAD_SELECTION)
                    notifySelectionChanged()
                }
            }
            holder.itemView.setOnLongClickListener { v ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemLongClick?.invoke(getItem(pos), v)
                }
                true
            }
            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_not_selected,
                item.name, holder.meta?.text ?: item.sizeReadable)
        } else {
            holder.check?.visibility = View.GONE
            // Wire click and long-click for non-selectable mode (use bindingAdapterPosition)
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(getItem(pos))
                }
            }
            holder.itemView.setOnLongClickListener { v ->
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemLongClick?.invoke(getItem(pos), v)
                }
                true
            }
            holder.itemView.contentDescription = ctx.getString(
                R.string.a11y_file_info, item.name, holder.meta?.text ?: item.sizeReadable)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<FileItem>, currentList: MutableList<FileItem>) {
        super.onCurrentListChanged(previousList, currentList)
        // Prune stale selections that no longer exist in the new list
        val validPaths = currentList.mapTo(HashSet(currentList.size)) { it.path }
        if (selectedPaths.retainAll(validPaths)) {
            notifySelectionChanged()
        }
    }

    private fun toggleSelection(path: String) {
        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
    }

    private fun notifySelectionChanged() {
        onSelectionChanged(currentList.filter { it.path in selectedPaths })
    }

    // D1: Use notifyItemRangeChanged for selection changes instead of
    // notifyDataSetChanged — preserves RecyclerView item animations and avoids
    // full layout recalculation.  notifyDataSetChanged is only justified when
    // the view type changes (e.g. list↔grid switch in viewMode setter).

    fun selectAll() {
        selectedPaths.addAll(currentList.map { it.path })
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    /** Select all-but-one from each duplicate group, keeping the newest copy. */
    fun selectAllDuplicatesExceptBest() {
        selectedPaths.clear()
        val groups = currentList.filter { it.duplicateGroup >= 0 }.groupBy { it.duplicateGroup }
        for ((_, group) in groups) {
            // Keep the newest file (highest lastModified); select the rest for deletion
            val best = group.maxByOrNull { it.lastModified }
            val bestPath = best?.path
            group.forEach { if (it.path != bestPath) selectedPaths.add(it.path) }
        }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    fun deselectAll() {
        selectedPaths.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        onSelectionChanged(emptyList())
    }

    fun getSelectedItems(): List<FileItem> = currentList.filter { it.path in selectedPaths }

    /** Returns current selection for persistence across config changes. */
    fun getSelectedPaths(): Set<String> = selectedPaths.toSet()

    /** Restores selection state (e.g. after config change). */
    fun restoreSelection(paths: Set<String>) {
        selectedPaths.clear()
        selectedPaths.addAll(paths)
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        notifySelectionChanged()
    }

    /**
     * Binds the 4dp accent stripe on the left (list) or top (grid) edge of
     * the card, using the current [colorMode] to determine the color.
     */
    private fun bindAccentStripe(holder: FileViewHolder, item: FileItem) {
        val stripe = holder.accentStripe ?: return
        val ctx = holder.itemView.context
        val accentColor = FileItemUtils.accentColor(ctx, item, colorMode)
        if (accentColor != null) {
            stripe.setBackgroundColor(accentColor)
            stripe.visibility = View.VISIBLE
        } else {
            stripe.visibility = View.GONE
        }
    }

}
