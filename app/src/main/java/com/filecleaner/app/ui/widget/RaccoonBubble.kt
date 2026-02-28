package com.filecleaner.app.ui.widget

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator

/**
 * Attaches drag-to-move and edge-snap behavior to an existing ImageView.
 * Not a custom View — just a touch listener setup.
 */
object RaccoonBubble {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(bubble: View, onClick: () -> Unit) {
        var downX = 0f
        var downY = 0f
        var startTransX = 0f
        var startTransY = 0f
        var moved = false
        val moveThreshold = 12f

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

        // Subtle pulse animation every 5 seconds
        startPulse(bubble)
    }

    private fun snapToEdge(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        val parentW = parent.width.toFloat()
        val centerX = view.x + view.width / 2f

        val targetX = if (centerX < parentW / 2f) {
            // Snap to left edge
            -view.left.toFloat() + 16f
        } else {
            // Snap to right edge
            parentW - view.left.toFloat() - view.width.toFloat() - 16f
        }

        ObjectAnimator.ofFloat(view, "translationX", view.translationX, targetX).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
    }

    private fun startPulse(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 600
            startDelay = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 600
            startDelay = 5000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        scaleX.start()
        scaleY.start()
    }
}
