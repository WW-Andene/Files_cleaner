# Raccoon File Manager — §XI Coherence Audit

**Date**: 2026-03-05
**Scope**: Full codebase coherence audit per §XI — comprehension, fracture analysis, and consolidated findings
**Status**: Read-only audit — no code changes

---

## §XI.0 — DEEP COMPREHENSION PHASE

### XI.0.1 — App Comprehension Record

```yaml
APP COMPREHENSION RECORD:

  Core Purpose:
    It helps Android users reclaim storage space and organize files by surfacing
    duplicates, junk, and large files through a scan-then-review workflow, with
    power-user tools (dual pane, tree view, cloud browser, antivirus) layered on top.

  User Mental Model:
    A personal storage assistant — "Ricky scans, shows me what's wasting space,
    I review and delete." The raccoon mascot frames it as a helper rummaging
    through files on the user's behalf.

  Core Loop:
    Scan → Review category (duplicates/junk/large) → Select items → Delete →
    Undo if mistake → See freed space.

  Emotional Contract:
    Safe and in control. The user never worries about losing important files
    because every destructive action has confirmation + undo. Ricky's personality
    makes a potentially stressful task (deleting files) feel friendly and low-stakes.

  Design Personality:
    Playful but competent — like a helpful friend who's good with computers.
    First-person raccoon voice ("Ricky is rummaging..."), emoji-rich, celebratory
    on success, reassuring on error. Never robotic, never condescending.

  Best-in-App Standard:
    The Raccoon Manager hub and the BaseFileListFragment screens (Duplicates,
    Large, Junk) are the most polished — consistent patterns, proper state
    handling, undo support, color-coded legends, and strong accessibility.
    The Cloud Browser and Dual Pane feel like bolted-on power-user features
    from a different era.

  Growth Archaeology:
    - Core built first: RaccoonManager → Scan → Duplicates/Large/Junk (shared
      BaseFileListFragment template, clean patterns, consistent naming)
    - Browse added next: More complex, own adapter (BrowseAdapter), own selection
      bar pattern, doesn't extend BaseFileListFragment
    - Dashboard/Analysis added as visualization layer: Two overlapping screens
      that serve similar purposes — likely one was built, then the other was
      requested, and neither was merged
    - Arborescence/TreeView: Unique custom view, sophisticated, feels like a
      side project integrated into the app
    - DualPane: Power-user feature, different selection pattern, no
      OnBackPressedCallback, different action bar styling — added later with
      less attention to consistency
    - Cloud Browser: Entirely different domain (network I/O), own adapter,
      own patterns — feels most foreign to the app's identity
    - Antivirus: Latest addition — foreground service, 5-phase scan, unique
      UI patterns, different threat model. Most "enterprise" feeling feature
      in a "friendly raccoon" app
    - Optimize: Late-stage feature combining scan data with suggestions,
      uses FrameLayout (only fragment to do so) for true floating bar
```

### XI.0.2 — Coherence Fracture Analysis

