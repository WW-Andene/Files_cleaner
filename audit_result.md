# RACCOON FILE MANAGER — FULL AUDIT RESULTS

---

## PHASE 0 — FOUNDATION

### Step 0.1 — App Context Block (§0)

```yaml
App Name: Raccoon File Manager
Platform: Android (minSdk 29 / Android 10, targetSdk 35 / Android 15)
Framework: Kotlin + Android SDK + Material Components 1.12.0
Architecture: Single-Activity MVVM — NavHostFragment with 14 fragment destinations,
              single MainViewModel as shared state holder, ViewBinding for UI,
              LiveData for reactive state, Coroutines for async.
Entry Point: MainActivity.kt (splash → single-top activity, OAuth callback handler)
             FileCleanerApp.kt (Application class — init UserPreferences, CloudConnectionStore)

Key Files:
  - MainViewModel.kt (790 LOC) — Central state: scan, files, duplicates, delete/undo, cache
  - ArborescenceView.kt (1235 LOC) — Custom Canvas-drawn tree visualization
  - FileViewerFragment.kt (920 LOC) — Multi-format file previewer
  - DualPaneFragment.kt (797 LOC) — Split-screen file browser
  - BrowseFragment.kt (756 LOC) — Primary file browser with search
  - AntivirusFragment.kt (754 LOC) — Security scanning UI
  - ConvertDialog.kt (739 LOC) — File format conversion dialog
  - FileConverter.kt (566 LOC) — Conversion logic
  - AppIntegrityScanner.kt (712 LOC) — App integrity verification engine
  - CloudBrowserFragment.kt (578 LOC) — Cloud storage browser
  - OAuthHelper.kt (354 LOC) — OAuth2 flow for cloud providers
  - BaseFileListFragment.kt (483 LOC) — Shared base for list fragments
  - ScanCache.kt (210 LOC) — Disk cache for scan results

Dependencies:
  - AndroidX Core 1.13.1, AppCompat 1.7.0, ConstraintLayout 2.1.4
  - Material Components 1.12.0
  - Navigation 2.7.7 (SafeArgs)
  - Lifecycle (ViewModel/LiveData/Runtime) 2.8.7
  - RecyclerView 1.3.2
  - Coroutines 1.8.1
  - Glide 4.16.0 (image loading, KSP annotation processing)
  - Browser 1.8.0 (Chrome Custom Tabs for OAuth)
  - Security Crypto 1.1.0-alpha06 (EncryptedSharedPreferences)
  - JSch 0.2.21 (SFTP/SSH — mwiede fork with Terrapin CVE fix)
  - SplashScreen 1.0.1
  - JUnit 4.13.2 (test only)

Constraints:
  - Single-activity, fragment-based navigation
  - No backend server — all local + optional cloud providers
  - localStorage via SharedPreferences + EncryptedSharedPreferences
  - File cache via app-private JSON files (ScanCache)
  - No build tool beyond Gradle (no Hilt/Dagger, no Room, no Retrofit)
  - ProGuard/R8 minification enabled for release
  - MANAGE_EXTERNAL_STORAGE required (file manager category app)

Design Identity:
  Theme: "Chromatic warm surfaces" — forest green (#247A58) primary + warm amber (#E8861F) accent
         Dark mode: green-tinted near-blacks, lifted green (#5ECE9E) + amber (#F2A84E)
         Never neutral gray — all surfaces have warm/green hue bias
  Personality: "Playful woodland utility" — Ricky Raccoon mascot, emoji-driven categories,
               friendly copy ("Ricky is rummaging through your files..."), but precise data presentation
  Signature:
    - Raccoon mascot character throughout (hub, bubble widget, scan messages)
    - Forest green + amber color pair — unique in file manager category
    - Chromatic warm-white surfaces (not Material default gray)
    - Arborescence tree visualization (custom Canvas-drawn directory tree)
    - Emoji-driven file category system

Domain Rules:
  - [CODE] File categorization: O(1) extension lookup via flatLookup map (FileItem.kt:32-36)
  - [CODE] Duplicate detection: MD5 hash-based grouping via DuplicateFinder.kt
  - [CODE] Large file threshold: configurable via UserPreferences.largeFileThresholdMb (default 50MB)
  - [CODE] Max large files displayed: configurable via UserPreferences.maxLargeFiles (default 200)
  - [CODE] Junk detection: pattern-based (cache, temp, thumbnail, old downloads, logs) via JunkFinder.kt
  - [CODE] Orphan duplicate pruning: groups with < 2 members removed (MainViewModel.kt:61-65)
  - [CODE] Trash-based delete: files moved to .trash dir, undo window before permanent deletion
  - [CODE] Cache debounce: 3000ms (MainViewModel.kt:58)
  - [CODE] Scan phases: INDEXING → DUPLICATES → ANALYZING → JUNK (4 phases)
  - [CODE] Protected paths: user-configured paths excluded from delete/junk operations
  - [CODE] Spacing base unit: 4dp (dimens.xml:4)
  - [CODE] Type scale: Major Third (1.25×) — 10/11/12/13/14/16/20/26/32 sp
  - [CODE] Motion vocabulary: micro=120ms, enter=220ms, exit=160ms, page=280ms, emphasis=400ms
  - [CODE] Elevation scale: 0/1/2/4/8/16 dp

Test Vectors:
  - [CODE] FileCategoryTest.kt (174 LOC) — extension → category mapping
  - [CODE] SearchQueryParserTest.kt (255 LOC) — search query parsing
  - [CODE] DuplicateFinderTest.kt — duplicate detection
  - [CODE] JunkFinderTest.kt — junk pattern matching
  - [CODE] FileOperationServiceTest.kt — file ops (rename, move, compress)
  - [CODE] UndoHelperTest.kt — undo/redo
  - [CODE] BatchRenameTest.kt — batch rename operations

Workflows:
  1: Scan → Browse → Select files → Delete (with undo) / Move / Rename
  2: Scan → View duplicates → Select duplicates → Delete to free space
  3: Scan → Browse → Open file viewer → Preview / Convert / Compress
  4: Cloud setup (OAuth) → Browse cloud files → Download
  5: Security scan (antivirus) → Review threats → Take action

Known Issues: None explicitly documented by developer

App Maturity: Active development (v1.2.1, versionCode 4)
Expected Scale: Single user (personal device file manager)
Likeliest Next Features:
  - Scheduled automatic scans
  - File sharing / send-to functionality
  - Advanced search filters (date range, size range)
  - Batch file operations (multi-select move/copy)
  - Storage usage trend history
```

