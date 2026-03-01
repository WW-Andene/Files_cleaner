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
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.ui.adapters.ViewMode
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.utils.SearchQueryParser
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
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
        }
        adapter.viewMode = currentViewMode
        adapter.onItemClick = { item -> FileOpener.open(requireContext(), item.file) }
        adapter.onItemLongClick = { item, anchor ->
            FileContextMenu.show(requireContext(), anchor, item, contextMenuCallback,
                hasClipboard = vm.clipboardEntry.value != null)
        }
        applyLayoutManager()
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.adapter = adapter
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

        // View mode toggle
        binding.btnViewMode.setOnClickListener { cycleViewMode() }
        updateViewModeIcon()

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
                searchRunnable = Runnable {
                    searchQuery = s?.toString()?.trim() ?: ""
                    applySearch()
                }
                handler.postDelayed(searchRunnable!!, SEARCH_DEBOUNCE_MS)
            }
        })

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
    }

    private fun applySearch() {
        val searched = if (searchQuery.isEmpty()) rawItems
        else if (SearchQueryParser.hasOperators(searchQuery)) {
            val parsed = SearchQueryParser.parse(searchQuery)
            rawItems.filter { SearchQueryParser.matches(it, parsed) }
        } else {
            val lowerQuery = searchQuery.lowercase()
            rawItems.filter { it.name.lowercase().contains(lowerQuery) }
        }

        val filtered = when (binding.spinnerSort.selectedItemPosition) {
            0 -> searched.sortedBy { it.name.lowercase() }
            1 -> searched.sortedByDescending { it.name.lowercase() }
            2 -> searched.sortedBy { it.size }
            3 -> searched.sortedByDescending { it.size }
            4 -> searched.sortedBy { it.lastModified }
            5 -> searched.sortedByDescending { it.lastModified }
            else -> searched
        }

        binding.tvSummary.text = if (rawItems.isEmpty()) emptySummary
        else summaryText(rawItems.size, UndoHelper.totalSize(rawItems))

        // Toggle empty/list visibility BEFORE submitting items so the RecyclerView
        // measures at its correct height (fixes items appearing too low on first scan).
        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            val isPreScan = vm.scanState.value !is ScanState.Done
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(com.filecleaner.app.R.string.empty_search_results, searchQuery)
                !isPreScan -> emptyPostScan
                else -> emptyPreScan
            }
            binding.btnScanNow.visibility = if (isPreScan && searchQuery.isEmpty()) View.VISIBLE else View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }

        adapter.submitList(filtered)
    }

    private fun confirmDelete() {
        val totalSize = com.filecleaner.app.utils.UndoHelper.totalSize(selected)
        val detailMessage = resources.getQuantityString(com.filecleaner.app.R.plurals.confirm_delete_detail,
            selected.size, selected.size, totalSize)
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
        binding.recyclerView.layoutManager = if (spanCount == 1) {
            LinearLayoutManager(requireContext())
        } else {
            GridLayoutManager(requireContext(), spanCount)
        }
    }

    private fun updateViewModeIcon() {
        val iconRes = when (currentViewMode) {
            ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> R.drawable.ic_view_list
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> R.drawable.ic_view_grid
        }
        binding.btnViewMode.setImageResource(iconRes)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::adapter.isInitialized) {
            outState.putStringArrayList(KEY_SELECTED_PATHS, ArrayList(adapter.getSelectedPaths()))
        }
        outState.putInt(KEY_VIEW_MODE, currentViewMode.ordinal)
        outState.putInt(KEY_SORT_ORDER, binding.spinnerSort.selectedItemPosition)
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}