```yaml
LOGIC FRACTURES — the app's internal logic contradicts itself

  L-1:
    Where:     BrowseFragment delete confirmation vs BaseFileListFragment delete confirmation
    History:   BrowseFragment was built independently from BaseFileListFragment
    Impact:    BrowseFragment uses hardcoded "8" for undo seconds in confirm_delete_message,
               while BaseFileListFragment reads UserPreferences.undoTimeoutMs dynamically.
               If user changes undo timeout in Settings, Browse ignores it in the dialog text.
    Example:   User sets undo to 20s in Settings. Browse dialog still says "8 seconds."

  L-2:
    Where:     "Delete" buttons/labels vs actual move-to-trash implementation
    History:   Strings say "Delete" but code moves to .trash with undo
    Impact:    Snackbar says "Deleted 3 files" but files are recoverable in trash.
               Misleading past tense for a reversible action.
    Example:   undo_deleted_single: "Deleted %s. Undo?" — contradicts itself.

  L-3:
    Where:     "Clean" label (Junk) vs confirm_delete_detail dialog
    History:   "Clean" was chosen as friendlier UX term for junk removal
    Impact:    Button says "Clean 5 files" but dialog says "5 files will be moved to trash."
               "Clean" implies permanent removal; "moved to trash" contradicts that.
    Example:   JunkFragment shows "Clean" button, confirmation mentions "trash."

  L-4:
    Where:     Antivirus "Delete" vs file manager "Delete"
    History:   Antivirus was built as separate feature with own deletion logic
    Impact:    In file manager, "Delete" = move to trash + undo. In Antivirus,
               "Delete" = permanent file.delete() with no undo. Same word, different
               behavior. Antivirus correctly says "Permanently delete" in its dialog,
               but the inconsistency across the app is a trust hazard.
    Example:   User learns "Delete" is safe (undoable) in Browse, then loses a file
               permanently through Antivirus delete.

  L-5:
    Where:     Dashboard vs Analysis — two screens for one purpose
    History:   Analysis was likely built first (simpler), Dashboard added later (richer)
    Impact:    Both show storage overview, category breakdown, quick actions. Two entry
               points to overlapping information with different layouts. Neither deprecated.
    Example:   "Analysis" card on Manager → AnalysisFragment. But StorageDashboardFragment
               exists too. User doesn't know which is canonical.

FLOW FRACTURES — the user's journey hits seams

  F-1:
    Where:     DualPaneFragment and CloudBrowserFragment — no OnBackPressedCallback
    History:   Built after Browse/BaseFileList pattern was established, didn't copy it
    Impact:    In Browse/Duplicates/Large/Junk, pressing back in selection mode
               deselects all (graceful). In DualPane and CloudBrowser, back navigates
               away entirely, losing selection state.
    Example:   User selects 10 files in DualPane, presses back → gone.

  F-2:
    Where:     CloudBrowserFragment back + selection
    History:   Has custom back logic (navigate up in directory) but no selection callback
    Impact:    If user is in a subdirectory with files selected and presses back,
               it navigates up (losing selection) instead of clearing selection first.

  F-3:
    Where:     CloudBrowser disconnect — no confirmation dialog
    History:   Disconnect was treated as non-destructive (unlike Remove Connection)
    Impact:    "Remove Connection" has confirmation. "Disconnect" does not. Inconsistent
               with the app's pattern of confirming actions that change state.

  F-4:
    Where:     Antivirus — completely different interaction paradigm
    History:   Added as distinct feature, not a file management tool
    Impact:    No item-level selection, no undo, no familiar patterns. The "Fix All"
               button is the only batch action. Individual threat actions via detail
               dialog, not inline selection. Feels like a different app.

  F-5:
    Where:     Quick Clean (Manager) vs Junk tab
    History:   Quick Clean was added as a convenience shortcut
    Impact:    Quick Clean deletes ALL junk with one confirmation. Junk tab lets
               user review and selectively delete. Two paths to same action with
               vastly different user control. No warning that Quick Clean is non-selective.

DESIGN FRACTURES — different visual eras coexist

  D-1:
    Era A:     Browse selection bar — MaterialCardView, elevation_floating (8dp),
               horizontal margins, rounded corners, rich controls
    Era B:     DualPane selection bar — plain LinearLayout, elevation_nav (8dp),
               NO margins, no card, no rounded corners
    Boundary:  User navigates from Browse (polished bar) to DualPane (flat bar)

  D-2:
    Era A:     Optimize empty state — Body style, textSecondary, uniform padding
    Era B:     All other empty states — Title style, textPrimary, asymmetric padding
    Boundary:  Same raccoon logo, same pattern, different visual weight

  D-3:
    Era A:     BaseFileListFragment delete — Widget.FileCleaner.Button.Destructive
    Era B:     Browse delete — Widget.FileCleaner.Button.Text + manual red color
    Era C:     DualPane delete — Widget.FileCleaner.Button.Outlined + error colors
    Era D:     Antivirus Fix All — Widget.FileCleaner.Button.Outlined + error colors
    Boundary:  Same destructive action, four different button styles

  D-4:
    Era A:     Bottom action bar: Browse elevation_floating (8dp)
    Era B:     Optimize elevation_raised (4dp), CloudBrowser elevation_raised (4dp)
    Boundary:  Floating bars at different heights; inconsistent shadow hierarchy

  D-5:
    Era A:     Arborescence empty — Body + textSecondary (same as Optimize)
    Era B:     Browse/List/DualPane empty — Title + textPrimary
    Boundary:  Two typographic treatments for same empty-state component

CONVENTION FRACTURES — same problem solved differently

  C-1:
    Pattern A: OnBackPressedCallback for selection exit
               Used in: BrowseFragment, BaseFileListFragment (Duplicates, Large, Junk)
    Pattern B: No selection-aware back handling
               Used in: DualPaneFragment, CloudBrowserFragment
    Canonical: Pattern A — provides graceful UX

  C-2:
    Pattern A: Confirmation uses confirm_delete_detail (pluralized, dynamic undo timeout)
               Used in: BaseFileListFragment, DualPaneFragment, FileContextMenu
    Pattern B: Confirmation uses confirm_delete_message (simple, hardcoded "8")
               Used in: BrowseFragment
    Canonical: Pattern A — dynamic, accurate

  C-3:
    Pattern A: Selection mode via long-press → adapter.enterSelectionMode()
               Used in: BrowseFragment
    Pattern B: Selection mode via long-press → context menu callback
               Used in: BaseFileListFragment
    Pattern C: Selection mode via adapter-internal state only
               Used in: DualPaneFragment
    Pattern D: Selection mode via checkbox-only, no long-press entry
               Used in: CloudBrowserFragment
    Canonical: Pattern A or B — long-press is the Android convention

  C-4:
    Pattern A: Empty state container ID "tv_empty" (actually a LinearLayout!)
               Used in: fragment_browse.xml, fragment_list_action.xml, fragment_optimize.xml
    Pattern B: Empty state container ID "empty_state"
               Used in: fragment_cloud_browser.xml, fragment_dual_pane.xml
    Canonical: "empty_state" — descriptive and type-accurate

  C-5:
    Pattern A: RecyclerView ID "recycler_view"
               Used in: fragment_browse.xml, fragment_list_action.xml
    Pattern B: "recycler_files" — fragment_cloud_browser.xml
    Pattern C: "recycler_suggestions" — fragment_optimize.xml
    Pattern D: "recycler_left"/"recycler_right" — fragment_dual_pane.xml
    Canonical: "recycler_view" when singular; contextual suffix when multiple

MENTAL MODEL FRACTURES — the app's conceptual model is inconsistent

  M-1:
    Model A:   "File management" — Browse, DualPane, Arborescence treat files as
               things to organize, move, rename, copy
    Model B:   "Storage cleanup" — Duplicates, Large, Junk, Optimize treat files as
               things to review and delete/move
    Model C:   "Security" — Antivirus treats apps and system state as things to audit
    Example:   Three distinct mental models coexist. A user who comes for cleanup
               encounters a full file manager and a security scanner — scope creep
               that dilutes the raccoon's identity as a "tidy-up assistant."

  M-2:
    Model A:   "Scan first, then work" — Duplicates, Large, Junk, Optimize, Tree
               all require scan data (snapshot)
    Model B:   "Work live" — Browse, DualPane, Cloud work with live filesystem
    Example:   User deletes files in Browse (live), then checks Duplicates (snapshot) —
               deleted files still appear in Duplicates until next scan.

  M-3:
    Model A:   "Ricky helps you" — Manager, scan flow, cleanup screens use Ricky voice
    Model B:   "Professional tool" — DualPane, Antivirus, Cloud use neutral/technical
               language ("Phase 1/5: App Integrity", "SFTP Server", "Data Exfiltration")
    Example:   The raccoon personality fades in power-user features.
```

