package com.filecleaner.app.ui.browse

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.MainActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentBrowseBinding
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.ui.adapters.BrowseAdapter
import com.filecleaner.app.ui.adapters.ViewMode
import com.filecleaner.app.ui.common.BaseFileListFragment
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.ui.common.FileListDividerDecoration
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.ui.common.BatchRenameDialog
import com.filecleaner.app.ui.common.CompressDialog
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.utils.SearchQueryParser
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.io.File

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: BrowseAdapter

    private var currentViewMode = ViewMode.LIST
    private val selectedExtensions = mutableSetOf<String>()
    private var searchQuery = ""
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var shouldScrollToTop = false
    private var dividerDecoration: FileListDividerDecoration? = null

    // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    // Virtual categories — sentinels, handled in refresh()
    private val VIRTUAL_RECENT = "RECENT"
    private val VIRTUAL_FAVORITES = "FAVORITES"

    private val categories by lazy {
        listOf(
            getString(R.string.all_files) to null,
            getString(R.string.cat_recent) to VIRTUAL_RECENT,
            getString(R.string.cat_favorites) to VIRTUAL_FAVORITES,
            *FileCategory.entries.map { "${it.emoji} ${getString(it.displayNameRes)}" to it as Any }.toTypedArray()
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentBrowseBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // B5: Restore all UI state from config change
        savedInstanceState?.let { state ->
            state.getInt(KEY_VIEW_MODE, -1).let { ordinal ->
                if (ordinal in ViewMode.entries.indices) currentViewMode = ViewMode.entries[ordinal]
            }
            state.getString(KEY_SEARCH_QUERY)?.let { searchQuery = it }
            state.getStringArrayList(KEY_EXTENSIONS)?.let { selectedExtensions.addAll(it) }
            state.getStringArrayList(KEY_COLLAPSED_FOLDERS)?.let { restoredCollapsedFolders.addAll(it) }
        }

        // RecyclerView with BrowseAdapter (supports folder headers)
        adapter = BrowseAdapter()
        adapter.viewMode = currentViewMode
        adapter.onItemClick = { item -> FileOpener.openInViewer(requireContext(), item.file) }
        adapter.onItemLongClick = { item, anchor ->
            FileContextMenu.show(requireContext(), anchor, item, contextMenuCallback,
                hasClipboard = vm.clipboardEntry.value != null)
        }
        adapter.onHeaderClick = { folderPath ->
            adapter.toggleFolder(folderPath)
            updateExpandCollapseButton()
        }
        // Selection mode callbacks
        adapter.onSelectionChanged = { selected ->
            updateSelectionBar(selected)
        }

        // Selection bar buttons
        binding.btnSelectAllFiles.setOnClickListener { adapter.selectAllFiles() }
        binding.btnSelectAllFolders.setOnClickListener { adapter.selectAllFolders() }
        binding.btnSelectAllBrowse.setOnClickListener { adapter.selectAll() }
        binding.btnDeselectAllBrowse.setOnClickListener { adapter.deselectAll() }
        binding.btnDeleteSelected.setOnClickListener {
            val items = adapter.getSelectedItems()
            if (items.isNotEmpty()) {
                val totalSize = UndoHelper.totalSize(items)
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(resources.getQuantityString(R.plurals.delete_n_files_title, items.size, items.size))
                    .setMessage(getString(R.string.confirm_delete_message, 8))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        vm.deleteFiles(items)
                        adapter.deselectAll()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        binding.btnCompressSelected.setOnClickListener {
            val items = adapter.getSelectedItems()
            if (items.isNotEmpty()) {
                CompressDialog.show(requireContext(), items) { archiveName, paths ->
                    vm.compressFiles(paths, archiveName)
                    adapter.deselectAll()
                }
            }
        }
        binding.btnRenameSelected.setOnClickListener {
            val items = adapter.getSelectedItems()
            if (items.size >= 2) {
                BatchRenameDialog.show(requireContext(), items) { renames ->
                    vm.batchRename(renames)
                    adapter.deselectAll()
                }
            }
        }

        // Restore collapsed folders from config change
        if (restoredCollapsedFolders.isNotEmpty()) {
            adapter.collapsedFolders.addAll(restoredCollapsedFolders)
            restoredCollapsedFolders.clear()
        }
        dividerDecoration = FileListDividerDecoration(requireContext()) { position ->
            adapter.isHeader(position)
        }
        applyLayoutManager()
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
        // Disable stagger animation when user prefers reduced motion (§G4)
        if (MotionUtil.isReducedMotion(requireContext())) {
            binding.recyclerView.layoutAnimation = null
        }

        // Collapsible filter panel toggle
        binding.btnToggleFilters.setOnClickListener { toggleFilterPanel() }

        // Expand All / Collapse All toggle button
        binding.btnToggleExpandCollapse.setOnClickListener { toggleAllFolders() }
        updateExpandCollapseButton()

        // View mode toggle
        binding.btnViewMode.setOnClickListener { cycleViewMode() }
        updateViewModeIcon()

        // Grid columns chips (inside filter panel)
        setupGridColumnChips()

        // Display options panel
        binding.btnToggleDisplay.setOnClickListener { toggleDisplayOptionsPanel() }
        setupDisplayModeChips()
        setupDisplayGridColumnChips()

        // Swipe drag-to-select touch listener
        setupDragToSelect()

        // Empty state "Scan Now" button
        binding.btnScanNow.setOnClickListener {
            (activity as? MainActivity)?.requestPermissionsAndScan()
        }

        // Search with 300ms debounce
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                val query = s?.toString()?.trim() ?: ""
                searchRunnable = Runnable {
                    if (_binding == null) return@Runnable  // Fragment view destroyed
                    searchQuery = query
                    shouldScrollToTop = true
                    refresh()
                }
                handler.postDelayed(searchRunnable!!, BaseFileListFragment.SEARCH_DEBOUNCE_MS)
            }
        })

        // Category spinner
        val labels = categories.map { it.first }
        val spinnerAdapter = ArrayAdapter(requireContext(),
            R.layout.item_spinner, labels)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spinnerCategory.adapter = spinnerAdapter

        // Sort spinner
        val sortOptions = listOf(
            getString(R.string.sort_name_asc), getString(R.string.sort_name_desc),
            getString(R.string.sort_size_asc), getString(R.string.sort_size_desc),
            getString(R.string.sort_date_asc), getString(R.string.sort_date_desc)
        )
        val sortAdapter = ArrayAdapter(requireContext(),
            R.layout.item_spinner, sortOptions)
        sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spinnerSort.adapter = sortAdapter

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedExtensions.clear()
                shouldScrollToTop = true
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // B5: Restore spinner positions and search text after setup
        savedInstanceState?.let { state ->
            binding.spinnerSort.setSelection(state.getInt(KEY_SORT_ORDER, 0))
            binding.spinnerCategory.setSelection(state.getInt(KEY_CATEGORY_POS, 0))
            if (searchQuery.isNotEmpty()) binding.etSearch.setText(searchQuery)
        }

        vm.filesByCategory.observe(viewLifecycleOwner) { refresh() }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }

        // Observe "Browse folder" navigation from other tabs (e.g. arborescence)
        vm.navigateToBrowse.observe(viewLifecycleOwner) { folderPath ->
            if (folderPath != null) {
                // Reset category filter to "All files" and set search to folder path
                binding.spinnerCategory.setSelection(0, false)
                selectedExtensions.clear()
                val folderName = File(folderPath).name
                searchQuery = folderName
                binding.etSearch.setText(folderName)
                refresh()
                val displayName = folderDisplayName(folderPath)
                Snackbar.make(binding.root, getString(R.string.browsing_folder, displayName),
                    Snackbar.LENGTH_SHORT).show()
                vm.clearBrowseNavigation()
            }
        }
    }

    private fun updateSelectionBar(selected: List<FileItem>) {
        val bar = binding.selectionActionBar
        if (selected.isNotEmpty()) {
            bar.visibility = View.VISIBLE
            binding.tvBrowseSelectionCount.text = getString(R.string.browse_selected, selected.size)
            binding.btnRenameSelected.visibility = if (selected.size >= 2) View.VISIBLE else View.GONE
        } else {
            bar.visibility = View.GONE
        }
    }

    private var filtersExpanded = false
    private val restoredCollapsedFolders = mutableSetOf<String>()

    /** Toggle all folders between expanded and collapsed. */
    private fun toggleAllFolders() {
        if (adapter.hasExpandedFolders()) {
            adapter.collapseAll()
        } else {
            adapter.expandAll()
        }
        updateExpandCollapseButton()
    }

    /** Updates the Expand All / Collapse All button text and icon to reflect current state. */
    private fun updateExpandCollapseButton() {
        val binding = _binding ?: return
        val allExpanded = adapter.hasExpandedFolders()
        if (allExpanded) {
            binding.btnToggleExpandCollapse.text = getString(R.string.collapse_all)
            binding.btnToggleExpandCollapse.setIconResource(R.drawable.ic_chevron_up)
        } else {
            binding.btnToggleExpandCollapse.text = getString(R.string.expand_all)
            binding.btnToggleExpandCollapse.setIconResource(R.drawable.ic_arrow_down)
        }
    }

    private fun toggleFilterPanel() {
        filtersExpanded = !filtersExpanded
        binding.filterPanel.visibility = if (filtersExpanded) View.VISIBLE else View.GONE
        binding.btnToggleFilters.setIconResource(
            if (filtersExpanded) R.drawable.ic_chevron_up else R.drawable.ic_arrow_down
        )
    }

    private fun cycleViewMode() {
        val modes = ViewMode.entries
        val nextIndex = (modes.indexOf(currentViewMode) + 1) % modes.size
        currentViewMode = modes[nextIndex]
        adapter.viewMode = currentViewMode
        applyLayoutManager()
        updateViewModeIcon()
    }

    private fun applyLayoutManager() {
        dividerDecoration?.let { binding.recyclerView.removeItemDecoration(it) }
        val isGridMode = currentViewMode in setOf(ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE)
        binding.recyclerView.layoutManager = if (isGridMode && currentViewMode.spanCount > 1) {
            val spanCount = currentViewMode.spanCount
            GridLayoutManager(requireContext(), spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter.isHeader(position)) spanCount else 1
                    }
                }
            }
        } else {
            // List modes and GRID_XLARGE (single-column grid cards)
            LinearLayoutManager(requireContext())
        }
        if (!isGridMode) {
            dividerDecoration?.let { binding.recyclerView.addItemDecoration(it) }
        }
    }

    private fun updateViewModeIcon() {
        val iconRes = when (currentViewMode) {
            ViewMode.LIST_COMPACT, ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> R.drawable.ic_view_list
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE -> R.drawable.ic_view_grid
        }
        (binding.btnViewMode as? com.google.android.material.button.MaterialButton)?.setIconResource(iconRes)
        updateGridColumnsVisibility()
    }

    private var suppressGridChipListener = false

    private fun setupGridColumnChips() {
        val chipGroup = binding.chipGroupGridColumns
        val gridModes = listOf(
            "1" to ViewMode.GRID_XLARGE,
            "2" to ViewMode.GRID_LARGE,
            "3" to ViewMode.GRID_MEDIUM,
            "4" to ViewMode.GRID_SMALL
        )
        for ((label, mode) in gridModes) {
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = currentViewMode == mode
                tag = mode
            }
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (suppressGridChipListener) return@setOnCheckedStateChangeListener
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val mode = selectedChip?.tag as? ViewMode ?: return@setOnCheckedStateChangeListener
                if (mode != currentViewMode) {
                    currentViewMode = mode
                    adapter.viewMode = currentViewMode
                    applyLayoutManager()
                    updateViewModeIcon()
                }
            }
        }
        updateGridColumnsVisibility()
    }

    private fun updateGridColumnsVisibility() {
        val isGrid = currentViewMode in setOf(ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE)
        binding.gridColumnsRow.visibility = if (isGrid) View.VISIBLE else View.GONE
        // Sync chip selection to current mode
        if (isGrid) {
            suppressGridChipListener = true
            val chipGroup = binding.chipGroupGridColumns
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                chip.isChecked = chip.tag == currentViewMode
            }
            suppressGridChipListener = false
        }
    }

    // ── Display Options Panel ────────────────────────────────────────────

    private var displayOptionsExpanded = false
    private var suppressDisplayModeChipListener = false
    private var suppressDisplayGridChipListener = false

    private fun toggleDisplayOptionsPanel() {
        displayOptionsExpanded = !displayOptionsExpanded
        binding.displayOptionsPanel.visibility = if (displayOptionsExpanded) View.VISIBLE else View.GONE
        binding.btnToggleDisplay.setIconResource(
            if (displayOptionsExpanded) R.drawable.ic_chevron_up else R.drawable.ic_arrow_down
        )
        if (displayOptionsExpanded) {
            updateDisplayGridColumnsVisibility()
            syncDisplayModeChips()
        }
    }

    private fun setupDisplayModeChips() {
        val chipGroup = binding.chipGroupDisplayMode
        val modes = listOf(
            getString(R.string.display_mode_list) to ViewMode.LIST,
            getString(R.string.display_mode_compact) to ViewMode.LIST_COMPACT,
            getString(R.string.display_mode_thumbnails) to ViewMode.LIST_WITH_THUMBNAILS,
            getString(R.string.display_mode_grid) to ViewMode.GRID_MEDIUM
        )
        for ((label, mode) in modes) {
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = when (mode) {
                    ViewMode.GRID_MEDIUM -> currentViewMode in setOf(ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE)
                    else -> currentViewMode == mode
                }
                tag = mode
            }
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (suppressDisplayModeChipListener) return@setOnCheckedStateChangeListener
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val mode = selectedChip?.tag as? ViewMode ?: return@setOnCheckedStateChangeListener
                if (mode != currentViewMode) {
                    currentViewMode = mode
                    adapter.viewMode = currentViewMode
                    applyLayoutManager()
                    updateViewModeIcon()
                    updateDisplayGridColumnsVisibility()
                }
            }
        }
    }

    private fun syncDisplayModeChips() {
        suppressDisplayModeChipListener = true
        val chipGroup = binding.chipGroupDisplayMode
        val isGridMode = currentViewMode in setOf(ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE)
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            val chipMode = chip.tag as? ViewMode ?: continue
            chip.isChecked = when (chipMode) {
                ViewMode.GRID_MEDIUM -> isGridMode
                else -> currentViewMode == chipMode
            }
        }
        suppressDisplayModeChipListener = false
    }

    private fun setupDisplayGridColumnChips() {
        val chipGroup = binding.chipGroupDisplayGridColumns
        val gridModes = listOf(
            "1" to ViewMode.GRID_XLARGE,
            "2" to ViewMode.GRID_LARGE,
            "3" to ViewMode.GRID_MEDIUM,
            "4" to ViewMode.GRID_SMALL
        )
        for ((label, mode) in gridModes) {
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = currentViewMode == mode
                tag = mode
            }
            chipGroup.addView(chip)
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (suppressDisplayGridChipListener) return@setOnCheckedStateChangeListener
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds.first())
                val mode = selectedChip?.tag as? ViewMode ?: return@setOnCheckedStateChangeListener
                if (mode != currentViewMode) {
                    currentViewMode = mode
                    adapter.viewMode = currentViewMode
                    applyLayoutManager()
                    updateViewModeIcon()
                    syncDisplayModeChips()
                }
            }
        }
        updateDisplayGridColumnsVisibility()
    }

    private fun updateDisplayGridColumnsVisibility() {
        val isGrid = currentViewMode in setOf(ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE, ViewMode.GRID_XLARGE)
        binding.displayGridColumnsRow.visibility = if (isGrid) View.VISIBLE else View.GONE
        if (isGrid) {
            suppressDisplayGridChipListener = true
            val chipGroup = binding.chipGroupDisplayGridColumns
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                chip.isChecked = chip.tag == currentViewMode
            }
            suppressDisplayGridChipListener = false
        }
    }

    // ── Drag-to-select (swipe finger multi-selection) ───────────────────

    private fun setupDragToSelect() {
        var isDragging = false
        var lastSelectedPosition = RecyclerView.NO_POSITION

        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                // Only intercept MOVE events when in selection mode and dragging
                if (e.actionMasked == MotionEvent.ACTION_MOVE && adapter.selectionMode && isDragging) {
                    return true
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        if (adapter.selectionMode && isDragging) {
                            val child = rv.findChildViewUnder(e.x, e.y)
                            if (child != null) {
                                val position = rv.getChildAdapterPosition(child)
                                if (position != RecyclerView.NO_POSITION && position != lastSelectedPosition) {
                                    adapter.selectAtPosition(position)
                                    lastSelectedPosition = position
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        lastSelectedPosition = RecyclerView.NO_POSITION
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        // Use a secondary touch listener to detect when the user starts dragging
        // while in selection mode (hold + move triggers multi-select)
        binding.recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            private var startX = 0f
            private var startY = 0f
            private val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        isDragging = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (adapter.selectionMode && !isDragging) {
                            val dx = e.x - startX
                            val dy = e.y - startY
                            if (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop) {
                                isDragging = true
                                // Select the item at the start position if not already selected
                                val child = rv.findChildViewUnder(startX, startY)
                                if (child != null) {
                                    val pos = rv.getChildAdapterPosition(child)
                                    if (pos != RecyclerView.NO_POSITION) {
                                        adapter.selectAtPosition(pos)
                                        lastSelectedPosition = pos
                                    }
                                }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        lastSelectedPosition = RecyclerView.NO_POSITION
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun refresh() {
        val catPos = binding.spinnerCategory.selectedItemPosition
        if (catPos < 0 || catPos >= categories.size) return
        val catEntry = categories[catPos]
        val selectedCat = catEntry.second

        val allFiles = vm.filesByCategory.value?.values?.flatten() ?: emptyList()
        val raw: List<FileItem> = when {
            selectedCat == null -> allFiles
            selectedCat == VIRTUAL_RECENT -> {
                val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
                allFiles.filter { it.lastModified >= cutoff }.sortedByDescending { it.lastModified }
            }
            selectedCat == VIRTUAL_FAVORITES -> {
                val favPaths = try { UserPreferences.favoritePaths } catch (_: Exception) { emptySet() }
                allFiles.filter { it.path in favPaths }
            }
            selectedCat is FileCategory -> vm.filesByCategory.value?.get(selectedCat) ?: emptyList()
            else -> allFiles
        }

        // Apply search filter (with operator support for advanced queries)
        val searched = SearchQueryParser.filterItems(raw, searchQuery)

        // Build extension chips from searched file set
        updateExtensionChips(searched)

        // Apply extension filter
        val filtered = if (selectedExtensions.isEmpty()) {
            searched
        } else {
            searched.filter { it.extension in selectedExtensions }
        }

        val sortPos = binding.spinnerSort.selectedItemPosition.coerceAtLeast(0)
        val sorted = SearchQueryParser.sortItems(filtered, sortPos)

        // Group files by parent folder and build list with section headers
        val browseItems = buildGroupedList(sorted)
        val fileCount = browseItems.count { it is BrowseAdapter.Item.File }

        // Toggle empty/list visibility BEFORE submitting items so the RecyclerView
        // measures at its correct height before items are laid out (fixes items
        // appearing too low on first scan).
        if (fileCount == 0) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            val scanState = vm.scanState.value
            val isPreScan = scanState !is ScanState.Done
            val isScanning = scanState is ScanState.Scanning
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(R.string.empty_search_results, searchQuery)
                isScanning -> getString(R.string.scanning_in_progress)
                !isPreScan -> getString(R.string.empty_browse_post_scan)
                else -> getString(R.string.empty_browse_pre_scan)
            }
            binding.btnScanNow.visibility = if (isPreScan && !isScanning && searchQuery.isEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }

        adapter.submitFullList(browseItems) {
            if (shouldScrollToTop) {
                _binding?.recyclerView?.scrollToPosition(0)
                shouldScrollToTop = false
            }
        }
        binding.tvCount.text = resources.getQuantityString(R.plurals.n_files, fileCount, fileCount)
        updateExpandCollapseButton()
    }

    /** Groups files by their parent folder and creates a list with folder headers. */
    private fun buildGroupedList(files: List<FileItem>): List<BrowseAdapter.Item> {
        if (files.isEmpty()) return emptyList()

        // Group by parent directory
        val grouped = files.groupBy { File(it.path).parent ?: "" }

        // Sort groups: root-level first (matching arborescence root), then by path
        val sortedGroups = grouped.entries.sortedWith(
            compareBy(
                { it.key.removePrefix(storagePath).count { c -> c == File.separatorChar } },
                { it.key.lowercase() }
            )
        )

        val result = mutableListOf<BrowseAdapter.Item>()
        for ((folderPath, folderFiles) in sortedGroups) {
            // Create a readable folder display name
            val displayName = folderDisplayName(folderPath)
            val totalSize = folderFiles.sumOf { it.size }
            result.add(BrowseAdapter.Item.Header(folderPath, displayName, folderFiles.size, totalSize))
            for (file in folderFiles) {
                result.add(BrowseAdapter.Item.File(file))
            }
        }
        return result
    }

    /** Converts a full folder path to a user-friendly display name relative to storage. */
    private fun folderDisplayName(folderPath: String): String {
        if (folderPath.isEmpty()) return "/"
        // Show path relative to external storage for readability
        val relative = if (folderPath.startsWith(storagePath)) {
            folderPath.removePrefix(storagePath)
        } else {
            folderPath
        }
        return if (relative.isEmpty()) getString(R.string.storage_root) else relative
    }

    private fun updateExtensionChips(files: List<FileItem>) {
        val chipGroup = binding.chipGroupExtensions

        // Count extensions
        val extCounts = mutableMapOf<String, Int>()
        for (file in files) {
            val ext = file.extension
            if (ext.isNotEmpty()) {
                extCounts[ext] = (extCounts[ext] ?: 0) + 1
            }
        }

        // Show top 15 extensions sorted by count
        val topExtensions = extCounts.entries
            .sortedByDescending { it.value }
            .take(15)

        if (topExtensions.isEmpty()) {
            binding.scrollExtensions.visibility = View.GONE
            return
        }

        binding.scrollExtensions.visibility = View.VISIBLE
        chipGroup.removeAllViews()

        for ((ext, count) in topExtensions) {
            val chip = Chip(requireContext()).apply {
                text = getString(R.string.extension_chip_format, ext, count)
                isCheckable = true
                isChecked = ext in selectedExtensions
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedExtensions.add(ext) else selectedExtensions.remove(ext)
                    refresh()
                }
            }
            chipGroup.addView(chip)
        }
    }

    private val contextMenuCallback by lazy {
        FileContextMenu.defaultCallback(vm,
            onMoveTo = { item -> showDirectoryPicker(item) },
            onRefresh = ::refresh)
    }

    private fun showDirectoryPicker(item: com.filecleaner.app.data.FileItem) {
        val tree = vm.directoryTree.value ?: return
        com.filecleaner.app.ui.common.DirectoryPickerDialog.show(
            requireContext(), tree, excludePath = java.io.File(item.path).parent
        ) { targetDir ->
            vm.moveFile(item.path, targetDir)
        }
    }

    // B5: Save all user-visible state for config change survival
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_VIEW_MODE, currentViewMode.ordinal)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
        outState.putInt(KEY_SORT_ORDER, _binding?.spinnerSort?.selectedItemPosition ?: 0)
        outState.putInt(KEY_CATEGORY_POS, _binding?.spinnerCategory?.selectedItemPosition ?: 0)
        outState.putStringArrayList(KEY_EXTENSIONS, ArrayList(selectedExtensions))
        if (::adapter.isInitialized) {
            outState.putStringArrayList(KEY_COLLAPSED_FOLDERS, ArrayList(adapter.collapsedFolders))
        }
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        binding.spinnerCategory.onItemSelectedListener = null
        binding.spinnerSort.onItemSelectedListener = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_VIEW_MODE = "browse_view_mode"
        private const val KEY_SEARCH_QUERY = "browse_search_query"
        private const val KEY_SORT_ORDER = "browse_sort_order"
        private const val KEY_CATEGORY_POS = "browse_category_pos"
        private const val KEY_EXTENSIONS = "browse_extensions"
        private const val KEY_COLLAPSED_FOLDERS = "browse_collapsed_folders"
    }
}
