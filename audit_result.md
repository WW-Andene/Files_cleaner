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

---

## PHASE 2 — STATE MANAGEMENT & DATA INTEGRITY (Category B)

### Step 2.1 — §B1: State Architecture

**Architecture overview:**
- **Single shared ViewModel**: `MainViewModel` (activityViewModels()) is the single source of truth for all scan results, file lists, duplicates, junk, large files, storage stats, and directory tree.
- **MutableLiveData → LiveData** pattern used consistently for all observable state (12 LiveData fields).
- **SingleLiveEvent** used for one-shot events: `_deleteResult`, `_moveResult`, `_operationResult` — prevents stale event delivery on config change.
- **Extracted managers**: `ClipboardManager` and `NavigationEvents` extracted from ViewModel with backward-compatible delegation properties.
- **Fragment state**: Both `BrowseFragment` and `BaseFileListFragment` properly save/restore all UI state via `onSaveInstanceState` (view mode, sort order, search query, selections, category position, collapsed folders, extension filters).

```
[LOW] — F-019: ClipboardManager state is not persisted across process death
Section: §B1 — State Architecture
Finding: ClipboardManager.kt — The cut/copy clipboard is backed by a MutableLiveData
  in memory only. If Android kills the process while the user has a file "cut", the
  clipboard state is lost. There is no SavedStateHandle or SharedPreferences backup.
  The MainViewModel does not use SavedStateHandle for any state.
Why it matters: Minor — clipboard is ephemeral by nature in most file managers.
  However, if the user "cuts" a file, switches to another app, and Android kills the
  process, they'll return to find their cut operation silently lost. The source file
  is still intact (cut only moves on paste), so no data loss occurs.
Recommendation: Accept as-is. Clipboard is inherently ephemeral. If desired, a
  SavedStateHandle could persist the clipboard path across process death.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[MEDIUM] — F-020: LiveData.postValue() coalescence may drop intermediate scan state transitions
Section: §B1 — State Architecture
Finding: MainViewModel.kt — Scan progress uses postValue() extensively (lines 297, 303,
  309, 316, 343, 346). The Android documentation explicitly states that if postValue()
  is called multiple times before the main thread processes the pending value, only the
  last value is dispatched to observers.
  During the DUPLICATES phase, progress updates fire per-file (line 307-309). If the main
  thread is busy (e.g., RecyclerView layout), multiple progress percentage updates will be
  coalesced — losing intermediate progress values. This is cosmetic for progress.
  More concerning: if cancelScan() calls `_scanState.postValue(ScanState.Cancelled)` while
  a concurrent postValue(ScanState.Done) is pending on the main thread, one of them will
  be dropped. The ordering depends on which postValue() call wins the internal lock.
Why it matters: Progress bar may jump or appear stuck during heavy I/O. In the worst case,
  a scan cancellation could be silently overwritten by a Done state (or vice versa), leaving
  the UI in an inconsistent state.
Recommendation: For progress updates: accept coalescence (cosmetic). For state transitions
  (Cancelled, Done, Error): use `withContext(Dispatchers.Main) { _scanState.value = ... }`
  instead of postValue() to ensure deterministic ordering on the main thread. Alternatively,
  use ensureActive() before the Done postValue (already done at line 342) and verify
  cancellation state on the main thread.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 2.2 — §B2: Persistence & Storage

```
[LOW] — F-021: ScanHistoryManager stores file paths in plaintext SharedPreferences
Section: §B2 — Persistence & Storage
Finding: ScanHistoryManager.kt — Scan history records (including scanned file paths and
  threat descriptions) are stored in regular SharedPreferences, not EncryptedSharedPreferences.
  The app stores cloud credentials in EncryptedSharedPreferences (CloudConnectionStore.kt)
  but does not extend encryption to scan history.
Why it matters: Minor privacy concern — scan history reveals which files exist on the device
  and which were flagged as threats. On a rooted device, another app could read this data.
  The 30-day cache expiry in ScanCache provides some mitigation for scan data, but
  ScanHistoryManager has its own separate 20-record limit without time-based expiry.
Recommendation: Consider adding a time-based expiry to ScanHistoryManager (e.g., 30 days
  matching ScanCache) to limit exposure window. Encryption is optional given the low
  sensitivity of file paths.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[LOW] — F-022: UserPreferences uses try/catch fallbacks that silently mask initialization failures
Section: §B2 — Persistence & Storage
Finding: MainViewModel.kt — Multiple callsites wrap UserPreferences access in try/catch
  with hardcoded defaults:
  - Line 204: `try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }`
  - Line 205: `try { UserPreferences.maxLargeFiles } catch (_: Exception) { 200 }`
  - Line 284: `try { UserPreferences.largeFileThresholdMb } catch (_: Exception) { 50 }`
  - Line 300: `try { UserPreferences.protectedPaths } catch (_: Exception) { emptySet() }`
  These try/catch blocks exist because UserPreferences.init(context) may not have been
  called yet. FileCleanerApp.onCreate() calls init(), but if any code runs before
  Application.onCreate() (e.g., ContentProvider), it would fail silently.
Why it matters: The fallback pattern works but masks bugs — if init() is accidentally
  removed or reordered, the app silently uses defaults instead of crashing early. Users
  would see their custom thresholds ignored without any error indication.
Recommendation: Consider making UserPreferences.init() throw a clear error if accessed
  before initialization, rather than relying on callers to catch. Or use lazy initialization
  that doesn't require an explicit init() call.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

### Step 2.3 — §B3: Input Validation & Sanitization

```
[LOW] — F-023: BatchRenameDialog regex pattern not validated for empty groups
Section: §B3 — Input Validation & Sanitization
Finding: BatchRenameDialog.kt — The "Find & Replace" mode accepts regex input from the
  user with ReDoS timeout protection (FutureTask with 500ms timeout). This is good.
  However, the "Pattern" mode (line 75-85) uses a template string with {n}, {name}, {ext}
  placeholders but does not validate that the resulting filename is valid for the filesystem
  (e.g., contains no '/', '\0', or reserved names like 'CON', 'NUL' on FAT32 volumes).
  The underlying File.renameTo() will simply fail, but the user gets no pre-validation
  feedback about which characters are invalid.
Why it matters: Minor UX issue — users won't understand why a pattern-based rename failed
  if the pattern produces invalid filenames. The app handles the failure gracefully (returns
  false from renameTo), but the error message is generic.
Recommendation: Add pre-validation in the dialog to check for invalid filesystem characters
  before attempting the rename. Show inline feedback for invalid patterns.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — BatchRenameDialog ReDoS protection
The regex "Find & Replace" mode uses a FutureTask with 500ms timeout to prevent
catastrophic backtracking. This is a robust defense against user-supplied regex patterns
that could hang the app. Confirmed working at BatchRenameDialog.kt:110-125.
```

### Step 2.4 — §B4: Import & Export Integrity

```
[POSITIVE VERIFICATION] — ZIP extraction path traversal and bomb protection
FileOperationService.kt:126-171 — extractArchive() validates that extracted entries
resolve within the target directory via canonicalPath comparison. ZIP bomb protection
limits total extracted bytes (MAX_EXTRACT_BYTES = 2GB) and entry count (MAX_EXTRACT_ENTRIES
= 10,000). Both checks are correctly applied before writing any data.
```

```
[POSITIVE VERIFICATION] — ScanCache version and bounds validation
ScanCache.kt — load() validates CACHE_VERSION (rejects mismatched versions), enforces
MAX_CACHED_FILES (50,000) and MAX_TREE_DEPTH (100) bounds, and applies 30-day expiry.
save() uses atomic temp-file-then-rename pattern to prevent corruption from crashes
during write. Streaming JSON reader prevents OOM on very large caches.
```

```
[LOW] — F-024: ScanCache atomic write uses renameTo() which can fail on some Android filesystems
Section: §B4 — Import & Export Integrity
Finding: ScanCache.kt — The atomic save pattern writes to a temp file, then calls
  `tempFile.renameTo(cacheFile)`. While renameTo() is atomic on ext4/f2fs (standard
  Android filesystems), it returns false (instead of throwing) when it fails. The code
  at save() does check the return value and falls back, but the fallback simply deletes
  the temp file without retrying — meaning the cache update is silently lost.
Why it matters: Minor — cache loss means the next cold start takes slightly longer
  (triggers a fresh scan instead of loading cached results). No data loss.
Recommendation: Accept as-is. The failure is rare on Android's standard filesystems
  and the consequence (cache miss) is benign.
Effort: N/A
Confidence: MEDIUM — Source: [CODE]
```

### Step 2.5 — §B5: Data Flow Map

**Data Flow Summary:**

```
┌─────────────────────────────────────────────────────────────┐
│ MainViewModel (Single Source of Truth)                       │
│                                                             │
│ ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│ │ latestFiles  │  │ latestTree  │  │ pendingTrash (CHM)  │  │
│ │ (in-memory)  │  │ (in-memory) │  │ (ConcurrentHashMap) │  │
│ └──────┬───────┘  └──────┬──────┘  └──────────┬──────────┘  │
│        │                 │                     │             │
│   ┌────▼────┐  ┌────────▼────────┐  ┌─────────▼─────────┐  │
│   │LiveData │  │ LiveData        │  │ trashMutex-guarded │  │
│   │postValue│  │ postValue       │  │ operations         │  │
│   └────┬────┘  └────────┬────────┘  └───────────────────┘  │
│        │                │                                   │
│   ┌────▼────────────────▼──────────────────────┐            │
│   │ filesByCategory, duplicates, largeFiles,   │            │
│   │ junkFiles, storageStats, directoryTree     │            │
│   │ (LiveData → Fragments observe)             │            │
│   └────────────────────┬───────────────────────┘            │
│                        │                                    │
│   ┌────────────────────▼───────────────────────┐            │
│   │ SingleLiveEvent: deleteResult, moveResult, │            │
│   │ operationResult (one-shot → Fragments)     │            │
│   └────────────────────────────────────────────┘            │
│                                                             │
│ Guards: stateMutex (scan/cache state), trashMutex (trash),  │
│         deleteMutex (double-tap), cacheLock (save debounce) │
└─────────────────────────────────────────────────────────────┘
         │                              ▲
         ▼                              │
┌─────────────────┐            ┌────────┴────────┐
│ ScanCache (disk)│            │ UserPreferences  │
│ JSON streaming  │            │ SharedPreferences│
│ atomic write    │            │ (thresholds,     │
│ 30-day expiry   │            │  protected paths)│
└─────────────────┘            └─────────────────┘
```

**Key observations:**
1. All file list mutations flow through `stateMutex.withLock {}` — ensuring consistent derived state.
2. The `latestFiles` / `latestTree` in-memory copies exist because `postValue()` is async and LiveData `.value` may be stale when `saveCache()` reads it. This is a correct design decision, documented in the code comment at line 153-154.
3. Delete operations correctly acquire `trashMutex` then `stateMutex` in that order. `deleteFiles()` (line 391 → 424), `undoDelete()` (line 455 → 473). This consistent ordering prevents deadlock.

### Step 2.6 — §B6: Mutation & Reference Integrity

```
[MEDIUM] — F-025: undoDelete() re-runs full DuplicateFinder.findDuplicates() — O(n) hashing on undo
Section: §B6 — Mutation & Reference Integrity
Finding: MainViewModel.kt:480 — When the user undoes a delete, the ViewModel re-runs the
  full duplicate detection pipeline on the entire file list (latestFiles + restored).
  DuplicateFinder.findDuplicates() performs MD5 hashing of file contents — which is I/O
  intensive. For a file list of 10,000+ files, this could take several seconds, during
  which the UI shows stale duplicate data.
  In contrast, deleteFiles() (line 431-441) only filters and prunes — no re-hashing.
  This asymmetry means undo is significantly slower than delete.
Why it matters: Undo should feel instant. After an undo, the user sees files restored
  in the browse tab but must wait for duplicate/junk recalculation. If they navigate
  to the duplicates tab during this window, they see stale data.
Recommendation: On undo, restore the pre-delete duplicate group assignments instead of
  re-hashing. Save a snapshot of the duplicate groups before delete, and restore it on
  undo. This makes undo O(1) for duplicates instead of O(n).
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-026: refreshAfterFileChange() reads files from disk that may have been concurrently modified
Section: §B6 — Mutation & Reference Integrity
Finding: MainViewModel.kt:590-620 (approximate) — After move/rename/copy operations,
  refreshAfterFileChange() reads the new file from disk (File(path)) to create a fresh
  FileItem. If another process or the user modified the file between the operation and
  the refresh, the FileItem metadata (size, lastModified) could be stale on creation
  but immediately stale after. This is inherent to any non-watching file manager.
Why it matters: Minimal — this is standard file manager behavior. The user can re-scan
  to get fresh metadata.
Recommendation: None — acceptable behavior.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — Mutex ordering is consistent, no deadlock risk
Examined all mutex acquisition sites:
- deleteFiles(): trashMutex (line 391) → stateMutex (line 424) — always this order
- undoDelete(): trashMutex (line 455) → stateMutex (line 473-485) — same order
- confirmDelete(): trashMutex only (line 499)
- startScan(): stateMutex only (line 323)
- cache load (init): stateMutex only (line 217)
No code path acquires stateMutex then trashMutex, so deadlock is impossible.
```

```
[POSITIVE VERIFICATION] — ConcurrentHashMap for pendingTrash
MainViewModel.kt:151 — pendingTrash uses ConcurrentHashMap, enabling safe snapshot in
onCleared() even when trashMutex cannot be acquired (line 263). The defensive
`pendingTrash.toMap()` creates a safe copy regardless of concurrent modification.
```

```
[POSITIVE VERIFICATION] — BrowseFragment complete state preservation
BrowseFragment saves ALL UI state in onSaveInstanceState (line 726-736):
- KEY_VIEW_MODE, KEY_SEARCH_QUERY, KEY_SORT_ORDER, KEY_CATEGORY_POS,
  KEY_EXTENSIONS, KEY_COLLAPSED_FOLDERS
All are correctly restored in onViewCreated (line 98-106, 261-262).
BaseFileListFragment similarly saves: KEY_SELECTED_PATHS, KEY_VIEW_MODE,
KEY_SORT_ORDER, KEY_SEARCH_QUERY (line 137-141, 253-263).
```

---

### Phase 2 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §B1 — State Architecture | 2 | 0 | 0 | 1 | 1 |
| §B2 — Persistence & Storage | 2 | 0 | 0 | 0 | 2 |
| §B3 — Input Validation | 1 | 0 | 0 | 0 | 1 |
| §B4 — Import/Export Integrity | 1 | 0 | 0 | 0 | 1 |
| §B5 — Data Flow Map | 0 (map) | 0 | 0 | 0 | 0 |
| §B6 — Mutation & Reference | 2 | 0 | 0 | 1 | 1 |
| **TOTAL** | **8** | **0** | **0** | **2** | **6** |

