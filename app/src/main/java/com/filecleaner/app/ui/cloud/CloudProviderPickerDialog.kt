package com.filecleaner.app.ui.cloud

import android.content.Context
import android.view.LayoutInflater
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.ProviderType
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Step 1 of the cloud connection flow: provider picker.
 *
 * Shows branded cards for each supported service (Google Drive, SFTP, WebDAV).
 * Selecting a card dismisses this dialog and opens [CloudSetupDialog] for the
 * chosen provider.
 */
object CloudProviderPickerDialog {

    /**
     * Show the provider picker dialog. When the user completes the full two-step
     * flow (pick provider -> enter credentials), [onAdded] fires with the new connection.
     */
    fun show(context: Context, onAdded: (CloudConnection) -> Unit) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_cloud_provider_picker, null)

        val cardGDrive = view.findViewById<MaterialCardView>(R.id.card_google_drive)
        val cardSftp = view.findViewById<MaterialCardView>(R.id.card_sftp)
        val cardWebDav = view.findViewById<MaterialCardView>(R.id.card_webdav)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        // Google Drive card & button
        val btnGoogleSignIn = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_google_sign_in
        )
        val launchGDrive = {
            dialog.dismiss()
            CloudSetupDialog.showForProvider(context, ProviderType.GOOGLE_DRIVE, onAdded)
        }
        cardGDrive.setOnClickListener { launchGDrive() }
        btnGoogleSignIn.setOnClickListener { launchGDrive() }

        // SFTP card & button
        val btnSftpConnect = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_sftp_connect
        )
        val launchSftp = {
            dialog.dismiss()
            CloudSetupDialog.showForProvider(context, ProviderType.SFTP, onAdded)
        }
        cardSftp.setOnClickListener { launchSftp() }
        btnSftpConnect.setOnClickListener { launchSftp() }

        // WebDAV card & button
        val btnWebDavConnect = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_webdav_connect
        )
        val launchWebDav = {
            dialog.dismiss()
            CloudSetupDialog.showForProvider(context, ProviderType.WEBDAV, onAdded)
        }
        cardWebDav.setOnClickListener { launchWebDav() }
        btnWebDavConnect.setOnClickListener { launchWebDav() }

        dialog.show()
    }
}
