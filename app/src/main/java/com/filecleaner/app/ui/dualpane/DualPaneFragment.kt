package com.filecleaner.app.ui.dualpane

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.databinding.FragmentDualPaneBinding
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dual-pane file manager (Total Commander style).
 * Two side-by-side panels, each with independent directory navigation.
 * Bottom bar provides cross-pane copy, move, and delete operations.
 */
class DualPaneFragment : Fragment() {

    private var _binding: FragmentDualPaneBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    private lateinit var leftAdapter: PaneAdapter
    private lateinit var rightAdapter: PaneAdapter

    private var leftPath: String = ""
    private var rightPath: String = ""

    // Track which pane is "active" (last interacted with)
    private var activePane = Pane.LEFT

    private enum class Pane { LEFT, RIGHT }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDualPaneBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        leftPath = savedInstanceState?.getString(KEY_LEFT_PATH) ?: storagePath
        rightPath = savedInstanceState?.getString(KEY_RIGHT_PATH) ?: "$storagePath/Download"

        // Ensure right pane path exists, fallback to storage root
        if (!File(rightPath).isDirectory) rightPath = storagePath

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // Setup left pane
        leftAdapter = PaneAdapter()
        setupPane(
            adapter = leftAdapter,
            recycler = binding.recyclerLeft,
            pathLabel = binding.tvPathLeft,
            countLabel = binding.tvCountLeft,
            upButton = binding.btnUpLeft,
            pane = Pane.LEFT
        )

        // Setup right pane
        rightAdapter = PaneAdapter()
        setupPane(
            adapter = rightAdapter,
            recycler = binding.recyclerRight,
            pathLabel = binding.tvPathRight,
            countLabel = binding.tvCountRight,
            upButton = binding.btnUpRight,
            pane = Pane.RIGHT
        )

        // Active pane visual indicator
        updatePaneHighlight()

        // Bottom action buttons
        binding.btnCopyToOther.setOnClickListener { performCrossOperation(copy = true) }
        binding.btnMoveToOther.setOnClickListener { performCrossOperation(copy = false) }
        binding.btnDeleteSelected.setOnClickListener { performDelete() }

        // Initial load
        loadDirectory(Pane.LEFT, leftPath)
        loadDirectory(Pane.RIGHT, rightPath)

