package com.filecleaner.app.ui.arborescence

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.R
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentArborescenceBinding
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.io.File

class ArborescenceFragment : Fragment() {

    private var _binding: FragmentArborescenceBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    private var filterPanelVisible = false
    private val selectedTreeExtensions = mutableSetOf<String>()
    private var savedExpandedPaths: Set<String>? = null
    private var lastTreeRef: DirectoryNode? = null

    private val treeCategories by lazy {
        listOf(
            getString(R.string.all_files) to null,
            *FileCategory.entries.map { "${it.emoji} ${it.displayName}" to it }.toTypedArray()
        )
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentArborescenceBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire file move callback with confirmation dialog
        binding.arborescenceView.onFileMoveRequested = { filePath, targetDirPath ->
            val fileName = File(filePath).name
            val targetName = File(targetDirPath).name
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.confirm_move_title))
                .setMessage(getString(R.string.confirm_move_message, fileName, targetName))
                .setPositiveButton(getString(R.string.move)) { _, _ ->
                    vm.moveFile(filePath, targetDirPath)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // Stats header updates
        binding.arborescenceView.onStatsUpdate = { totalFiles, totalSize, visibleNodes, zoom ->
            binding.tvStatsHeader.visibility = View.VISIBLE
            binding.tvStatsHeader.text = getString(R.string.stats_header_format,
                totalFiles, formatSize(totalSize))
            binding.tvZoomInfo.visibility = View.VISIBLE
            binding.tvZoomInfo.text = getString(R.string.zoom_info_format,
                (zoom * 100).toInt(), visibleNodes)
        }

        // Node selection detail
        binding.arborescenceView.onNodeSelected = { node ->
            if (node != null) {
                binding.tvNodeDetail.visibility = View.VISIBLE
                binding.tvNodeDetail.text = getString(
                    R.string.node_detail,
                    node.name,
                    node.totalFileCount,
                    formatSize(node.totalSize),
                    node.children.size
                )
            } else {
                binding.tvNodeDetail.visibility = View.GONE
            }
        }

        // File long-press context menu
        binding.arborescenceView.onFileLongPress = { filePath, fileName ->
            val file = File(filePath)
            val ext = file.extension.lowercase()
            val category = FileCategory.fromExtension(ext)
            val item = FileItem(
                path = filePath,
                name = fileName,
                size = file.length(),
                lastModified = file.lastModified(),
                category = category
            )
            FileContextMenu.show(requireContext(), binding.arborescenceView, item, contextMenuCallback)
        }

        // Tree search
        binding.etSearchTree.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etSearchTree.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    val found = binding.arborescenceView.searchAndHighlight(query)
                    if (!found) {
                        Snackbar.make(binding.root, getString(R.string.search_not_found), Snackbar.LENGTH_SHORT).show()
                    }
                } else {
                    binding.arborescenceView.clearHighlight()
                }
                true
            } else false
        }

        // Filter toggle
        binding.btnToggleFilters.setOnClickListener {
            filterPanelVisible = !filterPanelVisible
            binding.filterPanel.visibility = if (filterPanelVisible) View.VISIBLE else View.GONE
        }

        // Filter spinners
        setupTreeFilterSpinners()

        // Reset view button
        binding.fabResetView.setOnClickListener {
            lastTreeRef = null
            vm.directoryTree.value?.let { tree ->
                binding.arborescenceView.setTree(tree)
            }
        }

        // Observe tree data
        vm.directoryTree.observe(viewLifecycleOwner) { tree ->
            if (tree != null) {
                binding.arborescenceView.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                // Restore expansion state if navigating back
                if (tree === lastTreeRef) {
                    // Same tree reference, already set — skip
                } else if (savedExpandedPaths != null) {
                    binding.arborescenceView.setTreeWithState(tree, savedExpandedPaths!!)
                    savedExpandedPaths = null
                } else {
                    binding.arborescenceView.setTree(tree)
                }
                lastTreeRef = tree
                updateTreeExtensionChips()
            } else {
                binding.arborescenceView.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        // Observe move results
        vm.moveResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }

        // Observe operation results (rename, compress, extract)
        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }

        // Observe tree highlight navigation from other tabs
        vm.navigateToTree.observe(viewLifecycleOwner) { filePath ->
            if (filePath != null) {
                binding.arborescenceView.highlightFilePath(filePath)
                val fileName = File(filePath).name
                Snackbar.make(binding.root, "Located: $fileName", Snackbar.LENGTH_SHORT).show()
                vm.clearTreeHighlight()
            }
        }
    }

    private fun setupTreeFilterSpinners() {
        // Category spinner
        val labels = treeCategories.map { it.first }
        val catAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, labels)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTreeCategory.adapter = catAdapter

        // Sort spinner (not used for tree sorting, but shows filter category)
        val sortOptions = listOf(
            getString(R.string.sort_name_asc), getString(R.string.sort_name_desc),
            getString(R.string.sort_size_asc), getString(R.string.sort_size_desc),
            getString(R.string.sort_date_asc), getString(R.string.sort_date_desc)
        )
        val sortAdapter = ArrayAdapter(requireContext(),
            android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTreeSort.adapter = sortAdapter

        binding.spinnerTreeCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedTreeExtensions.clear()
                applyTreeFilter()
                updateTreeExtensionChips()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        binding.spinnerTreeSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                applyTreeFilter()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun applyTreeFilter() {
        val catEntry = treeCategories[binding.spinnerTreeCategory.selectedItemPosition]
        val category = catEntry.second
        binding.arborescenceView.setFilter(category, selectedTreeExtensions)
    }

    private fun updateTreeExtensionChips() {
        val tree = vm.directoryTree.value ?: return
        val chipGroup = binding.chipGroupTreeExtensions

        // Collect all files from tree
        val allFiles = collectAllFiles(tree)
        val catEntry = treeCategories[binding.spinnerTreeCategory.selectedItemPosition]
        val category = catEntry.second

        val filteredByCategory = if (category == null) allFiles
        else allFiles.filter { it.category == category }

        val extCounts = mutableMapOf<String, Int>()
        for (file in filteredByCategory) {
            val ext = file.name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                extCounts[ext] = (extCounts[ext] ?: 0) + 1
            }
        }

        val topExtensions = extCounts.entries.sortedByDescending { it.value }.take(15)

        if (topExtensions.isEmpty()) {
            binding.scrollTreeExtensions.visibility = View.GONE
            return
        }

        binding.scrollTreeExtensions.visibility = View.VISIBLE
        chipGroup.removeAllViews()

        for ((ext, count) in topExtensions) {
            val chip = Chip(requireContext()).apply {
                text = ".$ext ($count)"
                isCheckable = true
                isChecked = ext in selectedTreeExtensions
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedTreeExtensions.add(ext) else selectedTreeExtensions.remove(ext)
                    applyTreeFilter()
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun collectAllFiles(node: com.filecleaner.app.data.DirectoryNode): List<FileItem> {
        val result = mutableListOf<FileItem>()
        result.addAll(node.files)
        for (child in node.children) {
            result.addAll(collectAllFiles(child))
        }
        return result
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.0f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
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
            binding.arborescenceView.highlightFilePath(item.path)
        }
        override fun onRefresh() {}
    }

    override fun onDestroyView() {
        savedExpandedPaths = binding.arborescenceView.getExpandedPaths()
        super.onDestroyView()
        _binding = null
    }
}
