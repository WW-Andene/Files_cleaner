# 📱 File Cleaner — Android App

A powerful file management app for Android 10+ that scans, classifies, and cleans storage.

## ✨ Features

| Tab | What it does |
|-----|-------------|
| **Browse** | View all files, filter by category (Images, Video, Audio, Documents, APKs…), sort by name/size/date |
| **Duplicates** | Finds exact duplicates using MD5 hashing — colour-coded groups for easy review |
| **Large Files** | Lists all files ≥ 50 MB sorted by size so you can reclaim space quickly |
| **Junk** | Detects `.tmp`, `.log`, `.bak`, cache files, and downloads older than 90 days |

## 🚀 Getting Started

### Requirements
- **Android Studio** Hedgehog (2023.1) or newer
- **JDK 17**
- Android device or emulator running **Android 10 (API 29)+**

### Steps

1. **Open the project**
   - Launch Android Studio → *Open* → select the `FileCleanerApp` folder

2. **Sync Gradle**
   - Click *Sync Now* in the yellow bar (or File → Sync Project with Gradle Files)

3. **Run the app**
   - Connect your phone via USB (enable USB debugging) or start an emulator
   - Press ▶ Run

4. **Grant permissions**
   - On Android 10: grant *Read/Write External Storage* when prompted
   - On Android 11+: you'll be directed to *Settings → Allow access to manage all files*
   - On Android 13+: grant individual media permissions

5. **Scan your storage**
   - Tap the **Scan Storage** button (bottom-right)
   - Wait for the scan to complete (progress shown in the header)
   - Browse results in each tab

---

## 🏗️ Architecture

```
app/
├── data/
│   └── FileItem.kt          — Data model + FileCategory enum
├── utils/
│   ├── FileScanner.kt       — Walks storage, classifies files
│   ├── DuplicateFinder.kt   — Size-then-MD5 deduplication
│   └── JunkFinder.kt        — Junk + large file detection
├── viewmodel/
│   └── MainViewModel.kt     — Shared state (scan → results)
├── ui/
│   ├── adapters/FileAdapter.kt
│   ├── browse/BrowseFragment.kt
│   ├── duplicates/DuplicatesFragment.kt
│   ├── large/LargeFilesFragment.kt
│   └── junk/JunkFragment.kt
└── MainActivity.kt          — Permissions, navigation, FAB
```

**Stack:** Kotlin · MVVM · LiveData · Coroutines · Navigation Component · Material 3 · Glide

---

## ⚠️ Notes

- **Deletion is permanent** — there is no recycle bin on Android. Always review before tapping Delete.
- The app does **not** touch system files, `Android/data`, or `Android/obb`.
- Large scans (phones with 50k+ files) may take 30–60 seconds. Progress is shown in the header.

## 📦 Dependencies

All managed via Gradle — no manual setup needed:
- `androidx.navigation` 2.7.4
- `androidx.lifecycle` 2.6.2
- `kotlinx.coroutines` 1.7.3
- `glide` 4.16.0
- `material` 1.11.0