### Positive Verifications (Phase 2)

1. **BatchRenameDialog ReDoS protection** — FutureTask 500ms timeout on user regex [CODE]
2. **ZIP extraction path traversal + bomb protection** — canonical path validation, 2GB + 10K entry limits [CODE]
3. **ScanCache atomic writes + version/bounds validation** — temp-file-then-rename, MAX_CACHED_FILES, 30-day expiry [CODE]
4. **Mutex ordering consistent** — trashMutex → stateMutex always, no deadlock possible [CODE]
5. **ConcurrentHashMap for pendingTrash** — safe snapshot in onCleared() [CODE]
6. **BrowseFragment complete state preservation** — all 6 state keys saved/restored [CODE]
7. **BaseFileListFragment state preservation** — selections, view mode, sort, search saved/restored [CODE]
8. **latestFiles/latestTree in-memory copies** — correct workaround for postValue() async behavior [CODE]
9. **SingleLiveEvent for one-shot events** — prevents stale event delivery on config change [CODE]

---

**Phase 2 is complete.**

**Cumulative findings: Phase 1 + Phase 2**

| Severity | Phase 1 | Phase 2 | Total |
|----------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 |
| HIGH | 1 | 0 | 1 |
| MEDIUM | 6 | 2 | 8 |
| LOW | 11 | 6 | 17 |
| **Total** | **18** | **8** | **26** |

**Next: Phase 3 — Security, Privacy & Trust (Category C)**

Awaiting confirmation to proceed with Phase 3, or to fix findings from Phase 1/2.

---

## PHASE 3 — SECURITY, PRIVACY & TRUST (Category C)

> **Calibration reminder:** §C was elevated to HIGH stakes during Phase 0 due to
> MANAGE_EXTERNAL_STORAGE permission, OAuth token storage, antivirus claims, and
> encrypted credential storage. All findings in this phase are assessed at elevated severity.

### Step 3.1 — §C1: Authentication & Authorization

```
[HIGH] — F-027: CrashReporter GitHub token stored in plaintext SharedPreferences
Section: §C1 — Authentication & Authorization
Finding: UserPreferences.kt:113-115 — The crash report GitHub token is stored in
  regular SharedPreferences (`raccoon_prefs`), not EncryptedSharedPreferences. This
  token has the `repo` scope (can create issues) and is readable by any app on a
  rooted device, or extractable from an unencrypted backup.
  The app already uses EncryptedSharedPreferences for cloud connection credentials
  (CloudConnectionStore.kt) but does not apply the same protection to the crash
  reporter token.
Why it matters: A leaked GitHub token with `repo` scope can be used to read private
  repository code, create issues, and potentially push code. This is a real credential
  exposure risk — not theoretical.
Recommendation: Store the crash report token in EncryptedSharedPreferences alongside
  cloud credentials, or use a separate EncryptedSharedPreferences instance. At minimum,
  document the risk to users in the settings UI.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[MEDIUM] — F-028: OAuth callback does not validate state parameter for GitHub flow
Section: §C1 — Authentication & Authorization
Finding: OAuthHelper.kt:241-246 — parseCallbackCode() extracts the `code` parameter
  from the callback URI but does NOT validate the `state` parameter. For the GitHub
  OAuth flow, the `state` parameter is stored as `pendingCodeVerifier` (line 114) and
  sent in the authorization URL (line 120), but when the callback arrives, the state
  is never compared against the stored value.
  This omission allows a CSRF attack: a malicious app could craft a deep link
  `filecleaner://oauth/callback?code=ATTACKER_CODE` and trick the app into exchanging
  an attacker-controlled authorization code, potentially linking the user's account
  to the attacker's GitHub account.
Why it matters: CSRF protection is a core security requirement of the OAuth 2.0
  specification (RFC 6749 §10.12). Without state validation, the OAuth flow is
  vulnerable to authorization code injection.
Recommendation: In parseCallbackCode() or exchangeCodeForToken(), validate that
  uri.getQueryParameter("state") matches pendingCodeVerifier before proceeding
  with the code exchange.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-029: GoogleDriveProvider retains access token in connection object indefinitely
Section: §C1 — Authentication & Authorization
Finding: GoogleDriveProvider.kt:35 — `private val accessToken: String get() = connection.authToken`
  The access token is retained in the CloudConnection object for the lifetime of the
  provider instance. Unlike SftpProvider (which clears credentials after connect at
  line 82) and WebDavProvider (which caches auth header and clears raw credentials at
  line 76), GoogleDriveProvider keeps the raw token accessible indefinitely.
  If the provider instance is retained in memory (e.g., in a ViewModel or fragment),
  the token remains exposed to memory dumps.
Why it matters: Inconsistent credential hygiene across providers. OAuth access tokens
  typically expire after 1 hour (Google) so the exposure window is limited.
Recommendation: Apply the same credential-clearing pattern used by SftpProvider and
  WebDavProvider: cache the auth header on connect, then clear connection.authToken.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 3.2 — §C2: Injection & XSS

```
[MEDIUM] — F-030: GitHub API URL constructed with unsanitized remotePath — path injection
Section: §C2 — Injection
Finding: GitHubProvider.kt:57-65 — The remotePath is split and directly interpolated
  into GitHub API URLs without URL-encoding:
  `"https://api.github.com/repos/$owner/$repo/contents/$path"`
  If a malicious server returns a file listing with a crafted `path` containing URL
  special characters (e.g., `../` or `?inject=true`), the constructed URL could be
  manipulated to target unintended API endpoints.
  Similarly, GoogleDriveProvider.kt:73 interpolates folderId into a query string:
  `val query = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")`
  — this IS properly encoded but the folderId could contain single quotes that break
  the Google Drive query syntax, causing unexpected API behavior.
Why it matters: Path injection in API calls could leak data from other repos or
  cause unexpected API responses. The attack surface requires a compromised or
  malicious cloud provider, which is an elevated risk for user-configured servers.
Recommendation: URL-encode path components in GitHubProvider using
  URLEncoder.encode(segment, "UTF-8") for each path segment. For GoogleDriveProvider,
  escape single quotes in folderId before embedding in the query.
Effort: LOW
Confidence: HIGH — Source: [CODE]
```

```
[MEDIUM] — F-031: WebDavProvider remotePath directly concatenated into URLs
Section: §C2 — Injection
Finding: WebDavProvider.kt:131 — `URL("$baseUrl$remotePath")` directly concatenates
  the remotePath (which comes from server responses) into URLs. A malicious WebDAV
  server could return hrefs containing `/../../../` sequences or authority-rewriting
  characters (e.g., `@evil.com/`) that could redirect requests to a different host.
  The PROPFIND response parsing at line 269-270 URL-decodes the href but does not
  validate that it stays within the expected base URL.
Why it matters: A malicious WebDAV server could redirect the client to make requests
  to arbitrary URLs, potentially leaking the cached Basic Auth credentials (which are
  sent with every request at line 99, 134, 159, 184, 205) to an attacker-controlled
  server.
Recommendation: Validate that constructed URLs resolve to the same host as baseUrl
  before making requests. Use `URL(baseUrl).host == URL(constructed).host` check.
  Consider using URI resolution instead of string concatenation.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — FileOperationService path traversal protection
FileOperationService.kt — extractArchive() validates canonical paths to prevent ZIP
slip attacks. moveFile() and copyFile() use File.canonicalPath for validation.
```

### Step 3.3 — §C3: Prototype Pollution & Import Safety

Not applicable to Android/Kotlin (no prototype chain, no dynamic imports).

**Result: N/A**

### Step 3.4 — §C4: Network & Dependencies

```
[POSITIVE VERIFICATION] — Network security config enforces HTTPS
network_security_config.xml — `cleartextTrafficPermitted="false"` enforces HTTPS for
all network connections. Only system trust anchors are used (no custom CA pinning that
could be bypassed). This is correct for a general-purpose app.
```

```
[POSITIVE VERIFICATION] — WebDavProvider enforces HTTPS upgrade
WebDavProvider.kt:38-44 — The baseUrl getter automatically upgrades `http://` URLs
to `https://`, preventing cleartext Basic Auth credential transmission.
```

```
[LOW] — F-032: JSch TOFU host key verification accepts all new hosts without user confirmation
Section: §C4 — Network & Dependencies
Finding: SftpProvider.kt:65-67 — The UserInfo.promptYesNo() implementation auto-accepts
  all new host keys (`return message?.contains("has changed") == false`). While it
  correctly rejects changed keys (potential MITM), the initial connection to any new
  server is automatically trusted without showing the user a fingerprint confirmation
  dialog.
  True TOFU should present the fingerprint on first connection and let the user verify.
  The current implementation is "TOFU without the verification" — it stores the key but
  never gives the user a chance to reject it on first use.
Why it matters: An attacker who intercepts the very first connection to a new server
  can perform MITM without detection. After the first connection, changed keys are
  correctly rejected.
Recommendation: Show a dialog with the server fingerprint on first connection and
  let the user accept or reject. Only auto-accept if the user has explicitly opted
  into "trust all new servers."
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-033: CrashReporter uploads crash reports to user-configured GitHub repo without TLS pinning
Section: §C4 — Network & Dependencies
Finding: CrashReporter.kt:161 — Crash reports (which include stack traces with file
  paths and device info) are uploaded to a user-configured GitHub repo via the GitHub
  REST API. The URL is constructed from `UserPreferences.crashReportRepo` (default:
  "WW-Andene/File-Cleaner-app"). If the user changes this to a malicious repo, crash
  reports with device info go to an attacker.
  However, this is user-configured and requires explicit opt-in (`crashReportingEnabled`
  defaults to false at line 110). The real concern is that crash reports contain device
  model, Android version, and stack traces which could reveal internal app paths.
Why it matters: Low risk given opt-in requirement. The information disclosed (device
  model, Android version, stack trace) is standard crash reporting data.
Recommendation: Accept as-is. The opt-in requirement and user-configured repo provide
  sufficient user agency.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

### Step 3.5 — §C5: Privacy & Data Minimization

```
[MEDIUM] — F-034: Crash reports include full stack traces with file paths — PII leakage
Section: §C5 — Privacy & Data Minimization
Finding: CrashReporter.kt:115-133 — Crash reports include device manufacturer, model,
  Android version, and full stack traces. Stack traces can contain file paths from the
  user's storage (e.g., if a crash occurs while processing a file with a personal name
  in the path like "/storage/emulated/0/Documents/John_Medical_Records/report.pdf").
  These reports are uploaded as public GitHub Issues (line 160-183), making them
  visible to anyone who can view the repository.
Why it matters: Public GitHub Issues containing device info and file paths constitute
  inadvertent PII disclosure. The user may not realize their crash report will be
  publicly visible when they enable crash reporting.
Recommendation: (a) Warn users in the settings UI that crash reports are posted as
  public GitHub Issues. (b) Strip file paths from stack traces (replace with
  `<path-redacted>`) before uploading. (c) Consider making issues private via the
  GitHub API if the repo supports it, or use a private reporting channel.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-035: ScanCache stores full file paths on disk without encryption
Section: §C5 — Privacy & Data Minimization
Finding: ScanCache.kt — The disk cache stores the complete path, size, extension,
  category, and duplicate group for every scanned file in a JSON file in app-private
  storage. While this is internal storage (not world-readable on non-rooted devices),
  it persists for up to 30 days and could be extracted from device backups.
  The app sets `android:allowBackup="false"` (AndroidManifest.xml:37), which correctly
  prevents Google Drive auto-backup extraction.
Why it matters: Minimal — allowBackup=false mitigates the primary backup extraction
  vector. The 30-day expiry provides temporal mitigation.
Recommendation: Accept as-is. The allowBackup=false and 30-day expiry provide
  adequate protection for the sensitivity level of file paths.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

### Step 3.6 — §C6: Compliance & Legal

```
[LOW] — F-036: MANAGE_EXTERNAL_STORAGE usage is properly justified but privacy notice is dismissable
Section: §C6 — Compliance & Legal
Finding: AndroidManifest.xml:23 — MANAGE_EXTERNAL_STORAGE is correctly documented with
  a justification comment (F-C6-02). The app shows a privacy notice dialog
  (hasSeenPrivacyNotice at UserPreferences.kt:103-105) which the user must dismiss.
  However, the privacy notice is shown once and never again — if the user dismisses it
  quickly without reading, they can't revisit it from Settings.
Why it matters: Minor UX/compliance gap — users should be able to review the privacy
  notice at any time from Settings.
Recommendation: Add a "Privacy Notice" option in Settings that re-shows the dialog.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 3.7 — §C7: Mobile-Specific Security

```
[HIGH] — F-037: OAuth deep link scheme "filecleaner://" can be hijacked by malicious apps
Section: §C7 — Mobile-Specific Security
Finding: AndroidManifest.xml:64-72 — The OAuth callback uses a custom URI scheme
  `filecleaner://oauth/callback`. On Android, custom URI schemes are NOT exclusive —
  any app can register an intent filter for the same scheme and potentially intercept
  the OAuth callback, stealing the authorization code.
  Additionally, the legacy scheme `com.filecleaner.app://oauth2callback` (line 60-62)
  is also registered. Two competing intent filters increase the attack surface.
  Android App Links (HTTPS-based verified deep links) are the secure alternative, as
  they require domain ownership verification and are exclusive to the verified app.
Why it matters: Authorization code theft via deep link hijacking is a known Android
  attack vector (OWASP MSTG-AUTH-009). A malicious app installed on the same device
  could intercept the OAuth callback and use the stolen code to obtain the user's
  access token. PKCE mitigates this partially (the attacker would also need the code
  verifier), but the code verifier is in the same process and PKCE was designed for
  public clients, not as a defense against app impersonation.
Recommendation: Migrate to Android App Links (https://yourdomain.com/.well-known/
  assetlinks.json) for the OAuth redirect URI. This requires a web domain but
  provides cryptographic verification of the redirect target. As a shorter-term fix,
  validate the calling package in the intent if possible.
Effort: HIGH
Confidence: HIGH — Source: [CODE]
```

```
[MEDIUM] — F-038: ScanService exported=false but static state accessible across processes
Section: §C7 — Mobile-Specific Security
Finding: ScanService.kt:31-40 — While the service is correctly declared as
  `android:exported="false"` (AndroidManifest.xml:77), its scan results are communicated
  via static `@Volatile` fields on the companion object. These fields are process-local
  and thread-safe for single-process apps. However, if the service ever runs in a
  separate process (via `android:process`), the static fields would be in a different
  JVM and inaccessible from the fragment.
  The immediate concern is the lack of atomicity across multiple volatile fields (already
  reported as F-009 in Phase 1). The security concern is that scan results (which
  include file paths and threat assessments) are held in static fields indefinitely
  after scan completion — they're cleared only when a new scan starts or the service
  is destroyed.
Why it matters: Minor — the service is single-process and not exported. The static
  field retention is a code quality issue more than a security issue.
