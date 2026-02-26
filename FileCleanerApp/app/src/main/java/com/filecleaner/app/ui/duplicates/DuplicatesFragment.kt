package com.filecleaner.app.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.databinding.FragmentListActionBinding
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.viewmodel.MainViewModel

class DuplicatesFragment : Fragment() {

    private var _binding: FragmentListActionBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter
    private var selected = listOf<com.filecleaner.app.data.FileItem>()

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
            binding.btnAction.text = "Delete ${sel.size} selected  (${totalSize(sel)})"
        }
        binding.recyclerView.adapter = adapter

        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }

        binding.btnAction.text = "Delete selected"
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

        vm.duplicates.observe(viewLifecycleOwner) { dupes ->
            adapter.submitList(dupes)
            binding.tvSummary.text = if (dupes.isEmpty()) "No duplicates found" else
                "${dupes.size} duplicate files — ${totalSize(dupes)} wasted"
            binding.tvEmpty.visibility = if (dupes.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} files?")
            .setMessage("This will permanently delete the selected files. This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> vm.deleteFiles(selected); adapter.deselectAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun totalSize(list: List<com.filecleaner.app.data.FileItem>): String {
        val bytes = list.sumOf { it.size }
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
            else                   -> "%.0f KB".format(bytes / 1_024.0)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
