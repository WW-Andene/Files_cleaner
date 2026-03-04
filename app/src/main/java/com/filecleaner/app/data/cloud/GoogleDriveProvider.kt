package com.filecleaner.app.data.cloud

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive provider using the REST API v3 with OAuth2 access token.
 * The access token must be obtained via the Google Sign-In flow
 * and stored in the connection's authToken field.
 */
class GoogleDriveProvider(
    private val connection: CloudConnection,
    @Suppress("unused") context: Context
) : CloudProvider {

    // Store application context to avoid Activity leak
    private val appContext: Context = context.applicationContext

    override val displayName: String = connection.displayName
    override val type: ProviderType = ProviderType.GOOGLE_DRIVE

    @Volatile
    private var connected = false

    override val isConnected: Boolean get() = connected

    private val accessToken: String get() = connection.authToken

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://www.googleapis.com/drive/v3/about?fields=user")
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 15000
            }
            connected = conn.responseCode == 200
            connected
        } catch (e: Exception) {
            connected = false
            false
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun disconnect() {
        connected = false
    }

    override suspend fun listFiles(remotePath: String): List<CloudFile> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val folderId = if (remotePath == "/" || remotePath.isEmpty()) "root" else remotePath
            val query = URLEncoder.encode("'$folderId' in parents and trashed=false", "UTF-8")
            val fields = URLEncoder.encode(
                "files(id,name,mimeType,size,modifiedTime)", "UTF-8"
            )
            val url = URL(
                "https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&orderBy=folder,name&pageSize=1000"
            )
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode != 200) {
                return@withContext emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()

            val json = JSONObject(body)
            val filesArray = json.getJSONArray("files")
            val result = mutableListOf<CloudFile>()

            for (i in 0 until filesArray.length()) {
                val file = filesArray.getJSONObject(i)
                val mime = file.optString("mimeType", "")
                val isDir = mime == "application/vnd.google-apps.folder"
                result.add(CloudFile(
                    name = file.getString("name"),
                    remotePath = file.getString("id"),
                    isDirectory = isDir,
                    size = file.optLong("size", 0L),
                    lastModified = parseGoogleDate(file.optString("modifiedTime", "")),
                    mimeType = mime
                ))
            }

            result.sortedWith(compareBy<CloudFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    override suspend fun download(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val fileId = remotePath
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            conn = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
                readTimeout = 60000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("Download failed with HTTP $code")
            }
            conn.inputStream.use { it.copyTo(output) }
        } finally {
            conn?.disconnect()
        }
        Unit
    }

    override suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String) =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val parentId = if (remotePath == "/" || remotePath.isEmpty()) "root" else remotePath
                val metadata = JSONObject().apply {
                    put("name", fileName)
                    put("parents", org.json.JSONArray().put(parentId))
                }

                val boundary = "====${System.currentTimeMillis()}===="
                val url = URL(
                    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
                )
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                }

                conn.outputStream.use { out ->
                    val metaPart = "--$boundary\r\n" +
                            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                            metadata.toString() + "\r\n"
                    out.write(metaPart.toByteArray())

                    val mediaPart = "--$boundary\r\n" +
                            "Content-Type: $mimeType\r\n\r\n"
                    out.write(mediaPart.toByteArray())
                    input.copyTo(out)
                    out.write("\r\n--$boundary--".toByteArray())
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    throw java.io.IOException("Upload failed with HTTP $code")
                }
            } finally {
                conn?.disconnect()
            }
            Unit
        }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val fileId = remotePath
            val url = URL("https://www.googleapis.com/drive/v3/files/$fileId")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = 15000
            }
            val code = conn.responseCode
            if (code !in 200..299 && code != 204) {
                throw java.io.IOException("Delete failed with HTTP $code")
            }
        } finally {
            conn?.disconnect()
        }
        Unit
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val parentId = if (remotePath == "/" || remotePath.isEmpty()) "root"
            else remotePath.substringBeforeLast("/").ifEmpty { "root" }
            val dirName = remotePath.substringAfterLast("/")

            val metadata = JSONObject().apply {
                put("name", dirName)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", org.json.JSONArray().put(parentId))
            }

            val url = URL("https://www.googleapis.com/drive/v3/files")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connectTimeout = 15000
                doOutput = true
            }
            conn.outputStream.use { it.write(metadata.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("Create directory failed with HTTP $code")
            }
        } finally {
            conn?.disconnect()
        }
        Unit
    }

    private fun parseGoogleDate(dateStr: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