### Step 0.2 — Adaptive Calibration (§I)

#### §I.1 — Domain Classification

**Domain: Productivity/Utility (File Manager)**
- Amplify: §B (State Management), §D (Performance), §F (UX)
- Stakes: MEDIUM
- Additional amplifier: §C (Security) elevated to HIGH due to:
  - MANAGE_EXTERNAL_STORAGE permission (access to all user files)
  - Cloud provider OAuth with token storage
  - Antivirus/security scanning features claiming to protect users
  - EncryptedSharedPreferences for credential storage

#### §I.2 — Architecture Classification

**Architecture: Single-Activity MVVM with Fragment Navigation**

Primary failure modes to hunt:
- **Stale LiveData observers** — fragments re-observing after config change, receiving stale events
- **ViewModel scope leaks** — coroutines outliving expected lifecycle
- **Fragment back stack state loss** — SavedState not preserved on process death
- **Mutex deadlocks** — multiple Mutex instances (stateMutex, trashMutex, deleteMutex, cacheLock)
- **Race conditions** — concurrent scan + delete + cache operations
- **Memory pressure** — large file lists held in memory (latestFiles: List<FileItem>)
- **ANR risks** — file I/O on main thread, large list operations
- **RecyclerView performance** — large datasets without proper diffing
- **Navigation state corruption** — manual bottom nav handling vs NavController state

#### §I.3 — App Size → Audit Depth

**21,652 LOC (Kotlin) + ~8,000 LOC (XML) = ~30,000 LOC total**
→ **Full depth audit** (> 6,000 LOC threshold)
→ Confirming plan with user before continuing (> 3,000 LOC threshold)

#### §I.4 — Five-Axis Aesthetic Profile

