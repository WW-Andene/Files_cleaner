package com.filecleaner.app.ui.dualpane

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.databinding.FragmentDualPaneBinding
import com.filecleaner.app.ui.common.DirectoryPickerDialog
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dual-pane file manager (Total Commander style).
 * Two side-by-side panels, each with independent directory navigation,
 * content mode selection (file browser, category filter, tree view),
 * and long-press multi-selection with floating action bar.
 */
class DualPaneFragment : Fragment() {

    private var _binding: FragmentDualPaneBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    // File list adapters (used for FILE_BROWSER and category modes)
    private lateinit var leftAdapter: PaneAdapter
    private lateinit var rightAdapter: PaneAdapter

    // Tree view adapters (used for TREE_VIEW mode)
    private lateinit var leftTreeAdapter: TreeNodeAdapter
    private lateinit var rightTreeAdapter: TreeNodeAdapter

    private var leftPath: String = ""
    private var rightPath: String = ""

    // Content mode for each pane
    private var leftMode: PaneContentMode = PaneContentMode.FILE_BROWSER
    private var rightMode: PaneContentMode = PaneContentMode.FILE_BROWSER

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
        leftMode = savedInstanceState?.getString(KEY_LEFT_MODE)
            ?.let { PaneContentMode.valueOf(it) } ?: PaneContentMode.FILE_BROWSER
        rightMode = savedInstanceState?.getString(KEY_RIGHT_MODE)
            ?.let { PaneContentMode.valueOf(it) } ?: PaneContentMode.FILE_BROWSER

        // Ensure right pane path exists, fallback to storage root
        if (!File(rightPath).isDirectory) rightPath = storagePath

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // -- Swap panes button --
        binding.btnSwapPanes.setOnClickListener { swapPanes() }

        // -- Setup adapters --
        leftAdapter = PaneAdapter()
        rightAdapter = PaneAdapter()
        leftTreeAdapter = TreeNodeAdapter()
        rightTreeAdapter = TreeNodeAdapter()

        // Setup left pane
        setupPane(
            adapter = leftAdapter,
            treeAdapter = leftTreeAdapter,
            recycler = binding.recyclerLeft,
            pathLabel = binding.tvPathLeft,
            countLabel = binding.tvCountLeft,
            upButton = binding.btnUpLeft,
            pickDirButton = binding.btnPickDirLeft,
            modeButton = binding.btnModeLeft,
            pane = Pane.LEFT
        )

        // Setup right pane
        setupPane(
            adapter = rightAdapter,
            treeAdapter = rightTreeAdapter,
            recycler = binding.recyclerRight,
            pathLabel = binding.tvPathRight,
            countLabel = binding.tvCountRight,
            upButton = binding.btnUpRight,
            pickDirButton = binding.btnPickDirRight,
            modeButton = binding.btnModeRight,
            pane = Pane.RIGHT
        )

        // Active pane visual indicator
        updatePaneHighlight()

        // -- Selection action bar buttons --
        binding.btnCopyToOther.setOnClickListener { performCrossOperation(copy = true) }
        binding.btnMoveToOther.setOnClickListener { performCrossOperation(copy = false) }
        binding.btnDeleteSelected.setOnClickListener { performDelete() }
        binding.btnSelectAll.setOnClickListener {
            val adapter = if (activePane == Pane.LEFT) leftAdapter else rightAdapter
            if (adapter.getSelectedItems().size == adapter.currentList.size) {
                adapter.clearSelection()
            } else {
                adapter.selectAll()
            }
        }
        binding.btnClearSelection.setOnClickListener { clearActiveSelection() }

        // -- Initial load --
        applyContentMode(Pane.LEFT)
        applyContentMode(Pane.RIGHT)

        // -- Observe data from ViewModel --
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

        // Observe directory tree for tree view mode
        vm.directoryTree.observe(viewLifecycleOwner) { tree ->
            if (tree != null) {
                if (leftMode.isTreeView) leftTreeAdapter.setRootNode(tree)
                if (rightMode.isTreeView) rightTreeAdapter.setRootNode(tree)
            }
        }

