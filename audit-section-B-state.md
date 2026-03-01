# Section B — State Management & Data Integrity: Full Audit Report

**App**: Raccoon File Manager
**Auditor**: Claude (automated)
**Date**: 2026-03-01
**Scope**: MainViewModel, ScanCache, UserPreferences, SingleLiveEvent, all 8 Fragment files, FileAdapter, BrowseAdapter, ArborescenceView

---

## B1 — State Architecture

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B1-01 | **MEDIUM** | **FIXED** | `startScan()` didn't reset derived state (`_duplicates`, `_largeFiles`, `_junkFiles`) before scanning — stale data from previous scan remained visible during rescan. **Fix**: Reset all three to `emptyList()` at scan start. |
| B1-02 | **MEDIUM** | **FIXED** | `batchRename()` updated `_filesByCategory` but didn't recalculate duplicates/large/junk/stats — derived state became inconsistent after batch rename. **Fix**: Full recalculation of all derived state via `DuplicateFinder` and `JunkFinder` after batch rename. |
| B1-03 | **LOW** | Accepted | `SingleLiveEvent` only supports one observer — `deleteResult` observed by multiple fragments. Only the currently active fragment receives the event, which is acceptable for single-screen UX. |
| B1-04 | **LOW** | **FIXED** | `cancelScan()` used `setValue()` which must be called on main thread, but `cancelScan()` could be called from any thread. **Fix**: Changed to `postValue()`. |
| B1-05 | **PASS** | No fix needed | All `MutableLiveData` initialized with correct default values (`emptyList()`, `emptyMap()`, `ScanState.Idle`). |
| B1-06 | **PASS** | No fix needed | `stateMutex` correctly prevents cache load from overwriting a scan that has already started. |

---

## B2 — Persistence & Storage

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B2-01 | **MEDIUM** | **FIXED** | `ScanCache.save()` had no entry limit — a device with millions of files could produce a 100+ MB JSON cache. **Fix**: Added `MAX_CACHED_FILES = 50,000` cap to prevent unbounded growth. |
| B2-02 | **PASS** | No fix needed | Schema versioning via `CACHE_VERSION` works correctly — incompatible cache is deleted and recreated. |
| B2-03 | **PASS** | No fix needed | SharedPreferences properly scoped with `MODE_PRIVATE` in app-private storage. |
| B2-04 | **PASS** | No fix needed | `UserPreferences.init()` called in `MainActivity.onCreate()` before any fragment creation — no `lateinit` crash possible. |
| B2-05 | **PASS** | No fix needed | Cache stored in `context.filesDir` — correct app-private directory, auto-cleaned on uninstall. |
| B2-06 | **INFO** | Documented | Stale paths in cached data intentionally not validated at load time (comment documents rationale: storage permission may not be active yet on cold start). Stale entries refreshed on next scan. |

---

## B3 — Reactive State Correctness

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B3-01 | **PASS** | No fix needed | All fragment observers correctly use `viewLifecycleOwner` — no lifecycle mismatches. |
| B3-02 | **PASS** | No fix needed | `MainActivity` observers use `this` (Activity lifecycle) — correct for Activity-scoped navigation and badge updates. |
| B3-03 | **HIGH** | **FIXED** | `ArborescenceFragment.navigateToTree` observer registered a nested observer on `directoryTree` without proper lifecycle management — if tree never loaded before fragment destruction, observer leaked permanently. **Fix**: Replaced nested observer with `pendingHighlightPath` field + `tryHighlightPendingPath()` method called from the existing tree observer. |
| B3-04 | **PASS** | No fix needed | `SingleLiveEvent` correctly prevents re-delivery on config change using `AtomicBoolean.compareAndSet`. |
| B3-05 | **MEDIUM** | **FIXED** | `BaseFileListFragment` didn't save/restore `searchQuery` on config change — user's search was lost on rotation. **Fix**: Added `KEY_SEARCH_QUERY` to `onSaveInstanceState` and restoration in `onViewCreated`. |
| B3-06 | **MEDIUM** | **FIXED** | `BrowseFragment` didn't save/restore search query, sort order, category position, or extension selections on config change. **Fix**: Added 4 new Bundle keys for full state persistence. |
| B3-07 | **MEDIUM** | **FIXED** | `ArborescenceFragment` didn't save/restore `filterPanelVisible`, `selectedTreeExtensions`, or spinner category position. **Fix**: Added 3 new Bundle keys and restoration logic. |

---

