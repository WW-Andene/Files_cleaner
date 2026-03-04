package com.filecleaner.app.ui.adapters

enum class ViewMode(val spanCount: Int) {
    LIST_COMPACT(1),
    LIST(1),
    LIST_WITH_THUMBNAILS(1),
    GRID_SMALL(4),
    GRID_MEDIUM(3),
    GRID_LARGE(2),
    GRID_XLARGE(1)   // Full-width card with large preview
}
