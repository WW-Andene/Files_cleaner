package com.filecleaner.app.ui.analysis

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.databinding.FragmentAnalysisBinding
import com.filecleaner.app.utils.MotionUtil
import com.filecleaner.app.utils.UndoHelper
import com.filecleaner.app.viewmodel.MainViewModel
import com.filecleaner.app.viewmodel.ScanState
import com.google.android.material.card.MaterialCardView

class AnalysisFragment : Fragment() {

    private var _binding: FragmentAnalysisBinding? = null
    private val binding get() = _binding!!
    private val vm: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Quick action card clicks -> navigate to respective tabs
        val navAnimOptions = MotionUtil.navOptions()
        binding.cardJunk.setOnClickListener {
            findNavController().navigate(R.id.junkFragment, null, navAnimOptions)
        }
        binding.cardLarge.setOnClickListener {
            findNavController().navigate(R.id.largeFilesFragment, null, navAnimOptions)
        }
        binding.cardDuplicates.setOnClickListener {
            findNavController().navigate(R.id.duplicatesFragment, null, navAnimOptions)
        }

        // Set empty state text from the shared include layout
        binding.emptyState.tvEmptyTitle.setText(R.string.analysis_empty_title)
        binding.emptyState.tvEmptySubtitle.setText(R.string.analysis_empty_subtitle)

        // Observe scan state for empty/content toggle
        vm.scanState.observe(viewLifecycleOwner) { state ->
            val hasData = state is ScanState.Done
            binding.emptyState.root.visibility = if (hasData) View.GONE else View.VISIBLE
            binding.contentState.visibility = if (hasData) View.VISIBLE else View.GONE
        }

        // Observe storage stats
        vm.storageStats.observe(viewLifecycleOwner) { stats ->
            if (stats == null) return@observe
            updateStorageOverview(stats)
            updateSavingsBanner(stats)
        }

        // Observe categories
        vm.filesByCategory.observe(viewLifecycleOwner) { categories ->
            if (categories.isNullOrEmpty()) return@observe
            updateCategoryBreakdown(categories)
            updateSegmentedBar(categories)
        }

        // Observe quick-action data
        vm.junkFiles.observe(viewLifecycleOwner) { junk ->
            val count = junk?.size ?: 0
            val size = UndoHelper.formatBytes(junk?.sumOf { it.size } ?: 0L)
            binding.tvJunkDetail.text = getString(R.string.analysis_quick_junk_detail, count, size)
        }

        vm.largeFiles.observe(viewLifecycleOwner) { large ->
            val count = large?.size ?: 0
            val size = UndoHelper.formatBytes(large?.sumOf { it.size } ?: 0L)
            binding.tvLargeDetail.text = getString(R.string.analysis_quick_large_detail, count, size)
        }

        vm.duplicates.observe(viewLifecycleOwner) { dupes ->
            val count = dupes?.size ?: 0
            val size = UndoHelper.formatBytes(dupes?.sumOf { it.size } ?: 0L)
            binding.tvDuplicatesDetail.text = getString(R.string.analysis_quick_duplicates_detail, count, size)
        }

