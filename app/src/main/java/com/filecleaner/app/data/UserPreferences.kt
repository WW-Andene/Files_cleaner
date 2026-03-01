package com.filecleaner.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Singleton wrapper for user preferences. Replaces all previously hardcoded thresholds
 * with configurable values backed by SharedPreferences.
 */
object UserPreferences {

    private const val PREFS_NAME = "raccoon_prefs"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ── Scan thresholds ──

    var largeFileThresholdMb: Int
        get() = prefs.getInt("large_file_threshold_mb", 50)
        set(value) = prefs.edit().putInt("large_file_threshold_mb", value).apply()

    var staleDownloadDays: Int
        get() = prefs.getInt("stale_download_days", 90)
        set(value) = prefs.edit().putInt("stale_download_days", value).apply()

    var maxLargeFiles: Int
        get() = prefs.getInt("max_large_files", 200)
        set(value) = prefs.edit().putInt("max_large_files", value).apply()

    // ── Display ──

    var defaultViewModeOrdinal: Int
        get() = prefs.getInt("default_view_mode", 0)
        set(value) = prefs.edit().putInt("default_view_mode", value).apply()

    var defaultSortOrder: Int
        get() = prefs.getInt("default_sort_order", 0)
        set(value) = prefs.edit().putInt("default_sort_order", value).apply()

    var showHiddenFiles: Boolean
        get() = prefs.getBoolean("show_hidden_files", false)
        set(value) = prefs.edit().putBoolean("show_hidden_files", value).apply()

    // ── Undo ──

    var undoTimeoutMs: Int
        get() = prefs.getInt("undo_timeout_ms", 8000)
        set(value) = prefs.edit().putInt("undo_timeout_ms", value).apply()

    // ── Favorites & Protected (P3) ──

    var favoritePaths: Set<String>
        get() = prefs.getStringSet("favorite_paths", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("favorite_paths", value).apply()

    var protectedPaths: Set<String>
        get() = prefs.getStringSet("protected_paths", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("protected_paths", value).apply()

    fun toggleFavorite(path: String) {
        val current = favoritePaths.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        favoritePaths = current
    }

    fun toggleProtected(path: String) {
        val current = protectedPaths.toMutableSet()
        if (path in current) current.remove(path) else current.add(path)
        protectedPaths = current
    }

    fun isFavorite(path: String): Boolean = path in favoritePaths
    fun isProtected(path: String): Boolean = path in protectedPaths

    // ── Onboarding ──

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean("has_seen_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_seen_onboarding", value).apply()
}