### XI.0.3 — Unified Vision Statement

```yaml
UNIFIED VISION — Raccoon File Manager:
  Raccoon File Manager feels like a single, friendly storage assistant that
  always knows where your space went and helps you get it back. Every screen
  speaks in Ricky's voice — playful but precise. The core loop (scan → review →
  clean) is instant and satisfying. Power-user features (dual pane, tree view,
  cloud access) feel like natural extensions of the same tool, not bolt-ons from
  a different app. Every list behaves the same way: long-press to select, back to
  deselect, delete always confirms, undo always works. The user never wonders "how
  does this screen work?" because they learned the pattern on the first screen and
  it never changes. The antivirus is Ricky standing guard — same personality, same
  visual language, same interaction patterns. From first scan to hundredth cleanup,
  it feels like one thing, built by one mind, for one purpose: making storage
  management effortless and even a little fun.
```

---

## COHERENCE AUDIT — CONSOLIDATED FINDINGS

| # | FILE A | FILE B | TYPE | INCOHERENCE | SEVERITY |
|---|--------|--------|------|-------------|----------|
| 1 | `BrowseFragment.kt:146` | `BaseFileListFragment.kt:341` | COPY vs LOGIC | Browse hardcodes undo timeout "8" in confirm dialog; BaseFileList reads `UserPreferences.undoTimeoutMs` dynamically. Dialog text diverges from actual setting. | **HIGH** |
| 2 | `strings.xml:50 (confirm_delete_message)` | `strings.xml:51-54 (confirm_delete_detail)` | COPY vs LOGIC | Two different delete confirmation string templates exist. `confirm_delete_message` (simple, hardcoded) used only by Browse; `confirm_delete_detail` (rich, dynamic) used everywhere else. | **MEDIUM** |
| 3 | `strings.xml:342 (undo_deleted_single)` | `MainViewModel.kt:379 (deleteFiles)` | COPY vs LOGIC | Snackbar says "Deleted" (past tense, permanent) but files are moved to `.trash` and recoverable. Misleading label for a reversible action. | **MEDIUM** |
| 4 | `strings.xml:40 (clean_selected)` | `strings.xml:52 (confirm_delete_detail)` | COPY vs LOGIC | Junk button says "Clean" (implies permanent) but confirmation dialog says "moved to trash" (implies temporary). Semantic contradiction within same flow. | **MEDIUM** |
| 5 | `AntivirusFragment.kt:527 (file.delete())` | `MainViewModel.kt:379 (moveToTrash)` | COPY vs LOGIC | Antivirus "Delete" = permanent `file.delete()` with no undo. File manager "Delete" = move to trash with undo. Same verb, opposite behavior. | **HIGH** |
| 6 | `AnalysisFragment` | `StorageDashboardFragment` | VISION | Two separate screens for storage overview with category breakdown. Both accessible from Manager hub. Redundant — contradicts "one thing, built by one mind." | **HIGH** |
| 7 | `BrowseFragment.kt` | `DualPaneFragment.kt` | BEHAVIOR | Browse has `OnBackPressedCallback` to exit selection on back press; DualPane has none. Same gesture, different result (deselect vs navigate away). | **HIGH** |
| 8 | `BrowseFragment.kt` | `CloudBrowserFragment.kt` | BEHAVIOR | Browse has selection-aware back; Cloud has none. User loses file selection on back press in Cloud. | **HIGH** |
| 9 | `BaseFileListFragment.kt` | `DualPaneFragment.kt` | BEHAVIOR | BaseFileList subclasses have selection-aware back; DualPane does not. Inconsistent across all file-listing screens. | **HIGH** |
| 10 | `BrowseFragment.kt:112` | `CloudBrowserFragment.kt:115` | BEHAVIOR | Four different selection-mode entry patterns: Browse (long-press → enterSelectionMode), BaseFileList (long-press → context menu), DualPane (adapter-internal), Cloud (checkbox-only, no long-press). | **HIGH** |
| 11 | `CloudBrowserFragment.kt` | `BrowseFragment.kt` | BEHAVIOR | Cloud browser completely lacks FileContextMenu. All other file-browsing fragments provide it on long-press. | **MEDIUM** |
| 12 | `CloudBrowserFragment.kt:418` | `BaseFileListFragment.kt (UndoHelper)` | BEHAVIOR | Cloud deletion has no undo mechanism. Browse, BaseFileList, and DualPane all support undo via `UndoHelper.showUndoSnackbar()`. | **MEDIUM** |
| 13 | `CloudBrowserFragment.kt:137` | `CloudBrowserFragment.kt:424` | NAVIGATION | "Remove Connection" has confirmation dialog. "Disconnect" does not. Inconsistent within the same screen for state-changing actions. | **MEDIUM** |
| 14 | `RaccoonManagerFragment.kt (Quick Clean)` | `JunkFragment (selective clean)` | BEHAVIOR | Quick Clean deletes ALL junk with one confirmation. Junk tab allows selective review. Two paths to same action with vastly different control levels, no warning. | **MEDIUM** |
| 15 | `fragment_browse.xml:298` | `fragment_dual_pane.xml:381` | VISUAL | Browse selection bar: MaterialCardView, `elevation_floating`, margins, rounded corners. DualPane: plain LinearLayout, `elevation_nav`, no margins, no card. Same function, completely different visual treatment. | **HIGH** |
| 16 | `fragment_list_action.xml:336` | `fragment_browse.xml:435` | VISUAL | BaseFileList delete: `Button.Destructive` (correct style). Browse delete: `Button.Text` with manual red color. Same delete action, different button styles. | **HIGH** |
| 17 | `fragment_list_action.xml:336` | `fragment_dual_pane.xml:478` | VISUAL | BaseFileList delete: `Button.Destructive`. DualPane delete: `Button.Outlined` with error colors. Same action, inconsistent styling. | **HIGH** |
| 18 | `fragment_list_action.xml:336` | `fragment_antivirus.xml:366` | VISUAL | BaseFileList delete: `Button.Destructive`. Antivirus Fix All: `Button.Outlined` with error colors. | **MEDIUM** |
| 19 | `fragment_browse.xml:298 (elevation_floating)` | `fragment_optimize.xml:258 (elevation_raised)` | VISUAL | Browse floating bar: 8dp elevation. Optimize floating bar: 4dp. Same floating-bar concept at different shadow heights. | **MEDIUM** |
| 20 | `fragment_optimize.xml:258` | `fragment_cloud_browser.xml:217` | VISUAL | Optimize bar: MaterialCardView, `colorPrimary` bg. Cloud bar: LinearLayout, `surfaceDim` bg. Same bottom-bar pattern, different containers and colors. | **MEDIUM** |
| 21 | `fragment_optimize.xml:227` | `fragment_browse.xml:259` | VISUAL | Optimize empty text: `Body` + `textSecondary` + `spacing_lg`. Browse/List/DualPane: `Title` + `textPrimary` + `spacing_xxl`. Different typography for same component. | **MEDIUM** |
| 22 | `fragment_arborescence.xml:193` | `fragment_browse.xml:259` | VISUAL | Arborescence empty text: `Body` + `textSecondary`. Same inconsistency as Optimize. | **MEDIUM** |
| 23 | `fragment_optimize.xml:214` | `fragment_browse.xml:238` | VISUAL | Optimize empty: uniform `padding_4xl`. Others: asymmetric padding. Different spatial composition. | **LOW** |
| 24 | `fragment_cloud_browser.xml:158` | `fragment_browse.xml:238` | VISUAL | Cloud empty state container lacks `visibility="gone"`. All others start hidden. | **LOW** |
| 25 | `fragment_cloud_browser.xml (toolbar)` | `fragment_optimize.xml (toolbar)` | VISUAL | Cloud toolbar missing `android:minHeight="?attr/actionBarSize"`. Others include it. | **LOW** |
| 26 | `strings.xml:489 (nav_raccoon: "Manager")` | `strings.xml:490 (raccoon_manager_title: "Ricky Manager")` | NAMING | Bottom nav tab says "Manager", screen title says "Ricky Manager". | **LOW** |
| 27 | `strings.xml:693 (av_title: "Security Scanner")` | Package: `ui.security.AntivirusFragment` | NAMING | Nav label says "Security Scanner", class says "Antivirus", Manager hub card says "Antivirus." Three names for one feature. | **MEDIUM** |
| 28 | `fragment_browse.xml:239 (tv_empty)` | Actual type: `LinearLayout` | NAMING | ID prefix `tv_` implies TextView but element is a LinearLayout. Semantic type mismatch in 3 layouts. | **MEDIUM** |
| 29 | `fragment_browse.xml (recycler_view)` | `fragment_cloud_browser.xml (recycler_files)` | NAMING | Same concept (file list RecyclerView) with different IDs across fragments. 5 different ID patterns total. | **LOW** |
| 30 | `strings.xml:76 (n_files)` | `strings.xml:585 (n_items)` | NAMING | Two plurals for counting: "files" vs "items." Semantic distinction unclear. | **LOW** |
| 31 | `strings.xml:80 (no_files_found)` | `strings.xml:81 (nothing_found)` | NAMING | Two nearly identical strings for same state ("no scan data yet"). Redundant. | **LOW** |
| 32 | `strings.xml:303 (ctx_star: "Star")` | `strings.xml:74 (cat_favorites)` | NAMING | Action says "Star" but category says "Favorites." Two words for one concept. | **LOW** |
| 33 | `BrowseFragment.kt (search + sort + view mode)` | `CloudBrowserFragment.kt (none)` | VISION | Cloud browser lacks search, sort, view mode, filter chips — core file-browsing features Browse established. Feels like an MVP never brought up to standard. | **HIGH** |
| 34 | `AntivirusFragment.kt` | All other fragments | VISION | Entirely different interaction paradigm: no item selection, no undo, severity filter chips, foreground service polling, "Fix All" batch action. Tone shifts from playful to clinical. Feels like a different product. | **HIGH** |
| 35 | `DualPaneFragment.kt` | `BrowseFragment.kt` | VISION | DualPane lacks view mode, search, sort, filter chips, selection-aware back. Power-user feature with fewer capabilities than basic Browse. | **MEDIUM** |
| 36 | `BaseFileListFragment.kt:287` | `BrowseFragment.kt` | BEHAVIOR | BaseFileList provides haptic feedback (CONFIRM/REJECT) on scan completion. Browse, DualPane, Cloud do not. | **LOW** |
| 37 | `BrowseFragment.kt (view mode UI)` | `BaseFileListFragment.kt (view mode UI)` | BEHAVIOR | Browse uses collapsible chips panel for view/size selection. BaseFileList uses PopupMenu for style + chips for size. Two UI patterns for same feature. | **LOW** |
| 38 | `strings.xml:11 (scan_storage)` | `strings.xml:935 (optimize_analyzing)` | NAMING | "Scan" and "Analyze" used for overlapping concepts. "Scanning" is the main operation, but "analyzing" appears as both a scan phase and an independent Optimize action. | **LOW** |

