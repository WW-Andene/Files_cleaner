package com.filecleaner.app.ui.adapters

/**
 * Determines how the accent stripe on each file item card is colored.
 * Set on the [FileAdapter] to match the screen context.
 */
enum class ColorMode {
    /** No accent stripe — default for Browse. */
    NONE,

    /** Duplicate group alternating colors — used on Duplicates tab. */
    DUPLICATE_GROUP,

    /** File category colors (IMAGE, VIDEO, AUDIO, etc.) — used on Large tab. */
    CATEGORY,

    /** Junk sub-category colors (cache, temp, thumbnails, etc.) — used on Junk tab. */
    JUNK_CATEGORY,

    /** Size severity colors (>500 MB red, >100 MB orange, >50 MB yellow) — combined with CATEGORY on Large tab. */
    SIZE_SEVERITY
}
