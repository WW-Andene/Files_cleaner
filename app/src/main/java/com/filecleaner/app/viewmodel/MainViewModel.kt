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
import com.filecleaner.app.utils.FileOperationService
import com.filecleaner.app.utils.FileScanner
import com.filecleaner.app.utils.JunkFinder
import com.filecleaner.app.utils.ScanCache
import com.filecleaner.app.utils.SingleLiveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class ScanPhase(val order: Int, val totalPhases: Int = 4) {
    INDEXING(0),
    DUPLICATES(1),
    ANALYZING(2),
    JUNK(3);

    fun baseProgress(): Int = (order * 100) / totalPhases
    fun endProgress(): Int = ((order + 1) * 100) / totalPhases
}

sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(
        val filesFound: Int,
        val phase: ScanPhase = ScanPhase.INDEXING,
        val progressPercent: Int = -1
    ) : ScanState()
    object Done : ScanState()
    object Cancelled : ScanState()
    data class Error(val message: String) : ScanState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        // D5: Debounce interval for cache writes after file operations
        private const val SAVE_CACHE_DEBOUNCE_MS = 3000L

        /** I4-04: Remove orphan duplicate groups (groups with fewer than 2 members). */
        private fun pruneOrphanDuplicates(dupes: List<FileItem>): List<FileItem> {
            val validGroups = dupes.groupBy { it.duplicateGroup }
                .filter { it.value.size >= 2 }.keys
            return dupes.filter { it.duplicateGroup in validGroups }
        }
    }

    // I5-01: Extracted responsibilities into dedicated managers
    val clipboard = ClipboardManager()
    val navigation = NavigationEvents()
    // Delegation for backward compatibility
    val clipboardEntry: LiveData<ClipboardManager.ClipboardEntry?> get() = clipboard.clipboardEntry
    fun setCutFile(item: FileItem) = clipboard.setCutFile(item)
    fun setCopyFile(item: FileItem) = clipboard.setCopyFile(item)
    fun clearClipboard() = clipboard.clearClipboard()
    val navigateToBrowse: LiveData<String?> get() = navigation.navigateToBrowse
    fun requestBrowseFolder(folderPath: String) = navigation.requestBrowseFolder(folderPath)
    fun clearBrowseNavigation() = navigation.clearBrowseNavigation()
    val navigateToTree: LiveData<String?> get() = navigation.navigateToTree
    fun requestTreeHighlight(filePath: String) = navigation.requestTreeHighlight(filePath)
    fun clearTreeHighlight() = navigation.clearTreeHighlight()

    // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
    @Suppress("DEPRECATION")
    private val storagePath: String by lazy {
        Environment.getExternalStorageDirectory().absolutePath
    }

    // I4: File operations delegated to dedicated service
    private val fileOps by lazy { FileOperationService(app, storagePath) }

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
    // ConcurrentHashMap for safe snapshot in onCleared() when mutex is unavailable
    private val trashMutex = Mutex()
    private var pendingTrash = java.util.concurrent.ConcurrentHashMap<String, String>()

    // In-memory copies for reliable cache saving (postValue is async,
    // so LiveData .value may be stale when saveCache reads it)
    private var latestFiles: List<FileItem> = emptyList()
    private var latestTree: DirectoryNode? = null

    // B4: Guard against concurrent delete operations (rapid double-taps)
    private val deleteMutex = Mutex()

    // D5: Debounce cache writes — at most once per 3 seconds
    private var saveCacheJob: Job? = null
    private val cacheLock = Any()

    @Volatile
    private var _isScanning = false
    val isScanning: Boolean get() = _isScanning

    private fun str(@StringRes id: Int): String = getApplication<Application>().getString(id)
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        // B1: Use postValue for thread-safety (cancelScan can be called from any thread)
        _scanState.postValue(ScanState.Cancelled)
        _isScanning = false
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

                // D2: Run heavy analysis on background thread to avoid blocking cold start
                val analysisResult = withContext(Dispatchers.Default) {
                    val filesByDir = files.groupBy { File(it.path).parent ?: "" }
                    val enrichedTree = enrichTreeWithFiles(tree, filesByDir)
                    val byCategory = files.groupBy { it.category }
                    val dupes = pruneOrphanDuplicates(files.filter { it.duplicateGroup >= 0 })
                    val minLargeFileMb = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }
                    val maxLargeFileCount = try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }
                    val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L, maxLargeFileCount)
                    val junk = JunkFinder.findJunk(files)
                    object {
                        val enrichedTree = enrichedTree
                        val byCategory = byCategory
                        val dupes = dupes
                        val large = large
                        val junk = junk
                    }
                }

                stateMutex.withLock {
                    // Skip cache load if a scan has already started (user tapped scan
                    // before cache finished loading — the scan's results take priority)
                    if (_scanState.value !is ScanState.Idle) return@withLock

                    latestFiles = files
                    latestTree = analysisResult.enrichedTree
                    _directoryTree.postValue(analysisResult.enrichedTree)
                    _filesByCategory.postValue(analysisResult.byCategory)

                    // D2: Use cached duplicate group IDs from the saved cache instead
                    // of re-running the full MD5 hash pipeline on cold start.  The cache
                    // preserves duplicateGroup assignments from the last scan, so we only
                    // need to filter and prune orphan groups (< 2 members).
                    _duplicates.postValue(analysisResult.dupes)
                    _largeFiles.postValue(analysisResult.large)
                    _junkFiles.postValue(analysisResult.junk)

                    _storageStats.postValue(StorageStats(
                        totalFiles    = files.size,
                        totalSize     = files.sumOf { it.size },
                        junkSize      = analysisResult.junk.sumOf { it.size },
                        duplicateSize = analysisResult.dupes.sumOf { it.size },
                        largeSize     = analysisResult.large.sumOf { it.size }
                    ))
                    // P2-A4-02: Transition to Done inside mutex to prevent race with startScan()
                    _scanState.postValue(ScanState.Done)
                    _isScanning = false
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // P2-A6-01: Acquire trashMutex to prevent racing with deleteFiles()/undoDelete()
        val trashSnapshot: Map<String, String>
        if (trashMutex.tryLock()) {
            try {
                trashSnapshot = pendingTrash.toMap()
                pendingTrash.clear()
            } finally {
                trashMutex.unlock()
            }
        } else {
            // Lock held by another coroutine; take a defensive snapshot
            trashSnapshot = pendingTrash.toMap()
        }
        val files = latestFiles
        val tree = latestTree

        // Use a standalone coroutine instead of Thread + runBlocking.
        // NonCancellable ensures completion even after scope cancellation.
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            for ((_, trashPath) in trashSnapshot) {
                File(trashPath).delete()
            }
            if (files.isNotEmpty() && tree != null) {
                try {
                    ScanCache.save(getApplication(), files, tree)
                } catch (e: Exception) {
                    android.util.Log.w("MainViewModel", "Cache save failed in onCleared", e)
                }
            }
        }
    }

    fun startScan(minLargeFileMb: Int = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.Scanning(0)
            _isScanning = true
            // B1: Reset derived state so stale data from previous scan isn't visible
            _duplicates.value = emptyList()
            _largeFiles.value = emptyList()
            _junkFiles.value = emptyList()
            val scanStartMs = System.currentTimeMillis()
            try {
            runCatching {
                val (files, tree) = FileScanner.scanWithTree(getApplication()) { count ->
                    _scanState.postValue(ScanState.Scanning(count, ScanPhase.INDEXING, progressPercent = -1))
                }

                val protectedPaths = try { UserPreferences.protectedPaths } catch (_: Exception) { emptySet<String>() }

                // Finding 2 fix: Run heavy I/O outside stateMutex
                _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.DUPLICATES, progressPercent = ScanPhase.DUPLICATES.baseProgress()))
                val dupBase = ScanPhase.DUPLICATES.baseProgress()
                val dupEnd = ScanPhase.DUPLICATES.endProgress()
                val dupes = DuplicateFinder.findDuplicates(files) { done, total ->
                    if (total > 0) {
                        val pct = dupBase + (done * (dupEnd - dupBase)) / total
                        _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.DUPLICATES, progressPercent = pct))
                    }
                }.filter { it.path !in protectedPaths }

                // D2: Batch ANALYZING+JUNK into a single phase update to reduce
                // LiveData churn — both findLargeFiles and findJunk are fast in-memory
                // operations, so separate progress updates only cause UI flicker.
                _scanState.postValue(ScanState.Scanning(files.size, ScanPhase.ANALYZING, progressPercent = ScanPhase.ANALYZING.baseProgress()))
                val maxLargeFileCount = try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }
                val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L, maxLargeFileCount)
                val junk = JunkFinder.findJunk(files)
                    .filter { it.path !in protectedPaths }

                // Only hold stateMutex for the actual state updates
                stateMutex.withLock {
                    latestFiles = files
                    latestTree = tree
                    _directoryTree.postValue(tree)
                    _filesByCategory.postValue(files.groupBy { it.category })
                    _duplicates.postValue(dupes)
                    _largeFiles.postValue(large)
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

                ensureActive() // P2-A4-01: Check cancellation before transitioning to Done
                _scanState.postValue(ScanState.Done)
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _scanState.postValue(ScanState.Error(e.localizedMessage ?: str(R.string.op_scan_failed)))
            }

            // Cache results to disk — outside runCatching so save failure
            // doesn't override ScanState.Done with Error.
            // D5: Flush immediately after scan (no debounce) since this is the primary save.
            saveCacheNow()
            } finally {
                _isScanning = false
            }
        }
    }

    /** Move a file to a different directory and update lists incrementally. */
    fun moveFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val opResult = withContext(Dispatchers.IO) { fileOps.moveFile(filePath, targetDirPath) }
            val result = MoveResult(opResult.success, opResult.message)
            _moveResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(removedPath = filePath, addedFile = dst)
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
            // Filter out protected paths before deletion
            val protectedPaths = try { UserPreferences.protectedPaths } catch (_: Exception) { emptySet<String>() }
            val safeToDelete = toDelete.filter { it.path !in protectedPaths }
            if (safeToDelete.isEmpty()) return@launch

            // Commit any previous pending trash since its undo window is being replaced
            trashMutex.withLock {
                if (pendingTrash.isNotEmpty()) {
                    commitPendingTrashLocked()
                }
            }

            val (movedPaths, freedBytes) = withContext(Dispatchers.IO) {
                val dir = trashDir
                if (!dir.exists() && !dir.mkdirs()) {
                    return@withContext emptyMap<String, String>() to 0L
                }
                var freed = 0L
                val moved = mutableMapOf<String, String>()
                for (item in safeToDelete) {
                    val src = File(item.path)
                    val dst = File(dir, "${System.nanoTime()}_${src.name}")
                    if (safeMove(src, dst)) {
                        moved[item.path] = dst.absolutePath
                        freed += item.size
                    }
                }
                moved to freed
            }

            trashMutex.withLock {
                pendingTrash.clear()
                pendingTrash.putAll(movedPaths)
            }

            val failed = safeToDelete.size - movedPaths.size
            val singleName = if (safeToDelete.size == 1) safeToDelete[0].name else null
            _deleteResult.postValue(DeleteResult(movedPaths.size, failed, freedBytes, canUndo = true, singleFileName = singleName))

            stateMutex.withLock {
                val deletedPaths = movedPaths.keys
                val remaining = latestFiles.filter { it.path !in deletedPaths }
                latestFiles = remaining
                _filesByCategory.postValue(remaining.groupBy { it.category })

                // Filter deleted paths, then remove orphan groups (< 2 files remaining)
                val filteredDupes = pruneOrphanDuplicates(
                    (_duplicates.value ?: emptyList()).filter { it.path !in deletedPaths }
                )
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
                    if (safeMove(trashFile, origFile)) {
                        items.add(FileScanner.fileToItem(origFile))
                    }
                }
                items
            }

            if (restored.isNotEmpty()) {
                // Compute updated file list under mutex, then run heavy I/O outside
                val updated = stateMutex.withLock {
                    val files = latestFiles + restored
                    latestFiles = files
                    _filesByCategory.postValue(files.groupBy { it.category })
                    files
                }
                // Heavy I/O outside mutex to avoid blocking other state operations
                val dupes = DuplicateFinder.findDuplicates(updated)
                val minLargeFileMb = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }
                val maxLargeFileCount = try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }
                val large = JunkFinder.findLargeFiles(updated, minLargeFileMb * 1024L * 1024L, maxLargeFileCount)
                val junk = JunkFinder.findJunk(updated)
                stateMutex.withLock {
                    _duplicates.postValue(dupes)
                    _largeFiles.postValue(large)
                    _junkFiles.postValue(junk)
                    recalcStats(latestFiles, dupes, large, junk)
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

    /** Move file via rename; falls back to copy+delete across filesystem boundaries. */
    private fun safeMove(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        // Fallback: copy + delete (handles cross-filesystem moves)
        return try {
            src.copyTo(dst, overwrite = false)
            if (!src.delete()) {
                // Source still exists — clean up the copy and report failure
                dst.delete()
                return false
            }
            true
        } catch (_: Exception) {
            dst.delete() // Clean up partial copy
            false
        }
    }

    /** Copy a file to a target directory (for paste after copy). */
    fun copyFile(filePath: String, targetDirPath: String) {
        viewModelScope.launch {
            val opResult = withContext(Dispatchers.IO) { fileOps.copyFile(filePath, targetDirPath) }
            val result = MoveResult(opResult.success, opResult.message)
            _moveResult.postValue(result)
            if (result.success) {
                val dst = File(targetDirPath, File(filePath).name)
                refreshAfterFileChange(addedFile = dst)
            }
        }
    }

    // ── File operations ──
    private val _operationResult = SingleLiveEvent<MoveResult>()
    val operationResult: LiveData<MoveResult> = _operationResult

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val opResult = withContext(Dispatchers.IO) { fileOps.renameFile(oldPath, newName) }
            val result = MoveResult(opResult.success, opResult.message)
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
                    if (!src.exists() || dst.exists() || fileOps.hasInvalidFilenameChars(newName)) {
                        failed++
                        continue
                    }
                    if (safeMove(src, dst)) success++ else failed++
                }
                success to failed
            }
            val (success, failed) = results
            val msg = str(R.string.batch_rename_result, success, failed)
            _operationResult.postValue(MoveResult(success > 0, msg))

            // B1+D1: Rebuild derived state after batch rename, but carry duplicate
            // groups from old items since renaming doesn't change file content.
            val (files, dupes) = stateMutex.withLock {
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
                val dupesResult = pruneOrphanDuplicates(
                    currentDupes.filter { it.path !in renamedPaths }
                        .plus(newItems.filter { it.duplicateGroup >= 0 })
                )
                latestFiles to dupesResult
            }
            // Heavy I/O outside mutex
            val minLargeFileMb = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }
            val maxLargeFileCount = try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }
            val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L, maxLargeFileCount)
            val junk = JunkFinder.findJunk(files)
            stateMutex.withLock {
                _duplicates.postValue(dupes)
                _largeFiles.postValue(large)
                _junkFiles.postValue(junk)
                recalcStats(latestFiles, dupes, large, junk)
            }
        }
    }

    fun compressFile(filePath: String) {
        compressFiles(listOf(filePath), null)
    }

    fun compressFiles(filePaths: List<String>, archiveName: String?) {
        viewModelScope.launch {
            val opResult = withContext(Dispatchers.IO) {
                fileOps.compressFiles(filePaths, archiveName)
            }
            val result = MoveResult(opResult.success, opResult.message)
            _operationResult.postValue(result)
            if (result.success) {
                val firstFile = File(filePaths.first())
                val parentDir = firstFile.parent ?: return@launch
                val name = archiveName ?: "${firstFile.nameWithoutExtension}.zip"
                val zipFile = File(parentDir, name)
                refreshAfterFileChange(addedFile = zipFile)
            }
        }
    }

    fun extractArchive(filePath: String) {
        viewModelScope.launch {
            val opResult = withContext(Dispatchers.IO) { fileOps.extractArchive(filePath) }
            val result = MoveResult(opResult.success, opResult.message)
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
                val updatedDupes = pruneOrphanDuplicates(
                    currentDupes
                        .filter { it.path != removedPath }
                        .let { dupes ->
                            val newItem = if (addedFile != null) files.find { it.path == addedFile.absolutePath } else null
                            if (newItem != null && oldDupe != null) {
                                dupes + newItem.copy(duplicateGroup = oldDupe.duplicateGroup)
                            } else {
                                dupes
                            }
                        }
                )

                val minLargeFileMb = try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }
                val maxLargeFileCount = try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }
                val large = JunkFinder.findLargeFiles(files, minLargeFileMb * 1024L * 1024L, maxLargeFileCount)
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
        synchronized(cacheLock) {
            saveCacheJob?.cancel()
            saveCacheJob = viewModelScope.launch {
                delay(SAVE_CACHE_DEBOUNCE_MS)
                // Capture state after delay so the cache reflects the latest data
                val files = latestFiles.ifEmpty { return@launch }
                val tree = latestTree ?: return@launch
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        ScanCache.save(getApplication(), files, tree)
                    } catch (_: Exception) {
                        // Non-critical — cache will be rebuilt on next scan
                    }
                }
            }
        }
    }

    /** Immediately flush any pending cache write (e.g. after a scan). */
    private fun saveCacheNow() {
        val files = latestFiles.ifEmpty { return }
        val tree = latestTree ?: return
        synchronized(cacheLock) {
            saveCacheJob?.cancel()
            saveCacheJob = viewModelScope.launch {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        ScanCache.save(getApplication(), files, tree)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    /** Reconstruct per-directory file lists in the tree from the flat file list.
     *  D2: Iterative BFS to avoid stack overflow on deep directory trees. */
    private fun enrichTreeWithFiles(
        root: DirectoryNode,
        filesByDir: Map<String, List<FileItem>>
    ): DirectoryNode {
        // First pass: collect all nodes in BFS order
        val enriched = mutableMapOf<String, DirectoryNode>()
        val queue = ArrayDeque<DirectoryNode>()
        val order = mutableListOf<DirectoryNode>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            order.add(node)
            for (child in node.children) {
                queue.add(child)
            }
        }

        // Second pass: build enriched nodes bottom-up so children are ready
        // before their parent is constructed
        for (node in order.asReversed()) {
            val enrichedChildren = node.children.map { enriched[it.path] ?: it }
            enriched[node.path] = node.copy(
                files = filesByDir[node.path] ?: emptyList(),
                children = enrichedChildren
            )
        }
        return enriched[root.path] ?: root
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
