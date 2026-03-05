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

**Next: Phase 1 — Domain Logic & Correctness (Category A)**

This is a large codebase (21,652+ LOC). The full audit will cover all 17 phases systematically. Awaiting confirmation to proceed.
