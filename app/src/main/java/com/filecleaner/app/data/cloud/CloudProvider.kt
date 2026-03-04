package com.filecleaner.app.data.cloud

import java.io.InputStream
import java.io.OutputStream

/**
 * Abstract interface for cloud/remote storage providers.
 * Each provider (Google Drive, SFTP, WebDAV) implements this interface
 * to provide a uniform API for browsing, uploading, and downloading files.
 */
interface CloudProvider {

    /** Human-readable provider name (e.g., "Google Drive", "SFTP") */
    val displayName: String

    /** Unique type identifier */
    val type: ProviderType

    /** Whether this provider is currently connected/authenticated */
    val isConnected: Boolean

    /** Connect/authenticate to the provider. Returns true on success. */
    suspend fun connect(): Boolean

    /** Disconnect from the provider. */
    suspend fun disconnect()

    /** List files/folders at the given remote path. Root is "/". */
    suspend fun listFiles(remotePath: String): List<CloudFile>

    /** Download a file from the remote path to the given output stream. */
    suspend fun download(remotePath: String, output: OutputStream)

    /** Upload a file from the input stream to the remote path. */
    suspend fun upload(remotePath: String, input: InputStream, fileName: String, mimeType: String)

    /** Delete a file/folder at the remote path. */
    suspend fun delete(remotePath: String)

    /** Create a directory at the remote path. */
    suspend fun createDirectory(remotePath: String)
}

enum class ProviderType {
    GOOGLE_DRIVE,
    SFTP,
    WEBDAV
}

/**
 * Represents a file or directory on a cloud/remote provider.
 */
data class CloudFile(
    val name: String,
    val remotePath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = ""
)

/**
 * Configuration for a cloud connection, stored in SharedPreferences as JSON.
 */
data class CloudConnection(
    val id: String,
    val type: ProviderType,
    val displayName: String,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val path: String = "/",
    val authToken: String = ""
) {
    companion object {
        fun googleDrive(displayName: String, authToken: String) = CloudConnection(
            id = "gdrive_${System.currentTimeMillis()}",
            type = ProviderType.GOOGLE_DRIVE,
            displayName = displayName,
            authToken = authToken
        )

        fun sftp(displayName: String, host: String, port: Int = 22, username: String) = CloudConnection(
            id = "sftp_${System.currentTimeMillis()}",
            type = ProviderType.SFTP,
            displayName = displayName,
            host = host,
            port = port,
            username = username
        )

        fun webdav(displayName: String, host: String, username: String, authToken: String) = CloudConnection(
            id = "webdav_${System.currentTimeMillis()}",
            type = ProviderType.WEBDAV,
            displayName = displayName,
            host = host,
            username = username,
            authToken = authToken
        )
    }
}
