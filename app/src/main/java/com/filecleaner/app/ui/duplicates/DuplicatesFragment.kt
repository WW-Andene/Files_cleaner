package com.filecleaner.app.ui.duplicates

import androidx.lifecycle.LiveData
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class DuplicatesFragment : BaseFileListFragment() {
    override val screenTitle = "Duplicate Files"
    override val defaultActionLabel = "Delete selected"
    override fun actionLabel(count: Int, sizeText: String) = "Delete $count selected  ($sizeText)"
    override fun confirmTitle(count: Int) = "Delete $count files?"
    override val confirmPositiveLabel = "Delete"
    override fun liveData(): LiveData<List<FileItem>> = vm.duplicates
    override fun summaryText(count: Int, sizeText: String) = "$count duplicate files \u2014 $sizeText in duplicates"
    override val emptySummary = "No duplicates found"

    override fun onSelectAll() {
        adapter.selectAllDuplicatesExceptBest()
    }
}