        // Observe all files for top 10
        vm.filesByCategory.observe(viewLifecycleOwner) { categories ->
            if (categories.isNullOrEmpty()) return@observe
            val allFiles = categories.values.flatten()
            updateTopFiles(allFiles)
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Storage Overview: main bar + text
    // ──────────────────────────────────────────────────────────────────

    private fun updateStorageOverview(stats: MainViewModel.StorageStats) {
        // Get device storage info
        val statFs = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val totalDeviceBytes = statFs.totalBytes
        val usedDeviceBytes = totalDeviceBytes - statFs.availableBytes

        val usedText = UndoHelper.formatBytes(usedDeviceBytes)
        val totalText = UndoHelper.formatBytes(totalDeviceBytes)
        binding.tvStorageUsed.text = getString(R.string.analysis_storage_used, usedText, totalText)

        binding.tvTotalFiles.text = getString(R.string.analysis_total_files, stats.totalFiles)

        // Animate storage bar width
        val fraction = if (totalDeviceBytes > 0) {
            (usedDeviceBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f)
        } else 0f

        binding.viewStorageBarFill.post {
            val b = _binding ?: return@post
            val parent = b.viewStorageBarFill.parent as View
            val targetWidth = (parent.width * fraction).toInt()
            val lp = b.viewStorageBarFill.layoutParams
            lp.width = targetWidth
            b.viewStorageBarFill.layoutParams = lp
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Savings banner
    // ──────────────────────────────────────────────────────────────────

    private fun updateSavingsBanner(stats: MainViewModel.StorageStats) {
        val savingsBytes = stats.junkSize + stats.duplicateSize
        if (savingsBytes > 0) {
            binding.cardSavings.visibility = View.VISIBLE
            binding.tvSavings.text = getString(
                R.string.analysis_potential_savings,
                UndoHelper.formatBytes(savingsBytes)
            )
        } else {
            binding.cardSavings.visibility = View.GONE
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Segmented color bar (stacked per-category)
    // ──────────────────────────────────────────────────────────────────

    private fun updateSegmentedBar(categories: Map<FileCategory, List<FileItem>>) {
        val container = binding.layoutSegmentedBar
        container.removeAllViews()

        val totalSize = categories.values.flatten().sumOf { it.size }
        if (totalSize <= 0) return

        val segmentGap = resources.getDimensionPixelSize(R.dimen.segment_gap)
        val barSmRadius = resources.getDimension(R.dimen.radius_bar_sm)
        val orderedCategories = FileCategory.entries.filter { categories.containsKey(it) }

        for (cat in orderedCategories) {
            val items = categories[cat] ?: continue
            val catSize = items.sumOf { it.size }
            if (catSize <= 0) continue

            val fraction = catSize.toFloat() / totalSize

            val segment = View(requireContext())
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, fraction)
            lp.marginEnd = if (cat != orderedCategories.last()) segmentGap else 0
            segment.layoutParams = lp

            val bg = GradientDrawable()
            bg.setColor(getCategoryColor(cat))
            bg.cornerRadius = barSmRadius
            segment.background = bg

            container.addView(segment)
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Category Breakdown - per-category rows with progress bars
    // ──────────────────────────────────────────────────────────────────

    private fun updateCategoryBreakdown(categories: Map<FileCategory, List<FileItem>>) {
        val container = binding.layoutCategories
        container.removeAllViews()

        val totalSize = categories.values.flatten().sumOf { it.size }
        if (totalSize <= 0) return

        // Sort categories by size descending
        val sortedCategories = FileCategory.entries
            .map { cat -> cat to (categories[cat] ?: emptyList()) }
            .filter { it.second.isNotEmpty() }
            .sortedByDescending { it.second.sumOf { f -> f.size } }

        val maxCatSize = sortedCategories.firstOrNull()?.second?.sumOf { it.size } ?: 1L

        for ((cat, items) in sortedCategories) {
            val catSize = items.sumOf { it.size }
            val barFraction = catSize.toFloat() / maxCatSize

            val row = buildCategoryRow(cat, items.size, catSize, barFraction)
            container.addView(row)
        }
    }

    private fun buildCategoryRow(cat: FileCategory, count: Int, sizeBytes: Long, barFraction: Float): View {
        val ctx = requireContext()
        val catCardMargin = resources.getDimensionPixelSize(R.dimen.category_card_margin)
        val cardRadius = resources.getDimension(R.dimen.radius_sm)
        val strokeW = resources.getDimensionPixelSize(R.dimen.stroke_default)
        val catBarHeight = resources.getDimensionPixelSize(R.dimen.category_bar_height)
        val catBarRadius = resources.getDimension(R.dimen.category_bar_radius)
        val emojiMargin = resources.getDimensionPixelSize(R.dimen.emoji_margin_end)
        val barTopMargin = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val barMinWidth = resources.getDimensionPixelSize(R.dimen.bar_min_width)

        // Outer card
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = catCardMargin
            }
            radius = cardRadius
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surfaceColor))
            strokeWidth = strokeW
            strokeColor = ContextCompat.getColor(ctx, R.color.borderSubtle)
        }

        val catHPad = resources.getDimensionPixelSize(R.dimen.spacing_lg)
        val catVPad = resources.getDimensionPixelSize(R.dimen.spacing_md)
        val innerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(catHPad, catVPad, catHPad, catVPad)
        }

        // Top row: emoji + name + size
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val emojiView = TextView(ctx).apply {
            text = cat.emoji
            setTextAppearance(R.style.TextAppearance_FileCleaner_Emoji)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = emojiMargin }
        }

        val nameView = TextView(ctx).apply {
            text = getString(cat.displayNameRes)
            setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sizeLabel = TextView(ctx).apply {
            text = getString(R.string.analysis_category_count, count, UndoHelper.formatBytes(sizeBytes))
            setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
        }

        topRow.addView(emojiView)
        topRow.addView(nameView)
        topRow.addView(sizeLabel)

        // Progress bar (custom FrameLayout)
        val barTrack = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, catBarHeight
            ).apply { topMargin = barTopMargin }
            val trackBg = GradientDrawable()
            trackBg.setColor(getCategoryBgColor(cat))
            trackBg.cornerRadius = catBarRadius
            background = trackBg
        }

        val barFill = View(ctx).apply {
            val fillBg = GradientDrawable()
            fillBg.setColor(getCategoryColor(cat))
            fillBg.cornerRadius = catBarRadius
            background = fillBg
        }

        barTrack.addView(barFill)

