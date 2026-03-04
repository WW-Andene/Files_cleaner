package com.filecleaner.app.utils

import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.filecleaner.app.R
import com.google.android.material.snackbar.Snackbar

/**
 * §G1: Ensures the snackbar view has an accessibility live region so
 * TalkBack announces the message when it appears.  Material Snackbar
 * already sets this in most cases, but this guarantees it for custom-styled
 * snackbars and also fires an explicit accessibility event for assertive
 * error announcements.
 */
private fun Snackbar.ensureAccessible(assertive: Boolean = false): Snackbar = apply {
    addCallback(object : Snackbar.Callback() {
        override fun onShown(sb: Snackbar?) {
            sb?.view?.apply {
                accessibilityLiveRegion = if (assertive)
                    View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                else
                    View.ACCESSIBILITY_LIVE_REGION_POLITE
                // Force TalkBack to announce the snackbar content
                sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            }
        }
    })
}

fun Snackbar.styleAsError(): Snackbar = apply {
    setBackgroundTint(ContextCompat.getColor(context, R.color.colorError))
    setTextColor(ContextCompat.getColor(context, R.color.textOnPrimary))
    // §G1: Error snackbars use assertive live region for immediate announcement
    ensureAccessible(assertive = true)
}

fun Snackbar.styleAsSuccess(): Snackbar = apply {
    setBackgroundTint(ContextCompat.getColor(context, R.color.colorSuccess))
    setTextColor(ContextCompat.getColor(context, R.color.textOnPrimary))
    ensureAccessible()
}

fun Snackbar.styleAsWarning(): Snackbar = apply {
    setBackgroundTint(ContextCompat.getColor(context, R.color.colorWarning))
    setTextColor(ContextCompat.getColor(context, R.color.textOnPrimary))
    ensureAccessible()
}
