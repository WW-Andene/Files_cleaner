package com.filecleaner.app.ui.onboarding

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.filecleaner.app.R
import com.filecleaner.app.data.UserPreferences

/**
 * 3-step onboarding dialog shown on first launch (P13).
 */
object OnboardingDialog {

    private data class Step(val title: String, val body: String)

    fun showIfNeeded(context: Context) {
        if (UserPreferences.hasSeenOnboarding) return
        show(context, step = 0)
    }

    private fun show(context: Context, step: Int) {
        val steps = listOf(
            Step(
                context.getString(R.string.onboarding_title_1),
                context.getString(R.string.onboarding_body_1)
            ),
            Step(
                context.getString(R.string.onboarding_title_2),
                context.getString(R.string.onboarding_body_2)
            ),
            Step(
                context.getString(R.string.onboarding_title_3),
                context.getString(R.string.onboarding_body_3)
            )
        )

        val current = steps[step]
        val isLast = step == steps.lastIndex

        val padding = context.resources.getDimensionPixelSize(R.dimen.spacing_xxl)
        val halfPadding = context.resources.getDimensionPixelSize(R.dimen.spacing_md)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, halfPadding)
        }

        val stepIndicator = TextView(context).apply {
            text = context.getString(R.string.onboarding_step, step + 1, steps.size)
            setTextAppearance(R.style.TextAppearance_FileCleaner_BodySmall)
            setTextColor(context.getColor(R.color.textTertiary))
            // §G1: Announce step changes to TalkBack
            contentDescription = context.getString(R.string.a11y_onboarding_step, step + 1, steps.size)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        container.addView(stepIndicator)

        val iconRes = when (step) {
            0 -> R.drawable.ic_raccoon_logo
            1 -> R.drawable.ic_nav_browse
            2 -> R.drawable.ic_scan
            else -> R.drawable.ic_raccoon_logo
        }
        // §G1: Descriptive contentDescription for each onboarding icon
        val iconDesc = when (step) {
            0 -> context.getString(R.string.a11y_onboarding_icon_welcome)
            1 -> context.getString(R.string.a11y_onboarding_icon_browse)
            2 -> context.getString(R.string.a11y_onboarding_icon_scan)
            else -> context.getString(R.string.a11y_onboarding_icon_welcome)
        }
        val iconView = android.widget.ImageView(context).apply {
            setImageResource(iconRes)
            contentDescription = iconDesc
            val size = context.resources.getDimensionPixelSize(R.dimen.onboarding_icon_size)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = halfPadding
            }
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        container.addView(iconView)

        val bodyView = TextView(context).apply {
            text = current.body
            setTextAppearance(R.style.TextAppearance_FileCleaner_Body)
            setPadding(0, halfPadding, 0, 0)
            setLineSpacing(4f, 1.2f)
        }
        container.addView(bodyView)

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(current.title)
            .setView(container)
            .setCancelable(false)

        if (isLast) {
            builder.setPositiveButton(context.getString(R.string.onboarding_done)) { _, _ ->
                UserPreferences.hasSeenOnboarding = true
                (context as? com.filecleaner.app.MainActivity)?.requestPermissionsAndScan()
            }
        } else {
            builder.setPositiveButton(context.getString(R.string.onboarding_next)) { _, _ ->
                show(context, step + 1)
            }
            if (step > 0) {
                builder.setNeutralButton(context.getString(R.string.onboarding_back)) { _, _ ->
                    show(context, step - 1)
                }
            }
            builder.setNegativeButton(context.getString(R.string.onboarding_skip)) { _, _ ->
                UserPreferences.hasSeenOnboarding = true
            }
        }

        builder.show()
    }
}