## B4 — State Synchronization

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B4-01 | **HIGH** | **FIXED** | `deleteFiles()` had no protection against concurrent execution from rapid double-taps — two coroutines could try to move the same files to trash simultaneously. **Fix**: Added `deleteMutex` with `tryLock()` — second delete attempt is silently skipped if first is still running. |
| B4-02 | **PASS** | No fix needed | `postValue` vs `setValue` used correctly throughout — all background thread updates use `postValue`, main thread updates use `setValue`. |
| B4-03 | **INFO** | Documented | `SingleLiveEvent` events can be lost if fragment is destroyed between operation start and completion. Acceptable — undo snackbar is a best-effort UX enhancement, not a critical data path. |

---

## B5 — State Reset & Cleanup

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B5-01 | **PASS** | No fix needed | All 8 fragments properly null out `_binding` in `onDestroyView()`. |
| B5-02 | **PASS** | No fix needed | `BaseFileListFragment` and `BrowseFragment` remove Handler callbacks in `onDestroyView()`. |
| B5-03 | **HIGH** | **FIXED** | `ArborescenceView.onDetachedFromWindow()` cancelled `highlightAnimator` but didn't cancel pending `postDelayed` fade-out callbacks — could fire after view destruction causing NPE. **Fix**: Extracted `fadeOutRunnable` as a named field, added `removeCallbacks(fadeOutRunnable)` in `onDetachedFromWindow()`, and `removeCallbacks` before each `postDelayed` to prevent stacking. |
| B5-04 | **MEDIUM** | **FIXED** | `SettingsFragment.onDestroyView()` didn't remove SeekBar and Switch listeners — callbacks could fire on destroyed binding. **Fix**: Set all 4 listeners to `null` before nulling binding. |
| B5-05 | **PASS** | No fix needed | `ViewModel.onCleared()` properly commits pending trash and saves cache. |
| B5-06 | **PASS** | No fix needed | Glide properly scoped to lifecycle via `Glide.with(icon)` and `Glide.clear()` on non-image views. |
| B5-07 | **PASS** | No fix needed | RecyclerView adapters use `ListAdapter` with DiffUtil — proper lifecycle integration. |

---

## B6 — State Testing

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| B6-01 | **PASS** | No fix needed | All `ScanState` transitions are valid: `Idle→Scanning→Done`, `Scanning→Error`, `Scanning→Cancelled`, and restart from any terminal state. |
| B6-02 | **PASS** | No fix needed | `CancellationException` is rethrown (line 274) — ensures cancelled coroutines don't fall through to `Error` state. |
| B6-03 | **PASS** | No fix needed | `runCatching` wraps all scan logic — any exception transitions to `Error`. No path exists to get stuck in `Scanning` state. |
| B6-04 | **INFO** | Documented | Brief `postValue` timing inconsistency possible — if exception occurs right after a phase `postValue`, UI might show `Scanning(DUPLICATES)` momentarily before switching to `Error`. Not a stuck state, just a transient visual glitch. |

---

## Files Modified

| File | Changes |
|------|---------|
| `MainViewModel.kt` | Reset derived state at scan start (B1-01). Full derived state recalculation in `batchRename()` (B1-02). Thread-safe `cancelScan()` (B1-04). `deleteMutex` for concurrent delete protection (B4-01). |
| `ScanCache.kt` | `MAX_CACHED_FILES` cap on cache entries (B2-01). |
| `ArborescenceFragment.kt` | Replaced nested observer with `pendingHighlightPath` + `tryHighlightPendingPath()` (B3-03). Full filter/extension/search state persistence (B3-07). |
| `BaseFileListFragment.kt` | Search query persistence across config change (B3-05). |
| `BrowseFragment.kt` | Search, sort, category, extension state persistence (B3-06). |
| `SettingsFragment.kt` | Listener cleanup in `onDestroyView()` (B5-04). |
| `ArborescenceView.kt` | Named `fadeOutRunnable` with proper cleanup in `onDetachedFromWindow()` (B5-03). |

---

## Overall Section B Rating

| Subsection | Rating | Issues Found | Issues Fixed |
|------------|--------|--------------|--------------|
| B1 — State Architecture | **GOOD** (was FAIR) | 4 | 3 fixed, 1 accepted |
| B2 — Persistence & Storage | **GOOD** (was FAIR) | 1 | 1 fixed |
| B3 — Reactive State | **GOOD** (was POOR) | 4 | 4 fixed |
| B4 — State Synchronization | **GOOD** (was FAIR) | 1 | 1 fixed |
| B5 — State Reset & Cleanup | **GOOD** (was FAIR) | 2 | 2 fixed |
| B6 — State Testing | **EXCELLENT** | 0 | 0 |
| **OVERALL** | **GOOD** | **12** | **11 fixed, 1 accepted** |
