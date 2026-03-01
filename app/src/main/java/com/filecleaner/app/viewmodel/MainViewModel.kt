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
import com.filecleaner.app.data.UserPreferences
import com.filecleaner.app.utils.DuplicateFinder
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.JunkFinder
import com.filecleaner.app.utils.ScanCache
import com.filecleaner.app.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        // D5: Debounce interval for cache writes after file operations
        private const val SAVE_CACHE_DEBOUNCE_MS = 3000L

        // Characters invalid on FAT32/exFAT or that can cause shell injection issues
        private val INVALID_FILENAME_CHARS = charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|')
    }

    // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    /**
     * Validates that a file path is within external storage (not app-private dirs,
     * /data, /system, etc.) to prevent path traversal attacks.
     */
    private fun isPathWithinStorage(path: String): Boolean {
        val canonical = File(path).canonicalPath
        return canonical.startsWith(storagePath)
    }

    /** Returns true if the filename contains characters invalid on common filesystems. */
    private fun hasInvalidFilenameChars(name: String): Boolean {
        return INVALID_FILENAME_CHARS.any { it in name } || name.isBlank() || name.trim() != name
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
        val largeSize: Long,
        val scanDurationMs: Long = 0
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
    private val trashMutex = Mutex()
    private var pendingTrash = mutableMapOf<String, String>()

    // In-memory copies for reliable cache saving (postValue is async,
    // so LiveData .value may be stale when saveCache reads it)
    private var latestFiles: List<FileItem> = emptyList()
    private var latestTree: DirectoryNode? = null

    // B4: Guard against concurrent delete operations (rapid double-taps)
    private val deleteMutex = Mutex()

    // D5: Debounce cache writes — at most once per 3 seconds
    private var saveCacheJob: Job? = null

    val isScanning: Boolean get() = _scanState.value is ScanState.Scanning

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        // B1: Use postValue for thread-safety (cancelScan can be called from any thread)
        _scanState.postValue(ScanState.Cancelled)
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

                    // D2: Use cached duplicate group IDs from the saved cache instead
                    // of re-running the full MD5 hash pipeline on cold start.  The cache
                    // preserves duplicateGroup assignments from the last scan, so we only
                    // need to filter and prune orphan groups (< 2 members).
                    val dupes = files.filter { it.duplicateGroup >= 0 }
                        .let { dups ->
                            val validGroups = dups.groupBy { it.duplicateGroup }
                                .filter { it.value.size >= 2 }.keys
                            dups.filter { it.duplicateGroup in validGroups }
                        }
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
        // Use runBlocking since onCleared is synchronous and viewModelScope is cancelled
        runBlocking {
            trashMutex.withLock {
                if (pendingTrash.isNotEmpty()) {
                    for ((_, trashPath) in pendingTrash) {
                        File(trashPath).delete()
                    }
                    pendingTrash.clear()
                }
            }
        }
        // Last-resort synchronous cache save — ensures scan data is persisted
        // even when the app is killed (viewModelScope is already cancelled here)
        val files = latestFiles
        val tree = latestTree
        if (files.isNotEmpty() && tree != null) {
            runBlocking(Dispatchers.IO) {
                try {
                    ScanCache.save(getApplication(), files, tree)
                } catch (_: Exception) { }
            }
        }
    }

    fun startScan(minLargeFileMb: Int = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.Scanning(0)
            // B1: Reset derived state so stale data from previous scan isn't visible
            _duplicates.value = emptyList()
            _largeFiles.value = emptyList()
            _junkFiles.value = emptyList()
            val scanStartMs = System.currentTimeMillis()
            runCatching {
                val (files, tree) = FileScanner.scanWithTree(getApplication()) { count ->
                    _scanState.postValue(ScanState.Scanning(count, ScanPhase.INDEXING))
                }

                stateMutex.withLock {
                    latestFiles = files
                    latestTree = tree
                    _directoryTree.postValue(tree)
                    _filesByCategory.postValue(files.groupBy { it.category })

                    val protectedPaths = try { UserPreferences.protectedPaths } catch (_: Exception) { emptySet<String>() }

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.DUPLICATES))
                    val dupes = DuplicateFinder.findDuplicates(files)
                        .filter { it.path !in protectedPaths }
                    _duplicates.postValue(dupes)

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.ANALYZING))
                    val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L)
                    _largeFiles.postValue(large)

                    _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.JUNK))
                    val junk = JunkFinder.findJunk(files)
                        .filter { it.path !in protectedPaths }
                    _junkFiles.postValue(junk)

                    _storageStats.postValue(StorageStats(
                        totalFiles    = files.size,
                        totalSize     = files.sumOf { it.size },
                        junkSize      = junk.sumOf { it.size },
                        duplicateSize = dupes.sumOf { it.size },
                        largeSize     = large.sumOf { it.size },
                        scanDurationMs = System.currentTimeMillis() - scanStartMs
                    ))
                }

                _scanState.postValue(ScanState.Done)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _scanState.postValue(ScanState.Error(e.localizedMessage ?: str(R.string.op_scan_failed)))
            }

            // Cache results to disk — outside runCatching so save failure
            // doesn't override ScanState.Done with Error.
            // D5: Flush immediately after scan (no debounce) since this is the primary save.
            saveCacheNow()
        }
    }

    /** Move a file to a different directory and update lists incrementally. */
    fun moveFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(filePath)
                val dst = File(targetDirPath, src.name)
                // C2: Validate both source and target are within external storage
                if (!isPathWithinStorage(filePath) || !isPathWithinStorage(targetDirPath)) {
                    return@withContext MoveResult(false, str(R.string.op_invalid_path))
                }
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
            // B4: Prevent concurrent delete operations from rapid double-taps
            if (!deleteMutex.tryLock()) return@launch
            try {
            // Commit any previous pending trash since its undo window is being replaced
            trashMutex.withLock {
                if (pendingTrash.isNotEmpty()) {
                    commitPendingTrashLocked()
                }
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

            trashMutex.withLock {
                pendingTrash = movedPaths.toMutableMap()
            }

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
            } finally {
                deleteMutex.unlock()
            }
        }
    }

    /** Undo the last delete — move files back from trash to original locations. */
    fun undoDelete() {
        if (isScanning) return
        viewModelScope.launch {
            val snapshot = trashMutex.withLock {
                if (pendingTrash.isEmpty()) return@launch
                pendingTrash.toMap().also { pendingTrash.clear() }
            }
            val restored = withContext(Dispatchers.IO) {
                val items = mutableListOf<FileItem>()
                for ((origPath, trashPath) in snapshot) {
                    val trashFile = File(trashPath)
                    val origFile = File(origPath)
                    if (trashFile.renameTo(origFile)) {
                        items.add(FileScanner.fileToItem(origFile))
                    }
                }
                items
            }

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
        viewModelScope.launch {
            trashMutex.withLock {
                if (pendingTrash.isNotEmpty()) {
                    commitPendingTrashLocked()
                }
            }
        }
    }

    /** Must be called while holding trashMutex. */
    private suspend fun commitPendingTrashLocked() = withContext(Dispatchers.IO) {
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
                // C2: Validate both source and target are within external storage
                if (!isPathWithinStorage(filePath) || !isPathWithinStorage(targetDirPath)) {
                    return@withContext MoveResult(false, str(R.string.op_invalid_path))
                }
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

    // ── Navigate to Browse tab (filtered to a folder) ──
    private val _navigateToBrowse = MutableLiveData<String?>()
    val navigateToBrowse: LiveData<String?> = _navigateToBrowse

    fun requestBrowseFolder(folderPath: String) {
        _navigateToBrowse.value = folderPath
    }

    fun clearBrowseNavigation() {
        _navigateToBrowse.value = null
    }

    // ── Navigate to tree highlight ──
    // Uses MutableLiveData (not SingleLiveEvent) so both MainActivity (tab switch)
    // and ArborescenceFragment (highlight) can observe the same value.
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
                // C2: Validate filename for characters invalid on common filesystems
                if (hasInvalidFilenameChars(newName)) {
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

    fun batchRename(renames: List<Pair<FileItem, String>>) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                var success = 0
                var failed = 0
                for ((item, newName) in renames) {
                    val src = File(item.path)
                    val parentDir = src.parent ?: continue
                    val dst = File(parentDir, newName)
                    // C2: Validate filename for invalid filesystem characters
                    if (!src.exists() || dst.exists() || hasInvalidFilenameChars(newName)) {
                        failed++
                        continue
                    }
                    if (src.renameTo(dst)) success++ else failed++
                }
                success to failed
            }
            val (success, failed) = results
            val msg = str(R.string.batch_rename_result, success, failed)
            _operationResult.postValue(MoveResult(true, msg))

            // B1+D1: Rebuild derived state after batch rename, but carry duplicate
            // groups from old items since renaming doesn't change file content.
            stateMutex.withLock {
                val renamedPaths = renames.map { it.first.path }.toSet()
                // Build a map of old path → duplicate group for carrying over
                val oldDupeGroups = latestFiles.filter { it.path in renamedPaths && it.duplicateGroup >= 0 }
                    .associate { it.path to it.duplicateGroup }
                val remaining = latestFiles.filter { it.path !in renamedPaths }
                val newItems = renames.mapNotNull { (item, newName) ->
                    val parentDir = File(item.path).parent ?: return@mapNotNull null
                    val newFile = File(parentDir, newName)
                    if (newFile.exists()) {
                        val newItem = FileScanner.fileToItem(newFile)
                        // D1: Carry duplicate group from old item (content unchanged by rename)
                        val group = oldDupeGroups[item.path]
                        if (group != null) newItem.copy(duplicateGroup = group) else newItem
                    } else null
                }
                latestFiles = remaining + newItems
                _filesByCategory.postValue(latestFiles.groupBy { it.category })
                // D1: Incrementally update duplicate list by replacing old paths
                val currentDupes = _duplicates.value ?: emptyList()
                val dupes = currentDupes.filter { it.path !in renamedPaths }
                    .plus(newItems.filter { it.duplicateGroup >= 0 })
                    .let { dups ->
                        val validGroups = dups.groupBy { it.duplicateGroup }
                            .filter { it.value.size >= 2 }.keys
                        dups.filter { it.duplicateGroup in validGroups }
                    }
                val large = JunkFinder.findLargeFiles(latestFiles)
                val junk = JunkFinder.findJunk(latestFiles)
                _duplicates.postValue(dupes)
                _largeFiles.postValue(large)
                _junkFiles.postValue(junk)
                recalcStats(latestFiles, dupes, large, junk)
            }
        }
    }

    fun compressFile(filePath: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // C2: Validate path is within external storage
                    if (!isPathWithinStorage(filePath)) {
                        return@withContext MoveResult(false, str(R.string.op_invalid_path))
                    }
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
                    // C2: Validate path is within external storage
                    if (!isPathWithinStorage(filePath)) {
                        return@withContext MoveResult(false, str(R.string.op_invalid_path))
                    }
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
                        val outDirCanonical = outDir.canonicalPath + File.separator
                        var entry = zis.nextEntry
                        while (entry != null) {
                            // C2: Reject entries with path traversal components before resolving
                            if (entry.name.contains("..")) {
                                entry = zis.nextEntry
                                continue
                            }
                            val outFile = File(outDir, entry.name)
                            // Prevent zip slip — verify canonical path starts with output dir
                            if (!outFile.canonicalPath.startsWith(outDirCanonical) &&
                                outFile.canonicalPath != outDir.canonicalPath) {
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
                                            // Clean up partial file before returning
                                            out.close()
                                            outFile.delete()
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

    /** Incrementally update file lists after a single-file operation (rename, compress, move).
     *  D1: Avoids re-running the full DuplicateFinder hash pipeline — single-file rename/move
     *  doesn't change content, so duplicate membership is updated incrementally by path. */
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

                // D1: Incrementally update duplicate list — replace old path with new
                // path, preserving the duplicate group.  No file I/O required.
                val currentDupes = _duplicates.value ?: emptyList()
                val oldDupe = if (removedPath != null) currentDupes.find { it.path == removedPath } else null
                val updatedDupes = currentDupes
                    .filter { it.path != removedPath }
                    .let { dupes ->
                        val newItem = if (addedFile != null) files.find { it.path == addedFile.absolutePath } else null
                        if (newItem != null && oldDupe != null) {
                            dupes + newItem.copy(duplicateGroup = oldDupe.duplicateGroup)
                        } else {
                            dupes
                        }
                    }
                    .let { dupes ->
                        // Prune groups with < 2 members
                        val validGroups = dupes.groupBy { it.duplicateGroup }
                            .filter { it.value.size >= 2 }.keys
                        dupes.filter { it.duplicateGroup in validGroups }
                    }

                val large = JunkFinder.findLargeFiles(files)
                val junk = JunkFinder.findJunk(files)
                _duplicates.postValue(updatedDupes)
                _largeFiles.postValue(large)
                _junkFiles.postValue(junk)
                recalcStats(files, updatedDupes, large, junk)
            }
            saveCache()
        }
    }

    /** Persist current in-memory scan data to disk cache.
     *  D5: Debounced — rapid successive file operations (rename, move, delete)
     *  coalesce into a single write after 3 seconds of inactivity.
     *  Uses NonCancellable so the write finishes even if the scope is cancelled. */
    private fun saveCache() {
        val files = latestFiles.ifEmpty { return }
        val tree = latestTree ?: return
        saveCacheJob?.cancel()
        saveCacheJob = viewModelScope.launch {
            delay(SAVE_CACHE_DEBOUNCE_MS)
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    ScanCache.save(getApplication(), files, tree)
                } catch (_: Exception) {
                    // Non-critical — cache will be rebuilt on next scan
                }
            }
        }
    }

    /** Immediately flush any pending cache write (e.g. after a scan). */
    private fun saveCacheNow() {
        val files = latestFiles.ifEmpty { return }
        val tree = latestTree ?: return
        saveCacheJob?.cancel()
        saveCacheJob = viewModelScope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    ScanCache.save(getApplication(), files, tree)
                } catch (_: Exception) { }
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
