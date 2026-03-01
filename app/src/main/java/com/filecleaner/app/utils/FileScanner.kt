package com.filecleaner.app.utils

import android.content.Context
import android.os.Environment
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

object FileScanner {

    private val SKIP_DIRS = setOf(
        "Android/data", "Android/obb", ".thumbnails", ".cache",
        "lost+found", "proc", "sys", "dev"
    )

    /** Scan returning both a flat file list and a directory tree. */
    suspend fun scanWithTree(
        context: Context,
        onProgress: (Int) -> Unit = {}
    ): Pair<List<FileItem>, DirectoryNode> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
        val rootPath = root.absolutePath

        // Map of dir path → (directFiles, childDirPaths)
        data class DirInfo(
            val file: File,
            val files: MutableList<FileItem> = mutableListOf(),
            val childPaths: MutableList<String> = mutableListOf(),
            val depth: Int = 0
        )

        val dirMap = LinkedHashMap<String, DirInfo>()
        dirMap[rootPath] = DirInfo(root, depth = 0)

        val stack = ArrayDeque<Pair<File, Int>>() // (dir, depth)
        stack.push(root to 0)
        var scanned = 0

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val (dir, depth) = stack.pop()
            val dirPath = dir.absolutePath
            val children = dir.listFiles() ?: continue

            for (child in children) {
                if (child.isDirectory) {
                    val relative = child.absolutePath.substringAfter("$rootPath/")
                    if (SKIP_DIRS.any { relative.startsWith(it) } || child.name.startsWith(".")) continue
                    val childPath = child.absolutePath
                    dirMap[childPath] = DirInfo(child, depth = depth + 1)
                    dirMap[dirPath]?.childPaths?.add(childPath)
                    stack.push(child to (depth + 1))
                } else {
                    scanned++
                    if (scanned % 100 == 0) onProgress(scanned)
                    val item = child.toFileItem()
                    results.add(item)
                    dirMap[dirPath]?.files?.add(item)
                }
            }
        }

        // Build tree bottom-up: sort by depth descending so leaves are processed first
        val nodeMap = LinkedHashMap<String, DirectoryNode>()
        val sortedPaths = dirMap.entries.sortedByDescending { it.value.depth }

        for ((path, info) in sortedPaths) {
            val childNodes = info.childPaths.mapNotNull { nodeMap[it] }
            val ownFileSize = info.files.sumOf { it.size }
            val ownFileCount = info.files.size
            val totalSize = ownFileSize + childNodes.sumOf { it.totalSize }
            val totalFileCount = ownFileCount + childNodes.sumOf { it.totalFileCount }

            nodeMap[path] = DirectoryNode(
                path = path,
                name = if (path == rootPath) "Internal Storage" else info.file.name,
                files = info.files.toList(),
                children = childNodes.toMutableList(),
                totalSize = totalSize,
                totalFileCount = totalFileCount,
                depth = info.depth
            )
        }

        val rootNode = nodeMap[rootPath] ?: DirectoryNode(
            path = rootPath,
            name = "Internal Storage",
            files = emptyList(),
            totalSize = 0,
            totalFileCount = 0,
            depth = 0
        )

        Pair(results, rootNode)
    }

    fun fileToItem(file: File): FileItem = file.toFileItem()

    // File manager needs broad storage access; MANAGE_EXTERNAL_STORAGE grants it
    @Suppress("DEPRECATION")
    private val downloadPath: String by lazy {
        Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
    }

    fun File.toFileItem(): FileItem {
        val ext = extension.lowercase()
        val rawCategory = FileCategory.fromExtension(ext)
        // Only assign DOWNLOAD to unrecognized files in the actual Downloads folder
        val category = if (rawCategory == FileCategory.OTHER &&
            absolutePath.startsWith(downloadPath)) FileCategory.DOWNLOAD
        else rawCategory

        return FileItem(
            path         = absolutePath,
            name         = name,
            size         = length(),
            lastModified = lastModified(),
            category     = category
        )
    }
}
