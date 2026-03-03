package com.filecleaner.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.DeleteResult
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.data.OperationResult
import com.filecleaner.app.data.StorageStats
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.utils.DuplicateFinder
import com.filecleaner.app.utils.FileOperationHelper
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.JunkFinder
import com.filecleaner.app.utils.ScanCache
import com.filecleaner.app.data.ScanPhase
import com.filecleaner.app.data.ScanState
import com.filecleaner.app.utils.SingleLiveEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private var scanJob: Job? = null
    private val stateMutex = Mutex()

    private val _allFiles = MutableLiveData<List<FileItem>>(emptyList())

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

    private val _directoryTree = MutableLiveData<DirectoryNode?>(null)
    val directoryTree: LiveData<DirectoryNode?> = _directoryTree

    private val _deleteResult = SingleLiveEvent<DeleteResult>()
    val deleteResult: LiveData<DeleteResult> = _deleteResult

    // True while a file operation (delete, rename, compress, extract, move) is running
    private val _isOperating = MutableLiveData(false)
    val isOperating: LiveData<Boolean> = _isOperating

    // Trash directory for undo support (F-026)
    private val trashDir: File
        get() = File(
            getApplication<Application>().getExternalFilesDir(null),
            ".trash"
        )

    // Map of original path -> trash path for pending undo
    private var pendingTrash = mutableMapOf<String, String>()

    val isScanning: Boolean get() = _scanState.value is ScanState.Scanning

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Cancelled
    }

    init {
        // Load cached scan results on startup
        viewModelScope.launch {
            val cached = ScanCache.load(getApplication())
            if (cached != null) {
                val (files, tree) = cached
                stateMutex.withLock {
                    _allFiles.postValue(files)
                    _directoryTree.postValue(tree)
                    _filesByCategory.postValue(files.groupBy { it.category })

                    val dupes = DuplicateFinder.findDuplicates(files)
                    _duplicates.postValue(dupes)

                    val large = JunkFinder.findLargeFiles(files)
                    _largeFiles.postValue(large)

                    val junk = JunkFinder.findJunk(files)
                    _junkFiles.postValue(junk)

                    _storageStats.postValue(StorageStats(
                        totalFiles    = files.size,
                        totalSize     = files.sumOf { it.size },
                        junkSize      = junk.sumOf { it.size },
                        duplicateSize = dupes.sumOf { it.size },
                        largeSize     = large.sumOf { it.size }
                    ))
                }
                _scanState.postValue(ScanState.Done)
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            // Read saved large file threshold from preferences
            val thresholdMb = UserPreferences.largeFileThresholdMb(getApplication()).first()

            _scanState.value = ScanState.Scanning(0)
            runCatching {
                val (files, tree) = FileScanner.scanWithTree(getApplication()) { count ->
                    _scanState.postValue(ScanState.Scanning(count, ScanPhase.INDEXING))
                }

                stateMutex.withLock {
                    _allFiles.postValue(files)
                    _directoryTree.postValue(tree)
                    _filesByCategory.postValue(files.groupBy { it.category })

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.DUPLICATES))
                    val dupes = DuplicateFinder.findDuplicates(files)
                    _duplicates.postValue(dupes)

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.ANALYZING))
                    val large = JunkFinder.findLargeFiles(files, thresholdMb * 1024L * 1024L)
                    _largeFiles.postValue(large)

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.JUNK))
                    val junk = JunkFinder.findJunk(files)
                    _junkFiles.postValue(junk)

                    _storageStats.postValue(StorageStats(
                        totalFiles    = files.size,
                        totalSize     = files.sumOf { it.size },
                        junkSize      = junk.sumOf { it.size },
                        duplicateSize = dupes.sumOf { it.size },
                        largeSize     = large.sumOf { it.size }
                    ))
                }

                _scanState.postValue(ScanState.Done)

                // Cache results to disk for persistence
                ScanCache.save(getApplication(), files, tree)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _scanState.postValue(ScanState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    /** Move a file to a different directory and update lists + tree incrementally. */
    fun moveFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = FileOperationHelper.moveFile(filePath, targetDirPath)
            _operationResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(removedPath = filePath, addedFile = dst)
                // Rebuild tree from updated file list (no filesystem re-scan)
                rebuildTreeFromFiles()
            }
            _isOperating.value = false
        }
    }

    /** Rebuild the directory tree from the current file list (no filesystem re-scan). */
    private fun rebuildTreeFromFiles() {
        viewModelScope.launch {
            val currentFiles = _allFiles.value ?: return@launch
            val newTree = FileScanner.buildTreeFromFiles(currentFiles)
            _directoryTree.postValue(newTree)
        }
    }

    /**
     * Moves files to trash (app-private .trash dir) instead of deleting immediately.
     * Call confirmDelete() to permanently remove, or undoDelete() to restore.
     *
     * If a previous delete is pending undo, it is permanently committed first
     * (since the undo snackbar for it would be replaced by the new one).
     */
    fun deleteFiles(toDelete: List<FileItem>) {
        if (isScanning) return
        viewModelScope.launch {
            _isOperating.value = true
            // Commit any previous pending trash since its undo window is being replaced
            if (pendingTrash.isNotEmpty()) {
                commitPendingTrash()
            }

            val (movedPaths, freedBytes) = FileOperationHelper.softDeleteFiles(toDelete, trashDir)
            pendingTrash = movedPaths.toMutableMap()

            val failed = toDelete.size - movedPaths.size
            _deleteResult.postValue(DeleteResult(movedPaths.size, failed, freedBytes, canUndo = true))

            stateMutex.withLock {
                val deletedPaths = movedPaths.keys
                val remaining = _allFiles.value?.filter { it.path !in deletedPaths } ?: return@launch
                _allFiles.postValue(remaining)
                _filesByCategory.postValue(remaining.groupBy { it.category })
                _duplicates.postValue(_duplicates.value?.filter { it.path !in deletedPaths })
                _largeFiles.postValue(_largeFiles.value?.filter { it.path !in deletedPaths })
                _junkFiles.postValue(_junkFiles.value?.filter { it.path !in deletedPaths })
                recalcStats(remaining)
            }
            _isOperating.value = false
        }
    }

    /** Undo the last delete — move files back from trash to original locations. */
    fun undoDelete() {
        if (pendingTrash.isEmpty()) return
        viewModelScope.launch {
            val restored = FileOperationHelper.undoDelete(pendingTrash)
            pendingTrash.clear()

            if (restored.isNotEmpty()) {
                stateMutex.withLock {
                    val current = _allFiles.value ?: emptyList()
                    val updated = current + restored
                    _allFiles.postValue(updated)
                    _filesByCategory.postValue(updated.groupBy { it.category })
                    // Re-run classification for restored files
                    val dupes = DuplicateFinder.findDuplicates(updated)
                    _duplicates.postValue(dupes)
                    _largeFiles.postValue(JunkFinder.findLargeFiles(updated))
                    _junkFiles.postValue(JunkFinder.findJunk(updated))
                    recalcStats(updated)
                }
            }
        }
    }

    /** Permanently delete trashed files (called when undo window expires). */
    fun confirmDelete() {
        viewModelScope.launch { commitPendingTrash() }
    }

    private suspend fun commitPendingTrash() {
        FileOperationHelper.commitTrash(pendingTrash)
        pendingTrash.clear()
    }

    // ── Clipboard for cut/paste (moved from FileContextMenu static state) ──
    private val _clipboardItem = MutableLiveData<FileItem?>(null)
    val clipboardItem: LiveData<FileItem?> = _clipboardItem

    fun setCutFile(item: FileItem) { _clipboardItem.value = item }
    fun clearClipboard() { _clipboardItem.value = null }

    // ── Navigate to tree highlight ──
    private val _navigateToTree = MutableLiveData<String?>()
    val navigateToTree: LiveData<String?> = _navigateToTree

    fun requestTreeHighlight(filePath: String) {
        _navigateToTree.value = filePath
    }

    fun clearTreeHighlight() {
        _navigateToTree.value = null
    }

    // ── File operations ──
    private val _operationResult = SingleLiveEvent<OperationResult>()
    val operationResult: LiveData<OperationResult> = _operationResult

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = FileOperationHelper.renameFile(oldPath, newName)
            _operationResult.postValue(result)
            if (result.success) {
                refreshAfterFileChange(removedPath = oldPath, addedFile = File(File(oldPath).parent, newName))
            }
            _isOperating.value = false
        }
    }

    fun compressFile(filePath: String) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = FileOperationHelper.compressFile(filePath)
            _operationResult.postValue(result)
            if (result.success) {
                val zipFile = File(File(filePath).parent, "${File(filePath).nameWithoutExtension}.zip")
                refreshAfterFileChange(addedFile = zipFile)
            }
            _isOperating.value = false
        }
    }

    fun extractArchive(filePath: String) {
        viewModelScope.launch {
            _isOperating.value = true
            val result = FileOperationHelper.extractArchive(filePath)
            _operationResult.postValue(result)
            if (result.success) {
                // Extract creates many files — rescan is appropriate here
                startScan()
            }
            _isOperating.value = false
        }
    }

    /** Incrementally update file lists after a single-file operation (rename, compress, move). */
    private fun refreshAfterFileChange(removedPath: String? = null, addedFile: File? = null) {
        viewModelScope.launch {
            stateMutex.withLock {
                var files = _allFiles.value?.toMutableList() ?: return@launch

                if (removedPath != null) {
                    files = files.filter { it.path != removedPath }.toMutableList()
                }
                if (addedFile != null && addedFile.exists()) {
                    files.add(FileScanner.fileToItem(addedFile))
                }

                _allFiles.postValue(files)
                _filesByCategory.postValue(files.groupBy { it.category })
                _duplicates.postValue(DuplicateFinder.findDuplicates(files))
                _largeFiles.postValue(JunkFinder.findLargeFiles(files))
                _junkFiles.postValue(JunkFinder.findJunk(files))
                recalcStats(files)
            }
        }
    }

    private fun recalcStats(remaining: List<FileItem>) {
        val dupes = _duplicates.value ?: emptyList()
        val large = _largeFiles.value ?: emptyList()
        val junk = _junkFiles.value ?: emptyList()
        _storageStats.postValue(StorageStats(
            totalFiles    = remaining.size,
            totalSize     = remaining.sumOf { it.size },
            junkSize      = junk.sumOf { it.size },
            duplicateSize = dupes.sumOf { it.size },
            largeSize     = large.sumOf { it.size }
        ))
    }
}
