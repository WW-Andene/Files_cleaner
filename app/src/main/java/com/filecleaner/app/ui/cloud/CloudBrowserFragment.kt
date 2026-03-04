package com.filecleaner.app.ui.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.CloudConnectionStore
import com.filecleaner.app.data.cloud.CloudFile
import com.filecleaner.app.data.cloud.CloudProvider
import com.filecleaner.app.data.cloud.GitHubProvider
import com.filecleaner.app.data.cloud.GoogleDriveProvider
import com.filecleaner.app.data.cloud.ProviderType
import com.filecleaner.app.data.cloud.SftpProvider
import com.filecleaner.app.data.cloud.WebDavProvider
import com.filecleaner.app.databinding.FragmentCloudBrowserBinding
import com.filecleaner.app.utils.styleAsError
import com.google.android.material.snackbar.Snackbar
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Cloud/network file browser fragment.
 * Allows browsing, downloading, and uploading files to cloud providers.
 *
 * The "Add" flow uses a two-step approach:
 * 1. [CloudProviderPickerDialog] — branded service cards (Google Drive, SFTP, WebDAV)
 * 2. [CloudSetupDialog] — provider-specific credential entry with "Test Connection"
 *
 * The connection bar shows a status indicator dot (green = connected, red = disconnected)
 * and a "Test Connection" button for verifying connectivity without browsing.
 */
class CloudBrowserFragment : Fragment() {

    private var _binding: FragmentCloudBrowserBinding? = null
    private val binding get() = _binding!!

    private var connections = mutableListOf<CloudConnection>()
    private var currentProvider: CloudProvider? = null
    private var currentPath = "/"
    private var currentFiles = listOf<CloudFile>()
    /** Tracks whether we are currently connected to a provider */
    private var isProviderConnected = false

    private lateinit var fileAdapter: CloudFileAdapter

    @Suppress("DEPRECATION")
    private val downloadDir: String by lazy {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    private val uploadFilePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCloudBrowserBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CloudConnectionStore.init(requireContext())
        currentPath = savedInstanceState?.getString(KEY_PATH) ?: "/"

        binding.btnBack.setOnClickListener {
            if (currentProvider != null && currentPath != "/") {
                navigateUp()
            } else {
                findNavController().popBackStack()
            }
        }

        // File adapter
        fileAdapter = CloudFileAdapter()
        fileAdapter.onItemClick = { cloudFile ->
            if (cloudFile.isDirectory) {
                loadDirectory(cloudFile.remotePath)
            }
        }
        fileAdapter.onSelectionChanged = {
            updateActionBar()
        }
        binding.recyclerFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFiles.setHasFixedSize(true)
        binding.recyclerFiles.adapter = fileAdapter
        // §DM4: Disable stagger animation when user prefers reduced motion
        if (com.filecleaner.app.utils.MotionUtil.isReducedMotion(requireContext())) {
            binding.recyclerFiles.layoutAnimation = null
        }

        // Add connection buttons — now opens provider picker
        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnAddFirst.setOnClickListener { showAddDialog() }

        // Disconnect
        binding.btnDisconnect.setOnClickListener { disconnectCurrent() }

        // Remove connection
        binding.btnDeleteConnection.setOnClickListener { removeCurrentConnection() }

        // Test connection button in the connection bar
        binding.btnTestConnection.setOnClickListener { testCurrentConnection() }

        // Connection spinner
        binding.spinnerConnection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos < connections.size) {
                    connectTo(connections[pos])
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Download / Upload
        binding.btnDownload.setOnClickListener { downloadSelected() }
        binding.btnUpload.setOnClickListener { pickFileForUpload() }

        loadConnections()
    }

    private fun loadConnections() {
        connections = CloudConnectionStore.getConnections().toMutableList()
        updateUI()
    }

