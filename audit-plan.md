# Raccoon File Manager — Complete Audit Plan

## Context

Comprehensive audit of the Raccoon File Manager Android app using two audit skill documents:
1. **app-audit-SKILL** — 16 categories (A–P) covering logic, state, security, performance, visual design, UX, accessibility, platform, code quality, data, domain depths, polish, deployment, i18n, future scenarios, and R&D improvement
2. **design-aesthetic-audit-SKILL** — 21 deep design sections covering style classification, color science, typography, motion, hierarchy, surfaces, iconography, trends, brand identity, competitive positioning, character systems, state design, responsive character, component character, copy-visual alignment, illustration, data viz, and token architecture

The goal is to execute every applicable section, fix the issues found, and produce a polished, professional app.

---

## App-Audit Sections (A–P)

### Section A — Domain Logic & Correctness (§A1–A7)

- **A1: Business Rule & Formula Correctness** — Verify file size calculations, date handling, sorting logic, constants, operator precision, boundary values
- **A2: Probability & Statistical Correctness** — N/A for file manager
- **A3: Temporal & Timezone Correctness** — File timestamps, modification dates display, epoch arithmetic, relative time calculations
- **A4: State Machine Correctness** — Scan/browse/action states, transition completeness, guard conditions, race conditions on rapid clicks, idempotency
- **A5: Embedded Data Accuracy** — File type mappings, MIME types, category classifications (images/videos/audio/documents/other), extension-to-category correctness
- **A6: Async & Concurrency Bug Patterns** — Coroutine safety, scan cancellation, concurrent file operations, stale closure captures, promise swallowing, missing cleanup
- **A7: Type Safety** — Null safety, type casting issues, implicit conversions (Kotlin adaptation of JS-focused section)
- **Key files**: `MainViewModel.kt`, `DuplicateFinder.kt`, `JunkFinder.kt`, `FileItem.kt`, `BrowseFragment.kt`, `ArborescenceFragment.kt`

---

### Section B — State Management & Data Integrity (§B1–B6)

- **B1: State Architecture** — Schema completeness, normalization, derived state staleness, initialization correctness, reset completeness
- **B2: Persistence & Storage** — Completeness of persisted state, schema versioning, quota management, cold start validation
- **B3: Reactive State Correctness** — LiveData/StateFlow observers, state updates on config change, lifecycle awareness
- **B4: State Synchronization** — Multiple observers of same state, race between UI updates and background scans
- **B5: State Reset & Cleanup** — Proper cleanup on navigation changes, fragment lifecycle
- **B6: State Testing** — Verifiable state transitions
- **Key files**: `MainViewModel.kt`, all Fragment files, SharedPreferences usage

---

### Section C — Security & Trust (§C1–C6)

- **C1: Authentication & Authorization** — N/A (no auth), but check Android permission handling (storage, file access, MANAGE_EXTERNAL_STORAGE)
- **C2: Injection & XSS** — Path traversal in file operations, intent injection, SQL injection if using Room/SQLite
- **C3: Prototype Pollution & Import Safety** — N/A (Kotlin), but check JSON.parse safety for any config/data loading
- **C4: Network & Dependencies** — External library versions, known CVEs, dependency freshness
- **C5: Privacy & Data Minimization** — What file data is collected/stored, analytics, PII in logs
- **C6: Compliance & Legal** — Storage permissions (Android 11+ scoped storage), copyright on icons/assets, open source license compliance
- **Key files**: `AndroidManifest.xml`, `build.gradle`, Gradle dependency files, proguard rules

---

### Section D — Performance & Resources (§D1–D5)

