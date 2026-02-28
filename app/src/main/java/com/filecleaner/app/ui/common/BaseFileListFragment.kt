package com.filecleaner.app.ui.common

import android.os.Bundle
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
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel

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
        binding.recyclerView.adapter = adapter

        binding.btnSelectAll.setOnClickListener { onSelectAll() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }

        binding.btnAction.text = defaultActionLabel
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

        liveData().observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.tvSummary.text = if (items.isEmpty()) emptySummary
            else summaryText(items.size, UndoHelper.totalSize(items))
            binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(confirmTitle(selected.size))
            .setMessage(getString(com.filecleaner.app.R.string.confirm_delete_message))
            .setPositiveButton(confirmPositiveLabel) { _, _ ->
                vm.deleteFiles(selected)
                adapter.deselectAll()
            }
            .setNegativeButton(getString(com.filecleaner.app.R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