    private fun updateUI() {
        val b = _binding ?: return
        if (connections.isEmpty()) {
            b.emptyState.visibility = View.VISIBLE
            b.connectionBar.visibility = View.GONE
            b.recyclerFiles.visibility = View.GONE
            b.tvPath.visibility = View.GONE
            b.actionBar.visibility = View.GONE
        } else {
            b.emptyState.visibility = View.GONE
            b.connectionBar.visibility = View.VISIBLE

            val ctx = context ?: return
            val labels = connections.map { conn ->
                val typeLabel = when (conn.type) {
                    ProviderType.GOOGLE_DRIVE -> ctx.getString(R.string.cloud_type_google_drive)
                    ProviderType.SFTP -> ctx.getString(R.string.cloud_type_sftp)
                    ProviderType.WEBDAV -> ctx.getString(R.string.cloud_type_webdav)
                    ProviderType.GITHUB -> ctx.getString(R.string.cloud_type_github)
                }
                "${conn.displayName} ($typeLabel)"
            }
            val adapter = ArrayAdapter(ctx,
                android.R.layout.simple_spinner_dropdown_item, labels)
            b.spinnerConnection.adapter = adapter
        }
        updateConnectionStatus()
    }

    /**
     * Update the connection status indicator dot.
     * Green = provider is connected, Red = disconnected.
     */
    private fun updateConnectionStatus() {
        val b = _binding ?: return
        val connected = currentProvider?.isConnected == true
        isProviderConnected = connected

        if (connected) {
            b.ivConnectionStatus.setImageResource(R.drawable.ic_status_connected)
            b.ivConnectionStatus.contentDescription =
                getString(R.string.a11y_connection_status, getString(R.string.cloud_status_connected))
        } else {
            b.ivConnectionStatus.setImageResource(R.drawable.ic_status_disconnected)
            b.ivConnectionStatus.contentDescription =
                getString(R.string.a11y_connection_status, getString(R.string.cloud_status_disconnected))
        }
    }

    private fun connectTo(connection: CloudConnection) {
        val ctx = context ?: return

        // H4-03: Pre-flight network connectivity check
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!isConnected) {
            _binding?.root?.let { root ->
                Snackbar.make(root,
                    getString(R.string.cloud_no_network),
                    Snackbar.LENGTH_LONG).styleAsError().show()
            }
            updateConnectionStatus()
            return
        }

        val provider = createProvider(connection, ctx)
        currentProvider = provider
        updateConnectionStatus()

        _binding?.progress?.visibility = View.VISIBLE
        _binding?.recyclerFiles?.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = provider.connect()
                _binding?.progress?.visibility = View.GONE
                updateConnectionStatus()

