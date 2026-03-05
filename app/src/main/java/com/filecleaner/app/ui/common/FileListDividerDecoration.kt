package com.filecleaner.app.ui.common

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.filecleaner.app.R
import com.google.android.material.divider.MaterialDividerItemDecoration

/**
 * Subtle Material divider for file list RecyclerViews.
 * Uses borderSubtle color with horizontal insets for a clean inset divider look.
 * Supports an optional skip predicate to suppress dividers at specific positions
 * (e.g., around section headers in BrowseAdapter).
 *
 * §E6: Resolves divider color from the Material theme attribute
 * `colorOutlineVariant` first, falling back to the app's borderSubtle color.
 */
class FileListDividerDecoration(
    context: Context,
    private val shouldSkip: ((position: Int) -> Boolean)? = null
) : MaterialDividerItemDecoration(context, VERTICAL) {

    init {
        // §E6: Prefer Material theme attribute for consistent theming across light/dark
        dividerColor = resolveThemeDividerColor(context)
        dividerThickness = context.resources.getDimensionPixelSize(R.dimen.stroke_default)
        dividerInsetStart = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        dividerInsetEnd = context.resources.getDimensionPixelSize(R.dimen.spacing_lg)
        isLastItemDecorated = false
    }

    private fun resolveThemeDividerColor(context: Context): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(
                com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(context, R.color.borderSubtle)
        }
    }

    override fun shouldDrawDivider(position: Int, adapter: RecyclerView.Adapter<*>?): Boolean {
        if (shouldSkip?.invoke(position) == true) return false
        val nextPos = position + 1
        if (adapter != null && nextPos < adapter.itemCount && shouldSkip?.invoke(nextPos) == true) {
            return false
        }
        return true
    }
}
