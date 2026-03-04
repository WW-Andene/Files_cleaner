package com.filecleaner.app.ui.common

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.filecleaner.app.MainActivity
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentListActionBinding
import android.widget.HorizontalScrollView
import androidx.annotation.StringRes
import com.filecleaner.app.ui.adapters.ColorMode
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.ui.adapters.ViewMode
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.utils.SearchQueryParser
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.ui.common.FileListDividerDecoration
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

/**
 * Shared base for Junk / Large / Duplicates screens (F-039).
 * Subclasses only provide the varying text and data source.
 */
abstract class BaseFileListFragment : Fragment() {

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val KEY_SELECTED_PATHS = "base_selected_paths"
        private const val KEY_VIEW_MODE = "base_view_mode"
        private const val KEY_SORT_ORDER = "base_sort_order"
        private const val KEY_SEARCH_QUERY = "base_search_query"
    }

    private var _binding: FragmentListActionBinding? = null
    protected val binding get() = _binding!!
    protected val vm: MainViewModel by activityViewModels()
    protected lateinit var adapter: FileAdapter
    private var selected = listOf<FileItem>()
    private var pendingSelectionRestore: Set<String>? = null
    private var currentViewMode = ViewMode.LIST

    // Search state
    private var searchQuery = ""
    private var rawItems = listOf<FileItem>()
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var dividerDecoration: FileListDividerDecoration? = null

    /** Screen title shown in the header. */
    abstract val screenTitle: String

    /** Default action button label when nothing is selected. */
    abstract val defaultActionLabel: String

    /** Label for the action button when items are selected, e.g. "Delete 3 selected (12 MB)". */
    abstract fun actionLabel(count: Int, sizeText: String): String

    /** Confirm-dialog title, e.g. "Delete 5 files?". */
    abstract fun confirmTitle(count: Int): String

    /** Confirm-dialog positive button text, e.g. "Delete" or "Clean". */
    abstract val confirmPositiveLabel: String

    /** The LiveData list this screen observes. */
    abstract fun liveData(): LiveData<List<FileItem>>

    /** Summary text when the list has items, e.g. "12 large files — 500 MB total". */
    abstract fun summaryText(count: Int, sizeText: String): String

    /** Summary text when the list is empty, e.g. "No large files found". */
    abstract val emptySummary: String

    /** Empty state text before any scan has been run. */
    abstract val emptyPreScan: String

    /** Empty state text when scan completed but category is clean (positive framing). */
    abstract val emptyPostScan: String

    /** Color-coding mode for the accent stripe on each file card. Override per-screen. */
    open val colorMode: ColorMode get() = ColorMode.NONE

    /** Legend entries to display below the header card. Override per-screen. */
    open fun legendEntries(): List<ColorLegendHelper.LegendEntry> = emptyList()

    /** Legend title string resource. Override per-screen. */
    @get:StringRes
    open val legendTitleRes: Int? get() = null

    /** Called when "Select All" is tapped. Override for custom behavior (e.g. duplicates). */
    open fun onSelectAll() {
        adapter.selectAll()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListActionBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = screenTitle

        // Restore view mode & sort from config change
        savedInstanceState?.let { state ->
            state.getInt(KEY_VIEW_MODE, -1).let { ordinal ->
                if (ordinal in ViewMode.entries.indices) currentViewMode = ViewMode.entries[ordinal]
            }
        }

        adapter = FileAdapter(selectable = true) { sel ->
            selected = sel
            binding.btnAction.isEnabled = sel.isNotEmpty()
            binding.btnAction.text = actionLabel(sel.size, UndoHelper.totalSize(sel))
            binding.btnBatchRename.visibility = if (sel.size >= 2) View.VISIBLE else View.GONE
            binding.btnBatchCompress.visibility = if (sel.isNotEmpty()) View.VISIBLE else View.GONE
        }
        adapter.viewMode = currentViewMode
        adapter.colorMode = colorMode
        adapter.onItemClick = { item -> FileOpener.openInViewer(requireContext(), item.file) }
        adapter.onItemLongClick = { item, anchor ->
            FileContextMenu.show(requireContext(), anchor, item, contextMenuCallback,
                hasClipboard = vm.clipboardEntry.value != null)
        }
        dividerDecoration = FileListDividerDecoration(requireContext())
        applyLayoutManager()
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter

        // Populate color legend strip
        val legendScroll = binding.root.findViewById<HorizontalScrollView>(R.id.legend_scroll)
        if (legendScroll != null) {
            val entries = legendEntries()
            ColorLegendHelper.populate(legendScroll, entries, legendTitleRes)
        }

        // Disable stagger animation when user prefers reduced motion (§G4)
        if (MotionUtil.isReducedMotion(requireContext())) {
            binding.recyclerView.layoutAnimation = null
        }

        binding.btnSelectAll.setOnClickListener { onSelectAll() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }
        binding.btnBatchRename.setOnClickListener {
            if (selected.size >= 2) {
                BatchRenameDialog.show(requireContext(), selected) { renames ->
                    vm.batchRename(renames)
                    adapter.deselectAll()
                }
            }
        }
        binding.btnBatchCompress.setOnClickListener {
            if (selected.isNotEmpty()) {
                CompressDialog.show(requireContext(), selected) { archiveName, paths ->
                    vm.compressFiles(paths, archiveName)
                    adapter.deselectAll()
                }
            }
        }

        binding.btnAction.text = defaultActionLabel
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

        // Sort spinner
        val sortOptions = listOf(
            getString(R.string.sort_name_asc), getString(R.string.sort_name_desc),
            getString(R.string.sort_size_asc), getString(R.string.sort_size_desc),
            getString(R.string.sort_date_asc), getString(R.string.sort_date_desc)
        )
        val sortAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner, sortOptions)
        sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        binding.spinnerSort.adapter = sortAdapter
        savedInstanceState?.getInt(KEY_SORT_ORDER, 0)?.let { binding.spinnerSort.setSelection(it) }
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = applySearch()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // Collapsible filter panel toggle
        binding.btnToggleFilters.setOnClickListener { toggleFilterPanel() }

        // View mode toggle
        binding.btnViewMode.setOnClickListener { cycleViewMode() }
        updateViewModeIcon()

        // Grid columns chips
        setupGridColumnChips()

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
                    searchQuery = query
                    applySearch()
                }
                handler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_MS)
            }
        })

        // B5: Restore search query from config change
        savedInstanceState?.getString(KEY_SEARCH_QUERY)?.let { query ->
            if (query.isNotEmpty()) {
                searchQuery = query
                binding.etSearch.setText(query)
            }
        }

        // Restore selection from config change
        savedInstanceState?.getStringArrayList(KEY_SELECTED_PATHS)?.let { paths ->
            pendingSelectionRestore = paths.toSet()
        }

        liveData().observe(viewLifecycleOwner) { items ->
            rawItems = items
            applySearch()
            // Restore selection after data arrives (adapter needs items to select)
            pendingSelectionRestore?.let { paths ->
                adapter.restoreSelection(paths)
                pendingSelectionRestore = null
            }
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }

        vm.scanState.observe(viewLifecycleOwner) { state ->
            binding.progressScan.visibility = if (state is ScanState.Scanning) View.VISIBLE else View.GONE
        }
    }

    private fun applySearch() {
        val searched = SearchQueryParser.filterItems(rawItems, searchQuery)
        val filtered = SearchQueryParser.sortItems(searched, binding.spinnerSort.selectedItemPosition)

        // Show filtered count when a search is active, raw count otherwise
        val displayItems = if (searchQuery.isNotEmpty()) filtered else rawItems
        binding.tvSummary.text = if (rawItems.isEmpty()) emptySummary
        else summaryText(displayItems.size, UndoHelper.totalSize(displayItems))

        // Toggle empty/list visibility BEFORE submitting items so the RecyclerView
        // measures at its correct height (fixes items appearing too low on first scan).
        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            val scanState = vm.scanState.value
            val isPreScan = scanState !is ScanState.Done
            val isScanning = scanState is ScanState.Scanning
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(com.filecleaner.app.R.string.empty_search_results, searchQuery)
                isScanning -> getString(com.filecleaner.app.R.string.scanning_in_progress)
                !isPreScan -> emptyPostScan
                else -> emptyPreScan
            }
            binding.btnScanNow.visibility = if (isPreScan && !isScanning && searchQuery.isEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }

        adapter.submitList(filtered)
    }

    private fun confirmDelete() {
        // Re-read selection from adapter to avoid stale state
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return
        val totalSize = com.filecleaner.app.utils.UndoHelper.totalSize(selected)
        val undoSeconds = try { com.filecleaner.app.data.UserPreferences.undoTimeoutMs / 1000 } catch (_: Exception) { 8 }
        val detailMessage = resources.getQuantityString(com.filecleaner.app.R.plurals.confirm_delete_detail,
            selected.size, selected.size, totalSize, undoSeconds)
        AlertDialog.Builder(requireContext())
            .setTitle(confirmTitle(selected.size))
            .setMessage(detailMessage)
            .setPositiveButton(confirmPositiveLabel) { _, _ ->
                vm.deleteFiles(selected)
                adapter.deselectAll()
            }
            .setNegativeButton(getString(com.filecleaner.app.R.string.cancel), null)
            .show()
    }

    private val contextMenuCallback by lazy {
        FileContextMenu.defaultCallback(vm,
            onMoveTo = { item -> showDirectoryPicker(item) })
    }

    private fun showDirectoryPicker(item: com.filecleaner.app.data.FileItem) {
        val tree = vm.directoryTree.value ?: return
        DirectoryPickerDialog.show(
            requireContext(), tree, excludePath = java.io.File(item.path).parent
        ) { targetDir ->
            vm.moveFile(item.path, targetDir)
        }
    }

    private var filtersExpanded = false

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
        val spanCount = currentViewMode.spanCount
        dividerDecoration?.let { binding.recyclerView.removeItemDecoration(it) }
        binding.recyclerView.layoutManager = if (spanCount == 1) {
            LinearLayoutManager(requireContext())
        } else {
            GridLayoutManager(requireContext(), spanCount)
        }
        if (spanCount == 1) {
            dividerDecoration?.let { binding.recyclerView.addItemDecoration(it) }
        }
    }

    private fun updateViewModeIcon() {
        val iconRes = when (currentViewMode) {
            ViewMode.LIST_COMPACT, ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> R.drawable.ic_view_list
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> R.drawable.ic_view_grid
        }
        binding.btnViewMode.setImageResource(iconRes)
        updateGridColumnsVisibility()
    }

    private var suppressGridChipListener = false

    private fun setupGridColumnChips() {
        val chipGroup = binding.chipGroupGridColumns
        val gridModes = listOf(
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
        val isGrid = currentViewMode.spanCount > 1
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

    // B5: Save all user-visible state for config change survival
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::adapter.isInitialized) {
            outState.putStringArrayList(KEY_SELECTED_PATHS, ArrayList(adapter.getSelectedPaths()))
        }
        outState.putInt(KEY_VIEW_MODE, currentViewMode.ordinal)
        outState.putInt(KEY_SORT_ORDER, _binding?.spinnerSort?.selectedItemPosition ?: 0)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        binding.spinnerSort.onItemSelectedListener = null
        super.onDestroyView()
        _binding = null
    }
}
