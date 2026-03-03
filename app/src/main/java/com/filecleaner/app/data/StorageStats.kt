package com.filecleaner.app.data

data class StorageStats(
    val totalFiles: Int,
    val totalSize: Long,
    val junkSize: Long,
    val duplicateSize: Long,
    val largeSize: Long
)
