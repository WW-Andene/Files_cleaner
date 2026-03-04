package com.filecleaner.app.data.cloud

import com.filecleaner.app.utils.retryOnNetworkError
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Vector

/**
 * SFTP cloud provider using JSch library.
 * Connects to remote servers via SSH/SFTP protocol.
 */
class SftpProvider(private var connection: CloudConnection, private val context: android.content.Context) : CloudProvider {

    override val displayName: String = connection.displayName
    override val type: ProviderType = ProviderType.SFTP

    @Volatile
    private var session: Session? = null
    @Volatile
    private var channel: ChannelSftp? = null

    /** Cached credential for reconnection after credential is cleared from connection */
    private var cachedAuthToken: String = connection.authToken

    private val lock = Any()

    override val isConnected: Boolean
        get() = session?.isConnected == true && channel?.isConnected == true

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try {
                retryOnNetworkError {
                    val jsch = JSch()
                    val authToken = cachedAuthToken
                    // If authToken contains a private key path, use key-based auth
                    if (authToken.isNotEmpty() && authToken.startsWith("/")) {
                        jsch.addIdentity(authToken)
                    }

                    val s = jsch.getSession(connection.username, connection.host, connection.port)
                    // If authToken is not a path, treat as password
                    if (authToken.isNotEmpty() && !authToken.startsWith("/")) {
                        s.setPassword(authToken)
                    }
                    // TOFU (Trust On First Use): persist host keys, reject changed keys
                    val knownHostsFile = File(context.filesDir, "sftp_known_hosts")
                    if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
                    jsch.setKnownHosts(knownHostsFile.absolutePath)
                    s.setConfig("StrictHostKeyChecking", "ask")
                    s.userInfo = object : UserInfo {
                        override fun getPassphrase(): String? = null
                        override fun getPassword(): String? = null
                        override fun promptPassword(message: String?): Boolean = false
                        override fun promptPassphrase(message: String?): Boolean = false
                        override fun promptYesNo(message: String?): Boolean {
                            // TOFU: accept new keys, reject changed keys (potential MITM)
                            return message?.contains("has changed", ignoreCase = true) == false
                        }
                        override fun showMessage(message: String?) {}
                    }
                    s.connect(15000)
                    // H4-01: Set read timeout (30s) on the underlying socket for data transfers
                    s.setTimeout(30000)

                    val ch = s.openChannel("sftp") as ChannelSftp
                    ch.connect(10000)

                    session = s
                    channel = ch
                }
                // F-C3-02: Drop credential reference after auth completes
                connection = connection.copy(authToken = "")
                true
            } catch (e: Exception) {
                try { channel?.disconnect() } catch (_: Exception) {}
                try { session?.disconnect() } catch (_: Exception) {}
                channel = null
                session = null
                throw e
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            try { channel?.disconnect() } catch (_: Exception) {}
            try { session?.disconnect() } catch (_: Exception) {}
            channel = null
            session = null
        }
        Unit
    }

    override suspend fun listFiles(remotePath: String): List<CloudFile> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ch = channel ?: return@synchronized emptyList()
            try {
                retryOnNetworkError {
                    @Suppress("UNCHECKED_CAST")
                    val entries = ch.ls(remotePath) as Vector<ChannelSftp.LsEntry>
                    entries
                        .filter { it.filename != "." && it.filename != ".." }
                        .map { entry ->
                            val attrs = entry.attrs
                            CloudFile(
                                name = entry.filename,
                                remotePath = if (remotePath.endsWith("/")) "$remotePath${entry.filename}"
                                else "$remotePath/${entry.filename}",
                                isDirectory = attrs.isDir,
                                size = attrs.size,
                                lastModified = attrs.mTime.toLong() * 1000L,
                                mimeType = ""
                            )
                        }
                        .sortedWith(compareBy<CloudFile> { !it.isDirectory }.thenBy { it.name.lowercase() })
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun download(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ch = channel ?: throw IllegalStateException("Not connected")
            retryOnNetworkError {
                ch.get(remotePath, output)
            }
        }
        Unit
    }

    override suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val ch = channel ?: throw IllegalStateException("Not connected")
                val fullPath = if (remotePath.endsWith("/")) "$remotePath$fileName"
                else "$remotePath/$fileName"
                retryOnNetworkError {
                    ch.put(input, fullPath)
                }
            }
            Unit
        }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ch = channel ?: throw IllegalStateException("Not connected")
            try {
                ch.rm(remotePath)
            } catch (rmEx: Exception) {
                // Might be a directory, try rmdir; if that also fails, throw the original
                try {
                    ch.rmdir(remotePath)
                } catch (_: Exception) {
                    throw rmEx
                }
            }
        }
        Unit
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val ch = channel ?: throw IllegalStateException("Not connected")
            ch.mkdir(remotePath)
        }
        Unit
    }
}
