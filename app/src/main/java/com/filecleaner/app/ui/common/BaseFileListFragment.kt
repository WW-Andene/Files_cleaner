package com.filecleaner.app.ui.common

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentListActionBinding
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.utils.FileOpener
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.snackbar.Snackbar

/**
 * Shared base for Junk / Large / Duplicates screens (F-039).
 * Subclasses only provide the varying text and data source.
 */
abstract class BaseFileListFragment : Fragment() {

    private var _binding: FragmentListActionBinding? = null
    protected val binding get() = _binding!!
    protected val vm: MainViewModel by activityViewModels()
    protected lateinit var adapter: FileAdapter
    private var selected = listOf<FileItem>()

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

        adapter = FileAdapter(selectable = true) { sel ->
            selected = sel
            binding.btnAction.isEnabled = sel.isNotEmpty()
            binding.btnAction.text = actionLabel(sel.size, UndoHelper.totalSize(sel))
        }
        adapter.onItemClick = { item -> FileOpener.open(requireContext(), item.file) }
        adapter.onItemLongClick = { item, anchor ->
            FileContextMenu.show(requireContext(), anchor, item, contextMenuCallback)
        }
        binding.recyclerView.adapter = adapter

        binding.btnSelectAll.setOnClickListener { onSelectAll() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }

        binding.btnAction.text = defaultActionLabel
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

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
                handler.postDelayed(searchRunnable!!, 300)
            }
        })

        liveData().observe(viewLifecycleOwner) { items ->
            rawItems = items
            applySearch()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applySearch() {
        val filtered = if (searchQuery.isEmpty()) rawItems
        else rawItems.filter { it.name.lowercase().contains(searchQuery.lowercase()) }

        adapter.submitList(filtered)
        binding.tvSummary.text = if (rawItems.isEmpty()) emptySummary
        else summaryText(rawItems.size, UndoHelper.totalSize(rawItems))

        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmptyText.text = when {
                searchQuery.isNotEmpty() -> getString(com.filecleaner.app.R.string.empty_search_results, searchQuery)
                vm.scanState.value is ScanState.Done -> emptyPostScan
                else -> emptyPreScan
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun confirmDelete() {
        val totalSize = com.filecleaner.app.utils.UndoHelper.totalSize(selected)
        val detailMessage = getString(com.filecleaner.app.R.string.confirm_delete_detail,
            selected.size, totalSize)
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
        override fun onRefresh() {}
    }

    override fun onDestroyView() {
        searchRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}
