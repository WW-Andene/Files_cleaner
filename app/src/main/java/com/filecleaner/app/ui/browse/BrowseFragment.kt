package com.filecleaner.app.ui.browse

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentBrowseBinding
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.ui.adapters.ViewMode
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter

    private var currentViewMode = ViewMode.LIST
    private val selectedExtensions = mutableSetOf<String>()
    private var searchQuery = ""
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

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

        // RecyclerView
        adapter = FileAdapter(selectable = false)
        adapter.onItemClick = { item -> FileOpener.open(requireContext(), item.file) }
        adapter.onItemLongClick = { item, anchor ->
            FileContextMenu.show(requireContext(), anchor, item, contextMenuCallback)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

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
                handler.postDelayed(searchRunnable!!, 300)
            }
        })

        // Category spinner
        val labels = categories.map { it.first }
        val spinnerAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = spinnerAdapter

        // Sort spinner
        val sortOptions = listOf(
            getString(R.string.sort_name_asc), getString(R.string.sort_name_desc),
            getString(R.string.sort_size_asc), getString(R.string.sort_size_desc),
            getString(R.string.sort_date_asc), getString(R.string.sort_date_desc)
        )
        val sortAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
            raw.filter { it.name.lowercase().contains(searchQuery.lowercase()) }
        }

        // Build extension chips from searched file set
        updateExtensionChips(searched)

        // Apply extension filter
        val filtered = if (selectedExtensions.isEmpty()) {
            searched
        } else {
            searched.filter { file ->
                val ext = file.name.substringAfterLast('.', "").lowercase()
                ext in selectedExtensions
            }
        }

        val sorted = when (binding.spinnerSort.selectedItemPosition) {
            0 -> filtered.sortedBy { it.name.lowercase() }
            1 -> filtered.sortedByDescending { it.name.lowercase() }
            2 -> filtered.sortedBy { it.size }
            3 -> filtered.sortedByDescending { it.size }
            4 -> filtered.sortedBy { it.lastModified }
            else -> filtered.sortedByDescending { it.lastModified }
        }

        adapter.submitList(sorted)

        if (sorted.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(R.string.empty_search_results, searchQuery)
                vm.scanState.value is ScanState.Done -> getString(R.string.empty_browse_post_scan)
                else -> getString(R.string.empty_browse_pre_scan)
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
        binding.tvCount.text = getString(R.string.n_files, sorted.size)
    }

    private fun updateExtensionChips(files: List<FileItem>) {
        val chipGroup = binding.chipGroupExtensions

        // Count extensions
        val extCounts = mutableMapOf<String, Int>()
        for (file in files) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
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

    private val contextMenuCallback = object : FileContextMenu.Callback {
        override fun onDelete(item: FileItem) {
            vm.deleteFiles(listOf(item))
        }
        override fun onRename(item: FileItem, newName: String) {
            vm.renameFile(item.path, newName)
        }
        override fun onCompress(item: FileItem) {
            vm.compressFile(item.path)
        }
        override fun onExtract(item: FileItem) {
            vm.extractArchive(item.path)
        }
        override fun onOpenInTree(item: FileItem) {
            vm.requestTreeHighlight(item.path)
        }
        override fun onPaste(targetDirPath: String) {
            FileContextMenu.clipboardItem?.let { cut ->
                vm.moveFile(cut.path, targetDirPath)
            }
        }
        override fun onRefresh() {
            refresh()
        }
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}
