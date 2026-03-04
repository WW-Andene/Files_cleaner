package com.filecleaner.app.ui.cloud

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.CloudConnectionStore
import com.filecleaner.app.data.cloud.CloudProvider
import com.filecleaner.app.data.cloud.GitHubProvider
import com.filecleaner.app.data.cloud.GoogleDriveProvider
import com.filecleaner.app.data.cloud.OAuthHelper
import com.filecleaner.app.data.cloud.ProviderType
import com.filecleaner.app.data.cloud.SftpProvider
import com.filecleaner.app.data.cloud.WebDavProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 2 of the cloud connection flow: provider-specific credential entry.
 *
 * Called after [CloudProviderPickerDialog] selects a provider type.
 * Shows only the fields relevant to that provider, plus a "Test Connection"
 * button that verifies connectivity before saving.
 *
 * Legacy [show] method is still available for backwards compatibility and
 * opens the [CloudProviderPickerDialog] automatically.
 */
object CloudSetupDialog {

    /**
     * Handle the OAuth callback by exchanging the code for a token
     * and auto-filling the token field.
     */
    private var pendingOAuthCallback: ((String) -> Unit)? = null

    fun handleOAuthCallback(code: String) {
        pendingOAuthCallback?.invoke(code)
        pendingOAuthCallback = null
    }

    /**
     * Create a connection directly from an OAuth-obtained token.
     * Used when the OAuth flow was launched from the provider picker (not the setup dialog)
     * and the callback returns after the setup dialog was never opened.
     */
    fun showOAuthResult(
        context: Context,
        provider: ProviderType,
        token: String,
        onAdded: (CloudConnection) -> Unit
    ) {
        val displayName = when (provider) {
            ProviderType.GOOGLE_DRIVE -> context.getString(R.string.cloud_gdrive_default_name)
            ProviderType.GITHUB -> context.getString(R.string.cloud_github_default_name)
            else -> "Cloud"
        }
        val connection = when (provider) {
            ProviderType.GOOGLE_DRIVE -> CloudConnection.googleDrive(displayName, token)
            ProviderType.GITHUB -> CloudConnection.github(displayName, token)
            else -> return
        }
        CloudConnectionStore.saveConnection(connection)
        onAdded(connection)
    }

    /**
     * Legacy entry point: opens the provider picker first, then this dialog.
     * Kept for backwards compatibility with call sites that haven't been updated.
     */
    fun show(context: Context, onAdded: (CloudConnection) -> Unit) {
        CloudProviderPickerDialog.show(context, onAdded)
    }

    /**
     * Show the connection setup dialog for a specific provider type.
     * This is called by [CloudProviderPickerDialog] after the user picks a service.
     */
    fun showForProvider(
        context: Context,
        providerType: ProviderType,
        onAdded: (CloudConnection) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_cloud_connect, null)

