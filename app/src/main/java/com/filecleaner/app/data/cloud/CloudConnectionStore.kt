package com.filecleaner.app.data.cloud

import android.content.Context
import android.content.SharedPreferences
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
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        appContext = context.applicationContext
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
        val connections = getConnections().toMutableList()
        connections.removeAll { it.id == connection.id }
        connections.add(connection)
        saveAll(connections)
    }

    fun removeConnection(id: String) {
        val connections = getConnections().toMutableList()
        connections.removeAll { it.id == id }
        saveAll(connections)
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