| Axis | Rating | Notes |
|------|--------|-------|
| 1. Minimal ↔ Expressive | **6/10** | Emoji categories, raccoon mascot add expression; core UI is clean utility |
| 2. Functional ↔ Emotional | **5/10** | Balanced — precise data presentation + playful mascot personality |
| 3. Conventional ↔ Distinctive | **7/10** | Chromatic warm surfaces + raccoon identity diverge strongly from category norms |
| 4. Light ↔ Dark | **5/10** | Full DayNight support, both modes well-developed |
| 5. Static ↔ Animated | **6/10** | Defined motion vocabulary (5 duration tiers), stagger animations, custom transitions |

#### §I.5 — Domain Rule Extraction

All domain rules extracted and tagged in §0 above. Summary:
- 14 rules tagged `[CODE]` (directly from source)
- 0 rules tagged `[UNVERIFIED]`
- No formulas requiring external domain verification (this is a utility app, not financial/medical)

#### §I.6 — Iron Laws (§II) Confirmation

Confirmed:
- Will NOT invent findings — only report what is present in code
- Will NOT remove protected design elements (raccoon mascot, forest green + amber palette, chromatic surfaces)
- Will NOT over-report confidence — every finding gets explicit confidence tag
- Will NOT conflate code observations with domain truth

---

### Phase 0 Output Summary

| Deliverable | Status |
|-------------|--------|
| Completed §0 block | Done |
| Domain class + severity multipliers | Productivity/Utility, §B/§D/§F amplified, §C elevated to HIGH |
| Architecture class + failure mode list | Single-Activity MVVM, 9 failure modes identified |
| Five-Axis profile | 6/5/7/5/6 |
| Domain rules inventory (tagged) | 14 rules, all [CODE] |
| Feature Preservation Ledger | Below |

### Feature Preservation Ledger

Every working feature that MUST be preserved:

| # | Feature | Status | Key Files |
|---|---------|--------|-----------|
| 1 | Storage scanning (4-phase: index → duplicates → analyze → junk) | WORKING | MainViewModel.kt, FileScanner.kt, ScanService.kt |
| 2 | File browsing with category filtering | WORKING | BrowseFragment.kt, BrowseAdapter.kt |
| 3 | File search with parsed queries | WORKING | SearchQueryParser.kt, BrowseFragment.kt |
| 4 | Duplicate file detection (MD5 hash) | WORKING | DuplicateFinder.kt, DuplicatesFragment.kt |
| 5 | Large file detection (configurable threshold) | WORKING | JunkFinder.kt, LargeFilesFragment.kt |
| 6 | Junk file detection (pattern-based) | WORKING | JunkFinder.kt, JunkFragment.kt |
| 7 | File deletion with undo (trash-based) | WORKING | MainViewModel.kt, UndoHelper.kt |
| 8 | File rename (single + batch) | WORKING | MainViewModel.kt, BatchRenameDialog.kt |
| 9 | File move/copy with clipboard | WORKING | MainViewModel.kt, ClipboardManager.kt |
| 10 | File compression (ZIP) | WORKING | MainViewModel.kt, CompressDialog.kt |
| 11 | Archive extraction | WORKING | MainViewModel.kt, FileOperationService.kt |
| 12 | File format conversion | WORKING | FileConverter.kt, ConvertDialog.kt |
| 13 | Arborescence tree visualization | WORKING | ArborescenceView.kt, ArborescenceFragment.kt |
| 14 | Dual-pane file browser | WORKING | DualPaneFragment.kt, PaneAdapter.kt |
| 15 | Storage analysis dashboard | WORKING | StorageDashboardFragment.kt, AnalysisFragment.kt |
| 16 | Storage optimization | WORKING | OptimizeFragment.kt, StorageOptimizer.kt |
| 17 | Cloud storage (Google Drive, GitHub, SFTP, WebDAV) | WORKING | Cloud providers, CloudBrowserFragment.kt |
| 18 | OAuth2 authentication flow | WORKING | OAuthHelper.kt, CloudSetupDialog.kt |
| 19 | Antivirus/security scanning | WORKING | AntivirusFragment.kt, SignatureScanner.kt |
| 20 | App integrity verification | WORKING | AppIntegrityScanner.kt |
| 21 | Network security scanning | WORKING | NetworkSecurityScanner.kt |
| 22 | Privacy auditing | WORKING | PrivacyAuditor.kt |
| 23 | App verification scanning | WORKING | AppVerificationScanner.kt |
| 24 | File preview (images, text, code, audio) | WORKING | FileViewerFragment.kt, FilePreviewDialog.kt |
| 25 | Scan result caching (disk persistence) | WORKING | ScanCache.kt |
| 26 | Raccoon Manager hub (mascot-driven home) | WORKING | RaccoonManagerFragment.kt |
| 27 | Raccoon bubble widget | WORKING | RaccoonBubble.kt |
| 28 | Onboarding dialog | WORKING | OnboardingDialog.kt |
| 29 | Settings (theme, thresholds, protected paths) | WORKING | SettingsFragment.kt, UserPreferences.kt |
| 30 | Bottom navigation with badges | WORKING | MainActivity.kt |
| 31 | Keyboard shortcuts (Ctrl+S, Ctrl+F) | WORKING | MainActivity.kt |
| 32 | Privacy notice dialog | WORKING | MainActivity.kt |
| 33 | Dark mode (DayNight with full color system) | WORKING | colors.xml, colors.xml (night) |
| 34 | Crash reporting | WORKING | CrashReporter.kt |
| 35 | Context menu (long-press file actions) | WORKING | FileContextMenu.kt |
| 36 | Color-coded file categories with legend | WORKING | ColorLegendHelper.kt, ColorMode.kt |
| 37 | View modes (list, compact, grid) | WORKING | ViewMode.kt, FileAdapter.kt |
| 38 | Foreground scan service | WORKING | ScanService.kt |
| 39 | Protected path exclusion | WORKING | MainViewModel.kt, UserPreferences.kt |