        val tvHelp = dialogView.findViewById<TextView>(R.id.tv_provider_help)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_name)
        val tilHost = dialogView.findViewById<TextInputLayout>(R.id.til_host)
        val etHost = dialogView.findViewById<TextInputEditText>(R.id.et_host)
        val tilPort = dialogView.findViewById<TextInputLayout>(R.id.til_port)
        val etPort = dialogView.findViewById<TextInputEditText>(R.id.et_port)
        val tilUsername = dialogView.findViewById<TextInputLayout>(R.id.til_username)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.et_username)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.til_password)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_password)
        val btnTest = dialogView.findViewById<MaterialButton>(R.id.btn_test_connection)
        val testResultContainer = dialogView.findViewById<LinearLayout>(R.id.test_result_container)
        val ivTestIcon = dialogView.findViewById<ImageView>(R.id.iv_test_result_icon)
        val tvTestResult = dialogView.findViewById<TextView>(R.id.tv_test_result)
        val progressTest = dialogView.findViewById<ProgressBar>(R.id.progress_test)

        var testJob: Job? = null

        // Configure fields based on provider
        val title: String
        when (providerType) {
            ProviderType.SFTP -> {
                title = context.getString(R.string.cloud_setup_title_sftp)
                tilHost.visibility = View.VISIBLE
                tilPort.visibility = View.VISIBLE
                tilUsername.visibility = View.VISIBLE
                tilPassword.visibility = View.VISIBLE
                tilHost.hint = context.getString(R.string.cloud_sftp_host_hint)
                tilPassword.hint = context.getString(R.string.cloud_password_hint)
                etPort.setText("22")
                etName.setText(context.getString(R.string.cloud_sftp_default_name))
                tvHelp.text = context.getString(R.string.cloud_sftp_help)
                tvHelp.visibility = View.VISIBLE
            }
            ProviderType.WEBDAV -> {
                title = context.getString(R.string.cloud_setup_title_webdav)
                tilHost.visibility = View.VISIBLE
                tilPort.visibility = View.VISIBLE
                tilUsername.visibility = View.VISIBLE
                tilPassword.visibility = View.VISIBLE
                tilHost.hint = context.getString(R.string.cloud_webdav_host_hint)
                tilPassword.hint = context.getString(R.string.cloud_password_hint)
                etPort.setText("443")
                etName.setText(context.getString(R.string.cloud_webdav_default_name))
                tvHelp.text = context.getString(R.string.cloud_webdav_help)
                tvHelp.visibility = View.VISIBLE
            }
            ProviderType.GOOGLE_DRIVE -> {
                title = context.getString(R.string.cloud_setup_title_gdrive)
                tilHost.visibility = View.GONE
                tilPort.visibility = View.GONE
                tilUsername.visibility = View.GONE
                tilPassword.visibility = View.VISIBLE
                tilPassword.hint = context.getString(R.string.cloud_gdrive_token_hint)
                etName.setText(context.getString(R.string.cloud_gdrive_default_name))
                tvHelp.text = context.getString(R.string.cloud_gdrive_help)
                tvHelp.visibility = View.VISIBLE
            }
            ProviderType.GITHUB -> {
                title = context.getString(R.string.cloud_setup_title_github)
                tilHost.visibility = View.GONE
                tilPort.visibility = View.GONE
                tilUsername.visibility = View.GONE
                tilPassword.visibility = View.VISIBLE
                tilPassword.hint = context.getString(R.string.cloud_github_token_hint)
                etName.setText(context.getString(R.string.cloud_github_default_name))
                tvHelp.text = context.getString(R.string.cloud_github_help)
                tvHelp.visibility = View.VISIBLE
            }
        }

        // Helper to build a CloudConnection from the current form state, or null if invalid
        fun buildConnection(): CloudConnection? {
            val displayName = etName.text.toString().trim().ifEmpty {
                context.getString(R.string.cloud_default_server_name)
            }
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().toIntOrNull() ?: -1
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            // Clear previous errors
            tilHost.error = null
            tilPort.error = null
            tilUsername.error = null

            var valid = true
            val tokenOnlyTypes = setOf(ProviderType.GOOGLE_DRIVE, ProviderType.GITHUB)
            if (providerType !in tokenOnlyTypes && host.isBlank()) {
                tilHost.error = context.getString(R.string.cloud_error_host_required)
                valid = false
            }
            if (providerType !in tokenOnlyTypes && port !in 1..65535) {
                tilPort.error = context.getString(R.string.cloud_error_port_range)
                valid = false
            }
            if (providerType in listOf(ProviderType.SFTP, ProviderType.WEBDAV) && username.isBlank()) {
                tilUsername.error = context.getString(R.string.cloud_error_username_required)
                valid = false
            }
            if (!valid) return null

            return when (providerType) {
                ProviderType.SFTP -> CloudConnection.sftp(displayName, host, port, username)
                    .copy(authToken = password)
                ProviderType.WEBDAV -> CloudConnection.webdav(displayName, host, username, password)
                ProviderType.GOOGLE_DRIVE -> CloudConnection.googleDrive(displayName, password)
                ProviderType.GITHUB -> CloudConnection.github(displayName, password)
            }
        }

        // Helper to create a provider for testing
        fun createProvider(connection: CloudConnection): CloudProvider {
            return when (connection.type) {
                ProviderType.SFTP -> SftpProvider(connection, context)
                ProviderType.WEBDAV -> WebDavProvider(connection)
                ProviderType.GOOGLE_DRIVE -> GoogleDriveProvider(connection, context)
                ProviderType.GITHUB -> GitHubProvider(connection, context)
            }
        }

        // Test Connection button
        btnTest.setOnClickListener {
            val connection = buildConnection() ?: return@setOnClickListener

            testJob?.cancel()
            btnTest.isEnabled = false
            progressTest.visibility = View.VISIBLE
            testResultContainer.visibility = View.GONE

            testJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    val provider = createProvider(connection)
                    val success = provider.connect()
                    // Always disconnect after test
                    try { provider.disconnect() } catch (_: Exception) {}

                    progressTest.visibility = View.GONE
                    testResultContainer.visibility = View.VISIBLE
                    btnTest.isEnabled = true

                    if (success) {
                        ivTestIcon.setImageResource(R.drawable.ic_check_circle)
                        tvTestResult.text = context.getString(R.string.cloud_test_success)
                        tvTestResult.setTextColor(
                            context.getColor(R.color.colorSuccess)
                        )
                    } else {
                        ivTestIcon.setImageResource(R.drawable.ic_status_disconnected)
                        tvTestResult.text = context.getString(
                            R.string.cloud_test_failed,
                            context.getString(R.string.cloud_connection_failed, connection.displayName)
                        )
                        tvTestResult.setTextColor(
                            context.getColor(R.color.colorError)
                        )
                    }
                } catch (e: Exception) {
                    progressTest.visibility = View.GONE
                    testResultContainer.visibility = View.VISIBLE
                    btnTest.isEnabled = true
                    ivTestIcon.setImageResource(R.drawable.ic_status_disconnected)
                    tvTestResult.text = context.getString(
                        R.string.cloud_test_failed,
                        e.localizedMessage ?: e.javaClass.simpleName
                    )
                    tvTestResult.setTextColor(
                        context.getColor(R.color.colorError)
                    )
                }
            }
        }

        // OAuth sign-in button and configuration for token-based providers
        val btnOAuth = dialogView.findViewById<MaterialButton>(R.id.btn_oauth_sign_in)
        val oauthDivider = dialogView.findViewById<View>(R.id.oauth_divider)
        val oauthConfigSection = dialogView.findViewById<LinearLayout>(R.id.oauth_config_section)
        val etOAuthClientId = dialogView.findViewById<TextInputEditText>(R.id.et_oauth_client_id)
        val etOAuthClientSecret = dialogView.findViewById<TextInputEditText>(R.id.et_oauth_client_secret)

        if (providerType == ProviderType.GOOGLE_DRIVE || providerType == ProviderType.GITHUB) {
            btnOAuth?.visibility = View.VISIBLE
            oauthDivider?.visibility = View.VISIBLE
            oauthConfigSection?.visibility = View.VISIBLE
            btnOAuth?.text = when (providerType) {
                ProviderType.GOOGLE_DRIVE -> context.getString(R.string.cloud_oauth_google)
                ProviderType.GITHUB -> context.getString(R.string.cloud_oauth_github)
                else -> ""
            }

            // Pre-fill OAuth config from saved preferences
            val savedConfig = OAuthHelper.getConfig(context, providerType)
            if (savedConfig != null) {
                etOAuthClientId?.setText(savedConfig.clientId)
                etOAuthClientSecret?.setText(savedConfig.clientSecret)
            }

            btnOAuth?.setOnClickListener {
                // Read client ID/secret from the config fields
                val clientId = etOAuthClientId?.text?.toString()?.trim() ?: ""
                val clientSecret = etOAuthClientSecret?.text?.toString()?.trim() ?: ""

                if (clientId.isBlank()) {
                    testResultContainer.visibility = View.VISIBLE
                    ivTestIcon.setImageResource(R.drawable.ic_status_disconnected)
                    tvTestResult.text = context.getString(R.string.cloud_oauth_failed,
                        "Client ID is required for OAuth")
                    tvTestResult.setTextColor(context.getColor(R.color.colorError))
                    return@setOnClickListener
                }

                // Save OAuth config for future use
                val config = OAuthHelper.OAuthConfig(clientId, clientSecret)
                OAuthHelper.saveConfig(context, providerType, config)

                // Register callback to exchange code for token after OAuth redirect
                pendingOAuthCallback = { code ->
                    btnOAuth.isEnabled = false
                    progressTest.visibility = View.VISIBLE
                    testResultContainer.visibility = View.GONE
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = OAuthHelper.exchangeCodeForToken(context, code, providerType)
                        progressTest.visibility = View.GONE
                        btnOAuth.isEnabled = true
                        if (result.isSuccess) {
                            etPassword.setText(result.accessToken)
                            testResultContainer.visibility = View.VISIBLE
                            ivTestIcon.setImageResource(R.drawable.ic_check_circle)
                            tvTestResult.text = context.getString(R.string.cloud_oauth_success)
                            tvTestResult.setTextColor(context.getColor(R.color.colorSuccess))
                        } else {
                            testResultContainer.visibility = View.VISIBLE
                            ivTestIcon.setImageResource(R.drawable.ic_status_disconnected)
                            tvTestResult.text = context.getString(R.string.cloud_oauth_failed, result.error)
                            tvTestResult.setTextColor(context.getColor(R.color.colorError))
                        }
                    }
                }

                // Launch the OAuth flow with PKCE in a Custom Chrome Tab
                OAuthHelper.launchAuthFlow(context, providerType, clientId)
            }
        } else {
            btnOAuth?.visibility = View.GONE
            oauthDivider?.visibility = View.GONE
            oauthConfigSection?.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.cloud_connect), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setOnDismissListener { testJob?.cancel() }
            .show()

        // Override positive button to prevent dismiss on validation failure
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val connection = buildConnection() ?: return@setOnClickListener
            CloudConnectionStore.saveConnection(connection)
            onAdded(connection)
            dialog.dismiss()
        }
    }
}
