package com.filecleaner.app.ui.adapters

/**
 * 2 visual styles × 6 sizes = 12 view modes.
 *
 * - **LIST**: Standard two-line card, medium icon with padding.
 * - **GRID**: Multi-column card grid with image on top.
 */
enum class ViewMode(val spanCount: Int) {
    // List: standard card list with category icons
    LIST_XXS(1), LIST_XS(1), LIST_SM(1), LIST_MD(1), LIST_LG(1), LIST_XL(1),
    // Grid: multi-column card grid
    GRID_XXS(7), GRID_XS(6), GRID_SM(5), GRID_MD(4), GRID_LG(3), GRID_XL(2);

    enum class Style { LIST, GRID }
    enum class Size { XXS, XS, SM, MD, LG, XL }

    val style: Style get() = when {
        name.startsWith("LIST") -> Style.LIST
        name.startsWith("GRID") -> Style.GRID
        else -> Style.LIST
    }

    val size: Size get() = when {
        name.endsWith("_XXS") -> Size.XXS
        name.endsWith("_XS") -> Size.XS
        name.endsWith("_SM") -> Size.SM
        name.endsWith("_MD") -> Size.MD
        name.endsWith("_LG") -> Size.LG
        name.endsWith("_XL") -> Size.XL
        else -> Size.MD
    }

    /** True for modes that use the grid card layout (item_file_grid.xml). */
    val usesGridLayout: Boolean get() = style == Style.GRID

    /** True for modes that load rich thumbnails (real images, album art, APK icons). */
    val showsRichThumbnails: Boolean get() = style == Style.GRID

    /** Icon/container size in dp for list-based modes. */
    val iconSizeDp: Int get() = when (style) {
        Style.LIST -> when (size) {
            Size.XXS -> 24; Size.XS -> 32; Size.SM -> 36; Size.MD -> 44; Size.LG -> 52; Size.XL -> 60
        }
        else -> 0  // grid doesn't use icon sizing
    }

    companion object {
        /** All modes that use the grid card layout (for getItemViewType). */
        val GRID_LAYOUT_MODES: Set<ViewMode> = entries.filter { it.usesGridLayout }.toSet()

        /** Grid-only modes (multi-column, for layout manager). */
        val GRID_MODES: Set<ViewMode> = entries.filter { it.style == Style.GRID }.toSet()

        /** Get the mode for a given style + size combination. */
        fun of(style: Style, size: Size): ViewMode =
            entries.first { it.style == style && it.size == size }
    }
}
