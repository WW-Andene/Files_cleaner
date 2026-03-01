package com.filecleaner.app.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.DateFormat
import java.util.Date

object FileContextMenu {

    interface Callback {
        fun onDelete(item: FileItem)
        fun onRename(item: FileItem, newName: String)
        fun onCompress(item: FileItem)
        fun onExtract(item: FileItem)
        fun onOpenInTree(item: FileItem)
        fun onBrowseFolder(folderPath: String) {}
        fun onCut(item: FileItem) {}
        fun onCopy(item: FileItem) {}
        fun onPaste(targetDirPath: String) {}
        fun onMoveTo(item: FileItem) {}
        fun onRefresh()
    }

    /**
     * Creates the standard Callback wired to ViewModel operations.
     * [onMoveTo] must be provided by the fragment to show the directory picker.
     */
    fun defaultCallback(
        vm: MainViewModel,
        onOpenInTree: (FileItem) -> Unit = { vm.requestTreeHighlight(it.path) },
        onMoveTo: (FileItem) -> Unit = {},
        onRefresh: () -> Unit = {}
    ): Callback = object : Callback {
        override fun onDelete(item: FileItem) { vm.deleteFiles(listOf(item)) }
        override fun onRename(item: FileItem, newName: String) { vm.renameFile(item.path, newName) }
        override fun onCompress(item: FileItem) { vm.compressFile(item.path) }
        override fun onExtract(item: FileItem) { vm.extractArchive(item.path) }
        override fun onOpenInTree(item: FileItem) { onOpenInTree(item) }
        override fun onBrowseFolder(folderPath: String) { vm.requestBrowseFolder(folderPath) }
        override fun onCut(item: FileItem) { vm.setCutFile(item) }
        override fun onCopy(item: FileItem) { vm.setCopyFile(item) }
        override fun onPaste(targetDirPath: String) {
            val entry = vm.clipboardEntry.value ?: return
            when (entry.mode) {
                MainViewModel.ClipboardMode.CUT -> {
                    vm.moveFile(entry.item.path, targetDirPath)
                    vm.clearClipboard()
                }
                MainViewModel.ClipboardMode.COPY -> {
                    vm.copyFile(entry.item.path, targetDirPath)
                }
            }
        }
        override fun onMoveTo(item: FileItem) { onMoveTo(item) }
        override fun onRefresh() { onRefresh() }
    }