        // Observe file categories for category modes
        vm.filesByCategory.observe(viewLifecycleOwner) { categories ->
            if (leftMode.isCategoryMode) loadCategoryFiles(Pane.LEFT, leftMode)
            if (rightMode.isCategoryMode) loadCategoryFiles(Pane.RIGHT, rightMode)
        }
    }

    private fun setupPane(
        adapter: PaneAdapter,
        treeAdapter: TreeNodeAdapter,
        recycler: RecyclerView,
        pathLabel: TextView,
        countLabel: TextView,
        upButton: ImageButton,
        pickDirButton: ImageButton,
        modeButton: com.google.android.material.button.MaterialButton,
        pane: Pane
    ) {
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // --- File adapter callbacks ---
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
            updateSelectionBar()
        }

        // --- Tree adapter callbacks ---
        treeAdapter.onDirectorySelected = { path ->
            activePane = pane
            updatePaneHighlight()
            // Switch to file browser mode and navigate to this directory
            if (pane == Pane.LEFT) leftMode = PaneContentMode.FILE_BROWSER
            else rightMode = PaneContentMode.FILE_BROWSER
            updateModeButtonLabel(pane)
            applyContentMode(pane)
            loadDirectory(pane, path)
        }

        // Tap pane container to set active
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

        // Path label click — open directory picker dialog
        pathLabel.setOnClickListener {
            activePane = pane
            updatePaneHighlight()
            showDirectoryPicker(pane)
        }

        // Pick directory button
        pickDirButton.setOnClickListener {
            activePane = pane
            updatePaneHighlight()
            showDirectoryPicker(pane)
        }

        // Content mode selector button
        modeButton.setOnClickListener { anchor ->
            activePane = pane
            showContentModeMenu(anchor, pane)
        }

        // Set initial mode label
        updateModeButtonLabel(pane)
    }

    // ── Content Mode Handling ──

    private fun showContentModeMenu(anchor: View, pane: Pane) {
        val popup = PopupMenu(requireContext(), anchor)
        PaneContentMode.entries.forEachIndexed { index, mode ->
            popup.menu.add(0, index, index, getString(mode.labelRes))
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedMode = PaneContentMode.entries[item.itemId]
            if (pane == Pane.LEFT) leftMode = selectedMode else rightMode = selectedMode
            updateModeButtonLabel(pane)
            applyContentMode(pane)
            true
        }
        popup.show()
    }

    private fun updateModeButtonLabel(pane: Pane) {
        val mode = if (pane == Pane.LEFT) leftMode else rightMode
        val button = if (pane == Pane.LEFT) binding.btnModeLeft else binding.btnModeRight
        button.text = getString(mode.labelRes)
    }

    private fun applyContentMode(pane: Pane) {
        val mode = if (pane == Pane.LEFT) leftMode else rightMode
        val recycler = if (pane == Pane.LEFT) binding.recyclerLeft else binding.recyclerRight
        val adapter = if (pane == Pane.LEFT) leftAdapter else rightAdapter
        val treeAdapter = if (pane == Pane.LEFT) leftTreeAdapter else rightTreeAdapter
        val pathBar = if (pane == Pane.LEFT) binding.pathBarLeft else binding.pathBarRight

        when {
            mode.isFileBrowser -> {
                recycler.adapter = adapter
                pathBar.visibility = View.VISIBLE
                loadDirectory(pane, if (pane == Pane.LEFT) leftPath else rightPath)
            }
            mode.isCategoryMode -> {
                recycler.adapter = adapter
                pathBar.visibility = View.GONE
                loadCategoryFiles(pane, mode)
            }
            mode.isTreeView -> {
                recycler.adapter = treeAdapter
                pathBar.visibility = View.GONE
                val tree = vm.directoryTree.value
                if (tree != null) {
                    treeAdapter.setRootNode(tree)
                    updateCountLabel(pane, "${tree.totalFileCount} files")
                } else {
                    updateCountLabel(pane, getString(R.string.dual_pane_tree_empty))
                }
            }
        }
    }

    // ── Directory Navigation ──

    private fun loadDirectory(pane: Pane, path: String) {
        val dir = File(path)
        if (!dir.isDirectory) return

        viewLifecycleOwner.lifecycleScope.launch {
            val showHidden = try { UserPreferences.showHiddenFiles } catch (_: Exception) { false }

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

            adapter.submitList(files)

            val relative = path.removePrefix(storagePath)
            pathLabel.text = if (relative.isEmpty()) "/" else relative

            val dirCount = files.count { it.isDirectory }
            val fileCount = files.count { !it.isDirectory }
            updateCountLabel(pane, getString(R.string.dual_pane_count, dirCount, fileCount))

            if (pane == Pane.LEFT) leftPath = path else rightPath = path
        }
    }

    private fun loadCategoryFiles(pane: Pane, mode: PaneContentMode) {
        val category = mode.category ?: return
        val categoryFiles = vm.filesByCategory.value?.get(category) ?: emptyList()

        val items = categoryFiles
            .sortedByDescending { it.lastModified }
            .map { fileItem ->
                PaneAdapter.PaneItem(
                    file = File(fileItem.path),
                    isDirectory = false,
                    name = fileItem.name,
                    size = fileItem.size,
                    lastModified = fileItem.lastModified
                )
            }

        val adapter = if (pane == Pane.LEFT) leftAdapter else rightAdapter
        adapter.submitList(items)

        val totalSize = UndoHelper.formatBytes(categoryFiles.sumOf { it.size })
        updateCountLabel(pane, getString(R.string.dual_pane_category_count, items.size, totalSize))
    }

    private fun updateCountLabel(pane: Pane, text: String) {
        val label = if (pane == Pane.LEFT) binding.tvCountLeft else binding.tvCountRight
        label.text = text
    }

    // ── Directory Picker ──

    private fun showDirectoryPicker(pane: Pane) {
        val tree = vm.directoryTree.value
        if (tree != null) {
            DirectoryPickerDialog.show(
                context = requireContext(),
                rootNode = tree,
                onSelected = { selectedPath ->
                    // Switch to file browser mode when a directory is picked
                    if (pane == Pane.LEFT) leftMode = PaneContentMode.FILE_BROWSER
                    else rightMode = PaneContentMode.FILE_BROWSER
                    updateModeButtonLabel(pane)
                    applyContentMode(pane)
                    loadDirectory(pane, selectedPath)
                }
            )
        } else {
            // No tree available — use a simple path input dialog
            showSimplePathDialog(pane)
        }
    }

    private fun showSimplePathDialog(pane: Pane) {
        val currentPath = if (pane == Pane.LEFT) leftPath else rightPath
        val input = android.widget.EditText(requireContext()).apply {
            setText(currentPath)
            setSelectAllOnFocus(true)
            setPadding(48, 32, 48, 16)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dual_pane_pick_directory))
            .setView(input)
            .setPositiveButton(getString(R.string.select_directory)) { _, _ ->
                val path = input.text.toString().trim()
                if (File(path).isDirectory) {
                    loadDirectory(pane, path)
                } else {
                    Snackbar.make(binding.root, "Invalid directory", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── Swap Panes ──

    private fun swapPanes() {
        // Swap paths
        val tempPath = leftPath
        leftPath = rightPath
        rightPath = tempPath

        // Swap modes
        val tempMode = leftMode
        leftMode = rightMode
        rightMode = tempMode

        // Update mode labels
        updateModeButtonLabel(Pane.LEFT)
        updateModeButtonLabel(Pane.RIGHT)

        // Re-apply content modes (which triggers reload)
        applyContentMode(Pane.LEFT)
        applyContentMode(Pane.RIGHT)
    }

    // ── File Operations ──

    private fun showFileContextMenu(item: PaneAdapter.PaneItem, anchor: View, pane: Pane) {
        val fileItem = FileScanner.fileToItem(item.file)
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
            FileScanner.fileToItem(paneItem.file)
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

    // ── Selection Management ──

    private fun getActiveSelection(): List<PaneAdapter.PaneItem> {
        val adapter = if (activePane == Pane.LEFT) leftAdapter else rightAdapter
        return adapter.getSelectedItems()
    }

    private fun clearActiveSelection() {
        val adapter = if (activePane == Pane.LEFT) leftAdapter else rightAdapter
        adapter.clearSelection()
    }

    private fun updateSelectionBar() {
        val leftSelected = leftAdapter.getSelectedItems()
        val rightSelected = rightAdapter.getSelectedItems()
        val totalSelected = leftSelected.size + rightSelected.size

        if (totalSelected > 0) {
            binding.selectionActionBar.visibility = View.VISIBLE
            // Determine which pane has the selection
            if (leftSelected.isNotEmpty()) activePane = Pane.LEFT
            else if (rightSelected.isNotEmpty()) activePane = Pane.RIGHT
            updatePaneHighlight()

            binding.tvSelectionCount.text = getString(R.string.dual_pane_selected, totalSelected)
            binding.btnCopyToOther.isEnabled = true
            binding.btnMoveToOther.isEnabled = true
            binding.btnDeleteSelected.isEnabled = true
        } else {
            binding.selectionActionBar.visibility = View.GONE
        }
    }

    // ── Pane Visual State ──

    private fun refreshBothPanes() {
        applyContentMode(Pane.LEFT)
        applyContentMode(Pane.RIGHT)
    }

    private fun updatePaneHighlight() {
        binding.paneLeft.setBackgroundColor(
            if (activePane == Pane.LEFT)
                ContextCompat.getColor(requireContext(), R.color.surfaceColor)
            else
                ContextCompat.getColor(requireContext(), R.color.surfaceBase)
        )
        binding.paneRight.setBackgroundColor(
            if (activePane == Pane.RIGHT)
                ContextCompat.getColor(requireContext(), R.color.surfaceColor)
            else
                ContextCompat.getColor(requireContext(), R.color.surfaceBase)
        )
    }

    // ── Lifecycle ──

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LEFT_PATH, leftPath)
        outState.putString(KEY_RIGHT_PATH, rightPath)
        outState.putString(KEY_LEFT_MODE, leftMode.name)
        outState.putString(KEY_RIGHT_MODE, rightMode.name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_LEFT_PATH = "dual_pane_left_path"
        private const val KEY_RIGHT_PATH = "dual_pane_right_path"
        private const val KEY_LEFT_MODE = "dual_pane_left_mode"
        private const val KEY_RIGHT_MODE = "dual_pane_right_mode"
    }
}
