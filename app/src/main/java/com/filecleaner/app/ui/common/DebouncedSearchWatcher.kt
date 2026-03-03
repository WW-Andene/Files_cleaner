package com.filecleaner.app.ui.common

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Lifecycle-aware TextWatcher with debounce.
 * Automatically removes pending callbacks when the lifecycle is destroyed.
 */
class DebouncedSearchWatcher(
    lifecycleOwner: LifecycleOwner,
    private val delayMs: Long = 300L,
    private val onSearch: (String) -> Unit
) : TextWatcher, DefaultLifecycleObserver {

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        searchRunnable?.let { handler.removeCallbacks(it) }
        searchRunnable = Runnable {
            onSearch(s?.toString()?.trim() ?: "")
        }
        handler.postDelayed(searchRunnable!!, delayMs)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        searchRunnable?.let { handler.removeCallbacks(it) }
        searchRunnable = null
    }
}