                if (success) {
                    _binding?.root?.let { root ->
                        Snackbar.make(root,
                            getString(R.string.cloud_connected, connection.displayName),
                            Snackbar.LENGTH_SHORT).show()
                    }
                    currentPath = "/"
                    loadDirectory("/")
                } else {
                    _binding?.root?.let { root ->
                        Snackbar.make(root,
                            getString(R.string.cloud_connection_failed, connection.displayName),
                            Snackbar.LENGTH_LONG).styleAsError().show()
                    }
                }
            } catch (e: Exception) {
                // H4-04: Differentiated error messages based on failure type
                _binding?.progress?.visibility = View.GONE
                updateConnectionStatus()
                val message = when {
                    e is SocketTimeoutException ->
                        getString(R.string.cloud_timeout)
                    e is UnknownHostException ->
                        getString(R.string.cloud_host_not_found)
                    e is JSchException && e.message?.contains("Auth", ignoreCase = true) == true ->
                        getString(R.string.cloud_auth_failed)
                    e is java.io.IOException && e.message?.let { msg ->
                        msg.contains("HTTP 401") || msg.contains("HTTP 403")
                    } == true ->
                        getString(R.string.cloud_auth_failed)
                    else ->
                        getString(R.string.cloud_connection_failed, connection.displayName)
                }
                _binding?.root?.let { root ->
                    Snackbar.make(root, message, Snackbar.LENGTH_LONG).styleAsError().show()
                }
            }
        }
    }

    /**
     * Test the currently-selected connection without browsing files.
     * Connects, reports success/failure, then disconnects.
     */
    private fun testCurrentConnection() {
        val b = _binding ?: return
        val idx = b.spinnerConnection.selectedItemPosition
        if (idx < 0 || idx >= connections.size) return
        val connection = connections[idx]
        val ctx = context ?: return

        // Pre-flight network check
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        val hasNet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (!hasNet) {
            Snackbar.make(b.root, getString(R.string.cloud_no_network), Snackbar.LENGTH_LONG).styleAsError().show()
            return
        }

        b.progress.visibility = View.VISIBLE
        Snackbar.make(b.root, getString(R.string.cloud_testing_connection), Snackbar.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val testProvider = createProvider(connection, ctx)
                val success = testProvider.connect()
                // Always disconnect after test
                try { testProvider.disconnect() } catch (_: Exception) {}

                val b2 = _binding ?: return@launch
                b2.progress.visibility = View.GONE

                if (success) {
                    Snackbar.make(b2.root,
                        getString(R.string.cloud_test_success),
                        Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(b2.root,
                        getString(R.string.cloud_test_failed,
                            getString(R.string.cloud_connection_failed, connection.displayName)),
                        Snackbar.LENGTH_LONG).styleAsError().show()
                }
            } catch (e: Exception) {
                val b2 = _binding ?: return@launch
                b2.progress.visibility = View.GONE
                Snackbar.make(b2.root,
                    getString(R.string.cloud_test_failed,
                        e.localizedMessage ?: e.javaClass.simpleName),
                    Snackbar.LENGTH_LONG).styleAsError().show()
            }
        }
    }

    private fun createProvider(connection: CloudConnection, ctx: android.content.Context): CloudProvider {
        return when (connection.type) {
            ProviderType.SFTP -> SftpProvider(connection, ctx)
            ProviderType.WEBDAV -> WebDavProvider(connection)
            ProviderType.GOOGLE_DRIVE -> GoogleDriveProvider(connection, ctx)
            ProviderType.GITHUB -> GitHubProvider(connection, ctx)
        }
    }

    private fun loadDirectory(path: String) {
        val provider = currentProvider ?: return
        currentPath = path

        val b = _binding ?: return
        b.progress.visibility = View.VISIBLE
        b.tvPath.visibility = View.VISIBLE
        b.tvPath.text = path
        b.recyclerFiles.visibility = View.VISIBLE
        b.actionBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val files = try {
                provider.listFiles(path)
            } catch (e: Exception) {
                val b2 = _binding ?: return@launch
                b2.progress.visibility = View.GONE
                Snackbar.make(b2.root,
                    getString(R.string.cloud_list_failed, e.localizedMessage ?: e.javaClass.simpleName),
                    Snackbar.LENGTH_LONG).styleAsError().show()
                return@launch
            }
            val b2 = _binding ?: return@launch
            b2.progress.visibility = View.GONE
            currentFiles = files
            fileAdapter.submitList(files.map { cf ->
                CloudFileAdapter.CloudFileItem(
                    cloudFile = cf,
                    name = cf.name,
                    isDirectory = cf.isDirectory,
                    size = cf.size,
                    lastModified = cf.lastModified
                )
            })

            if (files.isEmpty()) {
                Snackbar.make(b2.root,
                    getString(R.string.cloud_empty_dir), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateUp() {
        val provider = currentProvider ?: return
        if (provider.type == ProviderType.GOOGLE_DRIVE) {
            // Google Drive uses file IDs, go back to root
            loadDirectory("/")
        } else {
            val parent = currentPath.trimEnd('/').substringBeforeLast('/')
            loadDirectory(if (parent.isEmpty()) "/" else parent)
        }
    }

    private fun disconnectCurrent() {
        val provider = currentProvider ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            provider.disconnect()
            currentProvider = null
            val b = _binding ?: return@launch
            b.recyclerFiles.visibility = View.GONE
            b.tvPath.visibility = View.GONE
            b.actionBar.visibility = View.GONE
            updateConnectionStatus()
            Snackbar.make(b.root,
                getString(R.string.cloud_disconnected), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun removeCurrentConnection() {
        val b = _binding ?: return
        val idx = b.spinnerConnection.selectedItemPosition
        if (idx < 0 || idx >= connections.size) return
        val conn = connections[idx]

        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.cloud_remove))
            .setMessage(getString(R.string.cloud_remove_confirm, conn.displayName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    currentProvider?.disconnect()
                    currentProvider = null
                    CloudConnectionStore.removeConnection(conn.id)
                    loadConnections()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun downloadSelected() {
        val selected = fileAdapter.getSelectedItems()
        if (selected.isEmpty()) {
            _binding?.root?.let { root ->
                Snackbar.make(root,
                    getString(R.string.dual_pane_no_selection), Snackbar.LENGTH_SHORT).show()
            }
            return
        }

        val provider = currentProvider ?: return
        _binding?.progress?.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            var success = 0
            var failed = 0
            for (item in selected) {
                if (item.cloudFile.isDirectory) continue
                try {
                    // Sanitize remote filename to prevent path traversal
                    val safeName = item.cloudFile.name
                        .replace("..", "_")
                        .replace("/", "_")
                        .replace("\\", "_")
                        .replace("\u0000", "")
                        .trim()
                        .ifBlank { "download_${System.currentTimeMillis()}" }
                    val targetFile = File(downloadDir, safeName)
                    // Verify the resolved path is still within downloadDir
                    if (!targetFile.canonicalPath.startsWith(File(downloadDir).canonicalPath)) {
                        failed++
                        continue
                    }
                    withContext(Dispatchers.IO) {
                        targetFile.outputStream().use { out ->
                            provider.download(item.cloudFile.remotePath, out)
                        }
                    }
                    success++
                } catch (e: Exception) {
                    failed++
                }
            }
            val b = _binding ?: return@launch
            b.progress.visibility = View.GONE
            Snackbar.make(b.root,
                getString(R.string.cloud_download_result, success, failed),
                Snackbar.LENGTH_LONG).show()
            fileAdapter.clearSelection()
        }
    }

    private fun pickFileForUpload() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        uploadFilePicker.launch(intent)
    }

    private fun uploadFile(uri: android.net.Uri) {
        val provider = currentProvider ?: return
        val ctx = context ?: return
        val contentResolver = ctx.contentResolver
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "uploaded_file"
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        _binding?.progress?.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        provider.upload(currentPath, input, fileName, mimeType)
                    }
                }
                val b = _binding ?: return@launch
                b.progress.visibility = View.GONE
                Snackbar.make(b.root,
                    getString(R.string.cloud_upload_success, fileName),
                    Snackbar.LENGTH_SHORT).show()
                loadDirectory(currentPath) // Refresh
            } catch (e: Exception) {
                val b = _binding ?: return@launch
                b.progress.visibility = View.GONE
                Snackbar.make(b.root,
                    getString(R.string.cloud_upload_failed, e.localizedMessage ?: ""),
                    Snackbar.LENGTH_LONG).styleAsError().show()
            }
        }
    }

    /**
     * Open the two-step add-connection flow:
     * 1. Provider picker dialog (branded service cards)
     * 2. Connection setup dialog (credentials for the chosen provider)
     */
    private fun showAddDialog() {
        val ctx = context ?: return
        CloudProviderPickerDialog.show(ctx) { connection ->
            loadConnections()
            _binding?.root?.let { root ->
                Snackbar.make(root,
                    getString(R.string.cloud_added, connection.displayName),
                    Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateActionBar() {
        val hasSelection = fileAdapter.getSelectedItems().isNotEmpty()
        _binding?.btnDownload?.isEnabled = hasSelection
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PATH, currentPath)
    }

    override fun onDestroyView() {
        // Disconnect cloud provider to prevent leaking network connections
        currentProvider?.let { provider ->
            // D5-06: Use NonCancellable scope — viewLifecycleOwner scope is cancelled during onDestroyView
            CoroutineScope(Dispatchers.IO + NonCancellable).launch {
                try { provider.disconnect() } catch (_: Exception) {}
            }
        }
        _binding?.spinnerConnection?.onItemSelectedListener = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PATH = "cloud_path"
    }
}
