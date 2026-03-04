package com.filecleaner.app.ui.dualpane

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.utils.UndoHelper
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Adapter for a single pane in the dual-pane file manager.
 * Displays files and directories with long-press multi-selection support.
 * Selection is indicated by a highlighted background instead of checkboxes.
 */
class PaneAdapter : ListAdapter<PaneAdapter.PaneItem, PaneAdapter.ViewHolder>(DIFF) {

    data class PaneItem(
        val file: File,
        val isDirectory: Boolean,
        val name: String,
        val size: Long,
        val lastModified: Long,
        var selected: Boolean = false
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PaneItem>() {
            override fun areItemsTheSame(a: PaneItem, b: PaneItem) =
                a.file.absolutePath == b.file.absolutePath

            override fun areContentsTheSame(a: PaneItem, b: PaneItem) =
                a == b
        }
    }

    var onItemClick: ((PaneItem) -> Unit)? = null
    var onItemLongClick: ((PaneItem, View) -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null

    /** Whether selection mode is active (at least one item is selected). */
    val isSelectionActive: Boolean get() = currentList.any { it.selected }

    fun getSelectedItems(): List<PaneItem> = currentList.filter { it.selected }

    fun clearSelection() {
        currentList.forEach { it.selected = false }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged?.invoke()
    }

    fun selectAll() {
        currentList.forEach { it.selected = true }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged?.invoke()
    }

    private fun toggleSelection(position: Int) {
        val item = getItem(position)
        item.selected = !item.selected
        notifyItemChanged(position)
        onSelectionChanged?.invoke()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dual_pane_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        holder.name.text = item.name

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            val childCount = item.file.listFiles()?.size ?: 0
            holder.meta.text = ctx.resources.getQuantityString(
                R.plurals.n_items, childCount, childCount
            )
            holder.chevron.visibility = View.VISIBLE
        } else {
            holder.icon.setImageResource(iconForFile(item.name))
            val dateStr = DateFormat.getDateInstance(DateFormat.SHORT)
                .format(Date(item.lastModified))
            holder.meta.text = "${UndoHelper.formatBytes(item.size)} \u2022 $dateStr"
            holder.chevron.visibility = View.GONE
        }

        // Selection highlight
        if (item.selected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_item_selected)
            holder.name.setTextColor(ContextCompat.getColor(ctx, R.color.colorPrimary))
        } else {
            holder.itemView.setBackgroundResource(
                android.R.attr.selectableItemBackground.let {
                    val ta = ctx.obtainStyledAttributes(intArrayOf(it))
                    val resId = ta.getResourceId(0, 0)
                    ta.recycle()
                    resId
                }
            )
            holder.name.setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary))
        }

        holder.itemView.setOnClickListener {
            if (isSelectionActive) {
                // In selection mode, tap toggles selection
                toggleSelection(holder.bindingAdapterPosition)
            } else {
                onItemClick?.invoke(item)
            }
        }

        holder.itemView.setOnLongClickListener { v ->
            if (!isSelectionActive) {
                // Enter selection mode with this item
                toggleSelection(holder.bindingAdapterPosition)
            } else {
                // Already in selection mode — show context menu for files
                onItemLongClick?.invoke(item, v)
            }
            true
        }
    }

    private fun iconForFile(name: String): Int {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg") -> R.drawable.ic_image
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") -> R.drawable.ic_video
            ext in setOf("mp3", "flac", "wav", "aac", "ogg", "m4a") -> R.drawable.ic_audio
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv") -> R.drawable.ic_document
            ext == "apk" -> R.drawable.ic_apk
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> R.drawable.ic_archive
            else -> R.drawable.ic_file
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        val meta: TextView = view.findViewById(R.id.tv_meta)
        val chevron: ImageView = view.findViewById(R.id.iv_chevron)
    }
}
