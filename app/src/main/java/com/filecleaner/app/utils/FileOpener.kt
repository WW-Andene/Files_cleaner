package com.filecleaner.app.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.filecleaner.app.R
import com.google.android.material.snackbar.Snackbar
import java.io.File

object FileOpener {

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

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.ctx_share)))
        } catch (_: ActivityNotFoundException) {
            showNoAppFound(context)
        } catch (_: IllegalArgumentException) {
            showNoAppFound(context)
        }
    }

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

            context.startActivity(Intent.createChooser(intent, context.getString(R.string.open_with)))
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
