package com.filecleaner.app.data.cloud

import android.util.Base64
import android.util.Xml
import com.filecleaner.app.utils.retryOnNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * WebDAV cloud provider using plain HttpURLConnection (no extra dependencies).
 * Supports Nextcloud, ownCloud, and other WebDAV servers.
 */
class WebDavProvider(private var connection: CloudConnection) : CloudProvider {

    override val displayName: String = connection.displayName
    override val type: ProviderType = ProviderType.WEBDAV

    @Volatile
    private var connected = false

    // F-C3-02: Cache the auth header to allow clearing the raw credential
    private var cachedAuthHeader: String? = null

    /** Cached original credentials for reconnection after credential clearing */
    private val originalUsername: String = connection.username
    private val originalAuthToken: String = connection.authToken

    // Base URL must end without trailing slash
    private val baseUrl: String
        get() {
            val raw = connection.host.trimEnd('/')
            // Enforce HTTPS for Basic Auth credential safety
            return if (raw.startsWith("http://", ignoreCase = true)) {
                "https://" + raw.substring(7) // "http://".length == 7
            } else if (!raw.startsWith("https://", ignoreCase = true)) {
                "https://$raw"
            } else {
                raw
            }
        }

    override val isConnected: Boolean get() = connected

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            retryOnNetworkError {
                var conn: HttpURLConnection? = null
                try {
                    val url = URL("$baseUrl/")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "PROPFIND"
                        setRequestProperty("Authorization", authHeader())
                        setRequestProperty("Depth", "0")
                        setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    val code = conn.responseCode
                    connected = code in 200..299 || code == 207
                    if (!connected) {
                        throw java.io.IOException("HTTP $code")
                    }
                } finally {
                    conn?.disconnect()
                }
            }
            if (connected) {
                // F-C3-02: Cache auth header and drop credential reference
                cachedAuthHeader = authHeader()
                connection = connection.copy(authToken = "", username = "")
            }
            connected
        } catch (e: Exception) {
            connected = false
            throw e
        }
    }

    override suspend fun disconnect() {
        connected = false
        cachedAuthHeader = null
    }

    override suspend fun listFiles(remotePath: String): List<CloudFile> = withContext(Dispatchers.IO) {
        try {
            retryOnNetworkError {
                var conn: HttpURLConnection? = null
                try {
                    val path = remotePath.trimEnd('/') + "/"
                    val url = URL("$baseUrl$path")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "PROPFIND"
                        setRequestProperty("Authorization", authHeader())
                        setRequestProperty("Depth", "1")
                        setRequestProperty("Content-Type", "application/xml; charset=utf-8")
                        connectTimeout = 15000
                        readTimeout = 15000
                        doOutput = true
                    }
                    conn.outputStream.use { out ->
                        out.write(PROPFIND_BODY.toByteArray(Charsets.UTF_8))
                    }

                    val code = conn.responseCode
                    if (code !in 200..299 && code != 207) {
                        return@retryOnNetworkError emptyList()
                    }

                    val responseBody = conn.inputStream.bufferedReader().readText()
                    parseMultiStatus(responseBody, path)
                        .sortedWith(compareBy<CloudFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
                } finally {
                    conn?.disconnect()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun download(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        retryOnNetworkError {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl$remotePath")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", authHeader())
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw java.io.IOException("Download failed with HTTP $code")
                }
                conn.inputStream.use { it.copyTo(output) }
            } finally {
                conn?.disconnect()
            }
        }
        Unit
    }

    override suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String) =
        withContext(Dispatchers.IO) {
            retryOnNetworkError {
                var conn: HttpURLConnection? = null
                try {
                    val path = remotePath.trimEnd('/') + "/$fileName"
                    val url = URL("$baseUrl$path")
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "PUT"
                        setRequestProperty("Authorization", authHeader())
                        setRequestProperty("Content-Type", mimeType)
                        connectTimeout = 15000
                        readTimeout = 30000
                        doOutput = true
                    }
                    conn.outputStream.use { out -> input.copyTo(out) }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw java.io.IOException("Upload failed with HTTP $code")
                    }
                } finally {
                    conn?.disconnect()
                }
            }
            Unit
        }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        retryOnNetworkError {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl$remotePath")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    setRequestProperty("Authorization", authHeader())
                    connectTimeout = 15000
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw java.io.IOException("Delete failed with HTTP $code")
                }
            } finally {
                conn?.disconnect()
            }
        }
        Unit
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        retryOnNetworkError {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$baseUrl$remotePath")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "MKCOL"
                    setRequestProperty("Authorization", authHeader())
                    connectTimeout = 15000
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw java.io.IOException("Create directory failed with HTTP $code")
                }
            } finally {
                conn?.disconnect()
            }
        }
        Unit
    }

    private fun authHeader(): String {
        cachedAuthHeader?.let { return it }
        // Use original credentials (connection fields may have been cleared after first connect)
        val credentials = "$originalUsername:$originalAuthToken"
        return "Basic ${Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)}"
    }

    /** Parse WebDAV multistatus XML response into CloudFile list */
    private fun parseMultiStatus(xml: String, requestPath: String): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var href = ""
            var isDirectory = false
            var contentLength = 0L
            var lastModified = 0L
            var contentType = ""
            var inResponse = false

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "response", "d:response", "D:response" -> {
                            inResponse = true
                            href = ""
                            isDirectory = false
                            contentLength = 0L
                            lastModified = 0L
                            contentType = ""
                        }
                        "href", "d:href", "D:href" -> if (inResponse) {
                            href = parser.nextText().trim()
                        }
                        "collection", "d:collection", "D:collection" -> isDirectory = true
                        "getcontentlength", "d:getcontentlength", "D:getcontentlength" -> {
                            contentLength = parser.nextText().trim().toLongOrNull() ?: 0L
                        }
                        "getcontenttype", "d:getcontenttype", "D:getcontenttype" -> {
                            contentType = parser.nextText().trim()
                        }
                        "getlastmodified", "d:getlastmodified", "D:getlastmodified" -> {
                            lastModified = parseHttpDate(parser.nextText().trim())
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "response", "d:response", "D:response" -> {
                            if (inResponse && href.isNotEmpty()) {
                                // Skip the directory itself (first entry)
                                val hrefPath = URL(if (href.startsWith("http")) href else "$baseUrl$href").path
                                val normalRequest = java.net.URLDecoder.decode(requestPath.trimEnd('/'), "UTF-8")
                                val normalHref = java.net.URLDecoder.decode(hrefPath.trimEnd('/'), "UTF-8")
                                if (normalHref != normalRequest) {
                                    val name = hrefPath.trimEnd('/').substringAfterLast('/')
                                    if (name.isNotEmpty()) {
                                        files.add(CloudFile(
                                            name = java.net.URLDecoder.decode(name, "UTF-8"),
                                            remotePath = hrefPath,
                                            isDirectory = isDirectory,
                                            size = contentLength,
                                            lastModified = lastModified,
                                            mimeType = contentType
                                        ))
                                    }
                                }
                            }
                            inResponse = false
                        }
                    }
                }
                parser.next()
            }
        } catch (_: Exception) {
            // Parse errors — return what we have
        }
        return files
    }

    /** Parse HTTP date format (RFC 2822 / RFC 1123) to epoch millis */
    private fun parseHttpDate(dateStr: String): Long {
        return try {
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US)
            format.parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:getcontenttype/>
  </d:prop>
</d:propfind>"""
    }
}
