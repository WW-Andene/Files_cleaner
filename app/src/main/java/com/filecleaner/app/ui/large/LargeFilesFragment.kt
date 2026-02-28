package com.filecleaner.app.ui.large

import androidx.lifecycle.LiveData
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class LargeFilesFragment : BaseFileListFragment() {
    override val screenTitle = "Large Files"
    override val defaultActionLabel = "Delete selected"
    override fun actionLabel(count: Int, sizeText: String) = "Delete $count selected  ($sizeText)"
    override fun confirmTitle(count: Int) = "Delete $count files?"
    override val confirmPositiveLabel = "Delete"
    override fun liveData(): LiveData<List<FileItem>> = vm.largeFiles
    override fun summaryText(count: Int, sizeText: String) = "$count large files \u2014 $sizeText total"
    override val emptySummary = "No large files found"
}