---

## SEVERITY SUMMARY

| Severity | Count | Percentage |
|----------|-------|------------|
| **HIGH** | 10 | 26% |
| **MEDIUM** | 15 | 39% |
| **LOW** | 13 | 34% |
| **TOTAL** | **38** | 100% |

### Root Cause Clusters

The 38 fractures cluster around three root causes:

1. **Features built at different times without back-porting patterns** (DualPane, Cloud, Antivirus missing conventions established in Browse/BaseFileList) — accounts for findings #7–#14, #33–#35
2. **Two visual eras coexisting** (polished MaterialCardView-based bars vs plain LinearLayouts; Destructive button style vs manual red coloring) — accounts for findings #15–#25
3. **Three mental models in one app** (cleanup assistant vs file manager vs security scanner) with the raccoon personality fading in the latter two — accounts for findings #1–#6, #34

---

## PHASE 3 — CROSS-REFERENCE PASS

Cross-referencing Phase 1 (300-item design aesthetic audit) and Phase 2 (122-item expanded UI audit) to identify compound issues per §VIII Cross-Cutting Concern Map.

### Compound Issue Table

| PHASE 1 # | PHASE 2 # | COMPOUND ISSUE | WHY LINKED | PRIORITY |
|---|---|---|---|---|
| #97 (§DCO1) | #403 (§H3) — FIXED | **button_height_sm 36dp touch target** | Same root cause: `button_height_sm` dimen set to 36dp. Phase 1 flagged the token value; Phase 2 audited actual layout usage and identified 2 true violations (dialog_cloud_connect, fragment_arborescence). Now FIXED — remaining usages covered by parent touch targets or superseded by #405 fix | RESOLVED |
| #221 (§DTA2) | #302 (§E1) | **spacing_10 off-scale token** | Same root cause: `spacing_10` (10dp) exists outside the 4dp-base progression. Phase 1 flagged the token definition; Phase 2 flagged its continued presence as token debt. One fix resolves both | LOW |
| #222 (§DTA2) | #302 (§E1) | **dot_legend duplicates spacing_10** | Chain: `dot_legend` aliases `spacing_10` at 10dp — both off-scale. Phase 1 flagged the duplication; Phase 2 inherited the off-scale concern. Fixing spacing_10 should also address dot_legend | LOW |
| #232 (§DDT2) | #333 (§E3) | **MaterialComponents M2 not M3** | Same root cause: theme parent is `Theme.MaterialComponents.DayNight.NoActionBar` instead of Material3. Phase 1 flagged the trend gap; Phase 2 flagged the missing M3 color system. Migration is a single coordinated effort | LOW |
| #232 (§DDT2) | #366 (§E11) | **M2 blocks Dynamic Color** | Compound chain: M2 parent (#232) prevents `DynamicColors.applyIfAvailable()` from working (#366). Cannot fix #366 without first migrating to M3 (#232/#333). Fixing either alone is incomplete | LOW |
| #28 (§DC1) [REVIEW] | — | **Category color chroma imbalance** | No Phase 2 counterpart — purely perceptual. M3 migration would regenerate category colors from M3 tonal palette, naturally fixing the chroma spread | LOW (linked to M3 migration) |
| #41 (§DC3) [REVIEW] | — | **Container OKLCH step unevenness** | No Phase 2 counterpart. M3 migration would replace manual OKLCH surface ladder with M3-generated tonal surfaces, resolving uneven steps automatically | LOW (linked to M3 migration) |
| #291 (§DC4) [REVIEW] | — | **chip_stroke_color.xml state ordering** | No Phase 2 counterpart. Standalone Phase 1 issue — state list selector ordering bug. Not compound | LOW |
| #34/#35 (§DC2) [REVIEW] | — | **textTertiary contrast borderline** | Phase 2 §G1 confirmed accessibility compliance. However, borderline 4.2-4.6:1 ratios at Caption (10sp) represent compound risk if typography scale ever decreases or text color shifts during M3 migration | LOW |
| — | #327 (§E2) + #408 (§H3) | **No landscape layouts + rotation supported** | #327 flags missing `layout-land/` variants; #408 flags no `screenOrientation` lock. App supports rotation but has no landscape layouts — degraded UX in landscape. Fix is either add landscape layouts OR lock to portrait | LOW |
| — | #392 (§G2) + #394 (§G3) | **Accessibility navigation gap** | #392 (no `screenReaderFocusable`) and #394 (no `OnBackPressedCallback`) both relate to incomplete accessibility navigation. #394 partially addressed by #367 (predictive back enabled) | LOW |
| — | #411 (§L3) | **Off-scale spacing tokens (2)** | Same root as Phase 1 #221/#222. One fix addresses all three entries | LOW |

