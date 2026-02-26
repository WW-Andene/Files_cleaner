package com.filecleaner.app.ui.junk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.databinding.FragmentListActionBinding
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.viewmodel.MainViewModel

class JunkFragment : Fragment() {

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
        binding.tvTitle.text = "Junk & Old Downloads"

        adapter = FileAdapter(selectable = true) { sel ->
            selected = sel
            binding.btnAction.isEnabled = sel.isNotEmpty()
            binding.btnAction.text = "Clean ${sel.size} files  (${totalSize(sel)})"
        }
        binding.recyclerView.adapter = adapter
        binding.btnSelectAll.setOnClickListener { adapter.selectAll() }
        binding.btnDeselectAll.setOnClickListener { adapter.deselectAll() }

        binding.btnAction.text = "Clean selected"
        binding.btnAction.isEnabled = false
        binding.btnAction.setOnClickListener { confirmDelete() }

        vm.junkFiles.observe(viewLifecycleOwner) { junk ->
            adapter.submitList(junk)
            binding.tvSummary.text = if (junk.isEmpty()) "No junk files found 🎉" else
                "${junk.size} junk files — ${totalSize(junk)} can be freed"
            binding.tvEmpty.visibility = if (junk.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clean ${selected.size} files?")
            .setMessage("Junk files, temp files, and old downloads will be permanently deleted.")
            .setPositiveButton("Clean") { _, _ -> vm.deleteFiles(selected); adapter.deselectAll() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun totalSize(list: List<FileItem>): String {
        val bytes = list.sumOf { it.size }
        return when {
            bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
            else                   -> "%.0f KB".format(bytes / 1_024.0)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
