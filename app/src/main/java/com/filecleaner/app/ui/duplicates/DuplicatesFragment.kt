package com.filecleaner.app.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentListActionBinding
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel

class DuplicatesFragment : Fragment() {

    private var _binding: FragmentListActionBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter
    private var selected = listOf<FileItem>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentListActionBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = "Duplicate Files"

        adapter = FileAdapter(selectable = true) { sel ->
            selected = sel
            binding.btnAction.isEnabled = sel.isNotEmpty()
            binding.btnAction.text = "Delete ${sel.size} selected  (${UndoHelper.totalSize(sel)})"
        }
        binding.recyclerView.adapter = adapter

        binding.btnSelectAll.setOnClickListener { adapter.selectAllDuplicatesExceptBest() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }

        binding.btnAction.text = "Delete selected"
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

        vm.duplicates.observe(viewLifecycleOwner) { dupes ->
            adapter.submitList(dupes)
            binding.tvSummary.text = if (dupes.isEmpty()) "No duplicates found" else
                "${dupes.size} duplicate files \u2014 ${UndoHelper.totalSize(dupes)} in duplicates"
            binding.tvEmpty.visibility = if (dupes.isEmpty()) View.VISIBLE else View.GONE
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            UndoHelper.showUndoSnackbar(binding.root, result, vm)
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} files?")
            .setMessage("Files will be moved to trash. You can undo within 8 seconds.")
            .setPositiveButton("Delete") { _, _ -> vm.deleteFiles(selected); adapter.deselectAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
