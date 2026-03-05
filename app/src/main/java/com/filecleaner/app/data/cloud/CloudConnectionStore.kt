package com.filecleaner.app.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists cloud connection configurations in SharedPreferences.
 * Stores connections as a JSON array string.
 */
object CloudConnectionStore {

    private const val PREFS_NAME = "cloud_connections"
    private const val KEY_CONNECTIONS = "connections"

    @Volatile
    private var appContext: Context? = null

    private val prefs: SharedPreferences by lazy {
        val ctx = appContext ?: throw IllegalStateException(
            "CloudConnectionStore.init(context) must be called first"
        )
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        try {
            val encPrefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                ctx,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // Migrate from old plaintext prefs if needed
            val oldPrefs = ctx.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
            val oldJson = oldPrefs.getString(KEY_CONNECTIONS, null)
            if (oldJson != null && encPrefs.getString(KEY_CONNECTIONS, null) == null) {
                encPrefs.edit().putString(KEY_CONNECTIONS, oldJson).apply()
                oldPrefs.edit().clear().apply()
            }
            encPrefs
        } catch (_: Exception) {
            // Fallback to regular prefs if encryption fails (e.g., device lacks secure hardware)
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        // Migrate old plaintext prefs to the intermediate file for encrypted migration.
        // Use a migration-complete flag to avoid re-running after encrypted prefs exist
        // (which would read encrypted bytes as plaintext and destroy stored connections).
        val migrationPrefs = context.applicationContext
            .getSharedPreferences("${PREFS_NAME}_migration", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean("done", false)) return

        val oldPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val oldData = oldPrefs.getString(KEY_CONNECTIONS, null)
        if (oldData != null) {
            context.applicationContext.getSharedPreferences("${PREFS_NAME}_plain", Context.MODE_PRIVATE)
                .edit().putString(KEY_CONNECTIONS, oldData).apply()
            oldPrefs.edit().clear().apply()
        }
        migrationPrefs.edit().putBoolean("done", true).apply()
    }

    fun getConnections(): List<CloudConnection> {
        val json = prefs.getString(KEY_CONNECTIONS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                CloudConnection(
                    id = obj.getString("id"),
                    type = ProviderType.valueOf(obj.getString("type")),
                    displayName = obj.getString("displayName"),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 0),
                    username = obj.optString("username", ""),
                    path = obj.optString("path", "/"),
                    authToken = obj.optString("authToken", "")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveConnection(connection: CloudConnection) {
        synchronized(this) {
            val connections = getConnections().toMutableList()
            connections.removeAll { it.id == connection.id }
            connections.add(connection)
            saveAll(connections)
        }
    }

    fun removeConnection(id: String) {
        synchronized(this) {
            val connections = getConnections().toMutableList()
            connections.removeAll { it.id == id }
            saveAll(connections)
        }
    }

    private fun saveAll(connections: List<CloudConnection>) {
        val array = JSONArray()
        for (conn in connections) {
            array.put(JSONObject().apply {
                put("id", conn.id)
                put("type", conn.type.name)
                put("displayName", conn.displayName)
                put("host", conn.host)
                put("port", conn.port)
                put("username", conn.username)
                put("path", conn.path)
                put("authToken", conn.authToken)
            })
        }
        prefs.edit().putString(KEY_CONNECTIONS, array.toString()).apply()
    }
}
