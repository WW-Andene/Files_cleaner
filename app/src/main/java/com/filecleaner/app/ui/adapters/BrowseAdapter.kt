package com.filecleaner.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem

class BrowseAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class Header(val folderPath: String, val displayName: String, val fileCount: Int) : Item()
        data class File(val fileItem: FileItem) : Item()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
    }

    private var items: List<Item> = emptyList()
    var viewMode: ViewMode = ViewMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileItem) -> Unit)? = null
    var onItemLongClick: ((FileItem, View) -> Unit)? = null

    fun submitList(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getFileCount(): Int = items.count { it is Item.File }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Header -> TYPE_HEADER
        is Item.File -> when (viewMode) {
            ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> TYPE_FILE
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> TYPE_FILE + 10 // grid type
        }
    }

    fun isHeader(position: Int): Boolean = position in items.indices && items[position] is Item.Header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_folder_header, parent, false))
            else -> {
                val layoutRes = if (viewType == TYPE_FILE) R.layout.item_file else R.layout.item_file_grid
                FileVH(inflater.inflate(layoutRes, parent, false))
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Header -> bindHeader(holder as HeaderVH, item)
            is Item.File -> bindFile(holder as FileVH, item.fileItem)
        }
    }

    private fun bindHeader(holder: HeaderVH, header: Item.Header) {
        holder.folderName.text = header.displayName
        holder.folderCount.text = holder.itemView.context.getString(R.string.n_files, header.fileCount)
    }

    private fun bindFile(holder: FileVH, item: FileItem) {
        holder.name.text = item.name

        if (viewMode == ViewMode.LIST_WITH_THUMBNAILS) {
            val lp = holder.icon.layoutParams
            lp.width = (72 * holder.itemView.resources.displayMetrics.density).toInt()
            lp.height = (72 * holder.itemView.resources.displayMetrics.density).toInt()
            holder.icon.layoutParams = lp
        }

        // Load thumbnail or icon
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

        // Default card colors
        val card = holder.itemView as? com.google.android.material.card.MaterialCardView
        val defaultColor = ContextCompat.getColor(holder.itemView.context, R.color.surfaceColor)
        card?.setCardBackgroundColor(defaultColor)
        card?.strokeColor = ContextCompat.getColor(holder.itemView.context, R.color.borderDefault)

        // Meta line
        holder.meta?.let { buildMeta(it, item) }

        // Hide checkbox
        holder.check?.visibility = View.GONE

        // Click handlers
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
        holder.itemView.setOnLongClickListener { v ->
            onItemLongClick?.invoke(item, v)
            true
        }

        val ctx = holder.itemView.context
        holder.itemView.contentDescription = ctx.getString(
            R.string.a11y_file_info, item.name, holder.meta?.text ?: item.sizeReadable)
    }

    private fun buildMeta(metaView: TextView, item: FileItem) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            metaView.resources.configuration.locales[0], "dd MMM yyyy")
        val date = android.text.format.DateFormat.format(pattern, item.lastModified)
        metaView.text = "${item.sizeReadable}  \u2022  $date"
    }

    private fun categoryDrawable(cat: FileCategory) = when (cat) {
        FileCategory.IMAGE -> R.drawable.ic_image
        FileCategory.VIDEO -> R.drawable.ic_video
        FileCategory.AUDIO -> R.drawable.ic_audio
        FileCategory.DOCUMENT -> R.drawable.ic_document
        FileCategory.APK -> R.drawable.ic_apk
        FileCategory.ARCHIVE -> R.drawable.ic_archive
        FileCategory.DOWNLOAD -> R.drawable.ic_download
        else -> R.drawable.ic_file
    }

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.tv_folder_name)
        val folderCount: TextView = view.findViewById(R.id.tv_folder_count)
    }

    class FileVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_file_icon)
        val name: TextView = view.findViewById(R.id.tv_file_name)
        val meta: TextView? = view.findViewById(R.id.tv_file_meta)
        val check: View? = view.findViewById(R.id.cb_select)
    }
}
