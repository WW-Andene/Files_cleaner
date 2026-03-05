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
