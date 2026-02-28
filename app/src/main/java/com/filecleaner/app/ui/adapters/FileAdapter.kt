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
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem

class FileAdapter(
    private val selectable: Boolean = true,
    private val onSelectionChanged: (List<FileItem>) -> Unit = {}
) : ListAdapter<FileItem, FileAdapter.FileVH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FileItem>() {
            override fun areItemsTheSame(a: FileItem, b: FileItem) = a.path == b.path
            override fun areContentsTheSame(a: FileItem, b: FileItem) = a == b
        }
        private const val TYPE_LIST = 0
        private const val TYPE_GRID = 1
    }

    // Selection tracked separately from FileItem (F-001)
    private val selectedPaths = mutableSetOf<String>()

    private val DUPLICATE_GROUP_COLOR_RES = listOf(
        R.color.dupGroup0, R.color.dupGroup1, R.color.dupGroup2,
        R.color.dupGroup3, R.color.dupGroup4, R.color.dupGroup5
    )

    var viewMode: ViewMode = ViewMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileItem) -> Unit)? = null
    var onItemLongClick: ((FileItem, View) -> Unit)? = null

    inner class FileVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
        val name: TextView = view.findViewById(R.id.tv_file_name)
        val meta: TextView? = view.findViewById(R.id.tv_file_meta)
        val check: CheckBox? = view.findViewById(R.id.cb_select)
    }

    override fun getItemViewType(position: Int): Int = when (viewMode) {
        ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> TYPE_LIST
        ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileVH {
        val layoutRes = if (viewType == TYPE_GRID) R.layout.item_file_grid else R.layout.item_file
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return FileVH(view)
    }

    override fun onBindViewHolder(holder: FileVH, position: Int) {
        val item = getItem(position)
        val isSelected = item.path in selectedPaths

        holder.name.text = item.name

        // Thumbnail strategy based on view mode
        when (viewMode) {
            ViewMode.LIST -> {
                // Standard list: thumbnails only for images/videos
                if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
                    Glide.with(holder.itemView)
                        .load(item.file)
                        .placeholder(categoryDrawable(item.category))
                        .centerCrop()
                        .into(holder.icon)
                } else {
                    Glide.with(holder.itemView).clear(holder.icon)
                    holder.icon.setImageResource(categoryDrawable(item.category))
                    holder.icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }
            ViewMode.LIST_WITH_THUMBNAILS -> {
                // List with larger thumbnails for all media types
                val lp = holder.icon.layoutParams
                lp.width = 72.dpToPx(holder.itemView)
                lp.height = 72.dpToPx(holder.itemView)
                holder.icon.layoutParams = lp

                if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
                    Glide.with(holder.itemView)
                        .load(item.file)
                        .placeholder(categoryDrawable(item.category))
                        .centerCrop()
                        .into(holder.icon)
                } else {
                    Glide.with(holder.itemView).clear(holder.icon)
                    holder.icon.setImageResource(categoryDrawable(item.category))
                    holder.icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> {
                // Grid: always try to load thumbnail
                if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
                    Glide.with(holder.itemView)
                        .load(item.file)
                        .placeholder(categoryDrawable(item.category))
                        .centerCrop()
                        .into(holder.icon)
                } else {
                    Glide.with(holder.itemView).clear(holder.icon)
                    holder.icon.setImageResource(categoryDrawable(item.category))
                    holder.icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }
        }

        // Visual state: duplicate group colouring → selection highlight → default
        val card = holder.itemView as? MaterialCardView
        if (item.duplicateGroup >= 0) {
            val colorRes = DUPLICATE_GROUP_COLOR_RES[item.duplicateGroup % DUPLICATE_GROUP_COLOR_RES.size]
            val color = ContextCompat.getColor(holder.itemView.context, colorRes)
            card?.setCardBackgroundColor(color) ?: holder.itemView.setBackgroundColor(color)
            card?.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.borderDefault)
        } else if (isSelected) {
            // §DP3: Selected state — primary character carrier, not just a checkbox
            val selBg = ContextCompat.getColor(holder.itemView.context, R.color.selectedBackground)
            val selBorder = ContextCompat.getColor(holder.itemView.context, R.color.selectedBorder)
            card?.setCardBackgroundColor(selBg) ?: holder.itemView.setBackgroundColor(selBg)
            card?.strokeColor = selBorder
        } else {
            val defaultColor = ContextCompat.getColor(holder.itemView.context, R.color.surfaceColor)
            card?.setCardBackgroundColor(defaultColor) ?: holder.itemView.setBackgroundColor(0x00000000)
            card?.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.borderDefault)
        }

        // Meta line (only in list layouts that have it)
        holder.meta?.let { buildMeta(it, item) }

        // Checkbox + accessibility (F-033)
        val ctx = holder.itemView.context
        if (selectable && holder.check != null) {
            holder.check.visibility = View.VISIBLE
            holder.check.isChecked = isSelected
            holder.check.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, item.name)
            val toggle = {
                toggleSelection(item.path)
                val nowSelected = item.path in selectedPaths
                holder.check.isChecked = nowSelected
                holder.check.contentDescription = ctx.getString(
                    if (nowSelected) R.string.a11y_deselect_file else R.string.a11y_select_file, item.name)
                // Immediate card visual feedback for selection (§DP3)
                if (item.duplicateGroup < 0) {
                    if (nowSelected) {
                        card?.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.selectedBackground))
                        card?.strokeColor = ContextCompat.getColor(ctx, R.color.selectedBorder)
                    } else {
                        card?.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surfaceColor))
                        card?.strokeColor = ContextCompat.getColor(ctx, R.color.borderDefault)
                    }
                }
                notifySelectionChanged()
            }
            holder.check.setOnClickListener { toggle() }
            holder.itemView.setOnClickListener { toggle() }
            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_not_selected,
                item.name, holder.meta?.text ?: "")
        } else {
            holder.check?.visibility = View.GONE
            // Wire click and long-click for non-selectable mode
            holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
            holder.itemView.setOnLongClickListener { v ->
                onItemLongClick?.invoke(item, v)
                true
            }
            holder.itemView.contentDescription = ctx.getString(
                R.string.a11y_file_info, item.name, holder.meta?.text ?: item.sizeReadable)
        }
    }

    private fun Int.dpToPx(view: View): Int =
        (this * view.resources.displayMetrics.density).toInt()

    private fun toggleSelection(path: String) {
        if (path in selectedPaths) selectedPaths.remove(path) else selectedPaths.add(path)
    }

    private fun notifySelectionChanged() {
        onSelectionChanged(currentList.filter { it.path in selectedPaths })
    }

    fun selectAll() {
        selectedPaths.addAll(currentList.map { it.path })
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    /** Select all-but-one from each duplicate group, keeping the newest copy. */
    fun selectAllDuplicatesExceptBest() {
        selectedPaths.clear()
        val groups = currentList.filter { it.duplicateGroup >= 0 }.groupBy { it.duplicateGroup }
        for ((_, group) in groups) {
            // Keep the newest file (highest lastModified); select the rest for deletion
            val sorted = group.sortedByDescending { it.lastModified }
            sorted.drop(1).forEach { selectedPaths.add(it.path) }
        }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun deselectAll() {
        selectedPaths.clear()
        notifyDataSetChanged()
        onSelectionChanged(emptyList())
    }

    fun getSelectedItems(): List<FileItem> = currentList.filter { it.path in selectedPaths }

    private fun buildMeta(metaView: TextView, item: FileItem): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            metaView.resources.configuration.locales[0], "dd MMM yyyy")
        val date = android.text.format.DateFormat.format(pattern, item.lastModified)
        val text = "${item.sizeReadable}  \u2022  $date"
        metaView.text = text
        return text
    }

    private fun categoryDrawable(cat: FileCategory) = when (cat) {
        FileCategory.IMAGE    -> R.drawable.ic_image
        FileCategory.VIDEO    -> R.drawable.ic_video
        FileCategory.AUDIO    -> R.drawable.ic_audio
        FileCategory.DOCUMENT -> R.drawable.ic_document
        FileCategory.APK      -> R.drawable.ic_apk
        FileCategory.ARCHIVE  -> R.drawable.ic_archive
        FileCategory.DOWNLOAD -> R.drawable.ic_download
        else                  -> R.drawable.ic_file
    }
}