- **D1: Runtime Performance** — Main thread blocking during scans, unnecessary recomputations, list virtualization (RecyclerView), debounce/throttle on user actions
- **D2: Loading Performance** — App startup time, cold start computations, resource loading strategy
- **D3: Resource Budget** — APK size, dependency sizes, unused resources/code
- **D4: Memory Management** — Closure/listener leaks, timer leaks, bitmap memory, Glide cache management, large array retention during scans
- **D5: Battery & IO** — File system access patterns, unnecessary rescans, wakelock usage
- **Key files**: `MainViewModel.kt`, `DuplicateFinder.kt`, `JunkFinder.kt`, `ArborescenceView.kt`, `BrowseAdapter.kt`, `FileAdapter.kt`

---

### Section E — Visual Design Quality & Polish (§E1–E10)

- **E1: Design Token System** — Spacing scale consistency (4/8 grid), color palette architecture, typography scale, border radius system, shadow hierarchy, z-index governance, animation tokens
- **E2: Visual Rhythm & Spatial Composition** — Vertical rhythm, density consistency, alignment grid, whitespace intention, proportion, focal point clarity
- **E3: Color Craft & Contrast** — Color harmony, dark mode craft, accent consistency, WCAG contrast compliance, non-text contrast, state colors
- **E4: Typography Craft** — Heading hierarchy, line height, font pairing, letter spacing, label quality
- **E5: Component Visual Quality** — Button states, input field states, card design, badge/chip design, icon quality, divider usage
- **E6: Interaction Design Quality** — Press feedback, transition quality, loading state quality, animation narrative, empty state design, error state design
- **E7: Overall Visual Professionalism** — Design coherence, attention to detail, brand consistency, polish delta
- **E8: Product Aesthetics** — Axis-derived assessment (commercial, use context, audience, subject, aesthetic role)
- **E9: Visual Identity & Recognizability** — Visual signature, visual metaphor, accent color intentionality, anti-genericness audit
- **E10: Data Storytelling & Visual Communication** — Numbers as visual elements, hierarchy of insight, progressive complexity revelation
- **Key files**: All layout XML files, `dimens.xml`, `colors.xml`, `themes.xml`, `styles.xml`, drawable resources

---

### Section F — UX, Information Architecture & Copy (§F1–F6)

- **F1: Information Architecture** — Navigation model (browse/scan/arborescence tabs), content hierarchy, progressive disclosure, categorization logic
- **F2: User Flow Quality** — Scan workflow friction audit, browse-to-action flow, default values, action reversibility, confirmation dialog quality, feedback immediacy
- **F3: Onboarding & First Use** — First impression assessment, empty state → filled state transition, progressive complexity, activation path clarity
- **F4: Copy Quality** — Tone consistency, clarity, conciseness, terminology consistency ("file" vs "item", "scan" vs "analyze"), capitalization convention, action verb quality, empty state copy, error message copy
- **F5: Micro-Interaction Quality** — Press/touch states on all interactive elements, loading states for async operations, success confirmation, scroll behavior, focus indicators
- **F6: Engagement, Delight & Emotional Design** — Reward moments (after scan completion, after file deletion), personality moments, notification quality
- **Key files**: All layout XML files, `strings.xml`, all Fragment files, all adapter files

---

### Section G — Accessibility (§G1–G4)

- **G1: WCAG 2.1 AA Compliance** —
  - Perceivable: contentDescription on images/icons, semantic views, reading order matches visual order, color not sole signal, 4.5:1 text contrast, 3:1 non-text contrast, tooltips
  - Operable: keyboard/d-pad navigation, no focus traps, logical focus order, visible focus indicators, 48x48dp touch targets
  - Understandable: locale attribute, no unexpected context changes, input error identification
  - Robust: correct accessibility roles on custom components, dynamic status announcements
- **G2: Screen Reader Trace** — TalkBack trace of primary workflows, modal focus management, dynamic update announcements, icon-only button labels
- **G3: Keyboard/D-pad Navigation** — Tab through full app, custom component navigation, dialog focus trapping, back button behavior
- **G4: Reduced Motion** — Honor `prefers-reduced-motion` for all animations (CSS transitions, Kotlin animations, RecyclerView item animations)
- **Key files**: All layout XML files, custom views, Fragment files

