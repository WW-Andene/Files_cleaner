package com.filecleaner.app.ui.large

import androidx.lifecycle.LiveData
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class LargeFilesFragment : BaseFileListFragment() {
    override val screenTitle get() = getString(R.string.title_large)
    override val defaultActionLabel get() = getString(R.string.delete_selected)
    override fun actionLabel(count: Int, sizeText: String) = getString(R.string.action_delete_n, count, sizeText)
    override fun confirmTitle(count: Int) = getString(R.string.delete_n_files_title, count)
    override val confirmPositiveLabel get() = getString(R.string.delete)
    override fun liveData(): LiveData<List<FileItem>> = vm.largeFiles
    override fun summaryText(count: Int, sizeText: String) = getString(R.string.large_summary, count, sizeText)
    override val emptySummary get() = getString(R.string.no_large_found)
}
