package com.filecleaner.app.utils

import android.view.View
import com.filecleaner.app.R
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar

/**
 * Shows a Snackbar with undo after deletion, and confirms delete when dismissed.
 */
object UndoHelper {

    private const val DEFAULT_UNDO_TIMEOUT_MS = 8000

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
            ctx.getString(R.string.undo_deleted_single, result.singleFileName ?: "file", sizeText)
        } else {
            ctx.getString(R.string.undo_deleted_multiple, result.moved, sizeText)
        }

        if (result.canUndo && result.moved > 0) {
            val timeoutMs = try { UserPreferences.undoTimeoutMs } catch (_: Exception) { DEFAULT_UNDO_TIMEOUT_MS }
            Snackbar.make(view, msg, Snackbar.LENGTH_LONG)
                .setDuration(timeoutMs)
                .setAction(ctx.getString(R.string.undo)) { vm.undoDelete() }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(bar: Snackbar?, event: Int) {
                        // Don't auto-confirm when displaced by a consecutive snackbar
                        if (event != DISMISS_EVENT_ACTION && event != DISMISS_EVENT_CONSECUTIVE) {
                            vm.confirmDelete()
                        }
                    }
                })
                .show()
        } else if (result.moved > 0 || result.failed > 0) {
            Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
        }
    }

    fun formatBytes(bytes: Long): String {
        // F-079: Clamp negative values to zero — prevents confusing "-1.5 MB" display
        // from corrupted data or subtraction deltas.
        val safe = bytes.coerceAtLeast(0L)
        val locale = java.util.Locale.getDefault()
        return when {
            safe >= 1_073_741_824 -> String.format(locale, "%.1f GB", safe / 1_073_741_824.0)
            safe >= 1_048_576     -> String.format(locale, "%.1f MB", safe / 1_048_576.0)
            safe >= 1_024         -> String.format(locale, "%.1f KB", safe / 1_024.0)
            else                   -> "$safe B"
        }
    }

    fun totalSize(list: List<com.filecleaner.app.data.FileItem>): String {
        return formatBytes(list.sumOf { it.size })
    }
}
