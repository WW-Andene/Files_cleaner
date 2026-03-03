package com.filecleaner.app.ui.cloud

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.filecleaner.app.R
import com.filecleaner.app.data.cloud.CloudConnection
import com.filecleaner.app.data.cloud.CloudConnectionStore
import com.filecleaner.app.data.cloud.CloudFile
import com.filecleaner.app.data.cloud.CloudProvider
import com.filecleaner.app.data.cloud.GoogleDriveProvider
import com.filecleaner.app.data.cloud.ProviderType
import com.filecleaner.app.data.cloud.SftpProvider
import com.filecleaner.app.data.cloud.WebDavProvider
import com.filecleaner.app.databinding.FragmentCloudBrowserBinding
import com.filecleaner.app.ui.dualpane.PaneAdapter
import com.filecleaner.app.utils.UndoHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cloud/network file browser fragment.
 * Allows browsing, downloading, and uploading files to cloud providers.
 */
class CloudBrowserFragment : Fragment() {

    private var _binding: FragmentCloudBrowserBinding? = null
    private val binding get() = _binding!!

    private var connections = mutableListOf<CloudConnection>()
    private var currentProvider: CloudProvider? = null
    private var currentPath = "/"
    private var currentFiles = listOf<CloudFile>()

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
        binding.recyclerFiles.adapter = fileAdapter

        // Add connection buttons
        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnAddFirst.setOnClickListener { showAddDialog() }

        // Disconnect
        binding.btnDisconnect.setOnClickListener { disconnectCurrent() }

        // Remove connection
        binding.btnDeleteConnection.setOnClickListener { removeCurrentConnection() }

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
            val labels = connections.map { "${it.displayName} (${it.type.name})" }
            val adapter = ArrayAdapter(ctx,
                android.R.layout.simple_spinner_dropdown_item, labels)
            b.spinnerConnection.adapter = adapter
        }
    }

    private fun connectTo(connection: CloudConnection) {
        val ctx = context ?: return
        val provider = createProvider(connection, ctx)
        currentProvider = provider

        _binding?.progress?.visibility = View.VISIBLE
        _binding?.recyclerFiles?.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val success = provider.connect()
            _binding?.progress?.visibility = View.GONE

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
                        Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createProvider(connection: CloudConnection, ctx: android.content.Context): CloudProvider {
        return when (connection.type) {
            ProviderType.SFTP -> SftpProvider(connection, ctx)
            ProviderType.WEBDAV -> WebDavProvider(connection)
            ProviderType.GOOGLE_DRIVE -> GoogleDriveProvider(connection, ctx)
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
            val files = provider.listFiles(path)
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
        AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.cloud_remove))
            .setMessage(getString(R.string.cloud_remove_confirm, conn.displayName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    currentProvider?.disconnect()
                    currentProvider = null
                }
                CloudConnectionStore.removeConnection(conn.id)
                loadConnections()
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
                    Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddDialog() {
        val ctx = context ?: return
        CloudSetupDialog.show(ctx) { connection ->
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
        binding.spinnerConnection.onItemSelectedListener = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PATH = "cloud_path"
    }
}
