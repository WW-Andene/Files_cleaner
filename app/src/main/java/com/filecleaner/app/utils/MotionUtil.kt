package com.filecleaner.app.utils

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.navigation.NavOptions
import com.filecleaner.app.R

/**
 * Centralized motion utilities for the File Cleaner app (§DM1-DM5).
 *
 * All animation durations derive from the motion vocabulary defined in
 * res/values/dimens.xml (motion_micro, motion_enter, motion_exit,
 * motion_page, motion_emphasis, motion_stagger_step).
 *
 * Every public method respects the user's "Remove animations" /
 * ANIMATOR_DURATION_SCALE accessibility setting.
 */
object MotionUtil {

    // ── Custom interpolators (match XML declarations) ──────────────────

    /** Loads the custom fast-out-slow-in interpolator used by enter/page animations. */
    fun enterInterpolator(context: Context): Interpolator =
        AnimationUtils.loadInterpolator(context, R.interpolator.fast_out_slow_in_custom)

    /** Loads the custom gentle-overshoot interpolator used by FAB/success animations. */
    fun overshootInterpolator(context: Context): Interpolator =
        AnimationUtils.loadInterpolator(context, R.interpolator.overshoot_gentle)

    // ── Reduced-motion check (§DM4) ─────────────────────────────────────

    /** Returns true when the user prefers reduced motion (animation scale below normal, including 0.5x). */
    fun isReducedMotion(context: Context): Boolean {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale < 1f
    }

    // ── Duration helpers ─────────────────────────────────────────────────

