package com.filecleaner.app.utils

import android.content.Context
import android.provider.Settings

/**
 * Checks whether the user has disabled or reduced animations via
 * Settings → Accessibility → Remove animations (or developer options).
 */
object MotionUtil {

    /** Returns true when animations should be skipped (scale == 0). */
    fun isReducedMotion(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale == 0f
    }
}
