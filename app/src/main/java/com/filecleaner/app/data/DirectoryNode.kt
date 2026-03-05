package com.filecleaner.app.data

data class DirectoryNode(
    val path: String,
    val name: String,
    val files: List<FileItem>,
    val children: List<DirectoryNode> = emptyList(),
    val totalSize: Long,
    val totalFileCount: Int,
    val depth: Int
)
