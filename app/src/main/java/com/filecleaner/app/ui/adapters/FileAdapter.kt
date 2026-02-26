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

    // Group header support
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

        holder.name.text = item.name
        holder.meta.text = buildMeta(item)

        // Thumbnail for images/videos; icon otherwise
        if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
            Glide.with(holder.icon.context)
                .load(item.file)
                .placeholder(categoryDrawable(item.category))
                .centerCrop()
                .into(holder.icon)
        } else {
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

        // Checkbox
        if (selectable) {
            holder.check.visibility = View.VISIBLE
            holder.check.isChecked = item.isSelected
            holder.check.setOnClickListener {
                item.isSelected = holder.check.isChecked
                onSelectionChanged(currentList.filter { it.isSelected })
            }
            holder.itemView.setOnClickListener {
                item.isSelected = !item.isSelected
                holder.check.isChecked = item.isSelected
                onSelectionChanged(currentList.filter { it.isSelected })
            }
        } else {
            holder.check.visibility = View.GONE
        }
    }

    fun selectAll() {
        currentList.forEach { it.isSelected = true }
        notifyDataSetChanged()
        onSelectionChanged(currentList)
    }

    fun deselectAll() {
        currentList.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(emptyList())
    }

    private fun buildMeta(item: FileItem): String {
        val date = android.text.format.DateFormat.format("dd MMM yyyy", item.lastModified)
        return "${item.sizeReadable}  •  $date"
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