---

### Section H — Browser Compatibility & Platform (§H1–H4, adapted for Android)

- **H1: Cross-Device Matrix** — API level support matrix (minSdk through targetSdk), device-specific quirks, manufacturer skins
- **H2: PWA & Service Worker** — N/A (native Android app)
- **H3: Mobile & Touch** — Touch target sizes (48dp minimum), gesture handling, safe area/insets (notch, navigation bar), back gesture navigation, edge-to-edge display
- **H4: Network Resilience** — Offline behavior for file operations, timeout handling, reconnection behavior
- **Key files**: `build.gradle` (minSdk/targetSdk), layout files, Fragment navigation code, `AndroidManifest.xml`

---

### Section I — Code Quality & Architecture (§I1–I6)

- **I1: Dead Code & Waste** — Unused functions, unused constants, unreachable branches, commented-out code, development artifacts (Log.d, TODO, FIXME)
- **I2: Naming Quality** — Casing conventions, semantic accuracy, boolean naming, event handler naming, magic numbers, unclear abbreviations
- **I3: Error Handling Coverage** — Every try/catch and async operation: caught, logged, surfaced to user, recovered to valid state
- **I4: Code Duplication** — Logic duplication across adapters/fragments, UI pattern duplication, constant duplication, copy-paste divergence
- **I5: Component & Module Architecture** — Single responsibility, god components (>300 lines), utility consolidation, dependency direction
- **I6: Documentation & Maintainability** — Algorithm comments, lying comments, architecture decisions, section organization
- **Key files**: All Kotlin source files

---

### Section J — Data Presentation & Portability (§J1–J4)

- **J1: Number & Data Formatting** — File size display consistency (KB/MB/GB formatting, decimal precision), date/time formatting (consistent format across all views), item count display ("45 items" not just "45"), null/zero/empty representation
- **J2: Data Visualization Quality** — Arborescence tree view accuracy (data points map to correct values), category breakdown correctness, small value visibility, visual vs computed agreement
- **J3: Asset Management** — File thumbnail loading reliability, icon loading, placeholder handling when thumbnails fail
- **J4: Import/Export** — Data export features if present, file sharing functionality
- **Key files**: `ArborescenceView.kt`, adapters, `FileItem.kt`, utility formatters, string resources

---

### Section K — Specialized Domain Depths (§K1–K5)

- **K1: Financial Precision** — N/A
- **K2: Medical/Health Precision** — N/A
- **K3: Probability & Gambling-Adjacent** — N/A
- **K4: Real-Time & Collaborative** — N/A
- **K5: AI/LLM Integration** — N/A unless AI features present
- **Note**: File manager domain does not activate these specialized depths. Skip or do a quick pass to confirm N/A.

---

### Section L — Optimization, Standardization & Polish Roadmap (§L1–L7)

- **L1: Code Optimization Opportunities** — Algorithm efficiency in scan/duplicate finding (O(n²) opportunities), memoization gaps, redundant computation, bundle size reduction
- **L2: Code Standardization** — Consistent patterns for error handling, data fetching/loading, utility consolidation, component API consistency, import ordering
- **L3: Design System Standardization** — Token consolidation (verify all values use tokens after E fixes), component variant audit, pattern library gaps, theme variable completeness
- **L4: Copy & Content Standardization** — Voice guide (3 adjective descriptors), terminology dictionary, capitalization audit, CTA optimization
- **L5: Interaction & Experience Polish** — Transition coherence, delight opportunities, state change communication, scroll experience, loading sequence, motion budget assessment
- **L6: Performance Polish** — Render jank identification, perceived performance improvements (skeleton screens, optimistic UI), startup sequence optimization, memory footprint reduction
- **L7: Accessibility Polish** — Landmark structure, heading hierarchy excellence, ARIA live region tuning (accessibility announcements), focus choreography, color-independent comprehension
- **Key files**: All source files — cross-cutting review