---

**Phase 0 is complete.**

---

## PHASE 1 — DOMAIN LOGIC & CORRECTNESS (Category A)

### Step 1.1 — §A1: Business Rule & Formula Correctness

```
[MEDIUM] — F-001: moveFile() fallback copy+delete is not atomic
Section: §A1 — Business Rule Correctness
Finding: FileOperationService.kt:54-62 — moveFile() falls back to copy+delete when
  renameTo() fails (cross-filesystem). If the app crashes after copyTo() succeeds but
  before delete() executes, the source file remains, creating a silent duplicate.
  Additionally, if delete() returns false (permissions, file locked), the copy is kept
  but no error is reported — the method returns OpResult(true, "Moved...").
Why it matters: User thinks file was moved, but original still exists. Could cause
  confusion and wasted storage — exactly the problem this app is designed to solve.
Recommendation: Check delete() return value. If false, delete the copy and return
  OpResult(false, "Move failed: could not remove source"). Same pattern exists in
  renameFile() at line 94-102.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-002: DuplicateFinder skips files >200MB silently
Section: §A1 — Business Rule Correctness
Finding: DuplicateFinder.kt:92 — Files exceeding MAX_FULL_HASH_SIZE (200MB) are silently
  skipped in Stage 3 (full hash). Two large identical files (e.g., movies, disk images)
  that pass the partial hash stage are dropped without being flagged as potential duplicates.
  No UI indication that large files were excluded from duplicate analysis.
Why it matters: Large files are the highest-value duplicates for space recovery. Users
  expect the "Duplicates" tab to show all duplicates, not silently exclude the biggest ones.
Recommendation: Either (a) add a "possible duplicates (too large to verify)" category with
  a warning badge, or (b) add a note in the duplicates UI: "Files >200MB excluded from
  hash verification." The partial hash match is already a strong signal — consider reporting
  these as "likely duplicates" with lower confidence.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-003: StorageOptimizer date formatting uses device locale
Section: §A1 — Business Rule Correctness
Finding: StorageOptimizer.kt:38 — SimpleDateFormat uses Locale.getDefault() for the
  month folder name (yyyy-MM format). While the format itself is locale-independent for
  year-month, the Locale.getDefault() could theoretically produce different calendar
  systems on some devices (e.g., Buddhist calendar on Thai locale returns year 2569
  instead of 2026), creating inconsistent folder names.
Why it matters: Photos organized by date could end up in unexpected folders on certain locales.
Recommendation: Use Locale.ROOT or Locale.US for the date formatter to ensure Gregorian
  calendar consistency: `SimpleDateFormat("yyyy-MM", Locale.ROOT)`.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[LOW] — F-004: Search size operators use binary units inconsistently
Section: §A1 — Business Rule Correctness
Finding: SearchQueryParser.kt:57-61 — Size operators use binary multipliers (1024-based:
  KB=1024, MB=1048576, GB=1073741824) while UndoHelper.formatBytes() also uses binary
  units. This is internally consistent, which is good. However, Android's storage APIs
  and most file managers use decimal units (1 KB = 1000 bytes). A user searching ">1gb"
  expects ~1,000,000,000 bytes but gets filtered at 1,073,741,824 bytes — a 7% gap.
Why it matters: Minor UX confusion. Users may think some files are missing from search results.
Recommendation: Document the behavior (binary units) in search help text. No code change
  strictly required since the app is internally consistent.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 1.2 — §A2: Probability & Statistical Correctness

No probability, RNG, or statistical calculations found in this codebase. The app is a
deterministic file management utility. The antivirus scanner uses hash-based matching,
not probabilistic scoring.

**Result: N/A — no findings.**

### Step 1.3 — §A3: Temporal & Timezone Correctness

```
[LOW] — F-005: JunkFinder stale download cutoff uses System.currentTimeMillis() without timezone consideration
Section: §A3 — Temporal Correctness
Finding: JunkFinder.kt:32 — The stale download cutoff uses `System.currentTimeMillis() -
  TimeUnit.DAYS.toMillis(staleDays)`. File.lastModified() also returns epoch millis. This
  is internally consistent and timezone-safe (both use UTC epoch). No DST or timezone
  issue exists here.
  However, StorageOptimizer.kt:46 uses `Date(file.lastModified)` which could produce
  incorrect month folders if the file's lastModified was set by a different timezone
  system (e.g., camera set to wrong timezone). This is an edge case.