Recommendation: Clear scanResults when the fragment reads them (consume-on-read
  pattern) to minimize the window of static data retention.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — PendingIntent uses FLAG_IMMUTABLE
ScanService.kt:184,192 — All PendingIntents use FLAG_IMMUTABLE, preventing intent
modification by other apps. This is correct for Android 12+ (API 31) compatibility.
```

```
[POSITIVE VERIFICATION] — FileProvider configured correctly
AndroidManifest.xml:80-88 — FileProvider is not exported (`exported="false"`) and uses
`grantUriPermissions="true"` for controlled access. This follows security best practices.
```

```
[POSITIVE VERIFICATION] — allowBackup="false"
AndroidManifest.xml:37 — Backup is disabled, preventing extraction of cached data,
preferences, and credentials from Google Drive backup or ADB.
```

```
[POSITIVE VERIFICATION] — Credential clearing after authentication
SftpProvider.kt:82 — Clears authToken from connection after connect.
WebDavProvider.kt:76 — Caches auth header and clears username+authToken after connect.
Both providers minimize the window of raw credential exposure in memory.
```

---

### Phase 3 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §C1 — Auth & Authorization | 3 | 0 | 1 | 1 | 1 |
| §C2 — Injection | 2 | 0 | 0 | 2 | 0 |
| §C3 — Import Safety | 0 (N/A) | 0 | 0 | 0 | 0 |
| §C4 — Network & Dependencies | 2 | 0 | 0 | 0 | 2 |
| §C5 — Privacy & Data Minimization | 2 | 0 | 0 | 1 | 1 |
| §C6 — Compliance & Legal | 1 | 0 | 0 | 0 | 1 |
| §C7 — Mobile-Specific Security | 2 | 0 | 1 | 1 | 0 |
| **TOTAL** | **12** | **0** | **2** | **5** | **5** |

### Positive Verifications (Phase 3)

1. **FileOperationService path traversal protection** — canonical path validation for ZIP extraction [CODE]
2. **Network security config enforces HTTPS** — cleartextTrafficPermitted="false" [CODE]
3. **WebDavProvider HTTPS upgrade** — auto-upgrades http:// to https:// [CODE]
4. **PendingIntent FLAG_IMMUTABLE** — prevents intent modification on Android 12+ [CODE]
5. **FileProvider not exported** — controlled access via grantUriPermissions [CODE]
6. **allowBackup="false"** — prevents credential/data extraction from backups [CODE]
7. **Credential clearing after auth** — SftpProvider and WebDavProvider clear raw credentials [CODE]
8. **ScanService not exported** — foreground service not accessible to other apps [CODE]
9. **EncryptedSharedPreferences for cloud credentials** — AES256_GCM encryption [CODE]
10. **OAuth PKCE implementation** — proper S256 code challenge with SecureRandom [CODE]

---

**Phase 3 is complete.**

**Cumulative findings: Phase 1 + Phase 2 + Phase 3**

| Severity | Phase 1 | Phase 2 | Phase 3 | Total |
|----------|---------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 | 0 |
| HIGH | 1 | 0 | 2 | 3 |
| MEDIUM | 6 | 2 | 5 | 13 |
| LOW | 11 | 6 | 5 | 22 |
| **Total** | **18** | **8** | **12** | **38** |

**Next: Phase 4 — Performance & Efficiency (Category D)**

Awaiting confirmation to proceed with Phase 4, or to fix findings from Phase 1/2/3.

---

## PHASE 4 — PERFORMANCE & EFFICIENCY (Category D)

> **Calibration reminder:** §D was amplified during Phase 0 (Productivity/Utility domain).
> File managers are judged heavily on perceived speed — scan time, list scroll performance,
> and file operation responsiveness are critical UX metrics.

### Step 4.1 — §D1: Runtime Performance

```
[MEDIUM] — F-039: JunkFinder.findJunk() calls String.lowercase() per file and per keyword
Section: §D1 — Runtime Performance
Finding: JunkFinder.kt:43-50 — For every file in the scan results (potentially 50,000+),
  the code calls `item.path.lowercase()` (line 43) and then checks against each
  JUNK_DIR_KEYWORD with `path.contains("/$it/")` (line 50). This is O(n × k) where n is
  file count and k is 7 keywords. The lowercase() call allocates a new String per file.
  For 50,000 files, that's 50,000 String allocations just for the lowercase conversion,
  plus 350,000 contains() checks.
Why it matters: On a device with many files, findJunk() could take noticeable time. While
  this runs on Dispatchers.IO, it still contributes to total scan duration.
Recommendation: Pre-lowercase the JUNK_DIR_KEYWORDS at init time. Consider storing
  lowercase paths in FileItem to avoid repeated conversion. Alternatively, use a single
  regex compiled once: `Regex("/(?:cache|temp|tmp|thumbnail|\\.thumbnails|lost\\+found)/")`.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[LOW] — F-040: FileScanner BFS uses LIFO stack (ArrayDeque.pop) — produces DFS, not BFS
Section: §D1 — Runtime Performance
Finding: FileScanner.kt:47,54 — The code uses `ArrayDeque` as a stack via `push()` and
  `pop()` (LIFO). This produces depth-first traversal, not breadth-first as the comment
  on line 76 implies ("Build tree bottom-up"). DFS is actually fine here — the tree is
  built correctly regardless of traversal order because the bottom-up pass uses depth
  sorting (line 78). However, DFS with a stack has slightly worse cache locality than
  BFS for very wide directory trees.
Why it matters: Negligible performance impact — both DFS and BFS visit all nodes exactly
  once. The naming is misleading but the algorithm is correct.
Recommendation: None — the algorithm is correct. Consider renaming the variable from
  "stack" to be consistent with DFS semantics, or switch to `addLast`/`removeFirst` for
  true BFS if preferred.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — DuplicateFinder 3-stage pipeline is well-optimized
DuplicateFinder.kt — The 3-stage approach (size grouping → partial hash → full MD5) is
an efficient duplicate detection strategy:
- Stage 1 (size): O(n) with HashSet grouping, eliminates ~90% of files immediately
- Stage 2 (partial hash): Only 8KB I/O per file (head + tail), eliminates most remaining
- Stage 3 (full MD5): Only for true collisions after partial hash
- Pre-allocated hex lookup table (line 22-31) avoids per-byte String.format
- Coroutine cancellation checks every ~512KB during hashing (line 141-143)
- MAX_FULL_HASH_SIZE skip for files >200MB (appropriate for mobile)
```

```
[POSITIVE VERIFICATION] — ArborescenceView pre-allocated draw objects
ArborescenceView.kt — All Paint objects, RectF objects, FontMetrics, and dimension
values are pre-allocated/pre-computed at construction time (lines 67-221). No allocations
occur in onDraw paths. Cached ellipsis widths, font metrics baselines, and dp-scaled
constants avoid repeated measurement. This is essential for 60 FPS scroll/zoom performance
on a custom Canvas view.
```

```
[POSITIVE VERIFICATION] — refreshAfterFileChange() incremental updates
MainViewModel.kt:661-703 — Single-file operations (rename, move, copy) use incremental
list updates instead of re-running the full scan. Duplicate group assignments are preserved
across renames (content unchanged). Only findLargeFiles and findJunk (both fast in-memory
operations) are re-run. This is O(n) in file count instead of O(n × fileSize) for hashing.
```

### Step 4.2 — §D2: Web Vitals & Loading (→ Mobile Rendering)

```
[LOW] — F-041: BrowseAdapter.notifyDataSetChanged() on viewMode change
Section: §D2 — Rendering Performance
Finding: BrowseAdapter.kt:44-47 and FileAdapter.kt:57-60 — Both adapters call
  `notifyDataSetChanged()` when viewMode changes. This triggers a full rebind of all
  visible items and prevents DiffUtil from computing incremental updates. On a list
  of 1000+ items, this causes a visible frame drop during the view mode switch.
  ListAdapter's submitList with DiffUtil handles this efficiently for data changes,
  but view type changes (list ↔ grid) genuinely require a full re-layout because
  the ViewHolder types differ.
Why it matters: Minor — viewMode changes are infrequent user actions (not scroll-path).
  The frame drop is acceptable during an intentional mode switch.
Recommendation: Accept as-is. notifyDataSetChanged() is the correct approach when view
  types change. Consider using `setHasStableIds(true)` for smoother recycling if needed.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — FileAdapter uses DiffUtil.ItemCallback
FileAdapter.kt:25-28 — Uses ListAdapter with DiffUtil.ItemCallback for efficient
incremental updates. areItemsTheSame compares by path (identity), areContentsTheSame
compares by full equality. Selection updates use payloads (PAYLOAD_SELECTION) for
partial rebind — only checkbox and background change, skipping icon/text/thumbnail.
This is the correct RecyclerView performance pattern.
```

```
[POSITIVE VERIFICATION] — RecyclerView setHasFixedSize(true)
BaseFileListFragment.kt:169 and BrowseFragment.kt:184 — Both set setHasFixedSize(true),
which avoids unnecessary measure/layout passes when the adapter content changes. This
is correct because the RecyclerView size is not determined by its content.
```

### Step 4.3 — §D3: Resource Budget

```
[MEDIUM] — F-042: SignatureScanner reads entire script content into memory for regex scanning
Section: §D3 — Resource Budget
Finding: SignatureScanner.kt:297 — `val content = file.readText(Charsets.UTF_8)` reads
  the entire script file (up to 1MB, per line 292 guard) into a single String. Then
  10 regex patterns are run against this String (lines 299-313). Each regex creates
  internal Matcher state, and the regexes themselves are compiled once (good).
  However, if the file contains binary data that happens to have a script extension,
  readText(UTF_8) may produce garbled output with many replacement characters,
  allocating a potentially large String for no useful purpose.
Why it matters: On a device with many script files (e.g., developer's device with
  node_modules), this could consume significant heap during antivirus scan. The 1MB
  limit provides adequate protection against catastrophic OOM, but the per-file
  allocation pattern creates GC pressure.
Recommendation: (a) Check if file is likely binary before reading (check for \0 bytes
  in first 512 bytes). (b) Read line-by-line with early exit on first match instead of
  loading entire file. This would reduce peak memory and GC pressure.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[LOW] — F-043: FileConverter.pdfToImages() uses 2x scale for all pages without size guard
Section: §D3 — Resource Budget
Finding: FileConverter.kt:232-234 — PDF page rendering uses a hardcoded 2x scale:
  `Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)`.
  A standard A4 PDF page at 72 DPI is 595×842 pixels. At 2x scale: 1190×1684 pixels ×
  4 bytes/pixel = ~8MB per page bitmap. For a 50-page PDF, this is 50 sequential 8MB
  allocations (though each is recycled after use).
  If the PDF has unusual page sizes (e.g., 2000×3000 at 72 DPI), the 2x scaled bitmap
  would be 4000×6000 = 96MB — potentially causing OOM on low-memory devices.
Why it matters: OOM risk on low-memory devices with large-page PDFs.
Recommendation: Add a max pixel dimension guard (e.g., 4096×4096) and reduce scale
  factor for pages that would exceed it. The existing image conversion has similar
  guards (FileConverter.kt:95 checks BMP file size), but pdfToImages does not.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — ScanCache MAX_CACHED_FILES = 50,000
ScanCache.kt:20 — Caps the cached file count to prevent multi-MB JSON files. This
bounds both memory usage during serialization and disk usage. The streaming reader
(android.util.JsonReader) on the read side avoids loading the entire JSON into memory.
```

### Step 4.4 — §D4: Memory Management

```
[MEDIUM] — F-044: MainViewModel holds entire file list in memory indefinitely
Section: §D4 — Memory Management
Finding: MainViewModel.kt:155-156 — `latestFiles: List<FileItem>` and `latestTree:
  DirectoryNode?` hold the entire scan result in memory for the lifetime of the ViewModel
  (which is Activity-scoped). For 50,000 files, each FileItem is ~200 bytes (path String
  + name String + primitives), totaling ~10MB of heap. The DirectoryNode tree duplicates
  some of this data (files lists within nodes).
  Additionally, _filesByCategory (line 98) holds the same data grouped differently —
  another reference set (though the FileItem objects are shared by reference).
  The Scanning state updates on the main thread while this data is in memory, meaning
  GC pauses during scan could cause frame drops.
Why it matters: On low-memory devices (2GB RAM, common in emerging markets), a 10MB+
  heap footprint for scan data could trigger low-memory kills of background processes
  or cause GC pauses visible as jank.
Recommendation: (a) Consider using WeakReference or clearing latestFiles after cache
  save (can be reloaded from cache). (b) The DirectoryNode tree could use file counts
  only instead of full file lists (enrichTreeWithFiles adds files that are already in
  the flat list). (c) Monitor heap usage with Android Profiler to validate actual impact.
Effort: MEDIUM
Confidence: MEDIUM — Source: [CODE]
```

```
[LOW] — F-045: FileConverter.imagesToPdf() PdfDocument holds all pages in memory
Section: §D4 — Memory Management
Finding: FileConverter.kt:192-201 — In imagesToPdf(), each bitmap is decoded, drawn to
  the PDF page, and recycled in a try/finally block. This is correct — bitmaps are
  released promptly. However, PdfDocument holds all completed pages in memory until
  doc.writeTo() is called (line 204). Large PDFs with many high-resolution images
  could exhaust memory.
Why it matters: Minor — the user would need to select many large images for conversion.
  The pattern is correct (decode → draw → recycle per page), and PdfDocument internal
  page buffering is a framework limitation.
Recommendation: Consider warning the user when converting more than ~20 images to PDF.
Effort: LOW
Confidence: LOW — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — Bitmap.recycle() called in all conversion code paths
FileConverter.kt — All bitmap conversion methods (convertImage, resizeImage, imagesToPdf,
pdfToImages) properly recycle bitmaps in finally blocks, preventing native memory leaks.
The BMP writer (writeBmp) operates on the source bitmap without creating additional copies.
```

### Step 4.5 — §D5: Mobile-Specific Performance

```
[LOW] — F-046: Search debounce creates a new coroutine on every keystroke
Section: §D5 — Mobile-Specific Performance
Finding: BaseFileListFragment.kt:241-248 and BrowseFragment.kt:216-224 — Each keystroke
  in the search field cancels the previous debounce Job and launches a new coroutine.
  For a 10-character search query typed quickly, this creates and cancels 9 coroutines.
  While individual coroutines are lightweight (~2KB), the pattern creates unnecessary
  garbage collection pressure.
Why it matters: Minimal — coroutines are designed for exactly this pattern. The 300ms
  debounce (SEARCH_DEBOUNCE_MS) is appropriate for the use case.
Recommendation: None — this is the idiomatic Kotlin coroutine debounce pattern. Consider
  using `flow { ... }.debounce(300)` with `collectLatest` for a cleaner implementation,
  but the current approach is functionally equivalent.
