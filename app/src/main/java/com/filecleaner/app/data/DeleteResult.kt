package com.filecleaner.app.data

data class DeleteResult(
    val moved: Int,
    val failed: Int,
    val freedBytes: Long,
    val canUndo: Boolean
)
