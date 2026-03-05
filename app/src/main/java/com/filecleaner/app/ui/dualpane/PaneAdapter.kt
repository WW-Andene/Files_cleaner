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
        val selected: Boolean = false
    )

    companion object {
        private const val PAYLOAD_SELECTION = "selection"

        private val DIFF = object : DiffUtil.ItemCallback<PaneItem>() {
            override fun areItemsTheSame(a: PaneItem, b: PaneItem) =
                a.file.absolutePath == b.file.absolutePath

            override fun areContentsTheSame(a: PaneItem, b: PaneItem) =
                a == b

            override fun getChangePayload(a: PaneItem, b: PaneItem): Any? {
                if (a.copy(selected = b.selected) == b) return PAYLOAD_SELECTION
                return null
            }
        }
    }

    var onItemClick: ((PaneItem) -> Unit)? = null
    var onItemLongClick: ((PaneItem, View) -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null
    /** Called when a drag should be initiated for the given item (long-press when not in selection mode). */
    var onDragStartRequested: ((PaneItem, View) -> Unit)? = null

    /** Whether selection mode is active (at least one item is selected). */
    val isSelectionActive: Boolean get() = currentList.any { it.selected }

    fun getSelectedItems(): List<PaneItem> = currentList.filter { it.selected }

    fun clearSelection() {
        submitList(currentList.map { it.copy(selected = false) })
        onSelectionChanged?.invoke()
    }

    fun selectAll() {
        submitList(currentList.map { it.copy(selected = true) })
        onSelectionChanged?.invoke()
    }

    private fun toggleSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val updated = currentList.toMutableList()
        val item = updated[position]
        updated[position] = item.copy(selected = !item.selected)
        submitList(updated)
        onSelectionChanged?.invoke()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dual_pane_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val item = getItem(position)
            val ctx = holder.itemView.context
            // Partial rebind: only update selection visual state
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
            return
        }
        super.onBindViewHolder(holder, position, payloads)
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
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionActive) {
                // In selection mode, tap toggles selection
                toggleSelection(pos)
            } else {
                onItemClick?.invoke(getItem(pos))
            }
        }

        holder.itemView.setOnLongClickListener { v ->
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val current = getItem(pos)
            if (!isSelectionActive) {
                onDragStartRequested?.invoke(current, v)
            } else {
                onItemLongClick?.invoke(current, v)
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
