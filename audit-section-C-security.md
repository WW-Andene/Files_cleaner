# Section C — Security & Trust: Full Audit Report

**App**: Raccoon File Manager
**Auditor**: Claude (automated)
**Date**: 2026-03-01
**Scope**: All 34 Kotlin source files, AndroidManifest.xml, build.gradle, proguard-rules.pro, file_paths.xml, all XML resources

---

## C1 — Authentication & Authorization (Android Permissions)

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C1-01 | **PASS** | No fix needed | Permission model correctly tiered by API level: Android 10 (`READ/WRITE_EXTERNAL_STORAGE`), Android 13+ (`READ_MEDIA_*`), Android 14+ (`READ_MEDIA_VISUAL_USER_SELECTED`), Android 11+ (`MANAGE_EXTERNAL_STORAGE`) |
| C1-02 | **PASS** | No fix needed | `WRITE_EXTERNAL_STORAGE` has `maxSdkVersion="29"` — correctly scoped since minSdk=29 |
| C1-03 | **PASS** | No fix needed | `MANAGE_EXTERNAL_STORAGE` accompanied by `tools:ignore="ScopedStorage"` — documented rationale |
| C1-04 | **PASS** | No fix needed | Runtime permission request uses `ActivityResultContracts.RequestMultiplePermissions()` — modern API |
| C1-05 | **PASS** | No fix needed | Settings intent for `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` uses `package:$packageName` URI |
| C1-06 | **PASS** | No fix needed | `android:allowBackup="false"` prevents backup data leaks via ADB |
| C1-07 | **PASS** | No fix needed | Permission denied dialog directs user to app settings with clear messaging |

### Summary: **ALL CLEAR** — Permission model is comprehensive and correctly implemented.

---

## C2 — Injection & Path Traversal

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C2-01 | **HIGH** | **FIXED** | `moveFile()` did not validate that `targetDirPath` is within external storage. A crafted path could move files to app-private dirs or system directories. **Fix**: Added `isPathWithinStorage()` validation. |
| C2-02 | **HIGH** | **FIXED** | `copyFile()` — same issue as C2-01. **Fix**: Added `isPathWithinStorage()` validation. |
| C2-03 | **MEDIUM** | **FIXED** | `renameFile()` blocked only `/` and `\0` but not other dangerous FAT32/exFAT characters (`:`, `*`, `?`, `"`, `<`, `>`, `\|`). **Fix**: Expanded validation via `hasInvalidFilenameChars()` helper. |
| C2-04 | **MEDIUM** | **FIXED** | `batchRename()` — same incomplete validation as C2-03. **Fix**: Uses shared `hasInvalidFilenameChars()`. |
| C2-05 | **MEDIUM** | **FIXED** | Zip slip check used `canonicalPath.startsWith()` without appending separator — `outDir="/storage/emulated/0/foo"` would pass for `outFile="/storage/emulated/0/foobar"`. Also didn't reject `..` in entry names before resolution. **Fix**: Appends `File.separator` to canonical dir, rejects entries containing `..`. |
| C2-06 | **MEDIUM** | **FIXED** | `compressFile()` had no path validation. **Fix**: Added `isPathWithinStorage()` check. |
| C2-07 | **MEDIUM** | **FIXED** | `extractArchive()` had no path validation for the archive itself. **Fix**: Added `isPathWithinStorage()` check. |
| C2-08 | **MEDIUM** | **FIXED** | `FileContextMenu` rename dialog passed user input directly without UI-layer validation. **Fix**: Added defense-in-depth filename character check before calling callback. |
| C2-09 | **MEDIUM** | **FIXED** | `BatchRenameDialog` could produce pattern-generated filenames with invalid characters. **Fix**: Filters out renames producing invalid filenames before confirming. |
| C2-10 | **PASS** | No fix needed | `FileOpener.open()` uses `FileProvider.getUriForFile()` — no raw file:// URIs exposed to other apps |
| C2-11 | **PASS** | No fix needed | Share intent uses `FLAG_GRANT_READ_URI_PERMISSION` — correct per-URI grant |
| C2-12 | **PASS** | No fix needed | Zip bomb protection: `MAX_EXTRACT_BYTES` (2 GB) and `MAX_EXTRACT_ENTRIES` (10,000) limits in place |

### Summary: **9 issues found, all FIXED.** Path traversal in move/copy operations and incomplete filename validation were the most critical.

---

## C3 — Import Safety & Data Loading

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C3-01 | **MEDIUM** | **FIXED** | `ScanCache.jsonToDirectoryNode()` recursively deserializes JSON trees with no depth limit — deeply nested malicious cache could cause `StackOverflowError`. **Fix**: Added `MAX_TREE_DEPTH = 100` guard that truncates recursion. |
| C3-02 | **PASS** | No fix needed | Cache file is in app-private storage (`context.filesDir`) — not accessible to other apps |
| C3-03 | **PASS** | No fix needed | JSON parsing wrapped in try/catch with automatic cache deletion on corruption |
| C3-04 | **PASS** | No fix needed | Cache version check discards incompatible formats (`CACHE_VERSION` constant) |
| C3-05 | **PASS** | No fix needed | Enum parsing (`FileCategory.valueOf()`) has fallback to `FileCategory.OTHER` |
| C3-06 | **PASS** | No fix needed | `FilePreviewDialog.showTextPreview()` limits reads to `MAX_TEXT_BYTES = 10 KB` — no memory bombs from large text files |