Why it matters: Minor — users might see photos in slightly wrong month folders in
  optimization suggestions.
Recommendation: No action required. The implementation is correct for the common case.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-006: SearchQueryParser "before:" date boundary is end-of-day but timezone-unaware
Section: §A3 — Temporal Correctness
Finding: SearchQueryParser.kt:82 — `beforeMs = ms + MILLIS_PER_DAY` adds 86,400,000ms
  to make "before:2025-06-01" inclusive of the whole day. SimpleDateFormat with Locale.US
  parses dates at midnight in the device's default timezone. If a user searches
  "before:2025-06-01" and their timezone is UTC+12, files modified at 11pm UTC on
  June 1st would be excluded because the cutoff is midnight June 2nd in NZST, which is
  noon June 1st UTC.
Why it matters: Edge case — only affects users in extreme timezones searching for files
  near date boundaries. Functionally acceptable for a personal file manager.
Recommendation: Accept as-is. This level of timezone precision is appropriate for the
  app's use case.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

### Step 1.4 — §A4: State Machine Correctness

```
[MEDIUM] — F-007: OAuth PKCE state stored in static variables — lost on process death
Section: §A4 — State Machine Correctness
Finding: OAuthHelper.kt:48-49 — `pendingCodeVerifier` and `pendingProvider` are stored as
  static fields in an `object`. When the user is redirected to the browser for OAuth
  authorization, Android may kill the app process (common on low-memory devices). When the
  browser redirects back via deep link, the app restarts with a fresh process —
  pendingCodeVerifier is null, and the token exchange fails silently.
Why it matters: OAuth flow silently fails after process death. Users get "No pending OAuth
  provider" error with no recovery path. On low-memory devices, this could happen frequently.
Recommendation: Persist pendingCodeVerifier and pendingProvider in SharedPreferences (or
  SavedStateHandle) before launching the browser. Clear them after token exchange completes
  or fails. The verifier is a cryptographic nonce — not a secret that needs encryption.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-008: ScanState transition from Cancelled lacks cleanup
Section: §A4 — State Machine Correctness
Finding: MainViewModel.kt:173-179 — cancelScan() posts ScanState.Cancelled and sets
  _isScanning = false, but does not clear partial state from in-progress scan phases.
  If cancelled during the DUPLICATES phase (after INDEXING completed), latestFiles still
  holds the partial file list from indexing. Starting a new scan immediately clears
  _duplicates, _largeFiles, _junkFiles (line 290-292) which is correct.
  However, if the user doesn't start a new scan, the stale latestFiles from the cancelled
  scan remain and get cached to disk via saveCache().
Why it matters: Minor — stale partial data from cancelled scan could be cached and loaded on
  next cold start, showing incomplete results without a "scan cancelled" indicator.
Recommendation: Consider clearing latestFiles and latestTree on cancellation, or marking
  the cache as "partial" so cold-start loading can show an appropriate message.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[MEDIUM] — F-009: ScanService shared static state has no synchronization
Section: §A4 — State Machine Correctness
Finding: ScanService.kt:31-39 — Scan results are communicated from the service to the
  fragment via `@Volatile` static fields: `isRunning`, `currentProgress`, `currentPhase`,
  `scanResults`, `scanComplete`. While `@Volatile` ensures visibility, it does not ensure
  atomicity across multiple fields. A fragment reading these values could see `scanComplete
  = true` but `scanResults = null` if the read happens between the two assignments at
  lines 152-153.
Why it matters: Race condition could cause NPE or missing results in the antivirus UI.
  Fragment polls these values on a timer (typical pattern for service communication).
Recommendation: Wrap all state updates in a synchronized block, or use a single
  `data class ScanStatus(progress, phase, results, complete)` atomic reference.
  Better yet, use a SharedFlow or BroadcastChannel for service → fragment communication.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-010: Undo state machine allows confirm after undo
Section: §A4 — State Machine Correctness
Finding: MainViewModel.kt:497-505 — confirmDelete() permanently deletes trashed files.
  UndoHelper.kt:38-44 — The snackbar calls confirmDelete() on dismissal (except ACTION
  and CONSECUTIVE events). If the user taps "Undo" (which calls undoDelete() and clears
  pendingTrash), and then the snackbar's onDismissed fires with DISMISS_EVENT_ACTION,
  confirmDelete() is correctly skipped. This is properly handled.
  However, if confirmDelete() is called manually from elsewhere in code while pendingTrash
  is empty, it's a no-op — which is safe. No actual bug here.
Why it matters: N/A — confirmed correct.
Recommendation: None — the state machine is sound.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

### Step 1.5 — §A5: Embedded Data Accuracy

```
[MEDIUM] — F-011: Antivirus malware hash DB is effectively empty
Section: §A5 — Embedded Data Accuracy
Finding: SignatureScanner.kt:28-36 — The "known malware" database contains only 3 MD5
  hashes and 1 SHA-256 hash. One is the EICAR test file (a standardized antivirus test
  string, not actual malware). The other two ("e1105070..." and "3395856c...") are
  unverifiable without more context.
  The code comment says "In production, load from updatable DB" but no such mechanism exists.
  The app presents this as a security scanner to users.
