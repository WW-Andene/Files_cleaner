package com.filecleaner.app.ui.junk

import androidx.lifecycle.LiveData
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.adapters.ColorMode
import com.filecleaner.app.ui.common.BaseFileListFragment
import com.filecleaner.app.ui.common.ColorLegendHelper

class JunkFragment : BaseFileListFragment() {
    override val screenTitle get() = getString(R.string.title_junk)
    override val defaultActionLabel get() = getString(R.string.clean_selected)
    override fun actionLabel(count: Int, sizeText: String) = resources.getQuantityString(R.plurals.action_clean_n, count, count, sizeText)
    override fun confirmTitle(count: Int) = resources.getQuantityString(R.plurals.clean_n_files_title, count, count)
    override val confirmPositiveLabel get() = getString(R.string.clean)
    override fun liveData(): LiveData<List<FileItem>> = vm.junkFiles
    override fun summaryText(count: Int, sizeText: String) = resources.getQuantityString(R.plurals.junk_summary, count, count, sizeText)
    override val emptySummary get() = getString(R.string.no_junk_found)
    override val emptyPreScan get() = getString(R.string.empty_junk_pre_scan)
    override val emptyPostScan get() = getString(R.string.empty_junk_post_scan)

    override val colorMode: ColorMode get() = ColorMode.JUNK_CATEGORY
    override fun legendEntries(): List<ColorLegendHelper.LegendEntry> =
        ColorLegendHelper.junkLegend(requireContext())
    override val legendTitleRes: Int get() = R.string.legend_junk_title
}
