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

    // Use FileCategory's single source of truth for extension mapping

    private val SKIP_DIRS = setOf(
        "Android/data", "Android/obb", ".thumbnails", ".cache",
        "lost+found", "proc", "sys", "dev"
    )

    suspend fun scanAll(context: Context, onProgress: (Int) -> Unit = {}): List<FileItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            val root = Environment.getExternalStorageDirectory()
            val rootPath = root.absolutePath

            // Iterative walk using explicit stack (F-018)
            val stack = ArrayDeque<File>()
            stack.push(root)
            var scanned = 0

            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dir = stack.pop()
                val children = dir.listFiles() ?: continue

                for (child in children) {
                    if (child.isDirectory) {
                        val relative = child.absolutePath.substringAfter("$rootPath/")
                        if (SKIP_DIRS.any { relative.startsWith(it) } || child.name.startsWith(".")) continue
                        stack.push(child)
                    } else {
                        scanned++
                        if (scanned % 100 == 0) onProgress(scanned)
                        results.add(child.toFileItem())
                    }
                }
            }
            results
        }

    /** Scan returning both a flat file list and a directory tree. */
    suspend fun scanWithTree(
        context: Context,
        onProgress: (Int) -> Unit = {}
    ): Pair<List<FileItem>, DirectoryNode> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
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
                name = info.file.name,
                files = info.files.toList(),
                children = childNodes.toMutableList(),
                totalSize = totalSize,
                totalFileCount = totalFileCount,
                depth = info.depth
            )
        }

        val rootNode = nodeMap[rootPath] ?: DirectoryNode(
            path = rootPath,
            name = root.name,
            files = emptyList(),
            totalSize = 0,
            totalFileCount = 0,
            depth = 0
        )

        Pair(results, rootNode)
    }

    /** Rebuild tree from a flat file list without re-scanning the filesystem. */
    fun buildTreeFromFiles(files: List<FileItem>): DirectoryNode {
        val root = Environment.getExternalStorageDirectory()
        val rootPath = root.absolutePath

        // Group files by parent directory
        val dirFiles = mutableMapOf<String, MutableList<FileItem>>()
        val allDirPaths = mutableSetOf<String>()

        for (item in files) {
            val parentDir = File(item.path).parent ?: continue
            dirFiles.getOrPut(parentDir) { mutableListOf() }.add(item)
            // Ensure all ancestor dirs are tracked
            var dir = parentDir
            while (dir != rootPath && dir.startsWith(rootPath) && allDirPaths.add(dir)) {
                dir = File(dir).parent ?: break
            }
        }
        allDirPaths.add(rootPath)

        // Build parent -> children mapping
        val childMap = mutableMapOf<String, MutableList<String>>()
        for (dirPath in allDirPaths) {
            if (dirPath != rootPath) {
                val parent = File(dirPath).parent ?: continue
                childMap.getOrPut(parent) { mutableListOf() }.add(dirPath)
            }
        }

        // Build tree bottom-up by sorting paths by depth descending
        val nodeMap = mutableMapOf<String, DirectoryNode>()
        val sortedDirs = allDirPaths.sortedByDescending { it.count { c -> c == '/' } }

        for (dirPath in sortedDirs) {
            val ownFiles = dirFiles[dirPath] ?: emptyList()
            val childNodes = childMap[dirPath]?.mapNotNull { nodeMap[it] }?.toMutableList()
                ?: mutableListOf()
            val ownSize = ownFiles.sumOf { it.size }
            val ownCount = ownFiles.size
            val totalSize = ownSize + childNodes.sumOf { it.totalSize }
            val totalCount = ownCount + childNodes.sumOf { it.totalFileCount }
            val depth = dirPath.substringAfter(rootPath).count { it == '/' }

            nodeMap[dirPath] = DirectoryNode(
                path = dirPath,
                name = File(dirPath).name,
                files = ownFiles,
                children = childNodes,
                totalSize = totalSize,
                totalFileCount = totalCount,
                depth = depth
            )
        }

        return nodeMap[rootPath] ?: DirectoryNode(
            path = rootPath,
            name = root.name,
            files = emptyList(),
            totalSize = 0,
            totalFileCount = 0,
            depth = 0
        )
    }

    fun fileToItem(file: File): FileItem = file.toFileItem()

    fun File.toFileItem(): FileItem {
        val ext = extension.lowercase()
        val rawCategory = FileCategory.fromExtension(ext)
        val category = if (rawCategory == FileCategory.OTHER &&
            absolutePath.contains("/Download/", ignoreCase = true)) FileCategory.DOWNLOAD
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
