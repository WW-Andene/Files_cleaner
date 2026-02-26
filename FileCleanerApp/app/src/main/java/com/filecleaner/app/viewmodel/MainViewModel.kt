package com.filecleaner.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.DuplicateFinder
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.JunkFinder
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val filesFound: Int) : ScanState()
    object Done : ScanState()
    data class Error(val message: String) : ScanState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private val _allFiles = MutableLiveData<List<FileItem>>(emptyList())

    // Derived lists (populated after scan)
    private val _filesByCategory = MutableLiveData<Map<FileCategory, List<FileItem>>>(emptyMap())
    val filesByCategory: LiveData<Map<FileCategory, List<FileItem>>> = _filesByCategory

    private val _duplicates = MutableLiveData<List<FileItem>>(emptyList())
    val duplicates: LiveData<List<FileItem>> = _duplicates

    private val _largeFiles = MutableLiveData<List<FileItem>>(emptyList())
    val largeFiles: LiveData<List<FileItem>> = _largeFiles

    private val _junkFiles = MutableLiveData<List<FileItem>>(emptyList())
    val junkFiles: LiveData<List<FileItem>> = _junkFiles

    private val _storageStats = MutableLiveData<StorageStats>()
    val storageStats: LiveData<StorageStats> = _storageStats

    // How many bytes we'd free by deleting junk
    data class StorageStats(
        val totalFiles: Int,
        val totalSize: Long,
        val junkSize: Long,
        val duplicateSize: Long,
        val largeSize: Long
    )

    fun startScan(minLargeFileMb: Int = 50) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning(0)
            runCatching {
                // 1. Scan
                val files = FileScanner.scanAll(getApplication()) { count ->
                    _scanState.postValue(ScanState.Scanning(count))
                }
                _allFiles.postValue(files)

                // 2. Classify by category
                _filesByCategory.postValue(files.groupBy { it.category })

                // 3. Find duplicates
                val dupes = DuplicateFinder.findDuplicates(files)
                _duplicates.postValue(dupes)

                // 4. Large files
                val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L)
                _largeFiles.postValue(large)

                // 5. Junk files
                val junk = JunkFinder.findJunk(files)
                _junkFiles.postValue(junk)

                // 6. Stats
                _storageStats.postValue(StorageStats(
                    totalFiles    = files.size,
                    totalSize     = files.sumOf { it.size },
                    junkSize      = junk.sumOf { it.size },
                    duplicateSize = dupes.sumOf { it.size },
                    largeSize     = large.sumOf { it.size }
                ))

                _scanState.postValue(ScanState.Done)
            }.onFailure { e ->
                _scanState.postValue(ScanState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /** Deletes a list of FileItems from disk and removes them from all caches. */
    fun deleteFiles(toDelete: List<FileItem>) {
        viewModelScope.launch {
            val paths = toDelete.map { it.path }.toSet()
            toDelete.forEach { it.file.delete() }

            val remaining = _allFiles.value?.filter { it.path !in paths } ?: return@launch
            _allFiles.postValue(remaining)
            _filesByCategory.postValue(remaining.groupBy { it.category })
            _duplicates.postValue(_duplicates.value?.filter { it.path !in paths })
            _largeFiles.postValue(_largeFiles.value?.filter { it.path !in paths })
            _junkFiles.postValue(_junkFiles.value?.filter { it.path !in paths })
        }
    }
}
