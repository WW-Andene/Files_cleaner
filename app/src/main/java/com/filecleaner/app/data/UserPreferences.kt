package com.filecleaner.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

object UserPreferences {

    private val SORT_ORDER = intPreferencesKey("sort_order")
    private val VIEW_MODE = intPreferencesKey("view_mode")
    private val LARGE_FILE_THRESHOLD_MB = intPreferencesKey("large_file_threshold_mb")
    private val LAST_CATEGORY = intPreferencesKey("last_category")
    private val TREE_SORT_ORDER = intPreferencesKey("tree_sort_order")

    fun sortOrder(context: Context): Flow<Int> =
        context.dataStore.data.map { it[SORT_ORDER] ?: 0 }

    fun viewMode(context: Context): Flow<Int> =
        context.dataStore.data.map { it[VIEW_MODE] ?: 0 }

    fun largeFileThresholdMb(context: Context): Flow<Int> =
        context.dataStore.data.map { it[LARGE_FILE_THRESHOLD_MB] ?: 50 }

    fun lastCategory(context: Context): Flow<Int> =
        context.dataStore.data.map { it[LAST_CATEGORY] ?: 0 }

    fun treeSortOrder(context: Context): Flow<Int> =
        context.dataStore.data.map { it[TREE_SORT_ORDER] ?: 0 }

    suspend fun saveSortOrder(context: Context, value: Int) {
        context.dataStore.edit { it[SORT_ORDER] = value }
    }

    suspend fun saveViewMode(context: Context, value: Int) {
        context.dataStore.edit { it[VIEW_MODE] = value }
    }

    suspend fun saveLargeFileThresholdMb(context: Context, value: Int) {
        context.dataStore.edit { it[LARGE_FILE_THRESHOLD_MB] = value }
    }

    suspend fun saveLastCategory(context: Context, value: Int) {
        context.dataStore.edit { it[LAST_CATEGORY] = value }
    }

    suspend fun saveTreeSortOrder(context: Context, value: Int) {
        context.dataStore.edit { it[TREE_SORT_ORDER] = value }
    }
}
