package com.filecleaner.app.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentBrowseBinding
import com.filecleaner.app.ui.adapters.FileAdapter
import com.filecleaner.app.viewmodel.MainViewModel

class BrowseFragment : Fragment() {

    private var _binding: FragmentBrowseBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: FileAdapter

    private val categories = listOf(
        "All files" to null,
        *FileCategory.values().map { "${it.emoji} ${it.displayName}" to it }.toTypedArray()
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentBrowseBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // RecyclerView
        adapter = FileAdapter(selectable = false)
        binding.recyclerView.adapter = adapter

        // Category spinner
        val labels = categories.map { it.first }
        val spinnerAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = spinnerAdapter

        // Sort spinner
        val sortOptions = listOf("Name ↑", "Name ↓", "Size ↑", "Size ↓", "Date ↑", "Date ↓")
        val sortAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSort.adapter = sortAdapter

        binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        vm.filesByCategory.observe(viewLifecycleOwner) { refresh() }
    }

    private fun refresh() {
        val catEntry = categories[binding.spinnerCategory.selectedItemPosition]
        val selectedCat = catEntry.second

        val raw: List<FileItem> = if (selectedCat == null) {
            vm.filesByCategory.value?.values?.flatten() ?: emptyList()
        } else {
            vm.filesByCategory.value?.get(selectedCat) ?: emptyList()
        }

        val sorted = when (binding.spinnerSort.selectedItemPosition) {
            0 -> raw.sortedBy   { it.name.lowercase() }
            1 -> raw.sortedByDescending { it.name.lowercase() }
            2 -> raw.sortedBy   { it.size }
            3 -> raw.sortedByDescending { it.size }
            4 -> raw.sortedBy   { it.lastModified }
            5 -> raw.sortedByDescending { it.lastModified }
            else -> raw
        }

        adapter.submitList(sorted)
        binding.tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        binding.tvCount.text = "${sorted.size} files"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