### Summary: **1 issue found, FIXED.** Recursive deserialization now has a depth guard.

---

## C4 — Network & Dependencies

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C4-01 | **INFO** | **DOCUMENTED** | Dependencies are outdated but not critically vulnerable. Recommended upgrades: Kotlin 1.9.0→1.9.24, AGP 8.1.0→8.2.2, core-ktx 1.12.0→1.13.1, appcompat 1.6.1→1.7.0, material 1.11.0→1.12.0, navigation 2.7.4→2.7.7, lifecycle 2.6.2→2.8.4, coroutines 1.7.3→1.8.1. *Note: Cannot apply in this environment (no network). Listed for developer action.* |
| C4-02 | **PASS** | No fix needed | App is fully offline — zero network calls, zero URLs, zero HTTP clients |
| C4-03 | **PASS** | No fix needed | No WebView, no dynamic code loading, no `Runtime.exec()`, no `ProcessBuilder` |
| C4-04 | **PASS** | No fix needed | ProGuard/R8 enabled in release builds with `minifyEnabled true` and `shrinkResources true` |
| C4-05 | **PASS** | No fix needed | Only standard AndroidX, Google Material, Glide, and JetBrains libraries — no suspicious or unmaintained dependencies |

### Summary: **Dependency updates recommended** but no active CVEs identified. Zero attack surface from network.

### Recommended Dependency Updates (for developer)

```gradle
// build.gradle (root)
ext.kotlin_version = "1.9.24"
classpath "com.android.tools.build:gradle:8.2.2"
classpath "androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7"

// app/build.gradle
implementation 'androidx.core:core-ktx:1.13.1'
implementation 'androidx.appcompat:appcompat:1.7.0'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.navigation:navigation-fragment-ktx:2.7.7'
implementation 'androidx.navigation:navigation-ui-ktx:2.7.7'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4'
implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.4'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.4'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
```

---

## C5 — Privacy & Data Minimization

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C5-01 | **PASS** | No fix needed | Zero `Log.d/e/w/i/v` calls in entire codebase — no PII leakage via logcat |
| C5-02 | **PASS** | No fix needed | No analytics, tracking, or telemetry |
| C5-03 | **PASS** | No fix needed | Scan cache stores only metadata (paths, sizes, dates) — no file content |
| C5-04 | **PASS** | No fix needed | SharedPreferences in `MODE_PRIVATE` — only UI thresholds and path sets |
| C5-05 | **PASS** | No fix needed | Favorites/protected paths stored in app-private prefs — not accessible to other apps |
| C5-06 | **LOW** | Accepted | Error messages use `e.localizedMessage` which could expose internal path information — acceptable for a file manager app where path transparency is expected |
| C5-07 | **PASS** | No fix needed | Onboarding dialog explicitly states "Your data stays on-device — nothing is uploaded" |

### Summary: **ALL CLEAR** — Excellent privacy posture. No logging, no analytics, no network data transmission.

---

## C6 — Compliance & Legal

### Findings

| ID | Severity | Status | Description |
|----|----------|--------|-------------|
| C6-01 | **INFO** | Documented | `file_paths.xml` uses `<external-path path="." />` (broadest scope). Correct for a file manager but requires Play Store `MANAGE_EXTERNAL_STORAGE` declaration form. |
| C6-02 | **PASS** | No fix needed | FileProvider is `android:exported="false"` with per-URI grants via `FLAG_GRANT_READ_URI_PERMISSION` |
| C6-03 | **PASS** | No fix needed | Only standard AndroidX, Google, and Glide libraries — no licensing concerns |
| C6-04 | **PASS** | No fix needed | `tools:ignore="ScopedStorage"` properly suppresses lint warning with documented justification |
| C6-05 | **PASS** | No fix needed | Main activity is the only exported component (required for launcher) |
| C6-06 | **PASS** | No fix needed | No custom permissions defined — no permission confusion risk |

### Summary: **ALL CLEAR** — Properly configured for Play Store file manager category.

---

## Files Modified

| File | Changes |
|------|---------|
| `MainViewModel.kt` | Added `isPathWithinStorage()`, `hasInvalidFilenameChars()`, `INVALID_FILENAME_CHARS`, path validation in `moveFile()`, `copyFile()`, `compressFile()`, `extractArchive()`. Hardened `renameFile()`, `batchRename()` character validation. Improved zip slip check with separator and `..` rejection. |
| `ScanCache.kt` | Added `MAX_TREE_DEPTH` constant and depth parameter to `jsonToDirectoryNode()` recursion. |
| `FileContextMenu.kt` | Added UI-layer filename character validation in rename dialog (defense-in-depth). |
| `BatchRenameDialog.kt` | Added output filename validation filter before confirming batch rename. |
| `strings.xml` | Added `op_invalid_path` string resource for path traversal error message. |

---

## Overall Security Rating

| Subsection | Rating | Issues Found | Issues Fixed |
|------------|--------|--------------|--------------|
| C1 — Auth & Permissions | **EXCELLENT** | 0 | 0 |
| C2 — Injection & Path Traversal | **GOOD** (was POOR) | 9 | 9 |
| C3 — Import Safety | **GOOD** (was FAIR) | 1 | 1 |
| C4 — Network & Dependencies | **GOOD** | 1 (info) | Documented |
| C5 — Privacy | **EXCELLENT** | 0 | 0 |
| C6 — Compliance | **EXCELLENT** | 0 | 0 |
| **OVERALL** | **GOOD** | **11** | **10 fixed, 1 documented** |