        // Observe operation results
        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
            refreshBothPanes()
        }
        vm.moveResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
            refreshBothPanes()
        }
        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
            refreshBothPanes()
        }
    }

    private fun setupPane(
        adapter: PaneAdapter,
        recycler: RecyclerView,
        pathLabel: TextView,
        countLabel: TextView,
        upButton: ImageButton,
        pane: Pane
    ) {
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        adapter.onItemClick = { item ->
            activePane = pane
            updatePaneHighlight()
            if (item.isDirectory) {
                loadDirectory(pane, item.file.absolutePath)
            } else {
                FileOpener.open(requireContext(), item.file)
            }
        }

        adapter.onItemLongClick = { item, anchor ->
            activePane = pane
            updatePaneHighlight()
            if (!item.isDirectory) {
                showFileContextMenu(item, anchor, pane)
            }
        }

        adapter.onSelectionChanged = {
            updateActionButtons()
        }

        // Tap anywhere on the pane container to set active
        val paneContainer = if (pane == Pane.LEFT) binding.paneLeft else binding.paneRight
        paneContainer.setOnClickListener {
            activePane = pane
            updatePaneHighlight()
        }

        // Navigate up
        upButton.setOnClickListener {
            activePane = pane
            val currentPath = if (pane == Pane.LEFT) leftPath else rightPath
            val parent = File(currentPath).parent
            if (parent != null && currentPath != storagePath) {
                loadDirectory(pane, parent)
            }
        }
    }

    private fun loadDirectory(pane: Pane, path: String) {
        val dir = File(path)
        if (!dir.isDirectory) return

        viewLifecycleOwner.lifecycleScope.launch {
            val showHidden = try { UserPreferences.showHiddenFiles } catch (_: Exception) { false }

            // D5-05: Move file I/O off the main thread
            val files = withContext(Dispatchers.IO) {
                (dir.listFiles() ?: emptyArray())
                    .filter { showHidden || !it.isHidden }
                    .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    .map { file ->
                        PaneAdapter.PaneItem(
                            file = file,
                            isDirectory = file.isDirectory,
                            name = file.name,
                            size = if (file.isFile) file.length() else 0L,
                            lastModified = file.lastModified()
                        )
                    }
            }

            val adapter = if (pane == Pane.LEFT) leftAdapter else rightAdapter
            val pathLabel = if (pane == Pane.LEFT) binding.tvPathLeft else binding.tvPathRight
            val countLabel = if (pane == Pane.LEFT) binding.tvCountLeft else binding.tvCountRight

            adapter.submitList(files)

            val relative = path.removePrefix(storagePath)
            pathLabel.text = if (relative.isEmpty()) "/" else relative

            val dirCount = files.count { it.isDirectory }
            val fileCount = files.count { !it.isDirectory }
            countLabel.text = getString(R.string.dual_pane_count, dirCount, fileCount)

            if (pane == Pane.LEFT) leftPath = path else rightPath = path
        }
    }

    private fun showFileContextMenu(item: PaneAdapter.PaneItem, anchor: View, pane: Pane) {
        // Convert to FileItem for context menu compatibility
        val fileItem = com.filecleaner.app.utils.FileScanner.fileToItem(item.file)
        val callback = FileContextMenu.defaultCallback(vm,
            onMoveTo = { fi ->
                val targetDir = if (pane == Pane.LEFT) rightPath else leftPath
                vm.moveFile(fi.path, targetDir)
            },
            onRefresh = ::refreshBothPanes
        )
        FileContextMenu.show(requireContext(), anchor, fileItem, callback,
            hasClipboard = vm.clipboardEntry.value != null)
    }

    private fun performCrossOperation(copy: Boolean) {
        val selected = getActiveSelection()
        if (selected.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.dual_pane_no_selection), Snackbar.LENGTH_SHORT).show()
            return
        }

        val targetPath = if (activePane == Pane.LEFT) rightPath else leftPath
        val opName = if (copy) getString(R.string.dual_pane_copy) else getString(R.string.dual_pane_move)

        AlertDialog.Builder(requireContext())
            .setTitle(opName)
            .setMessage(resources.getQuantityString(
                R.plurals.dual_pane_confirm_op, selected.size,
                selected.size, opName.lowercase(), File(targetPath).name
            ))
            .setPositiveButton(opName) { _, _ ->
                for (item in selected) {
                    if (copy) {
                        vm.copyFile(item.file.absolutePath, targetPath)
                    } else {
                        vm.moveFile(item.file.absolutePath, targetPath)
                    }
                }
                clearActiveSelection()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performDelete() {
        val selected = getActiveSelection()
        if (selected.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.dual_pane_no_selection), Snackbar.LENGTH_SHORT).show()
            return
        }

        val fileItems = selected.filter { !it.isDirectory }.map { paneItem ->
            com.filecleaner.app.utils.FileScanner.fileToItem(paneItem.file)
        }
        if (fileItems.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.dual_pane_no_files_to_delete), Snackbar.LENGTH_SHORT).show()
            return
        }

        val totalSize = UndoHelper.totalSize(fileItems)
        val undoSec = try { UserPreferences.undoTimeoutMs / 1000 } catch (_: Exception) { 8 }
        val detail = resources.getQuantityString(
            R.plurals.confirm_delete_detail, fileItems.size, fileItems.size, totalSize, undoSec
        )

        AlertDialog.Builder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.delete_n_files_title, fileItems.size, fileItems.size))
            .setMessage(detail)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                vm.deleteFiles(fileItems)
                clearActiveSelection()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun getActiveSelection(): List<PaneAdapter.PaneItem> {
        val adapter = if (activePane == Pane.LEFT) leftAdapter else rightAdapter
        return adapter.getSelectedItems()
    }

    private fun clearActiveSelection() {
        val adapter = if (activePane == Pane.LEFT) leftAdapter else rightAdapter
        adapter.clearSelection()
    }

    private fun refreshBothPanes() {
        loadDirectory(Pane.LEFT, leftPath)
        loadDirectory(Pane.RIGHT, rightPath)
    }

    private fun updatePaneHighlight() {
        val activeBorder = R.color.colorPrimary
        val inactiveBorder = android.R.color.transparent

        binding.paneLeft.setBackgroundColor(
            if (activePane == Pane.LEFT)
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surfaceColor)
            else
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surfaceBase)
        )
        binding.paneRight.setBackgroundColor(
            if (activePane == Pane.RIGHT)
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surfaceColor)
            else
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.surfaceBase)
        )
    }

    private fun updateActionButtons() {
        val hasSelection = getActiveSelection().isNotEmpty()
        binding.btnCopyToOther.isEnabled = hasSelection
        binding.btnMoveToOther.isEnabled = hasSelection
        binding.btnDeleteSelected.isEnabled = hasSelection
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LEFT_PATH, leftPath)
        outState.putString(KEY_RIGHT_PATH, rightPath)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_LEFT_PATH = "dual_pane_left_path"
        private const val KEY_RIGHT_PATH = "dual_pane_right_path"
    }
}
