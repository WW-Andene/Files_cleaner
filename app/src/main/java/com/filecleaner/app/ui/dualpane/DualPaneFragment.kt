package com.filecleaner.app.ui.dualpane

import android.content.ClipData
import android.content.ClipDescription
import android.os.Bundle
import android.os.Environment
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.google.android.material.button.MaterialButton
import com.filecleaner.app.utils.styleAsError
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dual-pane file manager (Total Commander style).
 * Two side-by-side panels, each with independent section tabs
 * (Browse, Duplicates, Large Files, Junk, Manager)
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

    // File list adapters (used for all content modes)
    private lateinit var leftAdapter: PaneAdapter
    private lateinit var rightAdapter: PaneAdapter

    private var leftPath: String = ""
    private var rightPath: String = ""

    // Content mode for each pane
    private var leftMode: PaneContentMode = PaneContentMode.FILE_BROWSER
    private var rightMode: PaneContentMode = PaneContentMode.FILE_BROWSER

    // Track which pane is "active" (last interacted with)
    private var activePane = Pane.LEFT

    private enum class Pane { LEFT, RIGHT }

    /** MIME type used for drag-and-drop between panes. */
    private val DRAG_MIME = "application/x-filecleaner-pane-drag"

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

        // Setup left pane
        setupPane(
            adapter = leftAdapter,
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

        // Observe scan data for all scan-result modes
        vm.duplicates.observe(viewLifecycleOwner) {
            if (leftMode == PaneContentMode.DUPLICATES) loadScanResults(Pane.LEFT, leftMode)
            if (rightMode == PaneContentMode.DUPLICATES) loadScanResults(Pane.RIGHT, rightMode)
        }
        vm.largeFiles.observe(viewLifecycleOwner) {
            if (leftMode == PaneContentMode.LARGE_FILES) loadScanResults(Pane.LEFT, leftMode)
            if (rightMode == PaneContentMode.LARGE_FILES) loadScanResults(Pane.RIGHT, rightMode)
        }
        vm.junkFiles.observe(viewLifecycleOwner) {
            if (leftMode == PaneContentMode.JUNK) loadScanResults(Pane.LEFT, leftMode)
            if (rightMode == PaneContentMode.JUNK) loadScanResults(Pane.RIGHT, rightMode)
        }
    }

    /** Tab labels for PaneContentMode entries (short form). */
    private val tabLabels by lazy {
        arrayOf(
            R.string.dual_pane_tab_browse,
            R.string.dual_pane_tab_duplicates,
            R.string.dual_pane_tab_large,
            R.string.dual_pane_tab_junk,
            R.string.dual_pane_tab_manager
        )
    }

    private fun setupPane(
        adapter: PaneAdapter,
        recycler: RecyclerView,
        pathLabel: TextView,
        countLabel: TextView,
        upButton: ImageButton,
        pickDirButton: ImageButton,
        modeButton: MaterialButton,
        pane: Pane
    ) {
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        // §DM4: Disable stagger animation when user prefers reduced motion
        if (com.filecleaner.app.utils.MotionUtil.isReducedMotion(requireContext())) {
            recycler.layoutAnimation = null
        }

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

        // --- Drag initiation callback from adapter ---
        adapter.onDragStartRequested = { item, itemView ->
            activePane = pane
            val items = if (adapter.isSelectionActive) adapter.getSelectedItems() else listOf(item)
            val paths = items.joinToString("\n") { it.file.absolutePath }
            val clipData = ClipData.newPlainText("files", paths)
            val shadow = View.DragShadowBuilder(itemView)
            val localState = DragPayload(pane, items)
            itemView.startDragAndDrop(clipData, shadow, localState, 0)
        }

        // --- Drag listener on the recycler (drop target) ---
        val paneContainer = if (pane == Pane.LEFT) binding.paneLeft else binding.paneRight
        recycler.setOnDragListener(createPaneDragListener(pane, paneContainer))

        // Tap pane container to set active
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

        // --- Setup mode selector popup ---
        updateModeButtonText(pane)
        modeButton.setOnClickListener { showModePopup(modeButton, pane) }
    }

    /** Payload carried by a drag operation between panes. */
    private data class DragPayload(val sourcePane: Pane, val items: List<PaneAdapter.PaneItem>)

    private fun createPaneDragListener(targetPane: Pane, paneContainer: View): View.OnDragListener {
        return View.OnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Accept drags that carry plain-text file paths
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    // Highlight the target pane
                    paneContainer.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.colorPrimaryLight)
                    )
                    true
                }
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DRAG_EXITED -> {
                    // Remove highlight
                    updatePaneHighlight()
                    true
                }
                DragEvent.ACTION_DROP -> {
                    updatePaneHighlight()
                    val payload = event.localState as? DragPayload
                    if (payload != null && payload.sourcePane != targetPane) {
                        showDragCopyMoveDialog(payload.items, targetPane)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    updatePaneHighlight()
                    true
                }
                else -> false
            }
        }
    }

    private fun showDragCopyMoveDialog(items: List<PaneAdapter.PaneItem>, targetPane: Pane) {
        val targetPath = if (targetPane == Pane.LEFT) leftPath else rightPath
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dual_pane_drag_copy_or_move))
            .setMessage(getString(R.string.dual_pane_drag_message, items.size))
            .setPositiveButton(getString(R.string.dual_pane_copy)) { _, _ ->
                for (item in items) {
                    vm.copyFile(item.file.absolutePath, targetPath)
                }
                clearActiveSelection()
            }
            .setNegativeButton(getString(R.string.dual_pane_move)) { _, _ ->
                for (item in items) {
                    vm.moveFile(item.file.absolutePath, targetPath)
                }
                clearActiveSelection()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showModePopup(anchor: View, pane: Pane) {
        val popup = PopupMenu(requireContext(), anchor)
        PaneContentMode.entries.forEachIndexed { index, mode ->
            popup.menu.add(0, index, index, getString(mode.labelRes))
        }
        popup.setOnMenuItemClickListener { item ->
            val selectedMode = PaneContentMode.entries[item.itemId]
            if (pane == Pane.LEFT) leftMode = selectedMode else rightMode = selectedMode
            activePane = pane
            updateModeButtonText(pane)
            applyContentMode(pane)
            true
        }
        popup.show()
    }

    /** Update the mode button text to reflect the current mode. */
    private fun updateModeButtonText(pane: Pane) {
        val mode = if (pane == Pane.LEFT) leftMode else rightMode
        val button = if (pane == Pane.LEFT) binding.btnModeLeft else binding.btnModeRight
        button.text = getString(mode.labelRes)
    }

    /** Sync the mode button text with the current mode (e.g., after swap or tree navigation). */
    private fun syncTabSelection(pane: Pane) {
        updateModeButtonText(pane)
    }

    // ── Content Mode Handling ──

    private fun applyContentMode(pane: Pane) {
        val mode = if (pane == Pane.LEFT) leftMode else rightMode
        val recycler = if (pane == Pane.LEFT) binding.recyclerLeft else binding.recyclerRight
        val adapter = if (pane == Pane.LEFT) leftAdapter else rightAdapter
        val pathBar = if (pane == Pane.LEFT) binding.pathBarLeft else binding.pathBarRight

        when {
            mode.isFileBrowser -> {
                recycler.adapter = adapter
                pathBar.visibility = View.VISIBLE
                loadDirectory(pane, if (pane == Pane.LEFT) leftPath else rightPath)
            }
            mode.isScanResultMode -> {
                recycler.adapter = adapter
                pathBar.visibility = View.GONE
                loadScanResults(pane, mode)
            }
            mode.isManager -> {
                recycler.adapter = adapter
                pathBar.visibility = View.GONE
                loadManagerSummary(pane)
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

    private fun loadScanResults(pane: Pane, mode: PaneContentMode) {
        val items = when (mode) {
            PaneContentMode.DUPLICATES -> vm.duplicates.value ?: emptyList()
            PaneContentMode.LARGE_FILES -> vm.largeFiles.value ?: emptyList()
            PaneContentMode.JUNK -> vm.junkFiles.value ?: emptyList()
            else -> emptyList()
        }

        val paneItems = items
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
        adapter.submitList(paneItems)

        val totalSize = UndoHelper.formatBytes(items.sumOf { it.size })
        updateCountLabel(pane, getString(R.string.dual_pane_category_count, paneItems.size, totalSize))
    }

    private fun loadManagerSummary(pane: Pane) {
        // Show a simple summary of all scan results
        val adapter = if (pane == Pane.LEFT) leftAdapter else rightAdapter
        adapter.submitList(emptyList())

        val dupCount = vm.duplicates.value?.size ?: 0
        val largeCount = vm.largeFiles.value?.size ?: 0
        val junkCount = vm.junkFiles.value?.size ?: 0
        updateCountLabel(pane, "Duplicates: $dupCount \u2022 Large: $largeCount \u2022 Junk: $junkCount")
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
                    syncTabSelection(pane)
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
        val px48 = resources.getDimensionPixelSize(R.dimen.spacing_4xl)
        val px32 = resources.getDimensionPixelSize(R.dimen.spacing_3xl)
        val px16 = resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val input = android.widget.EditText(requireContext()).apply {
            setText(currentPath)
            setSelectAllOnFocus(true)
            setPadding(px48, px32, px48, px16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dual_pane_pick_directory))
            .setView(input)
            .setPositiveButton(getString(R.string.select_directory)) { _, _ ->
                val path = input.text.toString().trim()
                if (File(path).isDirectory) {
                    loadDirectory(pane, path)
                } else {
                    Snackbar.make(binding.root, getString(R.string.dual_pane_invalid_dir), Snackbar.LENGTH_SHORT).styleAsError().show()
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

        // Sync tab selections to new modes
        syncTabSelection(Pane.LEFT)
        syncTabSelection(Pane.RIGHT)

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

        MaterialAlertDialogBuilder(requireContext())
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

        MaterialAlertDialogBuilder(requireContext())
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
