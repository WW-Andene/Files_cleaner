package com.filecleaner.app.utils

import android.content.Context
import com.filecleaner.app.data.DirectoryNode
import com.filecleaner.app.data.FileCategory
import com.filecleaner.app.data.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ScanCache {

    private const val CACHE_FILE = "scan_cache.json"
    private const val CACHE_VERSION = 1
    // C3: Guard against deeply nested JSON trees that could cause stack overflow
    private const val MAX_TREE_DEPTH = 100
    // B2: Limit cached entries to prevent unbounded cache file growth
    private const val MAX_CACHED_FILES = 50_000
    // F-C5-01/F-C3-04: Auto-expire cache to limit persistent file inventory exposure
    private const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    suspend fun save(context: Context, files: List<FileItem>, tree: DirectoryNode) =
        withContext(Dispatchers.IO) {
            val root = JSONObject()
            root.put("version", CACHE_VERSION)

            // B2: Cap cached file count to prevent multi-MB JSON files
            val capped = if (files.size > MAX_CACHED_FILES) files.take(MAX_CACHED_FILES) else files
            // Serialize file list
            val filesArray = JSONArray()
            for (item in capped) {
                filesArray.put(fileItemToJson(item))
            }
            root.put("files", filesArray)

            // Serialize directory tree (with depth guard matching read-side MAX_TREE_DEPTH)
            root.put("tree", directoryNodeToJson(tree, 0))

            // Write to file
            val cacheFile = File(context.filesDir, CACHE_FILE)
            val tempFile = File(context.filesDir, "$CACHE_FILE.tmp")
            tempFile.writeText(root.toString())
            if (!tempFile.renameTo(cacheFile)) {
                // Fallback: copy content if rename fails (cross-filesystem)
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
        }

    suspend fun load(context: Context): Pair<List<FileItem>, DirectoryNode>? =
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, CACHE_FILE)
            if (!cacheFile.exists()) return@withContext null

            // F-C5-01: Auto-expire old cache to limit persistent data exposure
            val cacheAge = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheAge > CACHE_MAX_AGE_MS) {
                cacheFile.delete()
                return@withContext null
            }

            try {
                // D2-01: Stream JSON instead of loading entire file into memory
                var version = 0
                var files: List<FileItem>? = null
                var tree: DirectoryNode? = null

                android.util.JsonReader(cacheFile.reader(Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "version" -> version = reader.nextInt()
                            "files" -> files = readFileArray(reader)
                            "tree" -> tree = readTreeNode(reader, 0)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }

                if (version != CACHE_VERSION) {
                    cacheFile.delete()
                    return@withContext null
                }

                val f = files ?: return@withContext null
                val t = tree ?: return@withContext null
                if (f.isEmpty()) return@withContext null

                Pair(f, t)
            } catch (e: Exception) {
                cacheFile.delete()
                null
            }
        }

    private fun fileItemToJson(item: FileItem): JSONObject = JSONObject().apply {
        put("path", item.path)
        put("name", item.name)
        put("size", item.size)
        put("lastModified", item.lastModified)
        put("category", item.category.name)
        put("duplicateGroup", item.duplicateGroup)
    }

    // D2-01: Streaming file array reader — parses one FileItem at a time
    private fun readFileArray(reader: android.util.JsonReader): List<FileItem> {
        val files = mutableListOf<FileItem>()
        reader.beginArray()
        while (reader.hasNext()) {
            files.add(readFileItem(reader))
        }
        reader.endArray()
        return files
    }

    private fun readFileItem(reader: android.util.JsonReader): FileItem {
        var path = ""
        var name = ""
        var size = 0L
        var lastModified = 0L
        var category = FileCategory.OTHER
        var duplicateGroup = -1

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "path" -> path = reader.nextString()
                "name" -> name = reader.nextString()
                "size" -> size = reader.nextLong()
                "lastModified" -> lastModified = reader.nextLong()
                "category" -> category = try {
                    FileCategory.valueOf(reader.nextString())
                } catch (_: Exception) {
                    FileCategory.OTHER
                }
                "duplicateGroup" -> duplicateGroup = reader.nextInt()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return FileItem(path, name, size, lastModified, category, duplicateGroup)
    }

    // D2-01: Streaming tree node reader
    private fun readTreeNode(reader: android.util.JsonReader, depth: Int): DirectoryNode {
        var path = ""
        var name = ""
        var totalSize = 0L
        var totalFileCount = 0
        var nodeDepth = 0
        val children = mutableListOf<DirectoryNode>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "path" -> path = reader.nextString()
                "name" -> name = reader.nextString()
                "totalSize" -> totalSize = reader.nextLong()
                "totalFileCount" -> totalFileCount = reader.nextInt()
                "depth" -> nodeDepth = reader.nextInt()
                "children" -> {
                    if (depth < MAX_TREE_DEPTH) {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            children.add(readTreeNode(reader, depth + 1))
                        }
                        reader.endArray()
                    } else {
                        reader.skipValue()
                    }
                }
                "files" -> reader.skipValue() // D6-02: files loaded from flat list
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return DirectoryNode(
            path = path,
            name = name,
            files = emptyList(),
            children = children,
            totalSize = totalSize,
            totalFileCount = totalFileCount,
            depth = nodeDepth
        )
    }

    // D6-02: Only serialize structural data in tree nodes.
    // File items are already in the flat "files" array — no need to duplicate them.
    private fun directoryNodeToJson(node: DirectoryNode, depth: Int = 0): JSONObject = JSONObject().apply {
        put("path", node.path)
        put("name", node.name)
        put("totalSize", node.totalSize)
        put("totalFileCount", node.totalFileCount)
        put("depth", node.depth)

        val childrenArray = JSONArray()
        if (depth < MAX_TREE_DEPTH) {
            for (child in node.children) {
                childrenArray.put(directoryNodeToJson(child, depth + 1))
            }
        }
        put("children", childrenArray)
    }

}