Effort: N/A
Confidence: HIGH — Source: [CODE]
```

```
[POSITIVE VERIFICATION] — Cache write debounce (3000ms)
MainViewModel.kt:57-58, 710-727 — Cache writes are debounced to at most once per 3 seconds
via saveCacheJob cancellation pattern. Rapid file operations (e.g., batch rename of 50
files) coalesce into a single cache write. Non-cancellable write ensures completion.
```

```
[POSITIVE VERIFICATION] — ArborescenceView tree identity tracking
ArborescenceView.kt:244-249 — Uses `computeTreeIdentity()` to skip redundant layout
recalculations when the same tree data is set. This prevents expensive layout passes
during LiveData re-delivery on configuration changes.
```

```
[POSITIVE VERIFICATION] — UserPreferences read-once at scan start
FileScanner.kt:50 — `val showHidden = try { UserPreferences.showHiddenFiles } ...`
reads the preference once before the scan loop, rather than per-directory. This avoids
SharedPreferences I/O inside the hot loop.
```

```
[POSITIVE VERIFICATION] — Reduced motion support
BaseFileListFragment.kt:180-182 and BrowseFragment.kt:187-189 — Both fragments check
`MotionUtil.isReducedMotion()` and disable RecyclerView layout animations when the user
has enabled reduced motion in system settings. This improves performance for users who
disable animations and respects accessibility preferences.
```

---

### Phase 4 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §D1 — Runtime Performance | 2 | 0 | 0 | 1 | 1 |
| §D2 — Rendering Performance | 1 | 0 | 0 | 0 | 1 |
| §D3 — Resource Budget | 2 | 0 | 0 | 1 | 1 |
| §D4 — Memory Management | 2 | 0 | 0 | 1 | 1 |
| §D5 — Mobile-Specific Performance | 1 | 0 | 0 | 0 | 1 |
| **TOTAL** | **8** | **0** | **0** | **3** | **5** |

### Positive Verifications (Phase 4)

1. **DuplicateFinder 3-stage pipeline** — efficient size → partial hash → full MD5, pre-allocated hex table [CODE]
2. **ArborescenceView zero-allocation onDraw** — all paints, RectFs, FontMetrics pre-allocated [CODE]
3. **refreshAfterFileChange() incremental updates** — avoids re-hashing on single-file operations [CODE]
4. **FileAdapter DiffUtil with payload rebind** — selection changes use PAYLOAD_SELECTION for partial rebind [CODE]
5. **RecyclerView setHasFixedSize(true)** — avoids unnecessary measure/layout passes [CODE]
6. **ScanCache MAX_CACHED_FILES** — bounds serialization memory and disk usage [CODE]
7. **Bitmap.recycle() in all conversion paths** — prevents native memory leaks [CODE]
8. **Cache write debounce (3000ms)** — coalesces rapid file operations into single write [CODE]
9. **ArborescenceView tree identity tracking** — skips redundant layout recalculations [CODE]
10. **UserPreferences read-once at scan start** — avoids SharedPreferences I/O in hot loop [CODE]
11. **Reduced motion support** — disables animations when system accessibility setting enabled [CODE]

---

**Phase 4 is complete.**

**Cumulative findings: Phase 1 + Phase 2 + Phase 3 + Phase 4**

| Severity | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Total |
|----------|---------|---------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 | 0 | 0 |
| HIGH | 1 | 0 | 2 | 0 | 3 |
| MEDIUM | 6 | 2 | 5 | 3 | 16 |
| LOW | 11 | 6 | 5 | 5 | 27 |
| **Total** | **18** | **8** | **12** | **8** | **46** |

---

## PHASE 5 — VISUAL DESIGN & AESTHETICS (Category E)

> **Calibration reminder:** §E was set to STANDARD during Phase 0 (Productivity/Utility domain).
> File managers are judged more on clarity, information density, and speed than on delight.
> Visual design should serve navigability and reduce cognitive load above all else.

### Step 5.1 — §E1: Design Token System

```
[POSITIVE VERIFICATION] — Comprehensive design token architecture
The app implements a thorough, well-documented design token system across three files:

