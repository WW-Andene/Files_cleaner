package com.filecleaner.app.ui.junk

import androidx.lifecycle.LiveData
import com.filecleaner.app.R
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class JunkFragment : BaseFileListFragment() {
    override val screenTitle get() = getString(R.string.title_junk)
    override val defaultActionLabel get() = getString(R.string.clean_selected)
    override fun actionLabel(count: Int, sizeText: String) = getString(R.string.action_clean_n, count, sizeText)
    override fun confirmTitle(count: Int) = getString(R.string.clean_n_files_title, count)
    override val confirmPositiveLabel get() = getString(R.string.clean)
    override fun liveData(): LiveData<List<FileItem>> = vm.junkFiles
    override fun summaryText(count: Int, sizeText: String) = getString(R.string.junk_summary, count, sizeText)
    override val emptySummary get() = getString(R.string.no_junk_found)
}
