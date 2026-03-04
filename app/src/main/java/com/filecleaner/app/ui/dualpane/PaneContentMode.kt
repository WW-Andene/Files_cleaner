package com.filecleaner.app.ui.dualpane

import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory

/**
 * Defines the content display mode for a dual-pane panel.
 * Each mode determines what files are shown and how navigation works.
 */
enum class PaneContentMode(val labelRes: Int, val category: FileCategory? = null) {
    /** Default file browser — navigates directories. */
    FILE_BROWSER(R.string.dual_pane_mode_browser),

    /** Show only images from the scanned file list. */
    IMAGES(R.string.dual_pane_mode_images, FileCategory.IMAGE),

    /** Show only videos from the scanned file list. */
    VIDEOS(R.string.dual_pane_mode_videos, FileCategory.VIDEO),

    /** Show only audio files from the scanned file list. */
    AUDIO(R.string.dual_pane_mode_audio, FileCategory.AUDIO),

    /** Show only document files from the scanned file list. */
    DOCUMENTS(R.string.dual_pane_mode_documents, FileCategory.DOCUMENT),

    /** Show only APK files from the scanned file list. */
    APKS(R.string.dual_pane_mode_apks, FileCategory.APK),

    /** Show only archive files from the scanned file list. */
    ARCHIVES(R.string.dual_pane_mode_archives, FileCategory.ARCHIVE),

    /** Directory tree (arborescence) view. */
    TREE_VIEW(R.string.dual_pane_mode_tree);

    /** Whether this mode shows a directory path bar and supports file browsing. */
    val isFileBrowser: Boolean get() = this == FILE_BROWSER

    /** Whether this mode shows files by category (no directory navigation). */
    val isCategoryMode: Boolean get() = category != null

    /** Whether this mode shows the tree view. */
    val isTreeView: Boolean get() = this == TREE_VIEW
}