**Color tokens (colors.xml, 217 lines light / 218 lines dark):**
- OKLCH perceptual lightness model for surface elevation ladder
- Forest green primary (#247A58) with warm amber accent (#E8861F)
- Chromatic warm surfaces (never neutral gray) — green-tinted hierarchy
- Full M3 surface container hierarchy (Lowest/Low/Mid/High/Highest)
- 8 file category colors, 6 duplicate group colors, 5 junk category colors
- All tokens have documented OKLCH lightness values and WCAG contrast ratios
- Complete dark mode parity with lifted colors and chromatic near-blacks

**Spacing tokens (dimens.xml, 204 lines):**
- 4dp base grid: micro/2dp → xs/4dp → sm/8dp → md/12dp → lg/16dp → xl/20dp → xxl/24dp → 3xl/32dp
- Documented off-grid exceptions (spacing_10 for chip dot/legend)
- Corner radius scale with semantic naming (radius_btn, radius_card, radius_modal, radius_pill)
- Elevation scale: 0/1/2/4/8/16dp with semantic names
- Stroke widths: none/default/selected
- Icon size scale: small/inline/nav/file_list/empty_state
- Touch target minimum: 48dp (Material guideline)

**Motion tokens (dimens.xml, lines 196-203):**
- 5-tier motion vocabulary: micro/120ms, enter/220ms, exit/160ms, page/280ms, emphasis/400ms
- Documented character: "considerate utility — brisk but not mechanical"
- Exit animations faster than enter (160ms vs 220ms) — correct asymmetry
- Stagger step: 40ms per item, capped at 160ms total

**Typography tokens (dimens.xml, lines 181-194):**
- Major Third (1.25×) scale for upper range: 14 → 16 → 20 → 26 → 32sp
- Compressed lower range (10, 11, 12, 13sp) for legibility at small sizes
- Separate emoji size (24sp)
```

```
[LOW] — F-047: Off-grid spacing_10 used without systematic need documentation
Section: §E1 — Design Token System
Finding: dimens.xml:9 — `spacing_10` (10dp) is the only off-grid value, documented
  as "chip dot and legend spacing." However, the comment doesn't explain WHY 8dp or
  12dp wouldn't work. Off-grid values weaken the spatial system's predictability.
  Additionally, `spacing_chip` (6dp) is another off-grid value without similar
  documentation. The `bottom_nav_height` at 57dp is also oddly off-grid.
Why it matters: Minor — 2-3 off-grid values in a 200-line token file is exceptionally
  clean. The system is well-governed overall.
Recommendation: Add brief rationale for each off-grid value (e.g., "10dp: optical
  correction for legend dot centering").
Effort: TRIVIAL
Confidence: LOW — Source: [CODE]
```

### Step 5.2 — §E2: Visual Rhythm & Spatial Composition

```
[POSITIVE VERIFICATION] — Consistent spatial rhythm across layouts
Layout files demonstrate disciplined use of the spacing token system:

- **fragment_raccoon_manager.xml**: All padding uses spacing_lg (16dp), card margins
  use spacing_md (12dp) or spacing_lg (16dp), icon-to-text margins use spacing_lg (16dp).
  The hero card uses spacing_xl (20dp) for more breathing room — correct hierarchy.
- **fragment_browse.xml**: Horizontal padding consistently spacing_lg (16dp), vertical
  spacing follows xs (4dp) → sm (8dp) → md (12dp) progression.
- **item_file.xml**: Padding spacing_md (12dp), icon-to-text margin spacing_md (12dp),
  consistent touch target 48dp minimum on checkbox.
- **fragment_dashboard.xml**: Card sections use consistent spacing_md (12dp) margins
  between cards, spacing_lg (16dp) inner padding.

All layouts use token references (`@dimen/spacing_*`) instead of hardcoded dp values.
Zero hardcoded spacing values found in key layout files.
```

```
[LOW] — F-048: Dashboard quick action buttons use Widget.MaterialComponents parent instead of branded style
Section: §E2 — Visual Rhythm & Spatial Composition
Finding: fragment_dashboard.xml:269-309 — The three quick action buttons (Clean Junk,
  View Duplicates, View Large) use `style="@style/Widget.MaterialComponents.Button.OutlinedButton"`
  directly instead of the branded `@style/Widget.FileCleaner.Button.Outlined`. This means
  they miss the branded cornerRadius, letterSpacing, and minHeight from the app's style system.
Why it matters: Visual inconsistency — these buttons will render with the Material default
  corner radius (4dp) instead of the branded 12dp, creating a visual seam with other buttons
  in the app.
Recommendation: Change to `style="@style/Widget.FileCleaner.Button.Outlined"`.
Effort: TRIVIAL
Confidence: HIGH — Source: [CODE]
```

### Step 5.3 — §E3: Color Craft & Contrast

```
[POSITIVE VERIFICATION] — OKLCH-based color system with documented contrast ratios
The color system is exceptionally well-crafted:

**Light mode brand colors:**
- Primary: #247A58 (forest green, OKLCH L=0.51) — strong identity
- Accent: #E8861F (warm amber, OKLCH L=0.65) — excellent complementary contrast
- Error: #C62828 (deep red) — distinct from primary/accent hue

**Surface elevation ladder (light):**
- surfaceBase: #FBFAF8 (L=0.98) — warmest, no pure white
- surfaceColor: #F5F4F1 (L=0.96) — card backgrounds
- surfaceElevated: #F0EFEC (L=0.94) — dialogs, drawers
- surfaceContainer hierarchy: Lowest(#F5F3F0), Low(#EBE9E5), Mid(#E1DFDB), High(#D7D5D1), Highest(#CDCBC7)
- All steps ~2-3% OKLCH lightness apart — perceptually uniform

**Dark mode counterparts:**
- surfaceBase: #1A1C1B (L=0.12) — chromatic near-black with green tint, never #000000
- Primary lifted: #5ECE9E (L=0.78) — higher lightness for dark backgrounds
- Surface elevation through lighter greens, not pure grays

**Text hierarchy (chromatic, never pure gray):**
- textPrimary: #1B3C2E (green-tinted near-black) vs dark #E8ECE9
- textSecondary: #4E6B5C vs dark #B5C4BB
- textTertiary: #7A9488 vs dark #8DA498
- All maintain ≥4.5:1 contrast on their respective surface colors (documented)

**Category colors (8 file types):**
- Each has unique hue + documented WCAG ratios for icon-on-tint usage
- catImage #3B82F6, catVideo #A855F7, catAudio #EC4899, catDocument #0D9488,
  catApp #F97316, catArchive #8B5CF6, catDownload #06B6D4, catOther #6B7280
```

```
[LOW] — F-049: Some alpha values hardcoded instead of using tokens
Section: §E3 — Color Craft & Contrast
Finding: Multiple layouts use hardcoded alpha values:
  - fragment_raccoon_manager.xml:131 — `android:alpha="@dimen/alpha_raccoon_logo"` (tokenized, good)
  - fragment_raccoon_manager.xml:141 — `android:alpha="0.87"` (hardcoded)
  - fragment_dashboard.xml:92 — `android:alpha="0.8"` (hardcoded)
  These represent secondary text emphasis levels that should be defined as tokens.
Why it matters: Minor inconsistency — some alpha values are tokenized (alpha_raccoon_logo)
  while others are inline constants.
Recommendation: Define alpha_text_emphasis (0.87) and alpha_text_secondary_on_surface (0.8)
  as token dimens.
Effort: TRIVIAL
Confidence: MEDIUM — Source: [CODE]
```

### Step 5.4 — §E4: Typography Craft

```
[POSITIVE VERIFICATION] — Complete typography system with optical corrections
themes.xml defines 20+ text appearances with careful optical corrections:

**Scale (Major Third 1.25×):**
- Display: 32sp bold, -0.02 tracking, 1.2× line height
- Headline: 26sp bold, -0.01 tracking, 1.2× line height
- Title: 20sp medium, -0.005 tracking, 1.2× line height
- Subtitle: 16sp medium, 0 tracking, 1.3× line height (bridge)
- Body: 14sp regular, +0.005 tracking, 1.4× line height
- BodySmall: 12sp regular, +0.01 tracking, 1.5× line height
- Label: 11sp medium, +0.06 tracking, ALL CAPS, 1.5× line height
- Caption: 10sp regular, +0.03 tracking, 1.5× line height

**Optical corrections:**
- Negative tracking on headings (-0.02, -0.01) for visual tightening at large sizes
- Positive tracking on small text (+0.01, +0.03, +0.06) for legibility
- Line height increases as text size decreases (1.2× → 1.5×) — correct for readability
- Label uses +0.06 tracking with ALL CAPS — mandatory for caps legibility

**Specialized variants:**
- Numeric: `fontFeatureSettings="tnum"` for tabular lining figures in data displays
- NumericDisplay/NumericHeadline/NumericTitle/NumericMedium — tnum at every scale
- Mono: monospace for code paths and package names
- FileViewer.Filename, FileViewer.Info, FileViewer.Content — domain-specific
- SectionHeader: primary-colored label for settings/grouped content

All text appearances use token references for sizes (@dimen/text_*), not hardcoded sp values.
```

```
[LOW] — F-050: Overline text style duplicates Label configuration
Section: §E4 — Typography Craft
Finding: themes.xml:440-447 — TextAppearance.FileCleaner.Overline uses 11sp, medium,
  ALL CAPS, +0.1 tracking, textSecondary — which is very similar to
  TextAppearance.FileCleaner.Label (11sp, medium, ALL CAPS, +0.06 tracking, textSecondary).
  The only difference is tracking (0.06 vs 0.1). Having two near-identical styles
  risks inconsistent usage by developers.
Why it matters: Minor — two visually-similar-but-not-identical styles can lead to
  arbitrary choice by developers, weakening the type system.
Recommendation: Consider consolidating or making the distinction more explicit in naming
  (e.g., "LabelWide" for the wider-tracked variant).
Effort: TRIVIAL
Confidence: LOW — Source: [CODE]
```

### Step 5.5 — §E5: Component Visual Quality

```
[POSITIVE VERIFICATION] — Comprehensive component style system
themes.xml defines 30+ component styles forming a complete, branded design language:

**Buttons (6 variants):**
- Filled (Widget.FileCleaner.Button): 12dp corners, no all-caps, 48dp min height
- Outlined (Button.Outlined): matching corners + branded stroke color
- Destructive (Button.Destructive): colorError background, textOnPrimary text
- Text (Button.Text): low-emphasis, primary-colored text
- Ghost (Button.Ghost): lowest emphasis, secondary text with border ripple
- Icon (Button.Icon): circular touch target with 50% corner size
- Small variants (Button.Small, Button.Outlined.Small): 36dp height for compact contexts

**Cards (4 variants):**
- Default (Widget.FileCleaner.Card): 16dp corners, 2dp elevation, 1dp stroke
- Selected (Card.Selected): highlighted background + selected border
- Elevated (Card.Elevated): 4dp elevation, no stroke — hero/raised emphasis
- Flat (Card.Flat): 0dp elevation, border only — less prominent
- Outlined (Card.Outlined): 0dp elevation, explicit outlined variant

**Chips (3 variants):** Filter, Choice, Action — all pill-shaped, state-aware backgrounds

**Interactive state selectors:** chip_bg_color, bottom_nav_color, switch_thumb/track,
card_stroke_color — all cover checked/pressed/focused/disabled states correctly.

**StateListAnimator:** card_state_list_anim provides translationZ changes:
- Dragged: +6dp (lift for drag), Pressed: 0dp (pushed in), Focused: +4dp, Default: +2dp
- All transitions use motion_micro (120ms) — correct for state feedback
```

```
[LOW] — F-051: Hero card uses stateListAnimator but also sets cardElevation
Section: §E5 — Component Visual Quality
Finding: fragment_raccoon_manager.xml:89 — The hero scan card sets both
  `app:cardElevation="@dimen/elevation_raised"` (4dp) and
  `android:stateListAnimator="@animator/hero_card_state_list_anim"`.
  The stateListAnimator controls translationZ (the interactive elevation delta),
  while cardElevation sets the base elevation. The interaction is correct —
  total visible elevation = cardElevation + translationZ. However, if the
  hero_card_state_list_anim sets different base translationZ values than the
  regular card_state_list_anim, the total elevation stack may not follow the
  intended elevation scale.
Why it matters: Minor — the combined elevation works correctly in practice.
  Both animators use the same structure with motion_micro timing.
Recommendation: Document the intended total elevation for each card variant
  (e.g., "hero: 4dp base + 2dp translationZ = 6dp resting").
Effort: TRIVIAL
Confidence: LOW — Source: [CODE]
```

### Step 5.6 — §E6: Interaction Design Quality

```
[POSITIVE VERIFICATION] — Rich, state-aware interaction design
The app demonstrates thorough attention to interaction feedback:

**Touch feedback:**
- All interactive cards have `android:foreground="@drawable/ripple_card"` or
  `android:foreground="?attr/selectableItemBackground"` for Material ripple feedback
- Hero card uses branded `ripple_hero_card` drawable
- Buttons maintain 48dp minimum touch targets (touch_target_min)
- CheckBox minWidth/minHeight both set to 48dp touch target

**State communication:**
- chip_bg_color.xml covers 6 states: disabled, checked+pressed, checked+focused,
  checked, pressed, focused, default — exhaustive state coverage
- bottom_nav_color.xml: disabled, checked+pressed, checked, pressed, default
- Card stroke colors change on focus/selection for keyboard navigation
- card_state_list_anim provides physical-feeling elevation changes on press/focus/drag

**Focus management (keyboard/accessibility):**
- fragment_browse.xml uses nextFocusDown/nextFocusRight/nextFocusLeft for logical
  focus traversal order in the selection action bar
- fragment_raccoon_manager.xml defines nextFocusUp/nextFocusDown/nextFocusRight/nextFocusLeft
  for the entire hub card grid — full 2D focus navigation

**Motion character:**
- Custom pathInterpolator (fast_out_slow_in_custom): "M 0,0 C 0.35,0 0.1,1 1,1"
  — slightly snappier than Material default, matching "considerate utility" character
- Enter animations: slide+fade with staggered content reveal
- Exit animations: faster than enter (160ms vs 220ms) — correct asymmetry
- Dialog enter: subtle 90% scale-up with fade — non-distracting
- Page transitions: 5% lateral slide — minimal, functional
```

### Step 5.7 — §E7: Overall Visual Professionalism

```
[POSITIVE VERIFICATION] — High visual professionalism for a utility app
The design system exhibits professional-grade craft:

1. **Consistent naming conventions** — all tokens follow prefix_category_variant pattern
   (spacing_*, radius_*, elevation_*, text_*, color prefix convention)
2. **Exhaustive dark mode** — 218 dark mode color tokens, 1:1 parity with light mode
3. **Documented rationale** — XML comments explain WHY choices were made (OKLCH values,
   WCAG ratios, "considerate utility" motion character)
4. **Chromatic warmth** — surfaces are never neutral gray; green tint carries through
   all surface levels, creating warmth distinctive from generic Material apps
5. **Hub layout information architecture** — hero card gradient for primary action,
   full-width cards for secondary actions, 2-column grid for advanced tools,
   section headers for grouping — clear visual hierarchy without complex ConstraintLayout
6. **Empty states** — branded with raccoon mascot, constrained max-width text,
   centered composition with appropriate vertical spacing
7. **Skeleton loading** — dedicated shimmer dimensions for loading placeholders
```

### Step 5.8 — §E8: Product Aesthetics Axis-Driven

```
[POSITIVE VERIFICATION] — Appropriate utility-first aesthetics
For a Productivity/Utility app (per Phase 0 calibration):

- **Clarity over delight**: Information density is prioritized — file names, sizes,
  dates are immediately visible without decoration
- **Functional color**: Category colors serve navigation (file type identification),
  not just decoration. Duplicate group colors distinguish grouping.
- **Restrained animation**: motion_micro at 120ms for state feedback, motion_page at
  280ms for navigation — no playful bounces or excessive easing
- **Warm but professional**: Chromatic green-tinted surfaces add personality without
  impeding utility. The raccoon branding (logo, mascot naming) adds warmth.
- **Data-first layouts**: Dashboard surfaces numeric data prominently
  (NumericBody, NumericMedium, NumericTitle styles all use tabular lining figures)
```

### Step 5.9 — §E9: Visual Identity & Recognizability

```
[POSITIVE VERIFICATION] — Strong visual identity through design choices
The app has distinctive visual DNA:

1. **Forest green + warm amber** — an unusual color combination that stands out from
   Material's default purple/teal palette. The green communicates nature/organic
   (raccoon theme), while amber adds energy.
2. **Raccoon mascot** — used in empty states, hub header, app icon. Creates memorable
   personality. Subdued at 0.85 alpha when decorative.
3. **Chromatic surfaces** — the green-tinted surface ladder is unique; most apps use
   neutral gray surfaces. This creates warmth recognizable even in screenshots.
4. **Hero card gradient** — heroCardStart to heroCardEnd at 135° creates a distinctive
   primary action card that anchors the hub screen.
5. **"Ricky" character naming** — the mascot has a name (referenced in empty state
   placeholder text), adding personality to a utility app.
```

### Step 5.10 — §E10: Data Storytelling & Visual Communication

```
[POSITIVE VERIFICATION] — Thoughtful data visualization tokens
The design system includes dedicated data visualization infrastructure:

- **Segmented bar** dimensions: category_bar_height (6dp), segment_gap (1dp),
  category_bar_radius (3dp) — for storage breakdown visualization
- **Top file bar** dimensions: top_file_bar_height (3dp), rank_width (20dp),
  bar_min_width (4dp/2dp) — for ranked file size display
- **Size severity colors**: sizeWarn, sizeDanger, sizeCritical —
  graduated urgency for large file indicators
- **Category colors with distinct hues**: 8 file types with maximally-different
  hues (blue, purple, pink, teal, orange, violet, cyan, gray)
- **Tabular numerals** throughout: tnum fontFeatureSettings ensures aligned
  columns in numeric displays (storage sizes, file counts, dates)
- **Dashboard card architecture**: storage card → savings card → stats card →
  category breakdown → top files → quick actions — progressive disclosure of
  increasing detail
```

```
[LOW] — F-052: Dashboard storage ProgressBar uses platform style instead of branded
Section: §E10 — Data Storytelling & Visual Communication
Finding: fragment_dashboard.xml:68-74 — The main storage usage progress bar uses
  `style="@android:style/Widget.ProgressBar.Horizontal"` (platform default) instead of
  the app's branded `@style/Widget.FileCleaner.ProgressIndicator`. This means it renders
  with the platform's default track shape and doesn't use the branded trackCornerRadius
  or trackThickness.
Why it matters: The storage bar is the hero data visualization on the dashboard —
  it should use the branded progress style for visual consistency.
Recommendation: Switch to Material LinearProgressIndicator with the branded style.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

---

### Phase 5 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §E1 — Design Token System | 1 | 0 | 0 | 0 | 1 |
| §E2 — Visual Rhythm & Spatial Composition | 1 | 0 | 0 | 0 | 1 |
| §E3 — Color Craft & Contrast | 1 | 0 | 0 | 0 | 1 |
| §E4 — Typography Craft | 1 | 0 | 0 | 0 | 1 |
| §E5 — Component Visual Quality | 1 | 0 | 0 | 0 | 1 |
| §E6 — Interaction Design Quality | 0 | 0 | 0 | 0 | 0 |
| §E7 — Overall Visual Professionalism | 0 | 0 | 0 | 0 | 0 |
| §E8 — Product Aesthetics Axis-Driven | 0 | 0 | 0 | 0 | 0 |
| §E9 — Visual Identity & Recognizability | 0 | 0 | 0 | 0 | 0 |
| §E10 — Data Storytelling | 1 | 0 | 0 | 0 | 1 |
| **TOTAL** | **6** | **0** | **0** | **0** | **6** |

### Positive Verifications (Phase 5)

1. **Comprehensive design token architecture** — 3-file token system covering color (OKLCH), spacing (4dp grid), motion (5 tiers), typography (Major Third) [CODE]
2. **Consistent spatial rhythm across layouts** — all spacing uses token references, zero hardcoded dp values [CODE]
3. **OKLCH-based color system** — perceptually uniform surface elevation ladder, chromatic warm surfaces, documented WCAG ratios [CODE]
4. **Complete typography system** — 20+ text appearances with optical tracking corrections and line height scaling [CODE]
5. **Comprehensive component style system** — 6 button variants, 4 card variants, 3 chip variants, state-aware selectors [CODE]
6. **Rich interaction design** — Material ripple on all interactive elements, 48dp touch targets, 2D focus navigation for keyboard/accessibility [CODE]
7. **State-aware interaction feedback** — exhaustive state coverage in color selectors (disabled/checked/pressed/focused) [CODE]
8. **Custom motion character** — pathInterpolator with "considerate utility" easing, enter/exit asymmetry, motion vocabulary tokens [CODE]
9. **High visual professionalism** — documented rationale, exhaustive dark mode parity, consistent naming conventions [CODE]
10. **Strong visual identity** — forest green + warm amber palette, raccoon mascot, chromatic surfaces, distinctive hub layout [CODE]
11. **Data visualization infrastructure** — tabular numerals, segmented bars, size severity colors, progressive disclosure dashboard [CODE]

---

**Phase 5 is complete.**

**Cumulative findings: Phase 1 + Phase 2 + Phase 3 + Phase 4 + Phase 5**

| Severity | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Total |
|----------|---------|---------|---------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 | 0 | 0 | 0 |
| HIGH | 1 | 0 | 2 | 0 | 0 | 3 |
| MEDIUM | 6 | 2 | 5 | 3 | 0 | 16 |
| LOW | 11 | 6 | 5 | 5 | 6 | 33 |
| **Total** | **18** | **8** | **12** | **8** | **6** | **52** |

---

## PHASE 6 — DEEP AESTHETIC ANALYSIS (Category E continued)

> **Calibration reminder:** This phase builds on the Character Brief confirmed in Step 6.2.
> Character Statement: *"A friendly, competent woodland helper — Ricky the raccoon is casual
> and encouraging, but quietly professional underneath. The app feels warm and alive without
> being childish."*

### Step 6.1 — §DS1-DS2: Style Classification

```
[POSITIVE VERIFICATION] — Coherent Material Design 2 with Chromatic Warm Overlay
Design School: Material Components 1.12.0 (structural framework) with a distinctive
chromatic warm character overlay. The app commits to Material's component library
(MaterialCardView, ChipGroup, TextInputLayout, BottomNavigationView, MaterialButton)
while overlaying a custom surface system that replaces Material's neutral grays with
green-tinted warm surfaces.

Style Coherence Score: 9/10
- All components are Material Components — no mixing of incompatible design systems
- The custom overlay (colors, motion, typography) is additive, not contradictory
- Only deviation: F-048 (dashboard buttons using un-branded Material parent style)
```

### Step 6.2 — §DP0-DP2: Character Brief

```
[POSITIVE VERIFICATION] — Character Brief (confirmed by user)

| Dimension    | Score | Evidence |
|-------------|-------|----------|
| Warmth      | 8/10  | Chromatic green-tinted surfaces, raccoon mascot, encouraging copy |
| Energy      | 6/10  | Brisk but restrained motion (120-280ms), utility-first |
| Formality   | 3/10  | "Ricky sniffed out", "space hogs", "copycats", contractions |
| Complexity  | 5/10  | Hub layout with clear hierarchy, progressive disclosure |
| Playfulness | 7/10  | Animal metaphors, emoji usage, celebration moments |
| Trust       | 7/10  | Forest green = reliability, shield icon, professional underneath |

Character Statement: "A friendly, competent woodland helper — Ricky the raccoon is
casual and encouraging, but quietly professional underneath. The app feels warm and
alive without being childish. It knows its job (file management) and does it efficiently,
but makes the experience feel personal rather than clinical."
```

### Step 6.3 — §DBI1 + §DBI3: Brand Archetype + Anti-Genericness

```
[POSITIVE VERIFICATION] — Strong brand archetype with high anti-genericness

Brand Archetype: **Caregiver / Everyman hybrid**
- Caregiver: "Ricky" takes care of your storage, cleans up, protects (antivirus shield)
- Everyman: Approachable, informal, "your buddy" rather than an authority figure
- NOT Hero (doesn't boast), NOT Sage (doesn't lecture), NOT Creator (doesn't inspire)

12-Signal Anti-Genericness Audit:
 1. ✅ Custom color palette (forest green + warm amber — unusual combination)
 2. ✅ Named mascot with personality ("Ricky" the raccoon)
 3. ✅ Chromatic warm surfaces (green-tinted, not default Material gray)
 4. ✅ Distinctive copy voice (raccoon metaphors throughout 800+ strings)
 5. ✅ Custom motion character ("considerate utility" with pathInterpolator)
 6. ✅ Hero card gradient (branded primary action card)
 7. ✅ Tabular numerals system (tnum for data displays)
 8. ✅ Hub icon tint system (unique tint per feature card)
 9. ✅ Category color system (8 distinct file type colors)
10. ⚠️ System font (sans-serif) — no custom typeface
11. ✅ Branded snackbar/dialog/chip styles
12. ✅ Success celebration with overshoot (400ms emphasis moment)

Anti-Genericness Score: 11/12 — Only system font usage prevents a perfect score.
Could this be any file manager? No — the forest green palette, raccoon persona,
chromatic surfaces, and copy voice are distinctive. This app has visual DNA.
```

### Step 6.4 — §DC1-DC5: Color Science

```
[POSITIVE VERIFICATION] — Professional-grade color architecture
Audited in depth during Phase 5 §E3. Key additional observations:

**Three-layer color architecture:**
- Primitive: Raw hex values with OKLCH documentation
- Semantic: Role-based tokens (surfaceBase, textPrimary, colorError)
- Component: Usage-specific tokens (heroCardStart, chip_bg_color, card_stroke_color)
All three layers present and complete.

**Perceptual color science:**
- OKLCH model used for lightness steps — perceptually uniform progression
- Surface ladder: L=0.98 → 0.96 → 0.94 — ~2% steps, very refined
- Dark mode: L=0.12 base — not inverted, genuinely designed dark palette

**Accent color narrative:**
- Forest green (#247A58) = nature, growth, reliability → matches Caregiver archetype
- Warm amber (#E8861F) = energy, warmth, friendliness → matches Everyman personality
- Complementary hue relationship (green/amber) creates visual tension without clash

**Gradient logic:**
- Hero card: heroCardStart → heroCardEnd at 135° — single gradient, reserved for
  primary action only. Not overused elsewhere.
```

```
[LOW] — F-053: Hub card icon tints reuse catDocument color across multiple cards
Section: §DC5 — Color Narrative
Finding: fragment_raccoon_manager.xml — Three hub cards (Janitor line 337, Tree line 510,
  Dual Pane line 571) all use `app:tint="@color/catDocument"` (teal) for their icons,
  despite having different background tints (tintJanitor, tintTree, tintDualPane).
  This creates a visual same-ness between cards that could otherwise be more distinctive.
Why it matters: The icon-on-tint color should ideally be unique per card to reinforce
  each feature's identity. Using catDocument as a generic "teal accent" weakens the
  color narrative.
Recommendation: Define dedicated icon accent colors per hub card (e.g., accentOnTintTree,
  accentOnTintJanitor) following the pattern already used for accentOnTintAnalysis.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 6.5 — §DT1-DT4: Typography Deep Audit

```
[POSITIVE VERIFICATION] — Typography system carries character effectively
Audited in depth during Phase 5 §E4. Additional deep observations:

**Type personality:**
- sans-serif (Roboto on Android) — clean, trustworthy, legible. Appropriate for a
  utility app. Roboto's openness aligns with the Caregiver warmth.
- sans-serif-medium used for emphasis (Subtitle, Label, BodyMedium) — adds weight
  without heavy bold. Matches "quietly professional" character.

**Scale audit (5 clear levels):**
1. Display (32sp bold) — hero stats numbers
2. Headline (26sp bold) — page titles
3. Title (20sp medium) — section headings
4. Subtitle (16sp medium) / Body (14sp regular) — content
5. Caption (10sp) / Label (11sp) — metadata, labels
Clear hierarchy with no ambiguous middle layers.

**Craft:**
- Optical sizing via tracking: -0.02 at 32sp → +0.03 at 10sp (correct)
- Line height scaling: 1.2× for headings → 1.5× for small text (correct)
- Max-width constraints on text blocks (280dp, 300dp) — line length control

**Expressiveness:**
- Tabular numeral variants at every scale level (NumericDisplay through NumericMedium)
- Monospace variant for code/paths (FileViewer.Content)
- Label with ALL CAPS + wide tracking for section markers
- Type contributes to identity through precision, not through custom fonts
```

### Step 6.6 — §DCO1-DCO6: Component Character

```
[POSITIVE VERIFICATION] — Components carry brand character consistently

1. **Buttons** — 6 variants forming clear priority hierarchy:
   Filled (primary) → Outlined (secondary) → Text (tertiary) → Ghost (quaternary)
   + Destructive (error-colored) + Icon (circular). All share 12dp corners, no all-caps,
   48dp touch targets. Communicate brand without shouting.

2. **Inputs** — TextInputLayout with rounded 12dp corners, branded hint color,
   branded stroke. Feels modern and safe. The Dense variant keeps compact feel.

3. **Cards** — 4 variants (Default, Selected, Elevated, Flat) with consistent 16dp
   corners. StateListAnimator provides physical-feeling press/focus/drag feedback.
   Elevation scale is intentional: Flat(0dp) → Subtle(2dp) → Raised(4dp) → Floating(8dp).
   Cards feel like real surfaces, not flat rectangles.

4. **Navigation** — Bottom nav with labeled items, primary-colored active state,
   tertiary inactive. Doesn't dominate — stays at elevation_nav (8dp) with
   background divider. Orients without competing with content.

5. **Modals** — Bottom sheets with 24dp top corners, branded surfaceColor background,
   custom enter/exit animations. Dialogs with 24dp corners, branded background drawable,
   scale+fade animation. Feel focused and intentional.

6. **Snackbar** — Branded with primaryDark background, 12dp corners, amber accent action
   text. Carries character — not default Material gray.
```

### Step 6.7 — §DH1-DH4: Hierarchy & Gestalt

```
[POSITIVE VERIFICATION] — Strong visual hierarchy with effective Gestalt application

**Visual weight map (Hub screen):**
- Primary: Hero gradient card (scan) — gradient background, white text, elevation_raised
- Secondary: Full-width feature cards — standard surface, Subtitle text
- Tertiary: 2-column advanced tool cards — smaller footprint, Caption descriptions
- Quaternary: Section header label — primary-colored, SectionHeader style

The hierarchy reads clearly: primary action → secondary features → advanced tools.

**Gestalt principles in use:**
- Proximity: Cards grouped by function (primary, secondary, advanced 2-column grid)
- Similarity: All hub cards share consistent structure (icon circle + title + description)
- Continuation: Vertical scroll with consistent card width creates reading flow
- Figure/ground: surfaceBase background vs surfaceColor cards — clear separation

**Contrast as composition:**
- Hero card gradient creates strong focal point against neutral cards
- Category colors create navigable landmarks in file lists
- Severity colors (Critical/High/Medium/Low) in antivirus create urgency hierarchy
- Accent stripe on file cards (optional 4dp color edge) adds dimensional interest

**Reading flow:**
- Hub: top-to-bottom with widening complexity (hero → cards → 2-column grid)
- Browse: toolbar → search → filters → count → list — correct inverted pyramid
- File cards: icon → name → meta → checkbox — left-to-right reading order
```

### Step 6.8 — §DSA1-DSA5: Surface & Atmosphere

```
[POSITIVE VERIFICATION] — Distinctive surface system with clear material character

**Background system:**
- 5-step elevation ladder: surfaceBase (L=0.98) → surfaceColor (0.96) →
  surfaceElevated (0.94) → surfaceDim (0.92) → surfaceContainerHighest (0.81)
- Each step is ~2-3% OKLCH lightness — perceptually uniform
- Dark mode mirrors with chromatic near-blacks (L=0.12 → 0.15 → 0.18)

**Material character:**
- The app feels like it's made of **warm paper** — surfaces have warmth (green tint)
  but not gloss or glass effects. No blur, no transparency, no glassmorphism.
- Cards have subtle elevation (2dp) with a 1dp stroke — feels tangible without
  being skeuomorphic. The stroke provides edge definition even in flat contexts.

**Light source:**
- Consistent top-down implied light (Material standard)
- elevation_raised (4dp) on hero card creates visible shadow below
- stateListAnimator moves translationZ on press (0dp = pressed in) — physical feel

**Atmosphere coherence:**
- The app has a **warm woodland study** mood — green-tinted surfaces + amber accents
  create a sense of natural warmth. Not clinical, not playful, not dark — just warm.
- This mood aligns perfectly with the raccoon character (forest creature, tidy helper)
```

### Step 6.9 — §DM1-DM5: Motion Vocabulary

```
[POSITIVE VERIFICATION] — Distinctive motion system with documented character

**Motion inventory:**
- nav_enter: 5% slide-right + fade (280ms) — page transitions
- nav_exit: -5% slide-left + fade (160ms) — faster exit
- item_enter: 12% slide-up + fade (220ms enter, 120ms fade) — list items
- dialog_enter: 90% scale-up + fade (220ms) — subtle appearance
- sheet_enter/exit: slide-up/down — bottom sheets
- success_check_enter: 30% scale-up with overshoot (400ms) — celebration
- card_state_list_anim: translationZ changes (120ms) — press/focus/drag
- layout_item_stagger: cascading item entrance — list reveal

**Duration and easing:**
- All animations use custom pathInterpolator (M 0,0 C 0.35,0 0.1,1 1,1) —
  snappier than Material default, consistent "considerate utility" feel
- 5-tier vocabulary used consistently: micro(120), enter(220), exit(160),
  page(280), emphasis(400)
- Exit < Enter — correct asymmetry (things leave faster than they arrive)

**Motion signature:**
- The 30% → 100% scale-up with gentle overshoot on success (success_check_enter)
  is the app's distinctive motion moment. Reserved for completion celebrations.
- The snappier-than-Material interpolator creates a feeling of efficiency.

**Reduced motion:**
- Confirmed in Phase 4: MotionUtil.isReducedMotion() disables RecyclerView
  layout animations. [CODE verified]
```

### Step 6.10 — §DI1-DI4: Iconography System

```
[POSITIVE VERIFICATION] — Consistent Material icon system with appropriate usage

**Style coherence:**
- All icons are Material Design filled style — 24dp viewport, single path, filled shapes
- Consistent across 58+ vector drawables (ic_*.xml)
- No mixing of outlined/rounded/sharp styles — all filled

**Stroke weight consistency:**
- N/A — filled icons don't have stroke weight variance. All use solid fills.
- Fill colors use semantic tokens: @color/textSecondary (default), @color/textPrimary
  (nav icons), @color/catOther (file icon)

**Expressiveness:**
- Icons are standard Material set (search, folder, delete, shield, etc.) — functional
  rather than expressive. This is appropriate for utility context.
- Hub cards add personality through colored circle backgrounds (bg_hub_icon_circle)
  rather than custom icon shapes.

**Custom icon opportunity:**
- The app uses ic_raccoon_logo for branding (empty states, hub header) — this is the
  primary custom icon. However, it's a PNG/vector asset I couldn't locate as an XML
  vector drawable (no ic_raccoon_logo.xml found). This may be a raster asset.
```

```
[LOW] — F-054: Icon fill colors baked into drawable XML instead of using runtime tint
Section: §DI1 — Iconography
Finding: Several icons have fill colors hardcoded in the drawable XML:
  - ic_file.xml: fillColor="@color/catOther" (semantic token, but still baked in)
  - ic_folder.xml: fillColor="@color/textSecondary"
  - ic_scan.xml: fillColor="@color/textSecondary"
  - ic_shield.xml: fillColor="@color/textSecondary"
  However, layouts that use these icons often apply runtime tints via `app:tint=`,
  which correctly override the baked-in fillColor.
Why it matters: The baked-in fillColor is technically overridden by runtime tint, so
  there's no visual bug. But having two color sources (drawable XML + layout tint) can
  confuse developers editing icons.
Recommendation: Set all icon fillColors to a neutral value (e.g., #000000 or @color/textPrimary)
  and rely exclusively on runtime tinting for context-specific colors.
Effort: LOW
Confidence: LOW — Source: [CODE]
```

### Step 6.11 — §DST1-DST4: State Design

```
[POSITIVE VERIFICATION] — All four non-default states are thoughtfully designed

1. **Empty states** — On-brand with raccoon personality:
   - include_empty_state.xml: mascot icon (0.85 alpha) + Title + Body text, max-width
     constrained, centered composition. 14 context-specific empty messages with
     raccoon metaphors ("Ricky hasn't sniffed out any files yet", "Ricky is eager to
     catch copycats!"). Pre-scan vs post-scan variants for each screen.
   - Clear next action: "Scan your storage" button appears when appropriate.
   Score: 10/10 — best-in-class empty state design with personality.

2. **Loading states** — Branded skeleton + progress:
   - include_loading_state.xml: CircularProgressIndicator (branded style) + Subtitle +
     BodySmall detail text. accessibilityLiveRegion="assertive" for screen readers.
   - item_skeleton_card.xml: Structural skeleton matching item_file.xml layout — same
     icon size, text block proportions, card styling. Uses bg_shimmer_placeholder
     (surfaceDim, rounded corners). Not raw spinners.
   Score: 9/10 — skeleton cards are excellent; could add shimmer animation for polish.

3. **Error states** — Human, specific, actionable:
   - 30+ error strings with raccoon personality ("Ricky hit a snag!", "Oops!")
   - Specific causes stated ("check permissions", "try a different name")
   - Actionable next steps ("give it another go", "check if the destination is accessible")
   - NOT generic "Something went wrong" — every error has context.
   Score: 9/10 — excellent error copy, though no dedicated error state layout.

4. **Success states** — Celebration with branded animation:
   - include_success_state.xml: ic_check_circle tinted colorSuccess + Title + Body,
     centered composition. success_check_enter.xml: 30%→100% scale with gentle
     overshoot (400ms emphasis) — a real celebration moment.
   - Success strings: "Raccoon swept away %s — nice and tidy!", "Ricky is done rummaging!"
   Score: 9/10 — genuine delight moments appropriate for utility app.
```

### Step 6.12 — §DCVW1-DCVW3: Copy × Visual Alignment

```
[POSITIVE VERIFICATION] — Copy voice and visual personality are strongly aligned

**Voice-Character Coherence Score: 9/10**

Evidence:
- Visual warmth (chromatic green surfaces) matches copy warmth ("give it another go")
- Visual playfulness (raccoon mascot, amber accent) matches copy playfulness
  ("space hogs", "copycats", "rummaging")
- Visual professionalism (Material components, token system) matches copy professionalism
  (clear error messages with specific causes)
- Success animation (overshoot celebration) matches success copy ("nice and tidy!", "Ricky approves!")
- The informal formality level (3/10) is consistent between visual and verbal — no
  formal-looking UI with casual copy, or vice versa

The -1 point: Cloud setup and security scanner sections use technical jargon
("OAuth2 Access Token", "ADB accessible over network", "ELF binary", "DEX bytecode")
that breaks the casual Ricky persona. These screens read like developer documentation
rather than a raccoon speaking.
```

```
[MEDIUM] — F-055: Cloud/security copy breaks Ricky persona with technical jargon
Section: §DCVW2 — Copy-Voice Coherence
Finding: Cloud setup strings use developer terminology:
  - "OAuth2 Access Token" (cloud_gdrive_token_hint)
  - "You can generate one from the Google API Console" (cloud_gdrive_help)
  - "Server URL (e.g. https://cloud.example.com/remote.php/webdav)" (cloud_webdav_host_hint)
  - "Port must be between 1 and 65535" (cloud_error_port_range)
  Security scanner threats use technical terms:
  - "Linux executable binary (ELF)" (threat_desc_elf_binary)
  - "Android bytecode file (DEX)" (threat_desc_loose_dex)
  - "Android Debug Bridge accessible over network" (threat_net_adb_network)
  These strings break the established casual Ricky persona.
Why it matters: Users encountering these screens after the warm hub/browse experience
  will feel a jarring tone shift. Non-technical users may feel excluded.
Recommendation: Rewrite technical strings in Ricky's voice:
  - "OAuth2 Access Token" → "Your Google access key (Ricky needs this to connect)"
  - "ELF binary" → "A program file that could be risky"
  - "Port must be between 1 and 65535" → "That port number doesn't look right — try a number between 1 and 65535"
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

### Step 6.13 — §DIL1-DIL3: Illustration Audit

```
[POSITIVE VERIFICATION] — Minimal illustration system, consistent usage
The app uses a single illustrative element: the raccoon mascot (ic_raccoon_logo).
This appears in:
- Hub header (56dp, full opacity)
- Empty states (96dp, 0.85 alpha — slightly faded to avoid competing with text)
- App icon (launcher_foreground)

No additional illustrations, custom artwork, or imported graphic elements found.
This is appropriate — a utility app doesn't need elaborate illustration. The raccoon
mascot carries all personality requirements without cluttering the interface.

The single illustrative element is consistent: same asset at different sizes/opacities.
No style clash between illustration and UI components.
```

### Step 6.14 — §DDV1-DDV3: Data Visualization Character

```
[POSITIVE VERIFICATION] — Data visualizations use app's design system
Data visualization elements use the app's color palette and tokens:

- **Storage bar**: Uses colorPrimary for fill, borderDefault for track background,
  category colors for segmented breakdown. Tokens: category_bar_height(6dp),
  category_bar_radius(3dp).
- **Top files bar**: size-proportional bars with rank numbers. Tokens:
  top_file_bar_height(3dp), rank_width(20dp), bar_min_width(4dp).
- **Severity cards**: colorPrimary/amber/error/success with light tint backgrounds.
  Consistent with the app's color system.
- **ArborescenceView**: Custom Canvas tree using the full color token system
  (tree_* dimensions, surfaceDim backgrounds, textPrimary labels).

All data displays use tabular numerals (tnum fontFeatureSettings) for aligned columns.
Chart types match data: bars for relative comparison, tree for hierarchy, severity
cards for categorical counts. No mismatched chart types.
```

### Step 6.15 — §DTA1-DTA2: Design Token Architecture

```
[POSITIVE VERIFICATION] — Complete three-layer token architecture

**Layer 1 — Primitive (raw values):**
Present in colors.xml as hex values with OKLCH documentation.
Examples: #247A58, #E8861F, #FBFAF8. All primitives documented with perceptual model.

**Layer 2 — Semantic (role-based):**
Present as named color resources: colorPrimary, surfaceBase, textPrimary, colorError.
Spacing: spacing_sm, spacing_lg. Typography: text_body, text_title.
Motion: motion_enter, motion_micro.

**Layer 3 — Component (usage-specific):**
Present as color selectors: chip_bg_color, card_stroke_color, bottom_nav_color,
switch_thumb_color. Component styles: Widget.FileCleaner.Card, Widget.FileCleaner.Button.

All three layers are present and well-separated. Semantic tokens reference primitives,
component tokens reference semantic tokens. Migration path: not needed — architecture
is complete.

**Character-carrying token gaps:**
- Accent color: ✅ tokenized (colorAccent, colorPrimary)
- Border radius: ✅ tokenized (radius_btn, radius_card, radius_modal, radius_pill)
- Transition durations: ✅ tokenized (motion_micro, motion_enter, motion_exit, motion_page, motion_emphasis)
- No gaps found in character-carrying tokens.
```

### Step 6.16 — §DRC1-DRC3: Responsive Character

```
[LOW] — F-056: No tablet-specific layout resources
Section: §DRC1-DRC3 — Responsive Character
Finding: The app provides only `res/layout/` resources — no `res/layout-sw600dp/`,
  `res/layout-w600dp/`, or `res/layout-land/` directories found. On tablets, the hub
  screen's full-width cards and the file list will stretch to fill the wider viewport,
  creating uncomfortably long line lengths and wasted space.
  The 2-column grid in the hub (advanced tools) is already responsive in concept, but
  it uses LinearLayout weight-based splitting which works identically at any width.
Why it matters: The app targets broad Android — tablets are ~15% of the market. The
  current single-column layout will feel like "stretched phone" on 10" tablets.
Recommendation: Consider adding sw600dp layouts for key screens:
  - Hub: 2-column primary cards, 3-column advanced tools
  - Browse: wider file cards or multi-column grid
  - Dashboard: side-by-side cards
Effort: MEDIUM
Confidence: MEDIUM — Source: [CODE]
```

### Step 6.17 — §DDT1-DDT2: Trend Calibration

```
[POSITIVE VERIFICATION] — Intentional trend usage with low dating risk

**Trend inventory:**
1. ✅ Rounded corners everywhere (12-24dp) — current trend, low risk of dating
2. ✅ Chromatic warm surfaces — emerging trend replacing neutral gray, low risk
3. ✅ Bottom navigation — established pattern, not trend-dependent
4. ✅ Skeleton loading — established best practice, not a trend
5. ✅ OKLCH color model — forward-looking (CSS Color Level 4), low risk
6. ⚠️ Card-heavy layout — current but showing signs of fatigue in 2025-2026 design
7. ✅ System font (Roboto) — timeless, never dates

**Strategic posture:** Intentional. The app uses current patterns (rounded corners,
warm surfaces) because they serve utility, not because they're fashionable.

**Trend risk (12-18 months):**
- Card fatigue: The hub screen uses 8+ cards in a vertical list. Industry is moving
  toward more compact information-dense layouts. LOW risk — cards work well for
  touch targets on mobile.
- No high-risk trends detected. The app avoids glassmorphism, neomorphism, micro-gradients,
  and other trend-of-the-year patterns that would date quickly.
```

### Step 6.18 — §DP3: Character Deepening

```
[POSITIVE VERIFICATION] — Character deepening analysis (no action required, analysis only)

Applying 7 deepening techniques to the confirmed Character Brief:

1. **Concentrate what already works:** The chromatic warm surface system is the strongest
   visual differentiator — it could be pushed further by adding very subtle warm-tinted
   shadows (currently shadows are default neutral).

2. **Remove what contradicts:** The technical jargon in cloud/security copy (F-055)
   contradicts the casual persona. This is the primary character contradiction.

3. **Add one signature detail per component:** Cards already have stateListAnimator
   (physical press feel). Chips have pill shape. Missing: search bar could have a
   branded empty state (raccoon magnifying glass?).

4. **Deepen color narrative:** The green-to-amber journey (nature → warmth → energy)
   could be more explicit — consider using amber in success states instead of generic
   green (colorSuccess).

5. **Intensify the motion signature:** The overshoot celebration (success_check_enter)
   is reserved for completion. Consider adding a subtle raccoon "tail wag" animation
   on the hub mascot when scan completes.

6. **Unify the type voice:** Typography is already unified. The only gap is that
   technical screens (cloud, security) don't use the same friendly formatting as
   file management screens.

7. **Make one element undeniably this app:** The hero gradient card on the hub screen
   is the most distinctive visual element. Its 135° gradient with rounded 20dp corners
   is recognizable in screenshots.
```

### Step 6.19 — §DBI2: Design Signature Specification

```
[POSITIVE VERIFICATION] — Design signature identified

**The one visual element that identifies this app on a white page:**
The forest green gradient card with warm amber accent and raccoon mascot.

Specifically: a card with a green-to-darker-green diagonal gradient (135°),
20dp rounded corners, white text, and the raccoon silhouette — placed on the
warm off-white (OKLCH L=0.98) surface.

**Systematic implementation:**
- Currently used only for the hero scan card on the hub screen
- Could be extended to: onboarding slides, app store screenshots, notification
  large icon, widget background
- The gradient + raccoon combination is the visual fingerprint
```

### Step 6.20 — §DCP1-DCP3: Competitive Positioning

```
[POSITIVE VERIFICATION] — Competitive analysis (observation only)

**Direct visual competitors (Android file managers):**
1. Google Files — minimal, white, blue accent, clean but impersonal
2. Solid Explorer — dark, power-user, blue/orange, complex
3. FX File Explorer — dated, gray, functional but generic
4. Total Commander — retro, dense, no personality
5. MiXplorer — Material, customizable, neutral

**Positioning matrix:**
                    Warm ←——→ Cold
Playful  |  RACCOON  |          |
         |           | Files    |
Serious  |           | Solid    |
         | MiX       | Total Cmd|

**Whitespace opportunity:**
Raccoon occupies "warm + playful" territory that NO major file manager competitor
claims. Most competitors are cold/neutral (Google Files) or cold/serious (Solid
Explorer). The raccoon persona + chromatic warm surfaces claim unique visual territory.

This is the app's strongest competitive advantage from a design perspective.
```

### Step 6.21 — §SR0-SR6: Source Material

```
[N/A] — No named source material referenced
The user did not reference a named source (game, brand, IP, show).
The raccoon character appears to be original, not based on an external source.
This step is not applicable.
```

---

### Phase 6 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §6.1 Style Classification | 0 | 0 | 0 | 0 | 0 |
| §6.2 Character Brief | 0 | 0 | 0 | 0 | 0 |
| §6.3 Brand Archetype | 0 | 0 | 0 | 0 | 0 |
| §6.4 Color Science | 1 | 0 | 0 | 0 | 1 |
| §6.5 Typography Deep | 0 | 0 | 0 | 0 | 0 |
| §6.6 Component Character | 0 | 0 | 0 | 0 | 0 |
| §6.7 Hierarchy & Gestalt | 0 | 0 | 0 | 0 | 0 |
| §6.8 Surface & Atmosphere | 0 | 0 | 0 | 0 | 0 |
| §6.9 Motion Vocabulary | 0 | 0 | 0 | 0 | 0 |
| §6.10 Iconography | 1 | 0 | 0 | 0 | 1 |
| §6.11 State Design | 0 | 0 | 0 | 0 | 0 |
| §6.12 Copy × Visual | 1 | 0 | 0 | 1 | 0 |
| §6.13 Illustration | 0 | 0 | 0 | 0 | 0 |
| §6.14 Data Viz | 0 | 0 | 0 | 0 | 0 |
| §6.15 Token Architecture | 0 | 0 | 0 | 0 | 0 |
| §6.16 Responsive | 1 | 0 | 0 | 0 | 1 |
| §6.17 Trend Calibration | 0 | 0 | 0 | 0 | 0 |
| §6.18 Character Deepening | 0 | 0 | 0 | 0 | 0 |
| §6.19 Design Signature | 0 | 0 | 0 | 0 | 0 |
| §6.20 Competitive Positioning | 0 | 0 | 0 | 0 | 0 |
| §6.21 Source Material | 0 | 0 | 0 | 0 | 0 |
| **TOTAL** | **4** | **0** | **0** | **1** | **3** |

### Positive Verifications (Phase 6)

1. **Material Design 2 with coherent chromatic overlay** — style coherence 9/10 [CODE]
2. **Character Brief confirmed** — Caregiver/Everyman hybrid, warmth 8, playfulness 7, formality 3 [USER]
3. **Anti-genericness 11/12** — forest green palette, raccoon persona, chromatic surfaces, branded copy [CODE]
4. **Professional-grade color science** — three-layer architecture, OKLCH perceptual model, accent narrative [CODE]
5. **Typography carries character** — 5 clear hierarchy levels, optical tracking, tabular numerals system [CODE]
6. **Components carry brand** — 6 button variants, 4 card variants, physical stateListAnimator feedback [CODE]
7. **Strong visual hierarchy** — hero gradient → full-width cards → 2-column grid → section labels [CODE]
8. **Distinctive surface atmosphere** — warm paper feel, green-tinted near-blacks, chromatic elevation ladder [CODE]
9. **Documented motion vocabulary** — 5-tier system, custom interpolator, celebration signature moment [CODE]
10. **Consistent Material filled icon system** — 58+ icons, all filled style, runtime tinting [CODE]
11. **Best-in-class state design** — branded empty/loading/error/success with raccoon personality [CODE]
12. **Copy-visual coherence 9/10** — voice matches personality across core experience [CODE]
13. **Complete three-layer token architecture** — primitive → semantic → component, no gaps [CODE]
14. **Intentional trend usage** — low dating risk, no fad patterns [CODE]
15. **Unique competitive position** — "warm + playful" territory unclaimed by major competitors [ANALYSIS]

---

**Phase 6 is complete.**

**Cumulative findings: Phase 1 through Phase 6**

| Severity | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Phase 6 | Total |
|----------|---------|---------|---------|---------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| HIGH | 1 | 0 | 2 | 0 | 0 | 0 | 3 |
| MEDIUM | 6 | 2 | 5 | 3 | 0 | 1 | 17 |
| LOW | 11 | 6 | 5 | 5 | 6 | 3 | 36 |
| **Total** | **18** | **8** | **12** | **8** | **6** | **4** | **56** |

---

## PHASE 7 — UX & INFORMATION ARCHITECTURE (Category F)

> **Calibration reminder:** §F was amplified during Phase 0 (Productivity/Utility domain).
> File managers live or die by flow efficiency — every extra tap costs trust.

### Step 7.1 — §F1: Information Architecture

```
[POSITIVE VERIFICATION] — Well-structured hub-and-spoke navigation model

**Navigation structure:**
- Single Activity (MainActivity) with NavHostFragment
- 5 bottom nav tabs: Browse | Duplicates | Raccoon Hub | Large | Junk
- 14 total fragment destinations in nav_graph.xml
- Hub (RaccoonManagerFragment) is the start destination and central spoke

**Bottom nav tab architecture (5 tabs):**
1. Browse — full file browser with search, filter, sort
2. Duplicates — duplicate files list with selection/delete
3. Raccoon Hub (center) — central hub with 8 feature cards
4. Large Files — files sorted by size with selection/delete
5. Junk — identified junk files with selection/delete

**Hub spoke destinations (from center tab):**
- Scan Storage → triggers scan (in-place)
- Analysis → StorageDashboardFragment
- Quick Clean → delete confirmation dialog (in-place)
- Janitor → re-scan + review (dialog, then tab navigation)
- Arborescence → ArborescenceFragment (tree view)
- Optimize → OptimizeFragment
- Dual Pane → DualPaneFragment
- Cloud → CloudBrowserFragment
- Antivirus → AntivirusFragment

**Feature discoverability (30-second test):**
- Bottom nav: Browse, Duplicates, Large, Junk — immediately visible (4 features)
- Center hub: Scan, Analysis, Quick Clean, Janitor, Tree, Optimize, Dual Pane,
  Cloud, Antivirus — visible after scrolling hub (9 features)
- Settings: gear icon in top-right header — standard placement
- Dashboard: accessible from Analysis card OR tapping scan status after completion
Total: 13 major features discoverable within 30 seconds ✓

**Depth vs breadth:**
- Maximum depth: 2 taps from hub to any feature (Hub → card → destination)
- Browse can go deeper via folder navigation, but breadcrumbs/back maintain orientation
- No feature is buried more than 2 levels deep ✓
```

```
[LOW] — F-057: Janitor card action is ambiguous — re-scans then expects manual tab navigation
Section: §F1 — Information Architecture
Finding: RaccoonManagerFragment.kt:219-233 — The "Janitor" (deep clean) card triggers a
  re-scan via requestPermissionsAndScan() and shows a snackbar "Ricky is starting a deep
  clean," but doesn't actually navigate anywhere after the scan completes. The user is
  left on the hub screen with a comment in code: "After scan completes, user can review
  duplicates/junk/large tabs" (line 226). There's no automatic navigation or guidance
  about what to do next.
Why it matters: The Janitor promises "deep clean" but delivers "scan + figure it out."
  The user must manually navigate to each tab (Duplicates, Large, Junk) to review.
  This breaks the "no dead ends" principle.
Recommendation: After scan completion, either auto-navigate to a summary screen or
  show a dialog with quick links to Duplicates/Large/Junk tabs.
Effort: MEDIUM
Confidence: HIGH — Source: [CODE]
```

```
[LOW] — F-058: Bottom nav uses ic_nav_large (server rack icon) for "Large Files"
Section: §F1 — Information Architecture
Finding: bottom_nav_menu.xml:20-21 — The Large Files tab uses `ic_nav_large` which is a
  server rack / horizontal bars icon (pathData shows three horizontal bar groups). This
  icon doesn't clearly communicate "large files" — it could mean "storage", "database",
  or "server." The icon would be more intuitive as a size-related symbol (e.g., expanding
  arrows, a large file with size indicator).
Why it matters: Minor — users learn icon meanings quickly, but first-time recognition
  could be improved.
Recommendation: Consider using a more size-suggestive icon for the Large Files tab.
Effort: TRIVIAL
Confidence: LOW — Source: [CODE]
```

### Step 7.2 — §F2: User Flow Quality

```
[POSITIVE VERIFICATION] — Critical user flows are well-designed

**Flow 1: Scan Storage**
Hub → "Scan Storage" card → permission check → scan progress (with phases, percentage,
cancel button) → completion snackbar with summary → scan status bar becomes tappable
(opens dashboard). No dead ends. Error recovery: permission denied dialog with "Open
Settings" button. Scan failure: error snackbar with styled error appearance.

**Flow 2: Browse Files**
Browse tab → search bar → filter panel (category, sort, extensions) → file list →
long-press file → context menu bottom sheet → actions (open, share, rename, move,
copy, compress, delete, favorites, protected). No dead ends. Back navigation works
at every level.

**Flow 3: Delete Files (with undo)**
Select files (checkbox) → selection action bar appears → "Delete" button →
confirmation dialog with file count + total size + undo timeout → delete →
undo snackbar with configurable timeout (default 8s) → files moved to trash.
Error recovery: move-to-trash failure shows specific error message.

**Flow 4: Quick Clean**
Hub → "Quick Clean" card → if no junk: "Sparkling clean!" snackbar → if junk exists:
confirmation dialog with count + size + undo timeout → clean → undo snackbar.
Pre-scan guard: "Ricky needs scan data first" snackbar. No dead ends.

**Flow 5: Cloud Setup**
Hub → "Cloud" card → CloudBrowserFragment → provider picker → OAuth flow (Google Drive,
GitHub) or manual setup (SFTP, WebDAV) → connection test → browse remote files.
Error recovery: connection failure snackbar with retry guidance.

**Back navigation:**
- All non-tab fragments have back button in toolbar (ImageButton with ic_arrow_back)
- Bottom nav reselect pops back stack to tab root
- Navigation animations: enter/exit + pop_enter/pop_exit for all transitions
- Bottom nav hides on non-tab screens, reappears on tab screens (with slide animation)
```

```
[MEDIUM] — F-059: Scan-dependent features show no affordance change when scan is needed
Section: §F2 — User Flow Quality
Finding: RaccoonManagerFragment.kt:175-179 — Cards requiring scan data (Analysis, Quick
  Clean, Arborescence, Optimize, Janitor) are dimmed to alpha 0.5f when no scan data
  exists. However, they remain clickable and tapping them shows a generic snackbar
  "Ricky needs scan data first." There's no visual indicator (badge, lock icon, or text
  overlay) explaining WHY the card is dimmed or what action is needed.
Why it matters: The alpha dimming is ambiguous — it could mean "disabled", "loading",
  or "unavailable." Combined with no inline explanation, this violates the principle of
  clear affordances. A new user may not understand why 5 of 8 hub cards appear inactive.
Recommendation: Add a subtitle text like "Scan first" or a small lock/scan icon overlay
  on dimmed cards. The existing card description text does update to "Scan first" —
  verify this is visible enough at 0.5f alpha.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 7.3 — §F3: Onboarding & First Use

```
[POSITIVE VERIFICATION] — Well-designed progressive onboarding

**First-run sequence:**
1. Splash screen (installSplashScreen) → brief branded moment
2. OnboardingDialog (3 steps): Welcome → Browse → Scan
   - Step indicator: "Step 1 of 3" with a11y announcement
   - Per-step icon: raccoon logo → browse icon → scan icon
   - Skip button on steps 1-2, Back button on steps 2-3
   - "Done" on final step triggers permission request
   - Non-cancelable (setCancelable(false)) — can't dismiss accidentally
3. Privacy notice dialog (if first launch): title + message + "Accept" button
   - Non-cancelable, must accept before proceeding
4. Permission request: MANAGE_EXTERNAL_STORAGE (Android 11+) or READ/WRITE (Android 10)
   - If denied: dialog explaining why permission is needed with "Open Settings" link
5. Auto-scan: on permission grant, scan starts automatically

**Value before permission:**
- Onboarding explains what the app does BEFORE requesting storage access ✓
- Privacy notice appears BEFORE scan starts ✓
- Permission request explains WHY access is needed ✓

**Progressive disclosure:**
- Hub shows all features but dims scan-dependent ones
- Advanced tools separated in "Advanced" section with section header
- Filters/display options in Browse are collapsible (hidden by default)
```

```
[LOW] — F-060: Onboarding dialog uses programmatic view construction instead of XML layout
Section: §F3 — Onboarding & First Use
Finding: OnboardingDialog.kt:45-91 — The onboarding dialog constructs its entire UI
  programmatically using LinearLayout, TextView, ImageView created in code with manual
  padding, margins, and text appearance references. This bypasses the design token system
  for spacing (hardcoded getDimensionPixelSize calls) and makes the layout harder to
  maintain and preview in Android Studio.
Why it matters: The programmatic approach works but is inconsistent with all other screens
  which use XML layouts. It also means the onboarding can't be previewed in the layout
  editor, making design iteration harder.
Recommendation: Create a dedicated onboarding step layout XML file with proper token
  references, matching the pattern used by all other screens.
Effort: LOW
Confidence: MEDIUM — Source: [CODE]
```

### Step 7.4 — §F4: Copy Quality

```
[POSITIVE VERIFICATION] — Excellent copy quality across 808 strings

**Labels:** Clear and unambiguous throughout:
- "Scan Storage" (not "Index"), "Move to Trash" (not "Delete permanently")
- Tab labels: Browse, Duplicates, Raccoon, Large, Junk — all under 12 chars
- Settings labels with descriptive section headers

**Error messages:** Human, specific, and actionable (30+ error strings):
- "Ricky couldn't move that file — check if the destination is accessible"
- "Oops! File names can't contain / : * ? \" < > or | — try a different name"
- Each error includes: what went wrong + likely cause + suggested action

**Terminology:** Mostly consistent:
- "Scan" for main action, "analyze" for post-scan processing
- "File" used consistently (not "item")
- "Move to Trash" preferred over "Delete" for file removal

**Pluralization:** Proper Android plurals used for countable items:
- scan_complete, confirm_delete_detail, optimize_applied all use <plurals> tags
- 25 plural string resources defined
```

```
[MEDIUM] — F-061: "Delete" vs "Move to Trash" terminology inconsistency
Section: §F4 — Copy Quality
Finding: The app uses "Move to Trash" in some contexts but "Delete" in others:
  - ctx_delete: "Move to Trash" (context menu — correct, soft language)
  - delete_selected: "Move selected to Trash" (selection bar title — correct)
  - btn_delete_selected button text: "Delete" (selection bar button — incorrect,
    says "Delete" but action is Move to Trash)
  - delete_n_files_title: "Move %d file(s) to trash?" (confirmation dialog — correct)
  The destructive button in the selection bar says "Delete" (hard language) while the
  confirmation dialog clarifies it's actually "Move to Trash" (soft language).
Why it matters: Users see "Delete" on the red button and may think files are permanently
  removed. The confirmation dialog then says "Move to Trash" which creates confusion.
  Consistent soft language throughout would reduce anxiety.
Recommendation: Change the destructive button label to "Trash" or "Move to Trash" to
  match the confirmation dialog language.
Effort: TRIVIAL
Confidence: HIGH — Source: [CODE]
```

### Step 7.5 — §F5: Micro-Interaction Quality

```
[POSITIVE VERIFICATION] — Comprehensive micro-interaction feedback

**Button press feedback:**
- All MaterialButtons have built-in ripple feedback ✓
- ImageButtons use `?attr/selectableItemBackgroundBorderless` for circular ripple ✓
- Cards use `android:foreground="@drawable/ripple_card"` for bounded ripple ✓
- Hero card uses branded `ripple_hero_card` for distinct feedback ✓

**State change confirmation:**
- File operations (move, copy, rename, compress, extract) all show success snackbars
  with specific messages ("Ricky moved %s to its new home!")
- Delete operations show undo snackbar with configurable timeout
- Scan completion shows both snackbar (summary) and status bar update
- Selection mode: count updates live in selection bar ("3 selected")

**Drag-and-drop feedback:**
- card_state_list_anim: translationZ +6dp on drag_hovered state
- Cards visually lift when dragged over ✓

**Form submission feedback:**
- Cloud connection test shows progress → success/failure snackbar
- Search: live filtering with 300ms debounce, count updates in real-time
- Permission request: dialog explains why → system dialog → result dialog if denied

**Selection feedback:**
- CheckBox toggles with primary-colored buttonTint
- Selected card background changes to selectedBackground color
- Selection action bar slides in with count + action buttons
- Bulk selection: "Select All Files", "Select All Folders", "Select All", "Deselect All"

**Hub card state feedback:**
- Dimmed (alpha 0.5f) when scan not done
- Description text updates during scan to show current phase
- Raccoon avatar bounces (scale 1.15f with overshoot) when scan completes
```

### Step 7.6 — §F6: Engagement, Delight & Emotional Design

```
[POSITIVE VERIFICATION] — Genuine personality and delight moments

**Delight moments:**
1. **Raccoon celebration bounce** — when scan completes, the raccoon avatar on the hub
   does a 1.15x scale-up with OvershootInterpolator (RaccoonManagerFragment.kt:140-152).
   Respects reduced motion settings.
2. **Success check animation** — success_check_enter.xml: 30%→100% scale with gentle
   overshoot at 400ms emphasis duration. Used for operation completions.
3. **Scan completion celebration** — "Ricky is done rummaging! Found %d files using %s."
   with snackbar summary showing duplicates, junk, large file counts.
4. **Quick Clean success** — "Raccoon swept away %s — nice and tidy!" with specific
   size information.
5. **Empty state personality** — 14 unique empty state messages with raccoon character:
   "Ricky searched high and low — no results for '%s'"

**Reward patterns:**
- Post-scan summary: immediate reward showing what was found
- Clean completion: size freed is highlighted ("swept away 250 MB")
- Hub badge counts on tabs: show actionable item counts (duplicates: 12, junk: 45)
  creating implicit "clean up" motivation

**Emotional design assessment:**
- The app feels alive: Ricky has opinions ("approves of your tidy storage!"),
  reactions ("hit a snag!"), and celebrations ("done rummaging!")
- Not purely transactional: operations have personality throughout
- The casual tone reduces anxiety around destructive operations (delete → "Move to Trash"
  with undo, not "permanently delete")

**Missing delight opportunities:**
- No streak/milestone rewards (e.g., "3rd scan this week!")
- No progress-over-time visualization (storage freed total)
- No animated transitions between scan phases on hub (just text updates)
These are nice-to-haves, not requirements for a utility app.
```

---

### Phase 7 Summary

| Step | Findings | CRIT | HIGH | MED | LOW |
|------|----------|------|------|-----|-----|
| §F1 — Information Architecture | 2 | 0 | 0 | 0 | 2 |
| §F2 — User Flow Quality | 1 | 0 | 0 | 1 | 0 |
| §F3 — Onboarding & First Use | 1 | 0 | 0 | 0 | 1 |
| §F4 — Copy Quality | 1 | 0 | 0 | 1 | 0 |
| §F5 — Micro-Interaction Quality | 0 | 0 | 0 | 0 | 0 |
| §F6 — Engagement & Delight | 0 | 0 | 0 | 0 | 0 |
| **TOTAL** | **5** | **0** | **0** | **2** | **3** |

### Positive Verifications (Phase 7)

1. **Hub-and-spoke navigation** — all 13 major features discoverable within 30 seconds, max 2-tap depth [CODE]
2. **Critical user flows** — scan, browse, delete, quick clean, cloud all have complete flows with error recovery [CODE]
3. **Progressive onboarding** — 3-step dialog with value-before-permission, skip/back navigation, privacy notice [CODE]
4. **808 strings with excellent copy** — human error messages, consistent terminology, proper pluralization [CODE]
5. **Comprehensive micro-interaction feedback** — ripple on all interactives, success snackbars, undo for deletes [CODE]
6. **Genuine personality and delight** — raccoon bounce, success celebration, 14 unique empty states, encouraging copy [CODE]
7. **Back navigation completeness** — all non-tab fragments have back buttons, reselect pops to tab root [CODE]
8. **Bottom nav visibility management** — slides in/out based on tab vs non-tab destinations [CODE]
9. **Keyboard shortcuts** — Ctrl+S (settings), Ctrl+F (search) for power users [CODE]
10. **Badge counts** — actionable item counts on Duplicates/Large/Junk tabs as implicit motivation [CODE]

---

**Phase 7 is complete.**

**Cumulative findings: Phase 1 through Phase 7**

| Severity | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 | Phase 6 | Phase 7 | Total |
|----------|---------|---------|---------|---------|---------|---------|---------|-------|
| CRITICAL | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| HIGH | 1 | 0 | 2 | 0 | 0 | 0 | 0 | 3 |
| MEDIUM | 6 | 2 | 5 | 3 | 0 | 1 | 2 | 19 |
| LOW | 11 | 6 | 5 | 5 | 6 | 3 | 3 | 39 |
| **Total** | **18** | **8** | **12** | **8** | **6** | **4** | **5** | **61** |

**Next: Phase 8 — Accessibility (Category G + §L7)**

Awaiting confirmation to proceed with Phase 8, or to fix findings from Phase 1-7.
