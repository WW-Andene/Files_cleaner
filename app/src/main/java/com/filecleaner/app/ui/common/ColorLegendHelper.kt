package com.filecleaner.app.ui.common

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.filecleaner.app.R
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.ui.adapters.FileItemUtils

/**
 * Builds and populates the horizontally-scrolling color legend strip that
 * appears below the header card on the Junk, Large, and Duplicates tabs.
 */
object ColorLegendHelper {

    /** A single entry in the legend: a colored dot + a text label. */
    data class LegendEntry(@ColorInt val color: Int, val label: String)

    // ── Pre-built legend sets ────────────────────────────────────────────

    /** Legend entries for the Junk tab (by junk sub-category). */
    fun junkLegend(ctx: Context): List<LegendEntry> =
        FileItemUtils.JunkType.entries.map { jt ->
            LegendEntry(
                color = ContextCompat.getColor(ctx, jt.colorRes),
                label = ctx.getString(jt.labelRes)
            )
        }

    /** Legend entries for the Large tab (by size severity). */
    fun sizeLegend(ctx: Context): List<LegendEntry> =
        FileItemUtils.SizeSeverity.entries.map { ss ->
            LegendEntry(
                color = ContextCompat.getColor(ctx, ss.colorRes),
                label = ctx.getString(ss.labelRes)
            )
        }

    /** Legend entries for the Large tab (by file category). */
    fun categoryLegend(ctx: Context): List<LegendEntry> =
        FileCategory.entries.map { cat ->
            LegendEntry(
                color = FileItemUtils.categoryColor(ctx, cat),
                label = ctx.getString(cat.displayNameRes)
            )
        }

    /** Legend entries for the Duplicates tab (alternating group colors). */
    fun duplicateGroupLegend(ctx: Context, groupCount: Int): List<LegendEntry> {
        val colorRes = listOf(
            R.color.dupGroup0, R.color.dupGroup1, R.color.dupGroup2,
            R.color.dupGroup3, R.color.dupGroup4, R.color.dupGroup5
        )
        val count = groupCount.coerceAtMost(colorRes.size)
        return (0 until count).map { i ->
            LegendEntry(
                color = ContextCompat.getColor(ctx, colorRes[i % colorRes.size]),
                label = ctx.getString(R.string.legend_group_n, i + 1)
            )
        }
    }

    // ── View building ────────────────────────────────────────────────────

    /**
     * Populates the legend [HorizontalScrollView] identified by its inner
     * [LinearLayout] container.  Pass an empty list to hide the legend.
     *
     * @param scrollView  The HorizontalScrollView (view_color_legend root).
     * @param entries     Legend entries to display.
     * @param title       Optional header text (e.g. "Color by file size").
     */
    fun populate(
        scrollView: HorizontalScrollView,
        entries: List<LegendEntry>,
        @StringRes title: Int? = null
    ) {
        val container = scrollView.findViewById<LinearLayout>(R.id.legend_container) ?: return
        container.removeAllViews()

        if (entries.isEmpty()) {
            scrollView.visibility = View.GONE
            return
        }

        val ctx = scrollView.context
        val res = ctx.resources
        val density = res.displayMetrics.density

        // Cached dimension lookups
        val textLabelPx = res.getDimension(R.dimen.text_label)
        val padStartPx = res.getDimensionPixelSize(R.dimen.spacing_xs)
        val padEndPx = res.getDimensionPixelSize(R.dimen.spacing_sm)

        // Optional title label
        if (title != null) {
            val titleView = TextView(ctx).apply {
                text = ctx.getString(title)
                setTextColor(ContextCompat.getColor(ctx, R.color.legendTextColor))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textLabelPx)
                setPadding(padStartPx, 0, padEndPx, 0)
            }
            container.addView(titleView)
        }

        // Legend entries
        for (entry in entries) {
            val chip = buildChip(ctx, entry, density)
            container.addView(chip)
        }

        scrollView.visibility = View.VISIBLE
    }

    private fun buildChip(ctx: Context, entry: LegendEntry, density: Float): LinearLayout {
        val res = ctx.resources

        // Cached dimension lookups
        val chipPadPx = res.getDimensionPixelSize(R.dimen.spacing_chip)
        val spacingXsPx = res.getDimensionPixelSize(R.dimen.spacing_xs)
        val cornerRadiusPx = res.getDimension(R.dimen.radius_md)
        val textLabelPx = res.getDimension(R.dimen.text_label)

        val vPad = (3 * density).toInt()  // no 3dp token
        val dotSize = (10 * density).toInt()  // no 10dp token

        val chipLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(chipPadPx, vPad, chipPadPx, vPad)

            val bg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.legendChipBg))
                cornerRadius = cornerRadiusPx
            }
            background = bg

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = spacingXsPx
            }
            layoutParams = lp
        }

        // Colored dot
        val dot = View(ctx).apply {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(entry.color)
            }
            background = dotBg
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                marginEnd = spacingXsPx
            }
        }
        chipLayout.addView(dot)

        // Label
        val label = TextView(ctx).apply {
            text = entry.label
            setTextColor(ContextCompat.getColor(ctx, R.color.legendTextColor))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textLabelPx)
            maxLines = 1
        }
        chipLayout.addView(label)

        return chipLayout
    }
}
