package com.filecleaner.app.ui.onboarding

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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

        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding / 2)
        }

        val stepIndicator = TextView(context).apply {
            text = context.getString(R.string.onboarding_step, step + 1, steps.size)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_body_small))
            setTextColor(context.getColor(R.color.textTertiary))
        }
        container.addView(stepIndicator)

        val bodyView = TextView(context).apply {
            text = current.body
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_body))
            setPadding(0, padding / 2, 0, 0)
            setLineSpacing(4f, 1.2f)
        }
        container.addView(bodyView)

        val builder = AlertDialog.Builder(context)
            .setTitle(current.title)
            .setView(container)
            .setCancelable(false)

        if (isLast) {
            builder.setPositiveButton(context.getString(R.string.onboarding_done)) { _, _ ->
                UserPreferences.hasSeenOnboarding = true
            }
        } else {
            builder.setPositiveButton(context.getString(R.string.onboarding_next)) { _, _ ->
                show(context, step + 1)
            }
            builder.setNegativeButton(context.getString(R.string.onboarding_skip)) { _, _ ->
                UserPreferences.hasSeenOnboarding = true
            }
        }

        builder.show()
    }
}
