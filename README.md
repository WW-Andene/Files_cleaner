# File Cleaner — Android App

A powerful file management app for Android 10+ that scans, classifies, and cleans storage. Built with Kotlin, MVVM, and Material 3.

## Features

| Section | What it does |
|---------|-------------|
| **Raccoon Manager** | Central hub — scan storage, quick clean, tree view, analysis, optimizer, dual pane, cloud, antivirus, and janitor |
| **Browse** | View all files, filter by category (Images, Video, Audio, Documents, APKs…), sort by name/size/date, list/grid view, folder expand/collapse |
| **Duplicates** | Finds exact duplicates using MD5 hashing — colour-coded groups for easy review |
| **Large Files** | Lists all files >= 50 MB sorted by size so you can reclaim space quickly |
| **Junk** | Detects `.tmp`, `.log`, `.bak`, cache files, and downloads older than 90 days |
| **Analysis** | Storage overview with segmented bar, category breakdown, top 10 largest files, quick action cards |
| **Tree View** | Interactive file tree visualization with search, filters, zoom, and node detail |
| **Storage Optimizer** | Rule-based file reorganization |
| **Dual Pane** | Two-panel file manager with cross-pane operations |
| **Cloud Browser** | SFTP, WebDAV, Google Drive file browsing |
| **Antivirus** | Hybrid security scanner with multi-phase threat detection |
| **File Viewer** | Fullscreen viewer for PDF, images, and text files |
| **Format Converter** | Convert between image and PDF formats |
| **Batch Rename** | 4 modes: pattern, prefix/suffix, find/replace, case |
| **Compression** | Multi-file compression with batch support |

## Getting Started

### Requirements
- **Android Studio** Hedgehog (2023.1) or newer
- **JDK 17**
- Android device or emulator running **Android 10 (API 29)+**

### Steps

1. **Open the project** — Launch Android Studio, select the project folder
2. **Sync Gradle** — Click *Sync Now* in the yellow bar
3. **Run the app** — Connect a device or start an emulator, press Run
4. **Grant permissions**
   - Android 10: grant *Read/Write External Storage*
   - Android 11+: *Settings > Allow access to manage all files*
   - Android 13+: grant individual media permissions
5. **Scan your storage** — Open the Raccoon Manager tab and tap *Scan Storage*

## Architecture

```
app/
├── data/
│   ├── FileItem.kt              — Data model + FileCategory enum
│   ├── UserPreferences.kt       — SharedPreferences wrapper
│   └── cloud/                   — Cloud connection storage
├── utils/
│   ├── FileScanner.kt           — Walks storage, classifies files
│   ├── DuplicateFinder.kt       — Size-then-MD5 deduplication
│   ├── JunkFinder.kt            — Junk + large file detection
│   ├── CrashReporter.kt         — Automatic crash reporting
│   └── antivirus/               — Multi-phase security scanning
├── viewmodel/
│   └── MainViewModel.kt         — Shared state (scan -> results)
├── ui/
│   ├── raccoon/                  — Raccoon Manager hub
│   ├── browse/                   — File browser with filters
│   ├── duplicates/               — Duplicate file detection
│   ├── large/                    — Large file listing
│   ├── junk/                     — Junk file detection
│   ├── analysis/                 — Storage analysis dashboard
│   ├── arborescence/             — Interactive tree view
│   ├── optimize/                 — Storage optimizer
│   ├── dualpane/                 — Dual pane file manager
│   ├── cloud/                    — Cloud/network browser
│   ├── security/                 — Antivirus scanner
│   ├── viewer/                   — File viewer
│   ├── settings/                 — App settings
│   ├── onboarding/               — First-run onboarding
│   └── widget/                   — Custom UI components
└── FileCleanerApp.kt            — Application class
```

**Stack:** Kotlin, MVVM, LiveData, Coroutines, Navigation Component, Material 3, Glide

## Notes

- **Deletion is permanent** — there is no recycle bin on Android. Always review before tapping Delete.
- The app does **not** touch system files, `Android/data`, or `Android/obb`.
- Large scans (phones with 50k+ files) may take 30-60 seconds. Progress is shown in the header.

## Reference Build

Canonical baseline: Build APK #124, PR #20, commit `8e8dc9f`.

## Dependencies

All managed via Gradle — no manual setup needed:
- `androidx.navigation` 2.7.4
- `androidx.lifecycle` 2.6.2
- `kotlinx.coroutines` 1.7.3
- `glide` 4.16.0
- `material` 1.11.0
