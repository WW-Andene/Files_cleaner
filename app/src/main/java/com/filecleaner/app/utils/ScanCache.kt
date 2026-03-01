package com.filecleaner.app.utils

import android.content.Context
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ScanCache {

    private const val CACHE_FILE = "scan_cache.json"
    private const val CACHE_VERSION = 1
    // C3: Guard against deeply nested JSON trees that could cause stack overflow
    private const val MAX_TREE_DEPTH = 100

    suspend fun save(context: Context, files: List<FileItem>, tree: DirectoryNode) =
        withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("version", CACHE_VERSION)

            // Serialize file list
            val filesArray = JSONArray()
            for (item in files) {
                filesArray.put(fileItemToJson(item))
            }
            root.put("files", filesArray)

            // Serialize directory tree
            root.put("tree", directoryNodeToJson(tree))

            // Write to file
            val cacheFile = File(context.filesDir, CACHE_FILE)
            cacheFile.writeText(root.toString())
        }

    suspend fun load(context: Context): Pair<List<FileItem>, DirectoryNode>? =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, CACHE_FILE)
            if (!cacheFile.exists()) return@withContext null

            try {
                val root = JSONObject(cacheFile.readText())

                // Version check — incompatible cache is discarded
                val version = root.optInt("version", 0)
                if (version != CACHE_VERSION) {
                    cacheFile.delete()
                    return@withContext null
                }

                val filesArray = root.getJSONArray("files")
                val files = mutableListOf<FileItem>()
                for (i in 0 until filesArray.length()) {
                    if (i % 100 == 0) ensureActive()
                    files.add(jsonToFileItem(filesArray.getJSONObject(i)))
                }
                // Skip File.exists() validation here — on app restart the storage
                // permission may not yet be active, causing all files to appear
                // missing.  Stale entries are harmless (they show "file not found"
                // if opened) and will be refreshed on the next scan.

                val tree = jsonToDirectoryNode(root.getJSONObject("tree"), depth = 0)

                Pair(files, tree)
            } catch (e: Exception) {
                // Corrupt cache — delete and return null
                cacheFile.delete()
                null
            }
        }

    /**
     * Recursively prune files from the tree that are NOT in [validPaths].
     * Uses the pre-validated path set from the flat file list, avoiding
     * redundant File.exists() disk checks for every tree entry.
     */
    private fun pruneTreeByPaths(node: DirectoryNode, validPaths: Set<String>): DirectoryNode {
        val validFiles = node.files.filter { it.path in validPaths }
        val prunedChildren = node.children.map { pruneTreeByPaths(it, validPaths) }.toMutableList()

        val ownFileSize = validFiles.sumOf { it.size }
        val ownFileCount = validFiles.size
        val totalSize = ownFileSize + prunedChildren.sumOf { it.totalSize }
        val totalFileCount = ownFileCount + prunedChildren.sumOf { it.totalFileCount }

        return node.copy(
            files = validFiles,
            children = prunedChildren,
            totalSize = totalSize,
            totalFileCount = totalFileCount
        )
    }

    private fun fileItemToJson(item: FileItem): JSONObject = JSONObject().apply {
        put("path", item.path)
        put("name", item.name)
        put("size", item.size)
        put("lastModified", item.lastModified)
        put("category", item.category.name)
        put("duplicateGroup", item.duplicateGroup)
    }

    private fun jsonToFileItem(json: JSONObject): FileItem = FileItem(
        path = json.getString("path"),
        name = json.getString("name"),
        size = json.getLong("size"),
        lastModified = json.getLong("lastModified"),
        category = try {
            FileCategory.valueOf(json.getString("category"))
        } catch (_: Exception) {
            FileCategory.OTHER
        },
        duplicateGroup = json.optInt("duplicateGroup", -1)
    )

    private fun directoryNodeToJson(node: DirectoryNode): JSONObject = JSONObject().apply {
        put("path", node.path)
        put("name", node.name)
        put("totalSize", node.totalSize)
        put("totalFileCount", node.totalFileCount)
        put("depth", node.depth)

        val filesArray = JSONArray()
        for (file in node.files) {
            filesArray.put(fileItemToJson(file))
        }
        put("files", filesArray)

        val childrenArray = JSONArray()
        for (child in node.children) {
            childrenArray.put(directoryNodeToJson(child))
        }
        put("children", childrenArray)
    }

    private fun jsonToDirectoryNode(json: JSONObject, depth: Int): DirectoryNode {
        val filesArray = json.getJSONArray("files")
        val files = mutableListOf<FileItem>()
        for (i in 0 until filesArray.length()) {
            files.add(jsonToFileItem(filesArray.getJSONObject(i)))
        }

        val children = mutableListOf<DirectoryNode>()
        // C3: Stop recursion beyond MAX_TREE_DEPTH to prevent stack overflow
        if (depth < MAX_TREE_DEPTH) {
            val childrenArray = json.getJSONArray("children")
            for (i in 0 until childrenArray.length()) {
                children.add(jsonToDirectoryNode(childrenArray.getJSONObject(i), depth + 1))
            }
        }

        return DirectoryNode(
            path = json.getString("path"),
            name = json.getString("name"),
            files = files,
            children = children,
            totalSize = json.getLong("totalSize"),
            totalFileCount = json.getInt("totalFileCount"),
            depth = json.getInt("depth")
        )
    }
}
