package com.filecleaner.app.ui.browse

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentBrowseBinding
import com.filecleaner.app.ui.adapters.BrowseAdapter
import com.filecleaner.app.ui.adapters.ViewMode
import com.filecleaner.app.ui.common.BaseFileListFragment
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.MotionUtil
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

    // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    private val categories by lazy {
        listOf(
            getString(R.string.all_files) to null,
            *FileCategory.entries.map { "${it.emoji} ${it.displayName}" to it }.toTypedArray()
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentBrowseBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore view mode from config change
        savedInstanceState?.getInt(KEY_VIEW_MODE, -1)?.let { ordinal ->
            if (ordinal in ViewMode.entries.indices) {
                currentViewMode = ViewMode.entries[ordinal]
            }
        }

        // RecyclerView with BrowseAdapter (supports folder headers)
        adapter = BrowseAdapter()
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

        // View mode toggle
        binding.btnViewMode.setOnClickListener { cycleViewMode() }
        updateViewModeIcon()

        // Search with 300ms debounce
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    searchQuery = s?.toString()?.trim() ?: ""
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
                refresh()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        vm.filesByCategory.observe(viewLifecycleOwner) { refresh() }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
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
            GridLayoutManager(requireContext(), spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (adapter.isHeader(position)) spanCount else 1
                    }
                }
            }
        }
    }

    private fun updateViewModeIcon() {
        val iconRes = when (currentViewMode) {
            ViewMode.LIST, ViewMode.LIST_WITH_THUMBNAILS -> R.drawable.ic_view_list
            ViewMode.GRID_SMALL, ViewMode.GRID_MEDIUM, ViewMode.GRID_LARGE -> R.drawable.ic_view_grid
        }
        binding.btnViewMode.setImageResource(iconRes)
    }

    private fun refresh() {
        val catEntry = categories[binding.spinnerCategory.selectedItemPosition]
        val selectedCat = catEntry.second

        val raw: List<FileItem> = if (selectedCat == null) {
            vm.filesByCategory.value?.values?.flatten() ?: emptyList()
        } else {
            vm.filesByCategory.value?.get(selectedCat) ?: emptyList()
        }

        // Apply search filter
        val searched = if (searchQuery.isEmpty()) {
            raw
        } else {
            val lowerQuery = searchQuery.lowercase()
            raw.filter { it.name.lowercase().contains(lowerQuery) }
        }

        // Build extension chips from searched file set
        updateExtensionChips(searched)

        // Apply extension filter
        val filtered = if (selectedExtensions.isEmpty()) {
            searched
        } else {
            searched.filter { it.extension in selectedExtensions }
        }

        val sorted = when (binding.spinnerSort.selectedItemPosition) {
            0 -> filtered.sortedBy { it.name.lowercase() }
            1 -> filtered.sortedByDescending { it.name.lowercase() }
            2 -> filtered.sortedBy { it.size }
            3 -> filtered.sortedByDescending { it.size }
            4 -> filtered.sortedBy { it.lastModified }
            else -> filtered.sortedByDescending { it.lastModified }
        }

        // Group files by parent folder and build list with section headers
        val browseItems = buildGroupedList(sorted)
        adapter.submitList(browseItems)
        binding.recyclerView.scrollToPosition(0)

        val fileCount = adapter.getFileCount()
        if (fileCount == 0) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(R.string.empty_search_results, searchQuery)
                vm.scanState.value is ScanState.Done -> getString(R.string.empty_browse_post_scan)
                else -> getString(R.string.empty_browse_pre_scan)
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
        binding.tvCount.text = resources.getQuantityString(R.plurals.n_files, fileCount, fileCount)
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
            result.add(BrowseAdapter.Item.Header(folderPath, displayName, folderFiles.size))
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
        return if (relative.isEmpty()) "/Storage" else relative
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
                text = ".$ext ($count)"
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_VIEW_MODE, currentViewMode.ordinal)
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_VIEW_MODE = "browse_view_mode"
    }
}