    fun show(context: Context, anchor: View, item: FileItem, callback: Callback, hasClipboard: Boolean = false) {
        val dialog = BottomSheetDialog(context, R.style.Theme_FileCleaner_BottomSheet)
        val contentView = View.inflate(context, R.layout.dialog_file_context, null)

        // Header
        val tvName = contentView.findViewById<TextView>(R.id.tv_file_name)
        val tvInfo = contentView.findViewById<TextView>(R.id.tv_file_info)
        val ivIcon = contentView.findViewById<ImageView>(R.id.iv_file_icon)

        tvName.text = item.name
        tvInfo.text = "${UndoHelper.formatBytes(item.size)} \u2022 ${context.getString(item.category.displayNameRes)}"

        val iconRes = when (item.category) {
            FileCategory.IMAGE -> R.drawable.ic_image
            FileCategory.VIDEO -> R.drawable.ic_video
            FileCategory.AUDIO -> R.drawable.ic_audio
            FileCategory.DOCUMENT -> R.drawable.ic_document
            FileCategory.APK -> R.drawable.ic_apk
            FileCategory.ARCHIVE -> R.drawable.ic_archive
            FileCategory.DOWNLOAD -> R.drawable.ic_download
            FileCategory.OTHER -> R.drawable.ic_file
        }
        ivIcon.setImageResource(iconRes)

        val container = contentView.findViewById<LinearLayout>(R.id.menu_container)
        val dp = context.resources.displayMetrics.density

        fun addItem(label: String, iconDrawable: Int, action: () -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (48 * dp).toInt()
                )
                setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
                background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
                setImageResource(iconDrawable)
                setColorFilter(ContextCompat.getColor(context, R.color.textSecondary))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            row.addView(icon)

            val text = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (16 * dp).toInt()
                }
                this.text = label
                setTextColor(ContextCompat.getColor(context, R.color.textPrimary))
                textSize = 15f
            }
            row.addView(text)

            container.addView(row)
        }

        fun addDivider() {
            val div = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
                ).apply {
                    topMargin = (4 * dp).toInt()
                    bottomMargin = (4 * dp).toInt()
                    marginStart = (20 * dp).toInt()
                    marginEnd = (20 * dp).toInt()
                }
                setBackgroundColor(ContextCompat.getColor(context, R.color.borderDefault))
            }
            container.addView(div)
        }

        // -- Menu items --
        val isFolder = item.file.isDirectory
        if (isFolder) {
            addItem(context.getString(R.string.ctx_browse_folder), R.drawable.ic_nav_browse) {
                callback.onBrowseFolder(item.path)
            }
        } else {
            addItem(context.getString(R.string.ctx_open), android.R.drawable.ic_menu_view) {
                FileOpener.open(context, item.file)
            }
            addItem(context.getString(R.string.ctx_preview), android.R.drawable.ic_menu_gallery) {
                FilePreviewDialog.show(context, item)
            }
            val parentDir = item.file.parent
            if (parentDir != null) {
                addItem(context.getString(R.string.ctx_browse_folder), R.drawable.ic_nav_browse) {
                    callback.onBrowseFolder(parentDir)
                }
            }
        }

        addDivider()

        addItem(context.getString(R.string.ctx_copy), R.drawable.ic_copy) {
            callback.onCopy(item)
            Snackbar.make(anchor, context.getString(R.string.copy_hint, item.name), Snackbar.LENGTH_SHORT).show()
        }
        addItem(context.getString(R.string.ctx_cut), android.R.drawable.ic_menu_edit) {
            callback.onCut(item)
            Snackbar.make(anchor, context.getString(R.string.cut_hint, item.name), Snackbar.LENGTH_SHORT).show()
        }
        if (hasClipboard) {
            val targetDir = File(item.path).parent
            if (targetDir != null) {
                addItem(context.getString(R.string.ctx_paste_here), android.R.drawable.ic_menu_add) {
                    callback.onPaste(targetDir)
                }
            }
        }
        addItem(context.getString(R.string.ctx_move_to), android.R.drawable.ic_menu_send) {
            callback.onMoveTo(item)
        }
        addItem(context.getString(R.string.ctx_rename), android.R.drawable.ic_menu_edit) {
            val editText = EditText(context).apply {
                setText(item.name)
                selectAll()
            }
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.ctx_rename))
                .setView(editText)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val newName = editText.text.toString().trim()
                    // C2: UI-layer defense-in-depth for invalid filesystem characters
                    val invalidChars = charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|')
                    if (newName.isNotEmpty() && invalidChars.any { it in newName }) {
                        Snackbar.make(anchor, context.getString(R.string.op_invalid_name), Snackbar.LENGTH_SHORT).show()
                    } else if (newName.isNotEmpty() && newName != item.name) {
                        callback.onRename(item, newName)
                    }
                }
                .setNegativeButton(context.getString(R.string.cancel), null)
                .show()
        }

        addDivider()

        addItem(context.getString(R.string.ctx_share), android.R.drawable.ic_menu_share) {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", item.file
            )
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extension) ?: "*/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.ctx_share)))
        }
        addItem(context.getString(R.string.ctx_compress), R.drawable.ic_archive) {
            callback.onCompress(item)
        }
        if (item.category == FileCategory.ARCHIVE) {
            addItem(context.getString(R.string.ctx_extract), R.drawable.ic_archive) {
                callback.onExtract(item)
            }
        }

        addDivider()

        val isFav = try { UserPreferences.isFavorite(item.path) } catch (_: Exception) { false }
        addItem(context.getString(if (isFav) R.string.ctx_unstar else R.string.ctx_star),
            android.R.drawable.btn_star) {
            UserPreferences.toggleFavorite(item.path)
            val nowFav = UserPreferences.isFavorite(item.path)
            Snackbar.make(anchor, context.getString(
                if (nowFav) R.string.favorite_added else R.string.favorite_removed, item.name),
                Snackbar.LENGTH_SHORT).show()
        }
        val isProt = try { UserPreferences.isProtected(item.path) } catch (_: Exception) { false }
        addItem(context.getString(if (isProt) R.string.ctx_unprotect else R.string.ctx_protect),
            android.R.drawable.ic_secure) {
            UserPreferences.toggleProtected(item.path)
            val nowProt = UserPreferences.isProtected(item.path)
            Snackbar.make(anchor, context.getString(
                if (nowProt) R.string.protected_added else R.string.protected_removed, item.name),
                Snackbar.LENGTH_SHORT).show()
        }

        addDivider()

        addItem(context.getString(R.string.ctx_open_in_tree), R.drawable.ic_folder) {
            callback.onOpenInTree(item)
        }
        addItem(context.getString(R.string.ctx_properties), android.R.drawable.ic_menu_info_details) {
            showProperties(context, item)
        }

        // Delete at the end, styled distinctly
        val deleteRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * dp).toInt()
            )
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
            background = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                val undoSec = try { UserPreferences.undoTimeoutMs / 1000 } catch (_: Exception) { 8 }
                val detail = context.resources.getQuantityString(
                    R.plurals.confirm_delete_detail, 1, 1, UndoHelper.formatBytes(item.size), undoSec)
                AlertDialog.Builder(context)
                    .setTitle(context.resources.getQuantityString(R.plurals.delete_n_files_title, 1, 1))
                    .setMessage("${item.name}\n\n$detail")
                    .setPositiveButton(context.getString(R.string.delete)) { _, _ ->
                        callback.onDelete(item)
                    }
                    .setNegativeButton(context.getString(R.string.cancel), null)
                    .show()
            }
        }
        val deleteIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
            setImageResource(R.drawable.ic_delete)
            setColorFilter(ContextCompat.getColor(context, R.color.colorError))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        deleteRow.addView(deleteIcon)
        val deleteText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (16 * dp).toInt()
            }
            text = context.getString(R.string.ctx_delete)
            setTextColor(ContextCompat.getColor(context, R.color.colorError))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        deleteRow.addView(deleteText)
        container.addView(deleteRow)

        dialog.setContentView(contentView)
        dialog.show()
    }

    private fun showProperties(context: Context, item: FileItem) {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extension) ?: "unknown"
        val parentDir = File(item.path).parent ?: "/"

        val info = buildString {
            appendLine("${context.getString(R.string.prop_name)}: ${item.name}")
            appendLine("${context.getString(R.string.prop_path)}: ${item.path}")
            appendLine("${context.getString(R.string.prop_size)}: ${UndoHelper.formatBytes(item.size)} (${item.size} bytes)")
            appendLine("${context.getString(R.string.prop_modified)}: ${dateFormat.format(Date(item.lastModified))}")
            appendLine("${context.getString(R.string.prop_category)}: ${context.getString(item.category.displayNameRes)}")
            appendLine("${context.getString(R.string.prop_type)}: $mimeType")
            appendLine("${context.getString(R.string.prop_folder)}: $parentDir")
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.ctx_properties))
            .setMessage(info)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