---

### Section M — Deployment & Operations (§M1–M3)

- **M1: Version & Update Management** — Version single source of truth (build.gradle versionName/versionCode), schema migration for SharedPreferences/DB across versions, cache busting for assets
- **M2: Observability** — Error reporting strategy (crash reporting), debug mode gating (Log.d behind BuildConfig.DEBUG), state inspection capability
- **M3: Feature Flags & Gradual Rollout** — Inventory of BuildConfig flags, dead flags cleanup, flag coupling documentation
- **Key files**: `build.gradle`, `BuildConfig` references, logging code

---

### Section N — Internationalization & Localization (§N1–N4)

- **N1: Hardcoded String Inventory** — Find ALL hardcoded user-visible strings in layouts (android:text="...") and Kotlin files (hardcoded Toast/Snackbar messages, error strings), extract to strings.xml
- **N2: Locale-Sensitive Formatting** — File sizes, dates, number formatting — ensure use of locale-aware formatters
- **N3: RTL Layout** — Check for hardcoded margin-left/padding-right vs marginStart/paddingEnd, icon mirroring considerations
- **N4: Locale Loading & Performance** — String resource structure, fallback chain, locale detection
- **Key files**: `res/values/strings.xml`, all layout XML files, all Kotlin files with hardcoded strings

---

### Section O — Development Scenario Projection (§O1–O7)

- **O1: Scale Cliff Analysis** — Large directories (1000+ files), deep trees (arborescence), many duplicates, scan performance cliffs with specific thresholds
- **O2: Feature Addition Risk Map** — Top 5 likely features and what breaks (cloud backup, favorites, search, batch operations, SD card support)
- **O3: Technical Debt Compounding Map** — Foundation coupling, terminology divergence, schema without migration, copy-paste divergence
- **O4: Dependency Decay Forecast** — Library version freshness, maintenance status, security history
- **O5: Constraint Evolution Analysis** — Migration complexity for storage expansion, multi-language, theming
- **O6: Maintenance Trap Inventory** — Functions with hidden side effects, order-dependent initialization, load-bearing magic values
- **O7: Bus Factor & Knowledge Concentration** — Black box code sections, undocumented algorithms
- **Key files**: Cross-cutting analysis of all architecture

---

### Section P — Research, Development & Improvement (§P1–P12)

#### Features

- **P1: Smart Cleanup Scheduling & Automation** — Background WorkManager-based scheduled scans (daily/weekly/monthly), auto-clean rules (e.g. "delete cache older than 7 days"), notification summaries after background cleanup, battery-aware scheduling, configurable thresholds per category, opt-in auto-delete for junk with user-defined confidence levels
- **P2: Storage Health Dashboard** — At-a-glance storage overview on app open (total/used/free as animated ring chart), category breakdown with proportional bars (images, videos, audio, documents, APKs, other), trend over time (storage usage history graph), largest growth category highlighting, "space saved" lifetime counter, quick-action cards ("You have 12 duplicates — clean now")
- **P3: Favorites, Starred & Protected Files** — Pin/star important files to prevent accidental deletion, protected-file list persisted across scans (excluded from junk/duplicate/large suggestions), quick-access "Favorites" view or section in Browse, visual badge on protected items in all list views, import/export favorites list
- **P4: Recent Files & Activity Log** — "Recents" view showing last-opened/modified/created files, operation history log (deleted X, moved Y, compressed Z) with timestamps, filterable by operation type, undo support extended to history (re-restore from log), exportable activity report
- **P5: Batch Rename Tool** — Pattern-based renaming (prefix, suffix, sequential numbering, date insertion), preview before apply showing old → new names, regex support for advanced users, template presets (e.g. "IMG_{date}_{seq}"), undo for batch renames, conflict detection (duplicate names after rename)
- **P6: Similar & Near-Duplicate Detection** — Perceptual image hashing (pHash/dHash) to find visually similar photos beyond byte-identical duplicates, configurable similarity threshold (e.g. 90% match), side-by-side comparison UI for review, group similar images for bulk cleanup, extend to audio fingerprinting (Chromaprint) as a stretch goal
- **P7: App Cache & Data Manager** — Per-app cache size breakdown (read from `/data/data/` or `UsageStatsManager`), one-tap cache clear for individual apps, "top cache offenders" ranking, total app cache vs. file cache distinction, safe-to-clear recommendations, integration with system storage settings intent as fallback
- **P8: File Preview & Quick Look** — In-app image viewer with pinch-to-zoom, basic text/code file viewer with syntax highlighting, audio waveform preview with playback, video thumbnail filmstrip, PDF first-page render, document metadata inspector (EXIF for images, ID3 for audio, dimensions for video), share-from-preview action
- **P9: Cloud Awareness & Backup Integration** — Detect cloud-synced folders (Google Drive, Dropbox, OneDrive local mirrors), flag locally-cached cloud files that can be safely evicted, optional backup-before-delete to a user-chosen cloud provider via SAF/DocumentProvider, display sync status badges on cloud-backed files
- **P10: Advanced Search & Smart Filters** — Full-text filename search across all storage (already partial — extend with saved searches), filter combinations (size range + date range + category + extension), natural language hints ("files larger than 100MB modified this month"), search result pinning, search history with suggestions

