package com.filecleaner.app.ui.optimize

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.databinding.FragmentOptimizeBinding
import com.filecleaner.app.utils.StorageOptimizer
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OptimizeFragment : Fragment() {

    private var _binding: FragmentOptimizeBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    /** All suggestions returned by the analyzer (unfiltered master list). */
    private var allSuggestions = listOf<StorageOptimizer.Suggestion>()

    /** Currently visible suggestions after applying quick-filters. */
    private var filteredSuggestions = listOf<StorageOptimizer.Suggestion>()

    /** Quick-filter state. */
    private var filterOldFiles = false
    private var filterLargeFiles = false

    /** Collapse state per category. true = collapsed. */
    private val collapsedCategories = mutableSetOf<FileCategory>()

    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentOptimizeBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.recyclerSuggestions.layoutManager = LinearLayoutManager(requireContext())

        // Re-analyze button
        binding.btnReanalyze.setOnClickListener { runAnalysis() }

        // Run initial analysis
        runAnalysis()

        // Re-run when scan data changes
        vm.filesByCategory.observe(viewLifecycleOwner) { runAnalysis() }

        // Global select / deselect all — operates on ALL suggestions, not just filtered
        binding.btnSelectAll.setOnClickListener {
            allSuggestions.forEach { it.accepted = true }
            rebuildAdapter()
            updateGlobalSummary()
        }

        binding.btnDeselectAll.setOnClickListener {
            allSuggestions.forEach { it.accepted = false }
            rebuildAdapter()
            updateGlobalSummary()
        }

        // Quick filter chips
        binding.chipOldFiles.setOnCheckedChangeListener { _, checked ->
            filterOldFiles = checked
            applyFiltersAndRebuild()
        }

        binding.chipLargeFiles.setOnCheckedChangeListener { _, checked ->
            filterLargeFiles = checked
            applyFiltersAndRebuild()
        }

        // Clear selection button on floating bar
        binding.btnClearSelection.setOnClickListener {
            filteredSuggestions.forEach { it.accepted = false }
            rebuildAdapter()
            updateGlobalSummary()
        }

        binding.btnApply.setOnClickListener { confirmApply() }

        vm.operationResult.observe(viewLifecycleOwner) { result ->
            Snackbar.make(binding.root, result.message, Snackbar.LENGTH_SHORT).show()
        }
    }

    // ─── Analysis ─────────────────────────────────────────────────────────────

    private fun runAnalysis() {
        val allFiles = vm.filesByCategory.value?.values?.flatten() ?: emptyList()

        if (allFiles.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = getString(R.string.optimize_no_scan_data)
            binding.recyclerSuggestions.visibility = View.GONE
            binding.btnApply.isEnabled = false
            binding.selectionControls.visibility = View.GONE
            binding.filterBar.visibility = View.GONE
            binding.selectionSummaryBar.visibility = View.GONE
            binding.progressAnalyzing.visibility = View.GONE
            return
        }

        // Show loading indicator
        binding.progressAnalyzing.visibility = View.VISIBLE
        binding.recyclerSuggestions.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            allSuggestions = withContext(Dispatchers.IO) {
                StorageOptimizer.analyze(allFiles, storagePath)
            }

            if (_binding == null) return@launch

            binding.progressAnalyzing.visibility = View.GONE

            if (allSuggestions.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.recyclerSuggestions.visibility = View.GONE
                binding.btnApply.isEnabled = false
                binding.selectionControls.visibility = View.GONE
                binding.filterBar.visibility = View.GONE
                binding.selectionSummaryBar.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.recyclerSuggestions.visibility = View.VISIBLE
                binding.selectionControls.visibility = View.VISIBLE
                binding.filterBar.visibility = View.VISIBLE
            }

            applyFiltersAndRebuild()
        }
    }

    // ─── Filtering ──────────────────────────────────────────────────────────────

    private fun applyFiltersAndRebuild() {
        filteredSuggestions = allSuggestions.filter { s ->
            val passOld = if (filterOldFiles) {
                val ageMs = System.currentTimeMillis() - s.file.lastModified
                val ageDays = ageMs / (24 * 60 * 60 * 1000L)
                ageDays > 90
            } else true

            val passLarge = if (filterLargeFiles) {
                s.file.size > 50 * 1024 * 1024L
            } else true

            passOld && passLarge
        }
        rebuildAdapter()
        updateGlobalSummary()
    }

    // ─── Adapter rebuild ────────────────────────────────────────────────────────

    private fun rebuildAdapter() {
        val grouped = buildGroupedList(filteredSuggestions)
        binding.recyclerSuggestions.adapter = GroupedSuggestionAdapter(
            items = grouped,
            storagePath = storagePath,
            collapsedCategories = collapsedCategories,
            onSelectionChanged = { updateGlobalSummary() },
            onCategoryToggleAll = { cat, selectAll -> toggleCategory(cat, selectAll) },
            onCategoryCollapseToggle = { cat -> toggleCollapse(cat) }
        )
    }

    // ─── Category-level actions ─────────────────────────────────────────────────

    private fun toggleCategory(category: FileCategory, selectAll: Boolean) {
        filteredSuggestions
            .filter { it.file.category == category }
            .forEach { it.accepted = selectAll }
        rebuildAdapter()
        updateGlobalSummary()
    }

    private fun toggleCollapse(category: FileCategory) {
        if (category in collapsedCategories) {
            collapsedCategories.remove(category)
        } else {
            collapsedCategories.add(category)
        }
        rebuildAdapter()
    }

    // ─── Summaries ──────────────────────────────────────────────────────────────

    private fun updateGlobalSummary() {
        val accepted = filteredSuggestions.filter { it.accepted }
        val totalSize = accepted.sumOf { it.file.size }
        val allTotalSize = allSuggestions.sumOf { it.file.size }

        // Show total reorganizable size as well as selection counts
        val summaryLine = getString(
            R.string.optimize_summary_detail,
            filteredSuggestions.size, accepted.size
        )
        val totalLine = if (allSuggestions.isNotEmpty()) {
            "\n" + getString(R.string.optimize_total_reorganize_size,
                allSuggestions.size, UndoHelper.formatBytes(allTotalSize))
        } else ""
        binding.tvSummary.text = summaryLine + totalLine
        binding.btnApply.isEnabled = accepted.isNotEmpty()

        // Floating summary bar
        if (accepted.isNotEmpty()) {
            binding.selectionSummaryBar.visibility = View.VISIBLE
            binding.tvSelectionSummary.text = getString(
                R.string.optimize_selection_summary,
                accepted.size,
                UndoHelper.formatBytes(totalSize)
            )
        } else {
            binding.selectionSummaryBar.visibility = View.GONE
        }
    }

    // ─── Grouped list builder ───────────────────────────────────────────────────

    private fun buildGroupedList(items: List<StorageOptimizer.Suggestion>): List<Any> {
        val result = mutableListOf<Any>()
        val grouped = items.groupBy { it.file.category }

        val order = listOf(
            FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO,
            FileCategory.DOCUMENT, FileCategory.APK, FileCategory.DOWNLOAD
        )

        for (cat in order) {
            val group = grouped[cat] ?: continue
            val selectedCount = group.count { it.accepted }
            val totalSize = group.sumOf { it.file.size }
            val isCollapsed = cat in collapsedCategories
            result.add(CategoryHeader(cat, group.size, selectedCount, totalSize, isCollapsed))
            if (!isCollapsed) {
                result.addAll(group)
            }
        }
        // Add any remaining categories not in the order list
        for ((cat, group) in grouped) {
            if (cat !in order) {
                val selectedCount = group.count { it.accepted }
                val totalSize = group.sumOf { it.file.size }
                val isCollapsed = cat in collapsedCategories
                result.add(CategoryHeader(cat, group.size, selectedCount, totalSize, isCollapsed))
                if (!isCollapsed) {
                    result.addAll(group)
                }
            }
        }
        return result
    }

    // ─── Confirm / Apply ────────────────────────────────────────────────────────

    private fun confirmApply() {
        // Apply operates on all accepted suggestions (not just filtered ones)
        val accepted = allSuggestions.filter { it.accepted }
        if (accepted.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.optimize_none_selected), Snackbar.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.optimize_confirm_title))
            .setMessage(resources.getQuantityString(R.plurals.optimize_confirm_message, accepted.size, accepted.size))
            .setPositiveButton(getString(R.string.move)) { _, _ ->
                for (suggestion in accepted) {
                    val targetDir = File(suggestion.suggestedPath).parent ?: continue
                    File(targetDir).mkdirs()
                    vm.moveFile(suggestion.currentPath, targetDir)
                }
                Snackbar.make(binding.root,
                    resources.getQuantityString(R.plurals.optimize_applied, accepted.size, accepted.size),
                    Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Data classes
    // ═══════════════════════════════════════════════════════════════════════════

    /** Category section header data — now includes selection counts, size, and collapse state. */
    data class CategoryHeader(
        val category: FileCategory,
        val totalCount: Int,
        val selectedCount: Int,
        val totalSize: Long,
        val collapsed: Boolean
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Adapter
    // ═══════════════════════════════════════════════════════════════════════════

    /** Adapter with category headers (checkbox, collapse, counters) and suggestion items. */
    class GroupedSuggestionAdapter(
        private val items: List<Any>,
        private val storagePath: String,
        private val collapsedCategories: Set<FileCategory>,
        private val onSelectionChanged: () -> Unit,
        private val onCategoryToggleAll: (FileCategory, Boolean) -> Unit,
        private val onCategoryCollapseToggle: (FileCategory) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_SUGGESTION = 1
        }

        // ── ViewHolders ─────────────────────────────────────────────────────

        class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val headerClickable: View = view.findViewById(R.id.header_clickable)
            val checkbox: CheckBox = view.findViewById(R.id.cb_category_select)
            val title: TextView = view.findViewById(R.id.tv_header_title)
            val sizeInfo: TextView = view.findViewById(R.id.tv_header_size_info)
            val count: TextView = view.findViewById(R.id.tv_header_count)
            val expandArrow: ImageView = view.findViewById(R.id.iv_expand_arrow)
        }

        class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.cb_accept)
            val filename: TextView = view.findViewById(R.id.tv_filename)
            val reason: TextView = view.findViewById(R.id.tv_reason)
            val movePath: TextView = view.findViewById(R.id.tv_move_path)
        }

        // ── Adapter overrides ───────────────────────────────────────────────

        override fun getItemViewType(position: Int) =
            if (items[position] is CategoryHeader) TYPE_HEADER else TYPE_SUGGESTION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                HeaderViewHolder(inflater.inflate(R.layout.item_optimize_header, parent, false))
            } else {
                SuggestionViewHolder(inflater.inflate(R.layout.item_optimize_suggestion, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is CategoryHeader -> bindHeader(holder as HeaderViewHolder, item)
                is StorageOptimizer.Suggestion -> bindSuggestion(holder as SuggestionViewHolder, item)
            }
        }

        override fun getItemCount() = items.size

        // ── Bind helpers ────────────────────────────────────────────────────

        private fun bindHeader(h: HeaderViewHolder, header: CategoryHeader) {
            val ctx = h.itemView.context

            // Title with emoji
            h.title.text = "${header.category.emoji} ${ctx.getString(header.category.displayNameRes)}"

            // Size info: "2.4 GB, 340 files"
            h.sizeInfo.text = ctx.getString(
                R.string.optimize_category_size_info,
                UndoHelper.formatBytes(header.totalSize),
                header.totalCount
            )

            // Selection badge: "5/10 selected"
            h.count.text = ctx.getString(
                R.string.optimize_category_selection,
                header.selectedCount,
                header.totalCount
            )

            // Checkbox state: checked if all selected, unchecked if none, indeterminate-like
            // behaviour via direct check state
            h.checkbox.setOnCheckedChangeListener(null)
            h.checkbox.isChecked = header.selectedCount > 0 && header.selectedCount == header.totalCount
            h.checkbox.setOnCheckedChangeListener { _, checked ->
                onCategoryToggleAll(header.category, checked)
            }

            // Expand / collapse arrow (rotated ic_arrow_back: -90 = expanded, 90 = collapsed)
            h.expandArrow.rotation = if (header.collapsed) 90f else -90f

            // Tap header row (outside checkbox) to toggle collapse
            h.headerClickable.setOnClickListener {
                onCategoryCollapseToggle(header.category)
            }
        }

        private fun bindSuggestion(h: SuggestionViewHolder, item: StorageOptimizer.Suggestion) {
            h.filename.text = item.file.name
            h.reason.text = item.reason

            val fromRelative = item.currentPath.removePrefix(storagePath)
            val toRelative = item.suggestedPath.removePrefix(storagePath)
            h.movePath.text = "$fromRelative \u2192 $toRelative"

            h.checkbox.setOnCheckedChangeListener(null)
            h.checkbox.isChecked = item.accepted
            h.checkbox.setOnCheckedChangeListener { _, checked ->
                item.accepted = checked
                onSelectionChanged()
            }
        }
    }
}
