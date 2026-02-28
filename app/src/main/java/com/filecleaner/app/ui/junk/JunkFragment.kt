package com.filecleaner.app.ui.junk

import androidx.lifecycle.LiveData
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.ui.common.BaseFileListFragment

class JunkFragment : BaseFileListFragment() {
    override val screenTitle = "Junk Files"
    override val defaultActionLabel = "Clean selected"
    override fun actionLabel(count: Int, sizeText: String) = "Clean $count files  ($sizeText)"
    override fun confirmTitle(count: Int) = "Clean $count files?"
    override val confirmPositiveLabel = "Clean"
    override fun liveData(): LiveData<List<FileItem>> = vm.junkFiles
    override fun summaryText(count: Int, sizeText: String) = "$count junk files \u2014 $sizeText can be freed"
    override val emptySummary = "No junk files found"
}
