package com.filecleaner.app.ui.adapters

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
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.adapters.FileItemUtils.dpToPx

class BrowseAdapter : ListAdapter<BrowseAdapter.Item, RecyclerView.ViewHolder>(DIFF) {

    sealed class Item {
        data class Header(val folderPath: String, val displayName: String, val fileCount: Int) : Item()
        data class File(val fileItem: FileItem) : Item()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_FILE = 1
        private const val TYPE_FILE_GRID = 11

        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item): Boolean = when {
                a is Item.Header && b is Item.Header -> a.folderPath == b.folderPath
                a is Item.File && b is Item.File -> a.fileItem.path == b.fileItem.path
                else -> false
            }
            override fun areContentsTheSame(a: Item, b: Item): Boolean = a == b
        }
    }

    var viewMode: ViewMode = ViewMode.LIST
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var onItemClick: ((FileItem) -> Unit)? = null
    var onItemLongClick: ((FileItem, View) -> Unit)? = null

    // Cached resolved colors (initialized on first bind)
    private var colorSurface = 0
    private var colorBorder = 0
    private var colorsResolved = false

    private fun resolveColors(ctx: android.content.Context) {
        if (colorsResolved) return
        colorSurface = ContextCompat.getColor(ctx, R.color.surfaceColor)
        colorBorder = ContextCompat.getColor(ctx, R.color.borderDefault)
        colorsResolved = true
    }

    fun getFileCount(): Int = currentList.count { it is Item.File }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Item.Header -> TYPE_HEADER
        is Item.File -> when (viewMode) {
            ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> TYPE_FILE
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> TYPE_FILE_GRID
        }
    }

    fun isHeader(position: Int): Boolean = position in currentList.indices && getItem(position) is Item.Header

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
        when (val item = getItem(position)) {
            is Item.Header -> bindHeader(holder as HeaderVH, item)
            is Item.File -> bindFile(holder as FileVH, item.fileItem)
        }
    }

    private fun bindHeader(holder: HeaderVH, header: Item.Header) {
        holder.folderName.text = header.displayName
        holder.folderCount.text = holder.itemView.context.resources.getQuantityString(R.plurals.n_files, header.fileCount, header.fileCount)
    }

    private fun bindFile(holder: FileVH, item: FileItem) {
        holder.name.text = item.name
        resolveColors(holder.itemView.context)

        if (viewMode == ViewMode.LIST_WITH_THUMBNAILS) {
            val lp = holder.icon.layoutParams
            lp.width = 72.dpToPx(holder.itemView)
            lp.height = 72.dpToPx(holder.itemView)
            holder.icon.layoutParams = lp
        }

        // Load thumbnail or icon
        val isGrid = viewMode != ViewMode.LIST && viewMode != ViewMode.LIST_WITH_THUMBNAILS
        FileItemUtils.loadThumbnail(holder.icon, item, isGrid)

        // Default card colors (using cached resolved colors)
        val card = holder.itemView as? com.google.android.material.card.MaterialCardView
        card?.setCardBackgroundColor(colorSurface)
        card?.strokeColor = colorBorder

        // Meta line
        holder.meta?.let { FileItemUtils.buildMeta(it, item) }

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
