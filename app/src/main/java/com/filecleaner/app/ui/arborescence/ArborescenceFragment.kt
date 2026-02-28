package com.filecleaner.app.ui.arborescence

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentArborescenceBinding
import com.filecleaner.app.ui.common.FileContextMenu
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.File

class ArborescenceFragment : Fragment() {

    private var _binding: FragmentArborescenceBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

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

        // Reset view button
        binding.fabResetView.setOnClickListener {
            vm.directoryTree.value?.let { tree ->
                binding.arborescenceView.setTree(tree)
            }
        }

        // Observe tree data
        vm.directoryTree.observe(viewLifecycleOwner) { tree ->
            if (tree != null) {
                binding.arborescenceView.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE
                binding.arborescenceView.setTree(tree)
            } else {
                binding.arborescenceView.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        // Observe move results
        vm.moveResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(
                binding.root,
                result.message,
                Snackbar.LENGTH_SHORT
            ).show()
        }
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
        super.onDestroyView()
        _binding = null
    }
}
