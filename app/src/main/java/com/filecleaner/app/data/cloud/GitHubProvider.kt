package com.filecleaner.app.data.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud provider for GitHub repositories via REST API v3.
 * Lists repos and their contents, supports downloading files.
 */
class GitHubProvider(
    private val connection: CloudConnection,
    private val context: Context
) : CloudProvider {

    override val displayName: String get() = connection.displayName
    override val type: ProviderType get() = ProviderType.GITHUB
    override var isConnected: Boolean = false
        private set

    private val token get() = connection.authToken

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/user")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            conn.disconnect()
            isConnected = code == 200
            isConnected
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    override suspend fun disconnect() {
        isConnected = false
    }

    override suspend fun listFiles(remotePath: String): List<CloudFile> = withContext(Dispatchers.IO) {
        val apiUrl = if (remotePath == "/" || remotePath.isEmpty()) {
            // List user's repos
            "https://api.github.com/user/repos?per_page=100&sort=updated"
        } else {
            // Path format: /owner/repo/path/to/dir
            val parts = remotePath.trimStart('/').split("/", limit = 3)
            val owner = parts.getOrElse(0) { "" }
            val repo = parts.getOrElse(1) { "" }
            val path = parts.getOrElse(2) { "" }
            if (path.isEmpty()) {
                "https://api.github.com/repos/$owner/$repo/contents/"
            } else {
                "https://api.github.com/repos/$owner/$repo/contents/$path"
            }
        }

        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val array = JSONArray(response)
        val files = mutableListOf<CloudFile>()

        if (remotePath == "/" || remotePath.isEmpty()) {
            // Parse repos
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val fullName = obj.getString("full_name")
                files.add(CloudFile(
                    name = obj.getString("name"),
                    remotePath = "/$fullName",
                    isDirectory = true,
                    size = obj.optLong("size", 0) * 1024L, // GitHub reports KB
                    lastModified = try {
                        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                            .parse(obj.optString("updated_at"))?.time ?: 0L
                    } catch (_: Exception) { 0L }
                ))
            }
        } else {
            // Parse repo contents
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val itemType = obj.getString("type")
                val parts = remotePath.trimStart('/').split("/", limit = 2)
                val repoPrefix = if (parts.size >= 2) "/${parts[0]}/${parts[1].split("/")[0]}" else remotePath
                files.add(CloudFile(
                    name = obj.getString("name"),
                    remotePath = "$repoPrefix/${obj.getString("path")}",
                    isDirectory = itemType == "dir",
                    size = obj.optLong("size", 0),
                    mimeType = if (itemType == "file") "application/octet-stream" else ""
                ))
            }
        }

        files
    }

    override suspend fun download(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val parts = remotePath.trimStart('/').split("/", limit = 3)
        val owner = parts.getOrElse(0) { "" }
        val repo = parts.getOrElse(1) { "" }
        val path = parts.getOrElse(2) { "" }

        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$path"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3.raw")
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000

        conn.inputStream.use { input ->
            input.copyTo(output)
        }
        conn.disconnect()
    }

    override suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String) {
        // GitHub API requires specific commit-based upload flow
        throw UnsupportedOperationException("Upload is not supported for GitHub repositories")
    }

    override suspend fun delete(remotePath: String) {
        throw UnsupportedOperationException("Delete is not supported for GitHub repositories")
    }

    override suspend fun createDirectory(remotePath: String) {
        throw UnsupportedOperationException("Create directory is not supported for GitHub repositories")
    }
}
