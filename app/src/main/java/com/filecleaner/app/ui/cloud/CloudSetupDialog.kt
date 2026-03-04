package com.filecleaner.app.ui.cloud

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.CloudConnectionStore
import com.filecleaner.app.data.cloud.ProviderType

/**
 * Dialog for adding/configuring a new cloud connection.
 */
object CloudSetupDialog {

    fun show(context: Context, onAdded: (CloudConnection) -> Unit) {
        val padding = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val fieldSpacing = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }

        // Provider type spinner
        val typeLabel = TextView(context).apply { text = context.getString(R.string.cloud_provider_type) }
        container.addView(typeLabel)

        val types = listOf("SFTP", "WebDAV", "Google Drive")
        val typeSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, types)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(typeSpinner)

        // Display name
        val nameInput = EditText(context).apply {
            hint = context.getString(R.string.cloud_display_name)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(nameInput)

        // Host / URL
        val hostInput = EditText(context).apply {
            hint = context.getString(R.string.cloud_host_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(hostInput)

        // Port
        val portInput = EditText(context).apply {
            hint = context.getString(R.string.cloud_port_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("22")
        }
        container.addView(portInput)

        // Username
        val userInput = EditText(context).apply {
            hint = context.getString(R.string.cloud_username_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(userInput)

        // Password / Token
        val passInput = EditText(context).apply {
            hint = context.getString(R.string.cloud_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        container.addView(passInput)

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cloud_add_connection))
            .setView(container)
            .setPositiveButton(context.getString(R.string.cloud_connect)) { _, _ ->
                val displayName = nameInput.text.toString().trim().ifEmpty { "My Server" }
                val host = hostInput.text.toString().trim()
                val port = portInput.text.toString().toIntOrNull() ?: 22
                val username = userInput.text.toString().trim()
                val password = passInput.text.toString()

                val connection = when (typeSpinner.selectedItemPosition) {
                    0 -> CloudConnection.sftp(displayName, host, port, username).copy(authToken = password)
                    1 -> CloudConnection.webdav(displayName, host, username, password)
                    2 -> CloudConnection.googleDrive(displayName, password)
                    else -> return@setPositiveButton
                }

                CloudConnectionStore.saveConnection(connection)
                onAdded(connection)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
}
