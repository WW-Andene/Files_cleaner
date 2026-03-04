package com.filecleaner.app.data.cloud

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Vector

/**
 * SFTP cloud provider using JSch library.
 * Connects to remote servers via SSH/SFTP protocol.
 */
class SftpProvider(private val connection: CloudConnection) : CloudProvider {

    override val displayName: String = connection.displayName
    override val type: ProviderType = ProviderType.SFTP

    @Volatile
    private var session: Session? = null
    @Volatile
    private var channel: ChannelSftp? = null

    override val isConnected: Boolean
        get() = session?.isConnected == true && channel?.isConnected == true

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            // If authToken contains a private key path, use key-based auth
            if (connection.authToken.isNotEmpty() && connection.authToken.startsWith("/")) {
                jsch.addIdentity(connection.authToken)
            }

            val s = jsch.getSession(connection.username, connection.host, connection.port)
            // If authToken is not a path, treat as password
            if (connection.authToken.isNotEmpty() && !connection.authToken.startsWith("/")) {
                s.setPassword(connection.authToken)
            }
            s.setConfig("StrictHostKeyChecking", "no")
            s.connect(15000)

            val ch = s.openChannel("sftp") as ChannelSftp
            ch.connect(10000)

            session = s
            channel = ch
            true
        } catch (e: Exception) {
            disconnect()
            false
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        try { channel?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        channel = null
        session = null
        Unit
    }

    override suspend fun listFiles(remotePath: String): List<CloudFile> = withContext(Dispatchers.IO) {
        val ch = channel ?: return@withContext emptyList()
        try {
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
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun download(remotePath: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val ch = channel ?: throw IllegalStateException("Not connected")
        ch.get(remotePath, output)
        Unit
    }

    override suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String) =
        withContext(Dispatchers.IO) {
            val ch = channel ?: throw IllegalStateException("Not connected")
            val fullPath = if (remotePath.endsWith("/")) "$remotePath$fileName"
            else "$remotePath/$fileName"
            ch.put(input, fullPath)
            Unit
        }

    override suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
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
        Unit
    }

    override suspend fun createDirectory(remotePath: String) = withContext(Dispatchers.IO) {
        val ch = channel ?: throw IllegalStateException("Not connected")
        ch.mkdir(remotePath)
        Unit
    }
}