#### Research & Exploration

- **P11: Machine-Learning Cleanup Suggestions** — Research on-device ML models (TFLite) for categorizing "importance" of files based on access frequency, age, and type; explore screenshot text extraction (OCR) to identify redundant screenshots; investigate clustering algorithms for photo organization; assess feasibility of a lightweight recommendation engine ("You haven't opened these 47 files in 6 months")
- **P12: Scoped Storage Deep Compliance** — Research Android 11+ scoped storage edge cases and MediaStore-first patterns, investigate `MANAGE_EXTERNAL_STORAGE` Play Store policy changes and timeline for migration, prototype MediaStore-based scanning as a parallel path, document which features break under scoped-only access and mitigation strategies

#### Quality-of-Life Improvements

- **P13: Onboarding & First-Run Experience** — Guided walkthrough on first launch (permission rationale screens, feature highlights), interactive tutorial for arborescence view (gesture hints overlay), progressive disclosure of advanced features (context menu, batch operations), "What's new" dialog on version upgrades, skip option for power users
- **P14: Customization & User Preferences** — User-configurable thresholds (large file size cutoff, junk age cutoff, download age), theme selection beyond system default (always light, always dark, AMOLED black), sort order preferences per tab (persisted), default view mode preference, configurable scan exclusion paths, notification preferences for background operations

#### Development Infrastructure

- **P15: Testing Framework & CI Hardening** — Unit tests for core algorithms (`DuplicateFinder`, `JunkFinder`, `FileScanner`, `ScanCache`), instrumentation tests for critical user flows (scan → select → delete → undo), snapshot/screenshot tests for visual regression on key screens, CI pipeline expansion (lint checks, test execution, coverage reporting), mock filesystem for deterministic test scenarios
- **P16: Analytics & Crash Reporting Foundation** — Integrate Firebase Crashlytics (or open-source alternative like Sentry) for production crash visibility, anonymous usage analytics for feature prioritization (which tabs are used most, average scan size, most-used file operations), performance traces for scan duration benchmarking, opt-in telemetry with clear user consent dialog
- **P17: Modularization & Build Performance** — Evaluate splitting into Gradle modules (`:core`, `:scan`, `:ui:browse`, `:ui:tree`, `:ui:common`) for build parallelism, extract `DuplicateFinder`/`JunkFinder`/`FileScanner` into a `:scan` module, move data models to a `:data` module, assess Kotlin version upgrade path (1.9.0 → 2.0+) and K2 compiler benefits, evaluate Jetpack Compose migration roadmap for new screens

