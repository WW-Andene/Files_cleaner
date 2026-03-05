package com.filecleaner.app.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun View.applyBottomInset(extraPadding: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            navBar.bottom + extraPadding
        )
        insets
    }
}

fun View.applyTopInset(extraPadding: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.setPadding(
            view.paddingLeft,
            statusBar.top + extraPadding,
            view.paddingRight,
            view.paddingBottom
        )
        insets
    }
}
