package com.filecleaner.app.viewmodel

import android.app.Application
import android.os.Environment
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.filecleaner.app.R
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

    companion object {
        private const val MAX_EXTRACT_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
        private const val MAX_EXTRACT_ENTRIES = 10_000
        private const val IO_BUFFER_SIZE = 8192
    }

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private var scanJob: Job? = null
    private val stateMutex = Mutex()

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
        val canUndo: Boolean,
        val singleFileName: String? = null
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

    // In-memory copies for reliable cache saving (postValue is async,
    // so LiveData .value may be stale when saveCache reads it)
    private var latestFiles: List<FileItem> = emptyList()
    private var latestTree: DirectoryNode? = null

    val isScanning: Boolean get() = _scanState.value is ScanState.Scanning

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scanState.value = ScanState.Cancelled
    }

    init {
        // Clean orphaned trash from previous sessions (crash/kill before confirm)
        viewModelScope.launch(Dispatchers.IO) {
            val dir = trashDir
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }

        // Load cached scan results on startup
        viewModelScope.launch {
            val cached = ScanCache.load(getApplication())
            if (cached != null) {
                val (files, tree) = cached
                // If all cached files were deleted on disk, don't show stale Done state
                if (files.isEmpty()) return@launch

                stateMutex.withLock {
                    // Skip cache load if a scan has already started (user tapped scan
                    // before cache finished loading — the scan's results take priority)
                    if (_scanState.value !is ScanState.Idle) return@withLock

                    latestFiles = files
                    latestTree = tree
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
                // Only transition to Done if no scan superseded the cache load
                if (_scanState.value is ScanState.Idle) {
                    _scanState.postValue(ScanState.Done)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Commit any pending trash — the undo window has expired since the UI is gone
        if (pendingTrash.isNotEmpty()) {
            for ((_, trashPath) in pendingTrash) {
                File(trashPath).delete()
            }
            pendingTrash.clear()
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
                    latestFiles = files
                    latestTree = tree
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
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _scanState.postValue(ScanState.Error(e.localizedMessage ?: str(R.string.op_scan_failed)))
            }

            // Cache results to disk — outside runCatching so save failure
            // doesn't override ScanState.Done with Error
            saveCache()
        }
    }

    /** Move a file to a different directory and update lists incrementally. */
    fun moveFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(filePath)
                val dst = File(targetDirPath, src.name)
                if (!src.exists()) return@withContext MoveResult(false, str(R.string.op_source_not_found))
                if (dst.exists()) return@withContext MoveResult(false, str(R.string.op_file_exists_in_target))
                if (src.renameTo(dst)) MoveResult(true, str(R.string.op_moved, src.name))
                else MoveResult(false, str(R.string.op_move_failed))
            }
            _moveResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(removedPath = filePath, addedFile = dst)
                // Tree will be stale until next scan, but showing scanning UI
                // for a single file move is disruptive — prefer incremental update
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
            val singleName = if (toDelete.size == 1) toDelete[0].name else null
            _deleteResult.postValue(DeleteResult(movedPaths.size, failed, freedBytes, canUndo = true, singleFileName = singleName))

            stateMutex.withLock {
                val deletedPaths = movedPaths.keys
                val remaining = latestFiles.filter { it.path !in deletedPaths }
                latestFiles = remaining
                _filesByCategory.postValue(remaining.groupBy { it.category })

                // Filter deleted paths, then remove orphan groups (< 2 files remaining)
                val filteredDupes = (_duplicates.value ?: emptyList())
                    .filter { it.path !in deletedPaths }
                    .let { dupes ->
                        val validGroups = dupes.groupBy { it.duplicateGroup }
                            .filter { it.value.size >= 2 }
                            .keys
                        dupes.filter { it.duplicateGroup in validGroups }
                    }
                val filteredLarge = (_largeFiles.value ?: emptyList())
                    .filter { it.path !in deletedPaths }
                val filteredJunk = (_junkFiles.value ?: emptyList())
                    .filter { it.path !in deletedPaths }

                _duplicates.postValue(filteredDupes)
                _largeFiles.postValue(filteredLarge)
                _junkFiles.postValue(filteredJunk)
                recalcStats(remaining, filteredDupes, filteredLarge, filteredJunk)
            }
            saveCache()
        }
    }

    /** Undo the last delete — move files back from trash to original locations. */
    fun undoDelete() {
        if (pendingTrash.isEmpty() || isScanning) return
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
                    val updated = latestFiles + restored
                    latestFiles = updated
                    _filesByCategory.postValue(updated.groupBy { it.category })
                    // Re-run classification for restored files
                    val dupes = DuplicateFinder.findDuplicates(updated)
                    val large = JunkFinder.findLargeFiles(updated)
                    val junk = JunkFinder.findJunk(updated)
                    _duplicates.postValue(dupes)
                    _largeFiles.postValue(large)
                    _junkFiles.postValue(junk)
                    recalcStats(updated, dupes, large, junk)
                }
                saveCache()
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

    // ── Clipboard for cut/copy/paste ──
    enum class ClipboardMode { CUT, COPY }

    data class ClipboardEntry(val item: FileItem, val mode: ClipboardMode)

    private val _clipboardEntry = MutableLiveData<ClipboardEntry?>(null)
    val clipboardEntry: LiveData<ClipboardEntry?> = _clipboardEntry

    fun setCutFile(item: FileItem) { _clipboardEntry.value = ClipboardEntry(item, ClipboardMode.CUT) }
    fun setCopyFile(item: FileItem) { _clipboardEntry.value = ClipboardEntry(item, ClipboardMode.COPY) }
    fun clearClipboard() { _clipboardEntry.value = null }

    /** Copy a file to a target directory (for paste after copy). */
    fun copyFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(filePath)
                val dst = File(targetDirPath, src.name)
                if (!src.exists()) return@withContext MoveResult(false, str(R.string.op_source_not_found))
                if (dst.exists()) return@withContext MoveResult(false, str(R.string.op_file_exists_in_target))
                try {
                    src.copyTo(dst)
                    MoveResult(true, str(R.string.op_copied, src.name))
                } catch (e: Exception) {
                    MoveResult(false, str(R.string.op_copy_failed, e.localizedMessage ?: ""))
                }
            }
            _moveResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(addedFile = dst)
            }
        }
    }

    // ── Navigate to tree highlight ──
    private val _navigateToTree = SingleLiveEvent<String?>()
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
                // Validate filename for characters invalid on Android/Linux filesystems
                if (newName.contains('/') || newName.contains('\u0000')) {
                    return@withContext MoveResult(false, str(R.string.op_invalid_name))
                }
                val src = File(oldPath)
                if (!src.exists()) return@withContext MoveResult(false, str(R.string.op_file_not_found))
                val parentDir = src.parent
                    ?: return@withContext MoveResult(false, str(R.string.op_no_parent_dir))
                val dst = File(parentDir, newName)
                if (dst.exists()) return@withContext MoveResult(false, str(R.string.op_name_exists))
                if (src.renameTo(dst)) MoveResult(true, str(R.string.op_renamed, newName))
                else MoveResult(false, str(R.string.op_rename_failed))
            }
            _operationResult.postValue(result)
            if (result.success) {
                val parentDir = File(oldPath).parent ?: return@launch
                refreshAfterFileChange(removedPath = oldPath, addedFile = File(parentDir, newName))
            }
        }
    }

    fun compressFile(filePath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val src = File(filePath)
                    if (!src.exists()) return@withContext MoveResult(false, str(R.string.op_file_not_found))
                    val parentDir = src.parent
                        ?: return@withContext MoveResult(false, str(R.string.op_no_parent_dir))
                    val zipFile = File(parentDir, "${src.nameWithoutExtension}.zip")
                    ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                        zos.putNextEntry(ZipEntry(src.name))
                        src.inputStream().buffered().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    MoveResult(true, str(R.string.op_compressed, zipFile.name))
                } catch (e: Exception) {
                    MoveResult(false, str(R.string.op_compress_failed, e.localizedMessage ?: ""))
                }
            }
            _operationResult.postValue(result)
            if (result.success) {
                val src = File(filePath)
                val parentDir = src.parent ?: return@launch
                val zipFile = File(parentDir, "${src.nameWithoutExtension}.zip")
                refreshAfterFileChange(addedFile = zipFile)
            }
        }
    }

    fun extractArchive(filePath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val src = File(filePath)
                    if (!src.exists()) return@withContext MoveResult(false, str(R.string.op_file_not_found))
                    if (!src.extension.equals("zip", ignoreCase = true)) {
                        return@withContext MoveResult(false, str(R.string.op_zip_only))
                    }
                    val parentDir = src.parent
                        ?: return@withContext MoveResult(false, str(R.string.op_no_parent_dir))
                    val outDir = File(parentDir, src.nameWithoutExtension)
                    outDir.mkdirs()
                    // Guard against zip bombs
                    var totalExtracted = 0L
                    var entryCount = 0
                    ZipInputStream(src.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(outDir, entry.name)
                            // Prevent zip slip
                            if (!outFile.canonicalPath.startsWith(outDir.canonicalPath)) {
                                entry = zis.nextEntry
                                continue
                            }
                            entryCount++
                            if (entryCount > MAX_EXTRACT_ENTRIES) {
                                return@withContext MoveResult(false,
                                    str(R.string.op_too_many_entries, MAX_EXTRACT_ENTRIES))
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().buffered().use { out ->
                                    val buf = ByteArray(IO_BUFFER_SIZE)
                                    var len: Int
                                    while (zis.read(buf).also { len = it } > 0) {
                                        totalExtracted += len
                                        if (totalExtracted > MAX_EXTRACT_BYTES) {
                                            return@withContext MoveResult(false,
                                                str(R.string.op_archive_too_large))
                                        }
                                        out.write(buf, 0, len)
                                    }
                                }
                            }
                            entry = zis.nextEntry
                        }
                    }
                    MoveResult(true, str(R.string.op_extracted, "${outDir.name}/"))
                } catch (e: Exception) {
                    MoveResult(false, str(R.string.op_extract_failed, e.localizedMessage ?: ""))
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
                var files = latestFiles.toMutableList()

                if (removedPath != null) {
                    files = files.filter { it.path != removedPath }.toMutableList()
                }
                if (addedFile != null && addedFile.exists()) {
                    files.add(FileScanner.fileToItem(addedFile))
                }

                latestFiles = files
                _filesByCategory.postValue(files.groupBy { it.category })
                val dupes = DuplicateFinder.findDuplicates(files)
                val large = JunkFinder.findLargeFiles(files)
                val junk = JunkFinder.findJunk(files)
                _duplicates.postValue(dupes)
                _largeFiles.postValue(large)
                _junkFiles.postValue(junk)
                recalcStats(files, dupes, large, junk)
            }
            saveCache()
        }
    }

    /** Persist current in-memory scan data to disk cache. */
    private fun saveCache() {
        val files = latestFiles.ifEmpty { return }
        val tree = latestTree ?: return
        viewModelScope.launch {
            try {
                ScanCache.save(getApplication(), files, tree)
            } catch (_: Exception) {
                // Non-critical — cache will be rebuilt on next scan
            }
        }
    }

    private fun recalcStats(
        remaining: List<FileItem>,
        dupes: List<FileItem>,
        large: List<FileItem>,
        junk: List<FileItem>
    ) {
        _storageStats.postValue(StorageStats(
            totalFiles    = remaining.size,
            totalSize     = remaining.sumOf { it.size },
            junkSize      = junk.sumOf { it.size },
            duplicateSize = dupes.sumOf { it.size },
            largeSize     = large.sumOf { it.size }
        ))
    }
}