Why it matters: Users may trust the "antivirus" scan to detect threats. With effectively
  0 real signatures, the hash-based detection provides false confidence. The heuristic
  checks (ELF binaries, suspicious scripts, etc.) add value, but the hash matching is
  essentially theater.
Recommendation: Either (a) clearly label the scanner as "heuristic-only" in the UI and
  remove the hash matching, or (b) implement an updatable signature database with
  periodic downloads from a threat feed. Option (a) is more honest for the app's
  current maturity level.
Effort: LOW (relabel) / HIGH (implement real DB)
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-012: File extension map is comprehensive but missing some modern formats
Section: §A5 — Embedded Data Accuracy
Finding: FileItem.kt:22-28 — The extension → category map covers 90+ extensions. Missing
  some increasingly common formats:
  - IMAGE: missing "jfif" (JPEG variant), "tif" (alternate tiff extension)
  - VIDEO: missing "vob" (DVD), "f4v" (Flash video)
  - AUDIO: missing "caf" (Core Audio), "mka" (Matroska audio)
  - DOCUMENT: missing "djvu" (DjVu documents), "tex" (LaTeX), "org" (Org-mode)
  - ARCHIVE: missing "lz4", "br" (Brotli), "apk" is in APK but "aab" (Android App Bundle) is not
Why it matters: Minor — unrecognized files fall to OTHER category, which is functionally
  correct but loses category-specific filtering benefits.
