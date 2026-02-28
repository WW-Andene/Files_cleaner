package com.filecleaner.app.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import com.filecleaner.app.utils.DuplicateFinder
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.JunkFinder
import com.filecleaner.app.utils.ScanCache
import com.filecleaner.app.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ScanPhase { INDEXING, DUPLICATES, ANALYZING, JUNK }

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val filesFound: Int, val phase: ScanPhase = ScanPhase.INDEXING) : ScanState()
    object Done : ScanState()
    object Cancelled : ScanState()
    data class Error(val message: String) : ScanState()
}

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

    private val _moveResult = SingleLiveEvent<MoveResult>()
    val moveResult: LiveData<MoveResult> = _moveResult

    private val _deleteResult = SingleLiveEvent<DeleteResult>()
    val deleteResult: LiveData<DeleteResult> = _deleteResult

    data class StorageStats(
        val totalFiles: Int,
        val totalSize: Long,
        val junkSize: Long,
        val duplicateSize: Long,
        val largeSize: Long
    )

    data class DeleteResult(
        val moved: Int,
        val failed: Int,
        val freedBytes: Long,
        val canUndo: Boolean
    )

    data class MoveResult(val success: Boolean, val message: String)

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

    fun startScan(minLargeFileMb: Int = 50) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
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
                    val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L)
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

    /** Move a file to a different directory and update lists incrementally. */
    fun moveFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(filePath)
                val dst = File(targetDirPath, src.name)
                if (!src.exists()) return@withContext MoveResult(false, "Source file not found")
                if (dst.exists()) return@withContext MoveResult(false, "File already exists in target")
                if (src.renameTo(dst)) MoveResult(true, "Moved ${src.name}")
                else MoveResult(false, "Failed to move file")
            }
            _moveResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(removedPath = filePath, addedFile = dst)
                // Also rebuild directory tree since paths changed
                startScan()
            }
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
            // Commit any previous pending trash since its undo window is being replaced
            if (pendingTrash.isNotEmpty()) {
                commitPendingTrash()
            }

            val (movedPaths, freedBytes) = withContext(Dispatchers.IO) {
                val dir = trashDir
                dir.mkdirs()
                var freed = 0L
                val moved = mutableMapOf<String, String>()
                for (item in toDelete) {
                    val src = File(item.path)
                    val dst = File(dir, "${System.nanoTime()}_${src.name}")
                    if (src.renameTo(dst)) {
                        moved[item.path] = dst.absolutePath
                        freed += item.size
                    }
                }
                moved to freed
            }

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
        }
    }

    /** Undo the last delete — move files back from trash to original locations. */
    fun undoDelete() {
        if (pendingTrash.isEmpty()) return
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                val items = mutableListOf<FileItem>()
                for ((origPath, trashPath) in pendingTrash) {
                    val trashFile = File(trashPath)
                    val origFile = File(origPath)
                    if (trashFile.renameTo(origFile)) {
                        items.add(FileScanner.fileToItem(origFile))
                    }
                }
                items
            }
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

    private suspend fun commitPendingTrash() = withContext(Dispatchers.IO) {
        for ((_, trashPath) in pendingTrash) {
            File(trashPath).delete()
        }
        pendingTrash.clear()
    }

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
    private val _operationResult = SingleLiveEvent<MoveResult>()
    val operationResult: LiveData<MoveResult> = _operationResult

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(oldPath)
                if (!src.exists()) return@withContext MoveResult(false, "File not found")
                val dst = File(src.parent, newName)
                if (dst.exists()) return@withContext MoveResult(false, "File with that name already exists")
                if (src.renameTo(dst)) MoveResult(true, "Renamed to $newName")
                else MoveResult(false, "Rename failed")
            }
            _operationResult.postValue(result)
            if (result.success) {
                refreshAfterFileChange(removedPath = oldPath, addedFile = File(File(oldPath).parent, newName))
            }
        }
    }

    fun compressFile(filePath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val src = File(filePath)
                    if (!src.exists()) return@withContext MoveResult(false, "File not found")
                    val zipFile = File(src.parent, "${src.nameWithoutExtension}.zip")
                    ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                        zos.putNextEntry(ZipEntry(src.name))
                        src.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    MoveResult(true, "Compressed to ${zipFile.name}")
                } catch (e: Exception) {
                    MoveResult(false, "Compression failed: ${e.message}")
                }
            }
            _operationResult.postValue(result)
            if (result.success) {
                val zipFile = File(File(filePath).parent, "${File(filePath).nameWithoutExtension}.zip")
                refreshAfterFileChange(addedFile = zipFile)
            }
        }
    }

    fun extractArchive(filePath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val src = File(filePath)
                    if (!src.exists()) return@withContext MoveResult(false, "File not found")
                    if (!src.extension.equals("zip", ignoreCase = true)) {
                        return@withContext MoveResult(false, "Only ZIP archives can be extracted")
                    }
                    val outDir = File(src.parent, src.nameWithoutExtension)
                    outDir.mkdirs()
                    ZipInputStream(src.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(outDir, entry.name)
                            // Prevent zip slip
                            if (!outFile.canonicalPath.startsWith(outDir.canonicalPath)) {
                                entry = zis.nextEntry
                                continue
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().buffered().use { zis.copyTo(it) }
                            }
                            entry = zis.nextEntry
                        }
                    }
                    MoveResult(true, "Extracted to ${outDir.name}/")
                } catch (e: Exception) {
                    MoveResult(false, "Extraction failed: ${e.message}")
                }
            }
            _operationResult.postValue(result)
            if (result.success) {
                // Extract creates many files — rescan is appropriate here
                startScan()
            }
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