### New Issues (visible only when cross-referencing both phases)

| NEW | AFFECTED FILES | ISSUE | WHY NEITHER PHASE CAUGHT IT | SEVERITY |
|---|---|---|---|---|
| N-1 | `colors.xml`, `themes.xml`, `build.gradle` | **M3 migration is a compound prerequisite for 4+ issues** | M2→M3 migration is a single root cause blocking resolution of #232, #333, #366, and would also naturally resolve #28, #41, and reduce regression risk for #34/#35. The migration is a 6-issue compound root — higher leverage than any individual finding suggests | LOW (highest leverage) |
| N-2 | `AndroidManifest.xml`, multiple fragments | **Predictive back enabled but selection mode has no back handler** | With predictive back enabled (#367), the animation previews a full navigation-back instead of selection-exit in DualPane/Cloud. This gap is only visible by combining "no back handler" (Phase 1) + "predictive back enabled" (Phase 2) | LOW |
| N-3 | `fragment_dual_pane.xml`, `dimens.xml` | **dual_pane_tab_height (48dp) + button_height_sm (36dp) minHeight redundancy** | Lines 88/263 now have `layout_height=48dp` AND `minHeight=36dp` — the minHeight is vestigial (48dp > 36dp always). Not a bug but dead code in layout | LOW |

---

**Phase 3 total**: 11 compound chains (1 resolved), 3 new findings.