Recommendation: Add the most common missing extensions. Priority: "jfif" (very common
  from web downloads), "aab" (developers), "vob" (DVD rips).
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

### Step 1.6 — §A6: Async & Concurrency

```
[HIGH] — F-013: SftpProvider holds Mutex lock during all I/O operations — potential deadlock and starvation
Section: §A6 — Async & Concurrency
Finding: SftpProvider.kt — Every operation (listFiles:105, download:133, upload:143,
  delete:156, createDirectory:173) acquires `lock.withLock {}` for the entire duration
  of I/O. This means:
  1. A large file download blocks ALL other SFTP operations (including listFiles) for
     the entire transfer duration
  2. If a download is in progress and the user navigates away, triggering disconnect(),
     disconnect() will block waiting for the lock — but the download won't be cancelled
     because there's no cancellation mechanism inside the lock
  3. retryOnNetworkError inside the lock means the lock is held during retry delays
     (up to 1s + 2s + 4s = 7 seconds of backoff)
Why it matters: SFTP browsing becomes unresponsive during any file transfer. The UI
  freezes (spinner never stops) because listFiles() is blocked waiting for the lock.
Recommendation: Use a separate lock for connection state vs operations, or use a
  Semaphore(1) with tryAcquire timeout. Better: don't lock I/O operations — only lock
  connect/disconnect which mutate session/channel state. Use the channel reference
  atomically but don't hold the lock during data transfer.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[MEDIUM] — F-014: onCleared() uses NonCancellable coroutine — potential resource leak
Section: §A6 — Async & Concurrency
Finding: MainViewModel.kt:270-282 — onCleared() launches a coroutine with
  `NonCancellable + Dispatchers.IO` to delete trash and save cache. This coroutine
  has no timeout and no scope management. If the file operations hang (e.g., storage
  unmounted, permission revoked), this coroutine runs indefinitely, holding references
  to the Application context and the trash/cache data.
  This is a known compromise (comment explains the intent), but the lack of timeout
  means the coroutine could outlive the Activity lifecycle by a significant margin.
Why it matters: Potential memory retention in edge cases (storage issues). The Application
  reference via getApplication() prevents GC of the Application object, though in practice
  the Application lives for the entire process anyway.
Recommendation: Add a `withTimeout(10_000)` wrapper around the operations in onCleared()
  to ensure the coroutine doesn't run indefinitely. Catch TimeoutCancellationException
  and log a warning.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[MEDIUM] — F-015: Race between cache load and scan start
Section: §A6 — Async & Concurrency
Finding: MainViewModel.kt:191-247 (init block) vs :284-357 (startScan). The init block
  launches a coroutine to load cached data. startScan() can be called immediately (user
  taps scan before cache finishes loading). The guard at line 220 (`if (_scanState.value
  !is ScanState.Idle) return@withLock`) correctly prevents cache from overwriting scan
  results. However, there's a window between lines 287-289 where startScan() sets
  `_scanState.value = ScanState.Scanning(0)` on the main thread, and the cache loader
  checks `_scanState.value` inside `stateMutex.withLock`. Since setValue() is synchronous
  on the main thread and the cache loader uses postValue(), the ordering is correct IF
  the cache loader hasn't already passed the `if` check before startScan() sets the value.
  The stateMutex protects the state update, and the value check is inside the lock, so
  this race is actually handled correctly.
Why it matters: N/A — confirmed correct after analysis.
Recommendation: None — the race is properly guarded by stateMutex. The code comment at
  line 219 accurately describes the protection.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-016: cacheLock uses synchronized {} but saveCacheJob cancellation + re-launch is not atomic
Section: §A6 — Async & Concurrency
Finding: MainViewModel.kt:710-727 — saveCache() uses `synchronized(cacheLock)` to cancel
  the previous job and launch a new one. However, `viewModelScope.launch {}` returns
  immediately — the actual coroutine body runs asynchronously. If two calls to saveCache()
  arrive in rapid succession, the first cancel + launch happens, then the second cancel +
  launch happens. The second cancel cancels the first launch (correct). But the
  `synchronized` block is unnecessary since viewModelScope serializes on the main thread
  and `saveCacheJob?.cancel()` is thread-safe.
  This is over-synchronized but not buggy.
Why it matters: No functional impact — just unnecessary synchronization overhead.
Recommendation: Remove `synchronized(cacheLock)` — viewModelScope.launch is main-thread-safe,
  and saveCacheJob cancel is already thread-safe via Kotlin coroutines internals.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 1.7 — §A7: Type Coercion & Implicit Conversion (Kotlin Equivalent)

```
[LOW] — F-017: BMP writer integer overflow for very large images
Section: §A7 — Type Coercion
Finding: FileConverter.kt:91-93 — BMP header calculation:
  `val rowSize = ((24L * w + 31) / 32) * 4` — correctly uses Long
  `val imageSize = rowSize * h` — h is Int, but rowSize is Long, so result is Long (correct)
  `val fileSize = 54 + imageSize` — Long addition (correct)
  Line 96: `if (fileSize > Int.MAX_VALUE)` — correctly guards against overflow
  Line 113: `out.write(intToBytes(imageSize.toInt()))` — but this is called AFTER the
  overflow check, so imageSize is guaranteed <= Int.MAX_VALUE. Safe.
  However, at line 120: `val row = ByteArray(rowSize.toInt())` — if rowSize exceeds
  Int.MAX_VALUE (image width > ~89 million pixels), this would throw. In practice,
  Bitmap.getPixel() would already fail for such images, so this is a non-issue.
