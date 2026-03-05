package com.filecleaner.app.ui.cloud

import android.content.Context
import android.view.LayoutInflater
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.OAuthHelper
import com.filecleaner.app.data.cloud.ProviderType
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Step 1 of the cloud connection flow: provider picker.
 *
 * Shows branded cards for each supported service (Google Drive, GitHub, SFTP, WebDAV).
 * For Google Drive and GitHub, clicking the button will attempt OAuth first if
 * OAuth credentials are configured, falling back to manual token entry otherwise.
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
        val cardGitHub = view.findViewById<MaterialCardView>(R.id.card_github)
        val cardSftp = view.findViewById<MaterialCardView>(R.id.card_sftp)
        val cardWebDav = view.findViewById<MaterialCardView>(R.id.card_webdav)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        // Google Drive card & button — tries OAuth first, falls back to manual token
        val btnGoogleSignIn = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_google_sign_in
        )
        val launchGDrive = {
            dialog.dismiss()
            CloudSetupDialog.showForProvider(context, ProviderType.GOOGLE_DRIVE, onAdded)
        }
        val launchGDriveOAuth = {
            val config = OAuthHelper.getConfig(context, ProviderType.GOOGLE_DRIVE)
            if (config != null) {
                dialog.dismiss()
                OAuthHelper.launchAuthFlow(context, ProviderType.GOOGLE_DRIVE, config.clientId)
            } else {
                // Fall back to manual token entry (which also has its own OAuth button)
                launchGDrive()
            }
        }
        cardGDrive.setOnClickListener { launchGDrive() }
        btnGoogleSignIn.setOnClickListener { launchGDriveOAuth() }

        // GitHub card & button — tries OAuth first, falls back to manual token
        val btnGitHubConnect = view.findViewById<com.google.android.material.button.MaterialButton>(
            R.id.btn_github_connect
        )
        val launchGitHub = {
            dialog.dismiss()
            CloudSetupDialog.showForProvider(context, ProviderType.GITHUB, onAdded)
        }
        val launchGitHubOAuth = {
            val config = OAuthHelper.getConfig(context, ProviderType.GITHUB)
            if (config != null) {
                dialog.dismiss()
                OAuthHelper.launchAuthFlow(context, ProviderType.GITHUB, config.clientId)
            } else {
                // Fall back to manual token entry (which also has its own OAuth button)
                launchGitHub()
            }
        }
        cardGitHub.setOnClickListener { launchGitHub() }
        btnGitHubConnect.setOnClickListener { launchGitHubOAuth() }

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