#### Documentation & Knowledge

- **P18: Architecture Decision Records** — Document key decisions (why Canvas-based tree instead of RecyclerView, why MD5 for duplicates instead of SHA-256, why JSON scan cache instead of Room DB), record trade-off rationale for future contributors, maintain a living ADR file updated with each major change
- **P19: User-Facing Help & Support** — In-app FAQ or help section (what counts as "junk"? how are duplicates detected? is deletion permanent?), contextual tooltips on first use of each feature, link to issue tracker or feedback form, storage permission explanation page for users who deny and want to understand why

- **Key files**: Cross-cutting — all source files, `build.gradle`, `AndroidManifest.xml`, `res/` resources, CI workflows, potential new modules

---

## Design-Aesthetic-Audit Sections

Per the general aesthetic audit execution order (§XII of the design-aesthetic-audit skill):

### 1. §DS1–§DS2 — Aesthetic Style Classification
- §DS1: Classify the app's visual language (Material/Elevation, Minimal, etc.)
- §DS2: Style coherence assessment — are all components using the same visual language?

### 2. §DP0/§DP1 — Character Extraction & Dimensions
- §DP0: Read what the design already communicates (spatial character, color character, motion character)
- §DP1: Assess six personality dimensions (temperature, weight, speed, complexity, formality, age)

### 3. §DP2 — Character Brief
- Produce a 1-page character brief defining the design's personality target

### 4. §DBI1 + §DBI3 — Brand Identity: Archetype + Genericness
- §DBI1: Identify the brand archetype
- §DBI3: Run the 12-signal genericness audit (default palette, default radius, default shadows, etc.)

### 5. §DC1–§DC5 — Color Science Deep Dive
- §DC1: Perceptual color architecture (OKLCH analysis, temperature coherence)
- §DC2: Palette architecture audit (semantic role inventory for every color)
- §DC3: Dark mode color craft (surface lightness steps, chromatic darks)
- §DC4: Color narrative (does the palette tell a story across the user journey?)
- §DC5: Gradient & color transition quality

### 6. §DT1–§DT4 — Typography as Visual Expression
- §DT1: Type scale architecture (modular scale analysis)
- §DT2: Typographic voice (what the typeface communicates)
- §DT3: Type craft details (tracking, leading, optical size, tabular nums)
- §DT4: Type hierarchy effectiveness (scanning test)

### 7. §DCO1–§DCO6 — Component Design Character
- §DCO1: Button system audit (hierarchy, character-specific treatment, craft checklist)
- §DCO2: Input & form system audit (border, focus, background, label, error character)
- §DCO3: Card & surface system audit (elevation, border, radius coherence)
- §DCO4: Navigation design character (active state, hover, icon treatment, density)
- §DCO5: Modal & overlay system audit (backdrop, entry animation, radius, spacing)
- §DCO6: Toast & notification design (severity system, motion, timing)

### 8. §DH1–§DH4 — Visual Hierarchy & Gestalt
- §DH1: Hierarchy audit (primary/secondary/tertiary content levels)
- §DH2: Gestalt principle assessment (proximity, similarity, continuity)
- §DH3: Contrast as composition (size, weight, color, space contrast)
- §DH4: Reading path analysis (where does the eye go?)

### 9. §DSA1–§DSA5 — Surface & Atmosphere Design
- §DSA1: Surface material vocabulary (paper, glass, metal, fabric metaphors)
- §DSA2: Light source consistency (shadow direction, elevation coherence)
- §DSA3: Atmospheric depth (layers, blur, transparency)
- §DSA4: Texture & noise (subtle patterns, grain)
- §DSA5: Focal vs ambient elements (figure-ground relationship)

