package com.filecleaner.app.ui.cloud

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudFile
import com.filecleaner.app.utils.UndoHelper
import java.text.DateFormat
import java.util.Date

/**
 * Adapter for displaying cloud files in the CloudBrowserFragment.
 */
class CloudFileAdapter : ListAdapter<CloudFileAdapter.CloudFileItem, CloudFileAdapter.ViewHolder>(DIFF) {

    data class CloudFileItem(
        val cloudFile: CloudFile,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        var selected: Boolean = false
    )

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CloudFileItem>() {
            override fun areItemsTheSame(a: CloudFileItem, b: CloudFileItem) =
                a.cloudFile.remotePath == b.cloudFile.remotePath
            override fun areContentsTheSame(a: CloudFileItem, b: CloudFileItem) =
                a == b
        }
    }

    var onItemClick: ((CloudFile) -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null

    fun getSelectedItems(): List<CloudFileItem> = currentList.filter { it.selected }

    fun clearSelection() {
        currentList.forEach { it.selected = false }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged?.invoke()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dual_pane_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.name.text = item.name

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            holder.meta.text = holder.itemView.context.getString(R.string.cloud_directory)
        } else {
            holder.icon.setImageResource(iconForMime(item.cloudFile.mimeType, item.name))
            val sizeStr = UndoHelper.formatBytes(item.size)
            if (item.lastModified > 0) {
                val dateStr = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.lastModified))
                holder.meta.text = "$sizeStr \u2022 $dateStr"
            } else {
                holder.meta.text = sizeStr
            }
        }

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = item.selected
        holder.checkbox.setOnCheckedChangeListener { _, checked ->
            item.selected = checked
            onSelectionChanged?.invoke()
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item.cloudFile)
        }
    }

    private fun iconForMime(mimeType: String, name: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.ic_image
            mimeType.startsWith("video/") -> R.drawable.ic_video
            mimeType.startsWith("audio/") -> R.drawable.ic_audio
            mimeType.startsWith("text/") || mimeType.contains("pdf") -> R.drawable.ic_document
            mimeType.contains("zip") || mimeType.contains("archive") || mimeType.contains("compressed") -> R.drawable.ic_archive
            name.endsWith(".apk") -> R.drawable.ic_apk
            else -> R.drawable.ic_file
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.cb_select)
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_name)
        val meta: TextView = view.findViewById(R.id.tv_meta)
    }
}
