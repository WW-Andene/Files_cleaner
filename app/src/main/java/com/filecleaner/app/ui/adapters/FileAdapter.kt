package com.filecleaner.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    }

    // Selection tracked separately from FileItem (F-001)
    private val selectedPaths = mutableSetOf<String>()

    private val DUPLICATE_HEADER_COLORS = listOf(
        0xFFE3F2FD.toInt(), 0xFFF3E5F5.toInt(), 0xFFE8F5E9.toInt(),
        0xFFFFF3E0.toInt(), 0xFFFFEBEE.toInt(), 0xFFF1F8E9.toInt()
    )

    inner class FileVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
        val name: TextView  = view.findViewById(R.id.tv_file_name)
        val meta: TextView  = view.findViewById(R.id.tv_file_meta)
        val check: CheckBox = view.findViewById(R.id.cb_select)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileVH(view)
    }

    override fun onBindViewHolder(holder: FileVH, position: Int) {
        val item = getItem(position)
        val isSelected = item.path in selectedPaths

        holder.name.text = item.name

        // Thumbnail for images/videos; icon otherwise
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

        // Duplicate group colouring
        if (item.duplicateGroup >= 0) {
            val color = DUPLICATE_HEADER_COLORS[item.duplicateGroup % DUPLICATE_HEADER_COLORS.size]
            holder.itemView.setBackgroundColor(color)
        } else {
            holder.itemView.setBackgroundColor(0x00000000)
        }

        // Checkbox + accessibility (F-033)
        val ctx = holder.itemView.context
        val meta = buildMeta(holder, item)
        if (selectable) {
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
                notifySelectionChanged()
            }
            holder.check.setOnClickListener { toggle() }
            holder.itemView.setOnClickListener { toggle() }
            holder.itemView.contentDescription = ctx.getString(
                if (isSelected) R.string.a11y_file_selected else R.string.a11y_file_not_selected, item.name, meta)
        } else {
            holder.check.visibility = View.GONE
            holder.itemView.contentDescription = ctx.getString(R.string.a11y_file_info, item.name, meta)
        }
    }

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

    private fun buildMeta(holder: FileVH, item: FileItem): String {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            holder.itemView.resources.configuration.locales[0], "dd MMM yyyy")
        val date = android.text.format.DateFormat.format(pattern, item.lastModified)
        holder.meta.text = "${item.sizeReadable}  \u2022  $date"
        return "${item.sizeReadable}  \u2022  $date"
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