### 10. §DM1–§DM5 — Motion Architecture
- §DM1: Motion vocabulary (what animation language does the app speak?)
- §DM2: Micro-interaction audit (every interactive element's response)
- §DM3: Transition choreography (screen-to-screen, state-to-state)
- §DM4: Focus ring & state transition motion
- §DM5: Motion signature (the one animation that defines the product)

### 11. §DI1–§DI4 — Iconography System
- §DI1: Icon family consistency (same set, same weight, same grid)
- §DI2: Optical alignment (icons visually centered, not mathematically centered)
- §DI3: Icon expressiveness (do icons carry character or are they generic?)
- §DI4: Custom icon direction (where custom icons would add value)

### 12. §DST1–§DST4 — State Design System
- §DST1: Empty state design (character-appropriate, actionable, illustrative)
- §DST2: Loading state design (skeleton vs spinner, geometry match, palette match)
- §DST3: Error state design (severity calibration, character consistency, path forward)
- §DST4: Success state design (intensity scale: micro/task/milestone/major)

### 13. §DCVW1–§DCVW3 — Copy × Visual Alignment
- §DCVW1: Voice-character alignment (formality, length, personality, user address, transparency)
- §DCVW2: Microcopy system audit (button labels, nav labels, empty state copy, error messages, placeholders)
- §DCVW3: Voice-visual coherence assessment (does visual character match copy voice?)

### 14. §DIL1–§DIL3 — Illustration & Graphic Language
- §DIL1: Current illustration audit (inventory of all illustrations/graphics, source identification)
- §DIL2: Illustration character specification (customization level direction)
- §DIL3: Spot graphic & abstract shape system

### 15. §DTA1–§DTA2 — Design Token Architecture
- §DTA1: Token layer architecture (primitive → semantic → component layers assessment)
- §DTA2: Character-carrying token gaps (which character-critical values are still hardcoded?)

### 16. §DRC1–§DRC3 — Responsive Design Character
- §DRC1: Breakpoint character audit — adapted for Android screen sizes (phone small/normal/large, tablet)
- §DRC2: Mobile character intensification (touch feedback, gestures, bottom sheets)
- §DRC3: Adaptive character specification (elements that change across screen sizes)

### 17. §DDT1–§DDT2 — Design Trend Calibration
- §DDT1: Current trend alignment (Material 3, current Android design trends)
- §DDT2: Trend strategy (which trends to adopt, which to resist, which to lead)

### 18. §DP3 — Character Deepening
- Apply 7 deepening techniques to concentrate the design character

### 19. §DBI2 — Design Signature Specification
- Define the distinctive visual element that makes this app recognizable

### 20. §DCP1–§DCP3 — Competitive Visual Positioning
- §DCP1: Competitive landscape mapping (Files by Google, Solid Explorer, MiXplorer, etc.)
- §DCP2: Differentiation opportunity identification
- §DCP3: Positioning strategy recommendation

### 21. §DDV1–§DDV3 — Data Visualization Character (if applicable)
- §DDV1: Chart color system (arborescence view uses custom drawing)
- §DDV2: Chart typography alignment
- §DDV3: Chart style × product character

---

## Execution Strategy

Each section will follow this workflow:
1. **Audit** — Launch Explore agents to examine relevant code areas and identify issues per the section checklist
2. **Implement** — Fix identified issues with targeted edits
3. **Verify** — Build and test where possible (`./gradlew assembleDebug`)
4. **Commit & Push** — One commit per section with descriptive message

**Phase ordering** (correctness before polish):
1. Core Correctness & Safety: A, B, C
2. Performance & Code Quality: D, I
3. Visual Design: E + Design-Aesthetic sections
4. User Experience: F, G, H
5. Data & Domain: J, K
6. Polish & Standardization: L
7. Operations & Future-Proofing: M, N, O
8. Research, Development & Improvement: P

---

## Verification

After all sections are complete:
- Run `./gradlew assembleDebug` to verify build succeeds
- Review git log to confirm all section commits are present
- Cross-reference the §VII Summary Dashboard format from app-audit-SKILL to produce a final status report