Why it matters: Theoretical only — Android Bitmap can't hold images large enough to trigger this.
Recommendation: None — the existing guard is sufficient for real-world usage.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-018: SignatureScanner large APK size comparison uses Long but Int display
Section: §A7 — Type Coercion
Finding: SignatureScanner.kt:352 — `(item.size / (1024 * 1024)).toInt()` — item.size is
  Long, 1024*1024 is Int (1048576), the division produces Long, .toInt() is safe because
  the result is always < 200,000 (max file size on Android storage). No overflow risk.
Why it matters: N/A — correct as-is.
Recommendation: None.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

---

### Phase 1 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §A1 — Business Rules | 4 | 0 | 0 | 1 | 3 |
| §A2 — Probability | 0 (N/A) | 0 | 0 | 0 | 0 |
| §A3 — Temporal | 2 | 0 | 0 | 0 | 2 |
| §A4 — State Machine | 4 | 0 | 0 | 2 | 2 |
| §A5 — Embedded Data | 2 | 0 | 0 | 1 | 1 |
| §A6 — Async/Concurrency | 4 | 0 | 1 | 2 | 1 |
| §A7 — Type Coercion | 2 | 0 | 0 | 0 | 2 |
| **TOTAL** | **18** | **0** | **1** | **6** | **11** |

### Positive Verifications (Phase 1)

1. **File categorization O(1) lookup** — correctly implemented via pre-built flatLookup map [CODE]
2. **Duplicate detection 3-stage pipeline** — sound algorithm (size → partial hash → full MD5) [CODE]
3. **Orphan duplicate pruning** — correctly removes groups with < 2 members [CODE]
4. **Trash-based undo** — robust state machine with proper mutex protection [CODE]
5. **Scan phase progress** — correctly maps 4 phases to percentage ranges [CODE]
6. **ZIP path traversal protection** — extractArchive() properly validates canonical paths [CODE]
7. **ZIP bomb protection** — MAX_EXTRACT_BYTES (2GB) and MAX_EXTRACT_ENTRIES (10K) limits [CODE]
8. **PKCE OAuth flow** — proper S256 code challenge with SecureRandom verifier [CODE]
9. **Cache race protection** — stateMutex guards cache-load vs scan-start race correctly [CODE]
10. **Delete concurrency guard** — deleteMutex.tryLock() prevents rapid double-tap issues [CODE]
11. **CSV parser** — proper RFC 4180 quote handling with escaped quotes [CODE]
12. **Search query parser** — robust regex-based parsing with all operators combinable [CODE]
13. **Coroutine cancellation** — ensureActive() checks at appropriate intervals throughout I/O loops [CODE]

---

**Phase 1 is complete.**

**Next: Phase 2 — State Management & Data Integrity (Category B)**

Awaiting confirmation to proceed with Phase 2, or to fix the HIGH/MEDIUM findings from Phase 1.