    /** Micro duration (120ms) — hover, press, toggle feedback. */
    fun microMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_micro).toLong()

    /** Enter duration (220ms) — element appearance, expand. */
    fun enterMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_enter).toLong()

    /** Exit duration (160ms) — element disappearance (faster than enter). */
    fun exitMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_exit).toLong()

    /** Page duration (280ms) — page/fragment transition. */
    fun pageMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_page).toLong()

    /** Emphasis duration (400ms) — delight moments, pulse, celebration. */
    fun emphasisMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_emphasis).toLong()

    /** Stagger step (40ms) — per-item stagger in lists, capped at 160ms total. */
    fun staggerStepMs(context: Context): Long =
        context.resources.getInteger(R.integer.motion_stagger_step).toLong()

    // ── Common animation patterns ────────────────────────────────────────

    /**
     * Fade-in + slide-up entrance for a view, respecting reduced motion.
     * Uses motion_enter duration with decelerate interpolator.
     */
    fun fadeSlideIn(view: View, startDelay: Long = 0L): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            view.visibility = View.VISIBLE
            return null
        }
        val duration = enterMs(view.context)
        view.alpha = 0f
        view.translationY = view.resources.getDimension(R.dimen.anim_slide_in_y)
        view.visibility = View.VISIBLE
        return view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(startDelay)
            .setInterpolator(enterInterpolator(view.context))
    }

    /**
     * Fade-out + slide-down exit for a view, respecting reduced motion.
     * Uses motion_exit duration with accelerate interpolator.
     */
    fun fadeSlideOut(view: View, onEnd: (() -> Unit)? = null): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            onEnd?.invoke()
            return null
        }
        val duration = exitMs(view.context)
        return view.animate()
            .alpha(0f)
            .translationY(view.resources.getDimension(R.dimen.anim_slide_out_y))
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.translationY = 0f
                view.alpha = 1f
                onEnd?.invoke()
            }
    }

    /**
     * Scale-bounce entrance (overshoot) for emphasis elements like FABs.
     * Uses motion_enter duration with overshoot interpolator.
     */
    fun scaleIn(view: View): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            view.visibility = View.VISIBLE
            return null
        }
        val duration = enterMs(view.context)
        view.scaleX = 0f
        view.scaleY = 0f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        return view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(overshootInterpolator(view.context))
    }

    /**
     * Scale-down exit for elements like FABs.
     * Uses motion_exit duration with accelerate interpolator.
     */
    fun scaleOut(view: View, onEnd: (() -> Unit)? = null): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.visibility = View.GONE
            onEnd?.invoke()
            return null
        }
        val duration = exitMs(view.context)
        return view.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                onEnd?.invoke()
            }
    }

    // ── Celebration / completion helpers ─────────────────────────────────

    /**
     * Success pulse animation — gentle scale overshoot for checkmarks
     * and completion icons. Uses motion_emphasis duration.
     */
    fun successPulse(view: View): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.alpha = 1f
            view.visibility = View.VISIBLE
            return null
        }
        val duration = emphasisMs(view.context)
        view.scaleX = 0.3f
        view.scaleY = 0.3f
        view.alpha = 0f
        view.visibility = View.VISIBLE
        return view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(overshootInterpolator(view.context))
    }

    // ── Crossfade helper ─────────────────────────────────────────────────

    /**
     * Crossfades from [outView] to [inView] using enter/exit durations.
     * Respects reduced motion by snapping visibility immediately.
     */
    fun crossfade(outView: View, inView: View) {
        if (isReducedMotion(outView.context)) {
            outView.alpha = 0f
            outView.visibility = View.GONE
            inView.alpha = 1f
            inView.visibility = View.VISIBLE
            return
        }
        val exitDur = exitMs(outView.context)
        val enterDur = enterMs(inView.context)
        outView.animate()
            .alpha(0f)
            .setDuration(exitDur)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                outView.visibility = View.GONE
                inView.alpha = 0f
                inView.visibility = View.VISIBLE
                inView.animate()
                    .alpha(1f)
                    .setDuration(enterDur)
                    .setInterpolator(enterInterpolator(inView.context))
                    .start()
            }
            .start()
    }

    // ── Stagger delay helper ─────────────────────────────────────────────

    /**
     * Computes a capped stagger delay for list item at [index].
     * Uses motion_stagger_step per item, capped at 160ms total (4 steps).
     * Returns 0 when reduced motion is enabled.
     */
    fun staggerDelay(context: Context, index: Int): Long {
        if (isReducedMotion(context)) return 0L
        val step = staggerStepMs(context)
        val maxStagger = step * 4  // cap at 160ms
        return (index * step).coerceAtMost(maxStagger)
    }

    // ── Effective duration helper ────────────────────────────────────────

    /**
     * Returns the given duration scaled by ANIMATOR_DURATION_SCALE.
     * If reduced motion is enabled, returns 0 (instant).
     * Useful when constructing ObjectAnimator durations that need to
     * respect the system scale but still use resource-based values.
     */
    fun effectiveDuration(context: Context, baseMs: Long): Long {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        if (scale < 1f) return 0L
        return (baseMs * scale).toLong()
    }

    // ── Micro-interaction helper ─────────────────────────────────────────

    /**
     * Quick alpha fade for micro-interactions (press feedback, toggle state).
     * Uses motion_micro duration with linear interpolation.
     * Ideal for checkbox/toggle visual feedback (§DM4).
     */
    fun microFade(view: View, targetAlpha: Float): ViewPropertyAnimator? {
        if (isReducedMotion(view.context)) {
            view.alpha = targetAlpha
            return null
        }
        val duration = microMs(view.context)
        return view.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
    }

    // ── Navigation helpers ───────────────────────────────────────────────

    /**
     * Builds standard NavOptions with consistent enter/exit animations
     * from the motion vocabulary. Use this instead of building NavOptions
     * manually to ensure all navigations share the same transition style.
     */
    fun navOptions(): NavOptions = NavOptions.Builder()
        .setEnterAnim(R.anim.nav_enter)
        .setExitAnim(R.anim.nav_exit)
        .setPopEnterAnim(R.anim.nav_pop_enter)
        .setPopExitAnim(R.anim.nav_pop_exit)
        .build()
}
