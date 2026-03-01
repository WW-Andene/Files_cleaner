package com.filecleaner.app.ui.duplicates

import androidx.lifecycle.LiveData
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class DuplicatesFragment : BaseFileListFragment() {
    override val screenTitle get() = getString(R.string.title_duplicates)
    override val defaultActionLabel get() = getString(R.string.delete_selected)
    override fun actionLabel(count: Int, sizeText: String) = getString(R.string.action_delete_n, count, sizeText)
    override fun confirmTitle(count: Int) = resources.getQuantityString(R.plurals.delete_n_files_title, count, count)
    override val confirmPositiveLabel get() = getString(R.string.delete)
    override fun liveData(): LiveData<List<FileItem>> = vm.duplicates
    override fun summaryText(count: Int, sizeText: String) = resources.getQuantityString(R.plurals.duplicates_summary, count, count, sizeText)
    override val emptySummary get() = getString(R.string.no_duplicates_found)
    override val emptyPreScan get() = getString(R.string.empty_duplicates_pre_scan)
    override val emptyPostScan get() = getString(R.string.empty_duplicates_post_scan)

    override fun onSelectAll() {
        adapter.selectAllDuplicatesExceptBest()
    }
}
