package com.filecleaner.app.ui.common

import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.FileOpener
import java.io.File

object FileContextMenu {

    interface Callback {
        fun onDelete(item: FileItem)
        fun onRename(item: FileItem, newName: String)
        fun onCompress(item: FileItem)
        fun onExtract(item: FileItem)
        fun onOpenInTree(item: FileItem)
        fun onCut(item: FileItem) {}
        fun onPaste(targetDirPath: String) {}
        fun onRefresh()
    }

    fun show(context: Context, anchor: View, item: FileItem, callback: Callback, hasCutFile: Boolean = false) {
        val popup = PopupMenu(context, anchor)
        popup.menu.apply {
            add(0, 1, 0, context.getString(R.string.ctx_open))
            add(0, 2, 1, context.getString(R.string.ctx_delete))
            add(0, 3, 2, context.getString(R.string.ctx_rename))
            add(0, 4, 3, context.getString(R.string.ctx_share))
            add(0, 5, 4, context.getString(R.string.ctx_cut))
            if (hasCutFile) {
                val targetDir = File(item.path).parent
                if (targetDir != null) {
                    add(0, 9, 5, context.getString(R.string.ctx_paste_here))
                }
            }
            add(0, 6, 6, context.getString(R.string.ctx_compress))
            if (item.category == FileCategory.ARCHIVE) {
                add(0, 7, 7, context.getString(R.string.ctx_extract))
            }
            add(0, 8, 8, context.getString(R.string.ctx_open_in_tree))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> { // Open
                    FileOpener.open(context, item.file)
                    true
                }
                2 -> { // Delete
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.confirm_delete_title))
                        .setMessage(context.getString(R.string.confirm_delete_message))
                        .setPositiveButton(context.getString(R.string.delete)) { _, _ ->
                            callback.onDelete(item)
                        }
                        .setNegativeButton(context.getString(R.string.cancel), null)
                        .show()
                    true
                }
                3 -> { // Rename
                    val editText = EditText(context).apply {
                        setText(item.name)
                        selectAll()
                    }
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.ctx_rename))
                        .setView(editText)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val newName = editText.text.toString().trim()
                            if (newName.isNotEmpty() && newName != item.name) {
                                callback.onRename(item, newName)
                            }
                        }
                        .setNegativeButton(context.getString(R.string.cancel), null)
                        .show()
                    true
                }
                4 -> { // Share
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", item.file
                    )
                    val ext = item.name.substringAfterLast('.', "").lowercase()
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.ctx_share)))
                    true
                }
                5 -> { // Cut — delegate to callback (clipboard lives in ViewModel)
                    callback.onCut(item)
                    Toast.makeText(context,
                        context.getString(R.string.cut_hint, item.name),
                        Toast.LENGTH_SHORT).show()
                    true
                }
                6 -> { // Compress
                    callback.onCompress(item)
                    true
                }
                7 -> { // Extract
                    callback.onExtract(item)
                    true
                }
                8 -> { // Open in Raccoon Tab
                    callback.onOpenInTree(item)
                    true
                }
                9 -> { // Paste here
                    val targetDir = File(item.path).parent
                    if (targetDir != null) {
                        callback.onPaste(targetDir)
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
