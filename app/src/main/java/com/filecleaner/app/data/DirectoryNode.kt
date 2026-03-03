package com.filecleaner.app.data

data class DirectoryNode(
    val path: String,
    val name: String,
    val files: List<FileItem>,
    val children: MutableList<DirectoryNode> = mutableListOf(),
    val totalSize: Long,
    val totalFileCount: Int,
    val depth: Int
) {
    /** Recursively collects all files from this node and all descendants. */
    fun allFiles(): List<FileItem> {
        val result = mutableListOf<FileItem>()
        result.addAll(files)
        for (child in children) {
            result.addAll(child.allFiles())
        }
        return result
    }
}
