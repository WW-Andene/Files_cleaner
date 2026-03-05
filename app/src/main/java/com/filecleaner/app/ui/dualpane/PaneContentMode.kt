package com.filecleaner.app.ui.dualpane

import com.filecleaner.app.R

/**
 * Defines the content display mode for a dual-pane panel.
 * Each mode maps to a main app section that can be shown in either pane.
 */
enum class PaneContentMode(val labelRes: Int, val iconRes: Int) {
    /** Default file browser — navigates directories. */
    FILE_BROWSER(R.string.dual_pane_tab_browse, R.drawable.ic_nav_browse),

    /** Show duplicate files from the scan. */
    DUPLICATES(R.string.dual_pane_tab_duplicates, R.drawable.ic_nav_duplicates),

    /** Show large files from the scan. */
    LARGE_FILES(R.string.dual_pane_tab_large, R.drawable.ic_nav_large),

    /** Show junk files from the scan. */
    JUNK(R.string.dual_pane_tab_junk, R.drawable.ic_nav_junk),

    /** Show optimize/manager view. */
    MANAGER(R.string.dual_pane_tab_manager, R.drawable.ic_raccoon_logo);

    /** Whether this mode shows a directory path bar and supports file browsing. */
    val isFileBrowser: Boolean get() = this == FILE_BROWSER

    /** Whether this mode shows scanned file results (not directory navigation). */
    val isScanResultMode: Boolean get() = this in setOf(DUPLICATES, LARGE_FILES, JUNK)

    /** Whether this mode is the manager view. */
    val isManager: Boolean get() = this == MANAGER
}
