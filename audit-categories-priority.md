# Raccoon File Manager — Audit Categories by Order of Importance

Based on analysis of **app-audit-SKILL** (16 categories, A-P) and **audit-plan.md** (including 21 design-aesthetic sections), prioritized for a file manager app where incorrect operations = data loss.

---

## Categories by Order of Importance

### Tier 1 — Critical (bugs here = data loss or crashes)

| Priority | Section | Why |
|----------|---------|-----|
| **1** | **A — Domain Logic & Correctness** | File operations must be correct. Wrong size calculations, bad sorting, broken timestamps, or race conditions during scan = users lose trust or lose files. A file manager that deletes the wrong file or shows incorrect sizes is fundamentally broken. |
| **2** | **C — Security & Trust** | Path traversal in file move/delete, permission handling (`MANAGE_EXTERNAL_STORAGE`), dependency CVEs. A security flaw in a file manager is devastating — it has access to the entire filesystem. |
| **3** | **B — State Management & Data Integrity** | LiveData lifecycle bugs (the NPE already hit), stale state after config changes, scan vs UI race conditions. This tier causes the crashes users actually see. |

### Tier 2 — High (directly impacts usability)

| Priority | Section | Why |
|----------|---------|-----|
| **4** | **D — Performance & Resources** | Scanning large directories, ArborescenceView canvas drawing, RecyclerView with thousands of items, memory from bitmaps. The 50-file cap lag issue already surfaced — there's likely more. Main thread blocking during scans is a UX killer. |
| **5** | **F — UX, Information Architecture & Copy** | Navigation flow between tabs (Browse/Tree), confirmation dialogs, empty states, action feedback. This is what makes or breaks day-to-day usage. Scan workflow friction, browse-to-action flow, and copy consistency ("file" vs "item", "scan" vs "analyze") all live here. |
| **6** | **I — Code Quality & Architecture** | Dead code, god classes (ArborescenceView is probably 1000+ lines), duplication across adapters/fragments. Directly affects your ability to keep building without introducing bugs. Every duplicated pattern is a future divergence bug. |

### Tier 3 — Medium (professional quality)

| Priority | Section | Why |
|----------|---------|-----|
| **7** | **E — Visual Design Quality** + **Design-Aesthetic sections** (DS1-DS2, DC1-DC5, DT1-DT4, DCO1-DCO6, DH1-DH4, DSA1-DSA5, DM1-DM5, DI1-DI4, DST1-DST4, etc.) | Token consistency, color/typography system, component polish, BottomSheet styling. Makes the app feel finished vs prototype. The design-aesthetic audit's 21 sections (style classification, color science, typography, motion, hierarchy, surfaces, iconography, state design, etc.) all fold into this tier. |
| **8** | **J — Data Presentation & Portability** | File size formatting consistency (KB vs MB), date formats, arborescence data accuracy, thumbnail handling. Users compare what they see in the app to what the OS shows — any discrepancy destroys trust. |
| **9** | **G — Accessibility** | Content descriptions, touch targets (48dp), TalkBack support, focus order. Legal/ethical requirement and reaches more users. Custom views like ArborescenceView need explicit accessibility roles. |
| **10** | **H — Platform Compatibility** | API level support matrix (minSdk through targetSdk), notch/inset handling, gesture navigation, edge-to-edge. Manufacturer skins and Android version differences can break file access silently. |

### Tier 4 — Polish & Future-proofing

| Priority | Section | Why |
|----------|---------|-----|
| **11** | **L — Optimization & Polish** | Token consolidation, transition coherence, skeleton screens, standardization pass after all fixes. Algorithm efficiency in scan/duplicate finding (O(n^2) opportunities), memoization gaps. Only valuable after the core issues are resolved. |
| **12** | **N — Internationalization** | Hardcoded strings extraction, RTL layout, locale-aware formatting. Needed before any translation effort. Every hardcoded `android:text="..."` and Toast message compounds the i18n migration cost. |
| **13** | **M — Deployment & Operations** | Version management (versionName/versionCode single source of truth), crash reporting, debug logging gated behind `BuildConfig.DEBUG`. Infrastructure that makes everything else measurable. |
| **14** | **O — Scenario Projection** | Scale cliffs (1000+ files, deep trees), feature addition risk map (cloud backup, favorites, search, batch operations), technical debt map. Planning, not fixing — but prevents expensive future mistakes. |

### Tier 5 — Enhancement (new features & research)

| Priority | Section | Why |
|----------|---------|-----|
| **15** | **K — Specialized Domain Depths** | Mostly N/A for a file manager (no financial, medical, gambling, real-time, or AI/LLM concerns). Quick pass to confirm nothing activates. |
| **16** | **P — R&D & Improvement** | New features: scheduled cleanup (P1), storage dashboard (P2), favorites (P3), batch rename (P5), similar image detection (P6), app cache manager (P7), file preview (P8), advanced search (P10), ML suggestions (P11), testing framework (P15), analytics (P16), modularization (P17). Only after the existing app is solid. |

---

## Summary

**Fix what crashes** (A, C, B) → **fix what lags or confuses** (D, F, I) → **make it look professional** (E, J, G, H) → **standardize and future-proof** (L, N, M, O) → **add new features** (K, P)

---

## Design-Aesthetic Sections Mapping

The 21 design-aesthetic audit sections from `design-aesthetic-audit-SKILL` integrate into the priority tiers as follows:

| Design-Aesthetic Section | Maps To | Tier |
|--------------------------|---------|------|
| DS1-DS2 (Style Classification) | E — Visual Design | 3 |
| DP0-DP2 (Character Extraction & Brief) | E — Visual Design | 3 |
| DBI1, DBI3 (Brand Identity & Genericness) | E9 — Visual Identity | 3 |
| DC1-DC5 (Color Science) | E3 — Color Craft | 3 |
| DT1-DT4 (Typography) | E4 — Typography Craft | 3 |
| DCO1-DCO6 (Component Character) | E5 — Component Quality | 3 |
| DH1-DH4 (Visual Hierarchy & Gestalt) | E2 — Spatial Composition | 3 |
| DSA1-DSA5 (Surface & Atmosphere) | E — Visual Design | 3 |
| DM1-DM5 (Motion Architecture) | E6 — Interaction Design | 3 |
| DI1-DI4 (Iconography) | E5 — Component Quality | 3 |
| DST1-DST4 (State Design System) | F5 — Micro-Interactions | 2 |
| DCVW1-DCVW3 (Copy x Visual Alignment) | F4 — Copy Quality | 2 |
| DIL1-DIL3 (Illustration & Graphics) | E — Visual Design | 3 |
| DTA1-DTA2 (Design Token Architecture) | L3 — Design Standardization | 4 |
| DRC1-DRC3 (Responsive Character) | H3 — Mobile & Touch | 3 |
| DDT1-DDT2 (Trend Calibration) | E7 — Visual Professionalism | 3 |
| DP3 (Character Deepening) | E — Visual Design | 3 |
| DBI2 (Design Signature) | E9 — Visual Identity | 3 |
| DCP1-DCP3 (Competitive Positioning) | E9 — Visual Identity | 3 |
| DDV1-DDV3 (Data Viz Character) | J2 — Data Visualization | 3 |
