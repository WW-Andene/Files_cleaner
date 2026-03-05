package com.filecleaner.app.ui.dashboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.StatFs
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentDashboardBinding
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState

/**
 * Storage dashboard showing device storage summary, category breakdown with
 * colored bars, top 10 largest files, potential savings, and quick action buttons.
 */
class StorageDashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    /** Map of FileCategory to its brand color resource. */
    private val categoryColorRes = mapOf(
        FileCategory.IMAGE to R.color.catImage,
        FileCategory.VIDEO to R.color.catVideo,
        FileCategory.AUDIO to R.color.catAudio,
        FileCategory.DOCUMENT to R.color.catDocument,
        FileCategory.APK to R.color.catApk,
        FileCategory.ARCHIVE to R.color.catArchive,
        FileCategory.DOWNLOAD to R.color.catDownload,
        FileCategory.OTHER to R.color.catOther
    )

    /** Cached keys from last buildCategoryRows call for diff-based view reuse. */
    private var lastCategoryKeys: List<FileCategory>? = null

    /** Cached file paths from last buildTopFiles call for diff-based view reuse. */
    private var lastTopFilePaths: List<String>? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back button
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        // Device storage via StatFs
        @Suppress("DEPRECATION")
        val statFs = StatFs(android.os.Environment.getExternalStorageDirectory().absolutePath)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.freeBytes
        val usedBytes = totalBytes - freeBytes
        val usedPct = if (totalBytes > 0) ((usedBytes * 100.0) / totalBytes).toInt() else 0

        binding.tvStorageTitle.text = getString(R.string.dashboard_storage_title)
        binding.tvStorageUsed.text = getString(R.string.dashboard_storage_used,
            UndoHelper.formatBytes(usedBytes), UndoHelper.formatBytes(totalBytes), usedPct)
        binding.tvStorageFree.text = getString(R.string.dashboard_storage_free,
            UndoHelper.formatBytes(freeBytes))
        binding.progressStorage.max = 100
        binding.progressStorage.progress = usedPct

        // Quick action buttons
        binding.btnCleanJunk.setOnClickListener {
            findNavController().navigate(R.id.junkFragment)
        }
        binding.btnViewDuplicates.setOnClickListener {
            findNavController().navigate(R.id.duplicatesFragment)
        }
        binding.btnViewLarge.setOnClickListener {
            findNavController().navigate(R.id.largeFilesFragment)
        }

        // Category breakdown from scan data
        vm.filesByCategory.observe(viewLifecycleOwner) { catMap ->
            buildCategoryRows(catMap)
        }

        // Scan stats
        binding.tvStatsDetail.text = getString(R.string.dashboard_scan_stats_empty)
        binding.tvStatsDetail.visibility = View.VISIBLE
        vm.storageStats.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.tvStatsDetail.text = getString(R.string.dashboard_stats_detail,
                    stats.totalFiles,
                    UndoHelper.formatBytes(stats.duplicateSize),
                    UndoHelper.formatBytes(stats.junkSize),
                    UndoHelper.formatBytes(stats.largeSize))
                binding.tvStatsDetail.visibility = View.VISIBLE

                // Potential savings
                val savings = stats.duplicateSize + stats.junkSize
                if (savings > 0) {
                    binding.cardSavings.visibility = View.VISIBLE
                    binding.tvPotentialSavings.text = getString(R.string.dashboard_potential_savings,
                        UndoHelper.formatBytes(savings))
                } else {
                    binding.cardSavings.visibility = View.GONE
                }

                // Show quick actions when scan data exists
                binding.cardQuickActions.visibility = View.VISIBLE
            }
        }

        // Top 10 largest files
        vm.largeFiles.observe(viewLifecycleOwner) { largeFiles ->
            buildTopFiles(largeFiles)
        }

        // Update category breakdown text when scan state changes
        vm.scanState.observe(viewLifecycleOwner) { state ->
            val catMap = vm.filesByCategory.value ?: emptyMap()
            if (catMap.isEmpty()) {
                binding.tvCategoryBreakdown.text = when (state) {
                    is ScanState.Scanning -> getString(R.string.dashboard_scanning)
                    else -> getString(R.string.dashboard_no_scan)
                }
                binding.tvCategoryBreakdown.visibility = View.VISIBLE
                binding.categoryRowsContainer.visibility = View.GONE
            }
        }
    }

    /**
     * Build individual category rows with colored indicators and progress bars.
     * Uses view recycling: if the category keys are unchanged, existing row views
     * are updated in-place to avoid layout thrashing and GC pressure.
     */
    private fun buildCategoryRows(catMap: Map<FileCategory, List<FileItem>>) {
        val container = binding.categoryRowsContainer

        val entries = catMap.entries.sortedByDescending { it.value.sumOf { f -> f.size } }
        val totalSize = entries.sumOf { it.value.sumOf { f -> f.size } }

        if (entries.isEmpty()) {
            binding.tvCategoryBreakdown.visibility = View.VISIBLE
            container.visibility = View.GONE
            lastCategoryKeys = null
            return
        }

        binding.tvCategoryBreakdown.visibility = View.GONE
        container.visibility = View.VISIBLE

        val ctx = requireContext()
        val spacingXs = ctx.resources.getDimensionPixelSize(R.dimen.spacing_xs)
        val spacingSm = ctx.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val spacingMd = ctx.resources.getDimensionPixelSize(R.dimen.spacing_md)
        val spacingChip = ctx.resources.getDimensionPixelSize(R.dimen.spacing_chip)
        val newKeys = entries.map { it.key }
        val canUpdateInPlace = lastCategoryKeys == newKeys && container.childCount == newKeys.size

        if (canUpdateInPlace) {
            // Update existing row views in-place without removing/recreating
            for ((i, entry) in entries.withIndex()) {
                val (cat, files) = entry
                val catSize = files.sumOf { it.size }
                val pct = if (totalSize > 0) ((catSize * 100.0) / totalSize).toInt() else 0
                val colorRes = categoryColorRes[cat] ?: R.color.catOther
                val color = ContextCompat.getColor(ctx, colorRes)

                val row = container.getChildAt(i) as LinearLayout
                // Child 0 is the dot, child 1 is the infoColumn
                val dot = row.getChildAt(0)
                (dot.background as? GradientDrawable)?.setColor(color)

                val infoColumn = row.getChildAt(1) as LinearLayout
                // infoColumn child 0 = nameText, child 1 = detailText, child 2 = progressBar
                val nameText = infoColumn.getChildAt(0) as TextView
                nameText.text = "${cat.emoji} ${getString(cat.displayNameRes)}"

                val detailText = infoColumn.getChildAt(1) as TextView
                val countAndSize = resources.getQuantityString(
                    R.plurals.n_files_with_size, files.size, files.size,
                    UndoHelper.formatBytes(catSize))
                detailText.text = "$countAndSize ($pct%)"

                val progressBar = infoColumn.getChildAt(2) as ProgressBar
                progressBar.progress = pct
                progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
            }
        } else {
            // Category keys changed — full rebuild required
            container.removeAllViews()

            for ((cat, files) in entries) {
                val catSize = files.sumOf { it.size }
                val pct = if (totalSize > 0) ((catSize * 100.0) / totalSize).toInt() else 0
                val colorRes = categoryColorRes[cat] ?: R.color.catOther
                val color = ContextCompat.getColor(ctx, colorRes)

                // Row container
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = spacingSm
                    }
                    setPadding(spacingXs, spacingChip, spacingXs, spacingChip)
                }

                // Color dot indicator
                val dot = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(spacingMd, spacingMd).apply {
                        marginEnd = spacingSm
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                }
                row.addView(dot)

                // Category info (name + count + size) column
                val infoColumn = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                // Category name with emoji and file count
                val nameText = TextView(ctx).apply {
                    text = "${cat.emoji} ${getString(cat.displayNameRes)}"
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
                }
                infoColumn.addView(nameText)

                // File count and size
                val detailText = TextView(ctx).apply {
                    val countAndSize = resources.getQuantityString(
                        R.plurals.n_files_with_size, files.size, files.size,
                        UndoHelper.formatBytes(catSize))
                    text = "$countAndSize ($pct%)"
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
                }
                infoColumn.addView(detailText)

                // Progress bar showing proportion
                val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        spacingChip
                    ).apply {
                        topMargin = spacingXs
                    }
                    max = 100
                    progress = pct
                    progressTintList = android.content.res.ColorStateList.valueOf(color)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, R.color.borderDefault))
                }
                infoColumn.addView(progressBar)

                row.addView(infoColumn)
                container.addView(row)
            }
        }

        lastCategoryKeys = newKeys
    }

    /**
     * Build the top 10 largest files list.
     * Uses view recycling: if the file paths are unchanged, existing row views
     * are updated in-place to avoid layout thrashing and GC pressure.
     */
    private fun buildTopFiles(largeFiles: List<FileItem>) {
        val container = binding.topFilesContainer

        val topFiles = largeFiles.sortedByDescending { it.size }.take(10)

        if (topFiles.isEmpty()) {
            binding.cardTopFiles.visibility = View.GONE
            lastTopFilePaths = null
            return
        }

        binding.cardTopFiles.visibility = View.VISIBLE
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val spacingXs = ctx.resources.getDimensionPixelSize(R.dimen.spacing_xs)
        val spacingSm = ctx.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val spacingChip = ctx.resources.getDimensionPixelSize(R.dimen.spacing_chip)
        val spacingXl = ctx.resources.getDimensionPixelSize(R.dimen.spacing_xl)
        val strokeDefault = ctx.resources.getDimensionPixelSize(R.dimen.stroke_default)
        val newPaths = topFiles.map { it.path }

        // For N files the container has N rows + (N-1) dividers = 2N-1 children.
        // Rows are at even indices (0, 2, 4, ...), dividers at odd indices.
        val expectedChildCount = topFiles.size * 2 - 1
        val canUpdateInPlace = lastTopFilePaths == newPaths && container.childCount == expectedChildCount

        if (canUpdateInPlace) {
            // Update existing row views in-place without removing/recreating
            for ((index, file) in topFiles.withIndex()) {
                val rowIndex = index * 2 // rows are at even positions, dividers at odd
                val row = container.getChildAt(rowIndex) as LinearLayout
                // Row children: [0] rank, [1] emoji, [2] nameText, [3] sizeText
                val rank = row.getChildAt(0) as TextView
                rank.text = "${index + 1}."

                val emoji = row.getChildAt(1) as TextView
                emoji.text = file.category.emoji

                val nameText = row.getChildAt(2) as TextView
                nameText.text = file.name

                val sizeText = row.getChildAt(3) as TextView
                sizeText.text = UndoHelper.formatBytes(file.size)
            }
        } else {
            // File list structure changed — full rebuild required
            container.removeAllViews()

            for ((index, file) in topFiles.withIndex()) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = spacingXs
                    }
                    setPadding(spacingXs, spacingChip, spacingXs, spacingChip)
                }

                // Rank number
                val rank = TextView(ctx).apply {
                    text = "${index + 1}."
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = spacingSm
                    }
                    minWidth = spacingXl
                }
                row.addView(rank)

                // Category emoji
                val emoji = TextView(ctx).apply {
                    text = file.category.emoji
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = spacingSm
                    }
                }
                row.addView(emoji)

                // File name (truncated)
                val nameText = TextView(ctx).apply {
                    text = file.name
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(nameText)

                // File size
                val sizeText = TextView(ctx).apply {
                    text = UndoHelper.formatBytes(file.size)
                    setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = spacingSm
                    }
                }
                row.addView(sizeText)

                container.addView(row)

                // Add divider between items (not after last)
                if (index < topFiles.size - 1) {
                    val divider = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            strokeDefault
                        ).apply {
                            topMargin = (2 * density).toInt()
                            bottomMargin = (2 * density).toInt()
                        }
                        setBackgroundColor(ContextCompat.getColor(ctx, R.color.borderSubtle))
                    }
                    container.addView(divider)
                }
            }
        }

        lastTopFilePaths = newPaths
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lastCategoryKeys = null
        lastTopFilePaths = null
    }
}
