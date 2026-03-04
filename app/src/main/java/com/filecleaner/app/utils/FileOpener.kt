package com.filecleaner.app.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.navigation.findNavController
import com.filecleaner.app.R
import com.filecleaner.app.ui.viewer.FileViewerFragment
import com.google.android.material.snackbar.Snackbar
import java.io.File

object FileOpener {

    /**
     * Opens a file in the native in-app viewer (FileViewerFragment).
     * Falls back to external app if navigation fails.
     */
    fun openInViewer(context: Context, file: File) {
        try {
            val activity = context as? Activity ?: run {
                open(context, file)
                return
            }
            val navController = activity.findNavController(R.id.nav_host_fragment)
            val args = Bundle().apply {
                putString(FileViewerFragment.ARG_FILE_PATH, file.absolutePath)
            }
            navController.navigate(R.id.fileViewerFragment, args)
        } catch (_: Exception) {
            // Fallback to external app if navigation fails
            open(context, file)
        }
    }

    /**
     * Shares a file via the system share sheet.
     */
    fun share(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.ctx_share))
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            showNoAppFound(context)
        } catch (_: IllegalArgumentException) {
            showNoAppFound(context)
        }
    }

    /**
     * Opens a file with an external app via the system chooser.
     */
    fun open(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val ext = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.open_with))
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            showNoAppFound(context)
        } catch (_: IllegalArgumentException) {
            showNoAppFound(context)
        }
    }

    private fun showNoAppFound(context: Context) {
        val rootView = (context as? Activity)?.findViewById<View>(android.R.id.content)
        if (rootView != null) {
            Snackbar.make(rootView, context.getString(R.string.no_app_found), Snackbar.LENGTH_SHORT).show()
        }
    }
}
