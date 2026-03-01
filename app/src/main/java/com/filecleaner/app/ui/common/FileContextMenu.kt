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
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileContextMenu {

    private const val ID_OPEN = 1
    private const val ID_DELETE = 2
    private const val ID_RENAME = 3
    private const val ID_SHARE = 4
    private const val ID_CUT = 5
    private const val ID_COMPRESS = 6
    private const val ID_EXTRACT = 7
    private const val ID_OPEN_IN_TREE = 8
    private const val ID_PASTE = 9
    private const val ID_COPY = 10
    private const val ID_MOVE_TO = 11
    private const val ID_PROPERTIES = 12
    private const val ID_TOGGLE_FAVORITE = 13
    private const val ID_TOGGLE_PROTECTED = 14

    interface Callback {
        fun onDelete(item: FileItem)
        fun onRename(item: FileItem, newName: String)
        fun onCompress(item: FileItem)
        fun onExtract(item: FileItem)
        fun onOpenInTree(item: FileItem)
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
                    // Don't clear clipboard on copy — allows pasting multiple times
                }
            }
        }
        override fun onMoveTo(item: FileItem) { onMoveTo(item) }
        override fun onRefresh() { onRefresh() }
    }

    fun show(context: Context, anchor: View, item: FileItem, callback: Callback, hasClipboard: Boolean = false) {
        val popup = PopupMenu(context, anchor)
        var order = 0
        popup.menu.apply {
            add(0, ID_OPEN, order++, context.getString(R.string.ctx_open))
            add(0, ID_COPY, order++, context.getString(R.string.ctx_copy))
            add(0, ID_CUT, order++, context.getString(R.string.ctx_cut))
            if (hasClipboard) {
                val targetDir = File(item.path).parent
                if (targetDir != null) {
                    add(0, ID_PASTE, order++, context.getString(R.string.ctx_paste_here))
                }
            }
            add(0, ID_MOVE_TO, order++, context.getString(R.string.ctx_move_to))
            add(0, ID_RENAME, order++, context.getString(R.string.ctx_rename))
            add(0, ID_SHARE, order++, context.getString(R.string.ctx_share))
            add(0, ID_COMPRESS, order++, context.getString(R.string.ctx_compress))
            if (item.category == FileCategory.ARCHIVE) {
                add(0, ID_EXTRACT, order++, context.getString(R.string.ctx_extract))
            }
            val isFav = try { UserPreferences.isFavorite(item.path) } catch (_: Exception) { false }
            add(0, ID_TOGGLE_FAVORITE, order++, context.getString(
                if (isFav) R.string.ctx_unstar else R.string.ctx_star))
            val isProt = try { UserPreferences.isProtected(item.path) } catch (_: Exception) { false }
            add(0, ID_TOGGLE_PROTECTED, order++, context.getString(
                if (isProt) R.string.ctx_unprotect else R.string.ctx_protect))
            add(0, ID_DELETE, order++, context.getString(R.string.ctx_delete))
            add(0, ID_OPEN_IN_TREE, order++, context.getString(R.string.ctx_open_in_tree))
            add(0, ID_PROPERTIES, order++, context.getString(R.string.ctx_properties))
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                ID_OPEN -> {
                    FileOpener.open(context, item.file)
                    true
                }
                ID_DELETE -> {
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
                ID_RENAME -> {
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
                ID_SHARE -> {
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
                    true
                }
                ID_CUT -> {
                    callback.onCut(item)
                    Toast.makeText(context,
                        context.getString(R.string.cut_hint, item.name),
                        Toast.LENGTH_SHORT).show()
                    true
                }
                ID_COMPRESS -> {
                    callback.onCompress(item)
                    true
                }
                ID_EXTRACT -> {
                    callback.onExtract(item)
                    true
                }
                ID_OPEN_IN_TREE -> {
                    callback.onOpenInTree(item)
                    true
                }
                ID_PASTE -> {
                    val targetDir = File(item.path).parent
                    if (targetDir != null) {
                        callback.onPaste(targetDir)
                    }
                    true
                }
                ID_COPY -> {
                    callback.onCopy(item)
                    Toast.makeText(context,
                        context.getString(R.string.copy_hint, item.name),
                        Toast.LENGTH_SHORT).show()
                    true
                }
                ID_MOVE_TO -> {
                    callback.onMoveTo(item)
                    true
                }
                ID_PROPERTIES -> {
                    showProperties(context, item)
                    true
                }
                ID_TOGGLE_FAVORITE -> {
                    UserPreferences.toggleFavorite(item.path)
                    val nowFav = UserPreferences.isFavorite(item.path)
                    Toast.makeText(context, context.getString(
                        if (nowFav) R.string.favorite_added else R.string.favorite_removed, item.name),
                        Toast.LENGTH_SHORT).show()
                    true
                }
                ID_TOGGLE_PROTECTED -> {
                    UserPreferences.toggleProtected(item.path)
                    val nowProt = UserPreferences.isProtected(item.path)
                    Toast.makeText(context, context.getString(
                        if (nowProt) R.string.protected_added else R.string.protected_removed, item.name),
                        Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showProperties(context: Context, item: FileItem) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
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
