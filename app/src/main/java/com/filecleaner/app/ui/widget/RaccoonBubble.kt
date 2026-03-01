package com.filecleaner.app.ui.widget

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import com.filecleaner.app.R
import com.filecleaner.app.utils.MotionUtil

/**
 * Attaches drag-to-move and edge-snap behavior to an existing ImageView.
 * Not a custom View — just a touch listener setup.
 */
object RaccoonBubble {

    private const val DRAG_THRESHOLD_DP = 12f
    private const val PULSE_DELAY_MS = 15000L
    private const val EDGE_MARGIN_DP = 16f

    private var pulseAnimatorX: ObjectAnimator? = null
    private var pulseAnimatorY: ObjectAnimator? = null

    private val TAG_ATTACHED = R.id.raccoon_bubble_attached

    @SuppressLint("ClickableViewAccessibility")
    fun attach(bubble: View, onClick: () -> Unit) {
        // Guard against duplicate attach — prevents stacking listeners and pulse animators
        if (bubble.getTag(TAG_ATTACHED) == true) return
        bubble.setTag(TAG_ATTACHED, true)
        var downX = 0f
        var downY = 0f
        var startTransX = 0f
        var startTransY = 0f
        var moved = false
        val moveThreshold = DRAG_THRESHOLD_DP

        bubble.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startTransX = v.translationX
                    startTransY = v.translationY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy > moveThreshold * moveThreshold) moved = true
                    v.translationX = startTransX + dx
                    v.translationY = startTransY + dy
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        v.performClick()
                        onClick()
                    } else {
                        snapToEdge(v)
                    }
                    true
                }
                else -> false
            }
        }

        // Cancel any previous pulse before starting a new one
        cancelPulse()

        // Subtle pulse animation every 15 seconds (skip if reduced motion)
        if (!MotionUtil.isReducedMotion(bubble.context)) {
            startPulse(bubble)
        }

        // Cancel animations when view is detached to prevent leaks
        bubble.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                cancelPulse()
                v.setTag(TAG_ATTACHED, null)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    private fun snapToEdge(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        val parentW = parent.width.toFloat()
        val centerX = view.x + view.width / 2f

        val targetX = if (centerX < parentW / 2f) {
            -view.left.toFloat() + EDGE_MARGIN_DP
        } else {
            parentW - view.left.toFloat() - view.width.toFloat() - EDGE_MARGIN_DP
        }

        if (MotionUtil.isReducedMotion(view.context)) {
            view.translationX = targetX
        } else {
            val pageMotion = view.resources.getInteger(R.integer.motion_page).toLong()
            ObjectAnimator.ofFloat(view, "translationX", view.translationX, targetX).apply {
                duration = pageMotion
                interpolator = OvershootInterpolator(1.2f)
                start()
            }
        }
    }

    private fun startPulse(view: View) {
        val emphasisMotion = view.resources.getInteger(R.integer.motion_emphasis).toLong()
        pulseAnimatorX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f).apply {
            duration = emphasisMotion
            startDelay = PULSE_DELAY_MS
            repeatCount = 2
            repeatMode = ObjectAnimator.RESTART
        }
        pulseAnimatorY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f).apply {
            duration = emphasisMotion
            startDelay = PULSE_DELAY_MS
            repeatCount = 2
            repeatMode = ObjectAnimator.RESTART
        }
        pulseAnimatorX?.start()
        pulseAnimatorY?.start()
    }

    private fun cancelPulse() {
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()
        pulseAnimatorX = null
        pulseAnimatorY = null
    }
}
