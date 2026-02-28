package com.filecleaner.app.utils

import android.view.View
import com.filecleaner.app.R
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * Shows a Snackbar with undo after deletion, and confirms delete when dismissed.
 */
object UndoHelper {

    fun showUndoSnackbar(
        view: View,
        result: MainViewModel.DeleteResult,
        vm: MainViewModel
    ) {
        val ctx = view.context
        val sizeText = formatBytes(result.freedBytes)

        val msg = if (result.failed > 0) {
            ctx.getString(R.string.delete_partial_failure, result.moved, result.failed)
        } else if (result.moved == 1) {
            ctx.getString(R.string.undo_deleted_single, "file", sizeText)
        } else {
            ctx.getString(R.string.undo_deleted_multiple, result.moved, sizeText)
        }

        if (result.canUndo && result.moved > 0) {
            Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                .setDuration(8000) // 8 seconds to undo
                .setAction(ctx.getString(R.string.undo)) { vm.undoDelete() }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(bar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            vm.confirmDelete()
                        }
                    }
                })
                .show()
        } else if (result.moved > 0 || result.failed > 0) {
            Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }

    fun totalSize(list: List<com.filecleaner.app.data.FileItem>): String {
        return formatBytes(list.sumOf { it.size })
    }
}
