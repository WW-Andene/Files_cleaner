package com.filecleaner.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.filecleaner.app.data.FileItem

/**
 * I5-01: Extracted from MainViewModel to reduce god-class responsibilities.
 * Manages cut/copy/paste clipboard state for file operations.
 */
class ClipboardManager {

    enum class ClipboardMode { CUT, COPY }

    data class ClipboardEntry(val item: FileItem, val mode: ClipboardMode)

    private val _clipboardEntry = MutableLiveData<ClipboardEntry?>(null)
    val clipboardEntry: LiveData<ClipboardEntry?> = _clipboardEntry

    fun setCutFile(item: FileItem) { _clipboardEntry.value = ClipboardEntry(item, ClipboardMode.CUT) }
    fun setCopyFile(item: FileItem) { _clipboardEntry.value = ClipboardEntry(item, ClipboardMode.COPY) }
    fun clearClipboard() { _clipboardEntry.value = null }
}