        // Set bar fill width after layout (guard against detached fragment)
        barTrack.post {
            if (!isAdded) return@post
            val targetWidth = (barTrack.width * barFraction).toInt().coerceAtLeast(barMinWidth)
            barFill.layoutParams = FrameLayout.LayoutParams(targetWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        innerLayout.addView(topRow)
        innerLayout.addView(barTrack)
        card.addView(innerLayout)
        return card
    }

    // ──────────────────────────────────────────────────────────────────
    // Top 10 Largest Files
    // ──────────────────────────────────────────────────────────────────

    private fun updateTopFiles(allFiles: List<FileItem>) {
        val container = binding.layoutTopFiles
        container.removeAllViews()

        val top10 = allFiles.sortedByDescending { it.size }.take(10)
        if (top10.isEmpty()) return

        val maxSize = top10.first().size

        for ((index, file) in top10.withIndex()) {
            val row = buildTopFileRow(index + 1, file, maxSize)
            container.addView(row)
        }
    }

    private fun buildTopFileRow(rank: Int, file: FileItem, maxSize: Long): View {
        val ctx = requireContext()
        val barFraction = if (maxSize > 0) file.size.toFloat() / maxSize else 0f
        val topFileCardMargin = resources.getDimensionPixelSize(R.dimen.top_file_card_margin)
        val cardRadius = resources.getDimension(R.dimen.radius_sm)
        val strokeW = resources.getDimensionPixelSize(R.dimen.stroke_default)
        val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        val rankWidth = resources.getDimensionPixelSize(R.dimen.rank_width)
        val topFileBarHeight = resources.getDimensionPixelSize(R.dimen.top_file_bar_height)
        val topFileBarRadius = resources.getDimension(R.dimen.top_file_bar_radius)
        val catCardMargin = resources.getDimensionPixelSize(R.dimen.category_card_margin)
        val barMinWidthSm = resources.getDimensionPixelSize(R.dimen.bar_min_width_sm)

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = topFileCardMargin
            }
            radius = cardRadius
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surfaceColor))
            strokeWidth = strokeW
            strokeColor = ContextCompat.getColor(ctx, R.color.borderSubtle)
        }

        val hPad = resources.getDimensionPixelSize(R.dimen.spacing_md)
        val vPad = resources.getDimensionPixelSize(R.dimen.spacing_10)
        val innerLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(hPad, vPad, hPad, vPad)
        }

        // Top row: rank + emoji + name + size
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val rankView = TextView(ctx).apply {
            text = "$rank"
            setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
            layoutParams = LinearLayout.LayoutParams(
                rankWidth, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
        }

        val emojiView = TextView(ctx).apply {
            text = file.category.emoji
            setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = spacingSm }
        }

        val nameView = TextView(ctx).apply {
            text = file.name
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setTextAppearance(R.style.TextAppearance_FileCleaner_BodySmall)
            setTextColor(ContextCompat.getColor(ctx, R.color.textPrimary))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sizeView = TextView(ctx).apply {
            text = UndoHelper.formatBytes(file.size)
            setTextAppearance(R.style.TextAppearance_FileCleaner_Numeric)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = spacingSm }
        }

        topRow.addView(rankView)
        topRow.addView(emojiView)
        topRow.addView(nameView)
        topRow.addView(sizeView)

        // Thin progress bar
        val barTrack = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, topFileBarHeight
            ).apply {
                topMargin = catCardMargin
                marginStart = rankWidth
            }
            val trackBg = GradientDrawable()
            trackBg.setColor(getCategoryBgColor(file.category))
            trackBg.cornerRadius = topFileBarRadius
            background = trackBg
        }

        val barFill = View(ctx).apply {
            val fillBg = GradientDrawable()
            fillBg.setColor(getCategoryColor(file.category))
            fillBg.cornerRadius = topFileBarRadius
            background = fillBg
        }

        barTrack.addView(barFill)
        barTrack.post {
            if (!isAdded) return@post
            val targetWidth = (barTrack.width * barFraction).toInt().coerceAtLeast(barMinWidthSm)
            barFill.layoutParams = FrameLayout.LayoutParams(targetWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        innerLayout.addView(topRow)
        innerLayout.addView(barTrack)
        card.addView(innerLayout)
        return card
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    private fun getCategoryColor(cat: FileCategory): Int {
        val colorRes = when (cat) {
            FileCategory.IMAGE -> R.color.catImage
            FileCategory.VIDEO -> R.color.catVideo
            FileCategory.AUDIO -> R.color.catAudio
            FileCategory.DOCUMENT -> R.color.catDocument
            FileCategory.APK -> R.color.catApk
            FileCategory.ARCHIVE -> R.color.catArchive
            FileCategory.DOWNLOAD -> R.color.catDownload
            FileCategory.OTHER -> R.color.catOther
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun getCategoryBgColor(cat: FileCategory): Int {
        val colorRes = when (cat) {
            FileCategory.IMAGE -> R.color.catImageBg
            FileCategory.VIDEO -> R.color.catVideoBg
            FileCategory.AUDIO -> R.color.catAudioBg
            FileCategory.DOCUMENT -> R.color.catDocumentBg
            FileCategory.APK -> R.color.catApkBg
            FileCategory.ARCHIVE -> R.color.catArchiveBg
            FileCategory.DOWNLOAD -> R.color.catDownloadBg
            FileCategory.OTHER -> R.color.catOtherBg
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
