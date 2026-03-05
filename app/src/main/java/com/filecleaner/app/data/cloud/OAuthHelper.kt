package com.filecleaner.app.data.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * Handles OAuth 2.0 authorization code flow with PKCE for cloud providers.
 * Uses Custom Chrome Tabs for the browser-based authorization step.
 *
 * Users configure their own OAuth app credentials (client ID / client secret)
 * via the connection setup dialog. Credentials are stored in SharedPreferences.
 *
 * Flow:
 * 1. User clicks "Sign in with OAuth" in the setup dialog
 * 2. App opens Custom Chrome Tab with the authorization URL (includes PKCE challenge)
 * 3. User authorizes in the browser
 * 4. Browser redirects to filecleaner://oauth/callback?code=...
 * 5. App exchanges the authorization code for an access token
 * 6. Token is filled into the setup dialog's token field
 */
object OAuthHelper {

    // Google Drive OAuth 2.0 endpoints
    private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val GOOGLE_SCOPE = "https://www.googleapis.com/auth/drive"

    // GitHub OAuth endpoints
    private const val GITHUB_AUTH_URL = "https://github.com/login/oauth/authorize"
    private const val GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val GITHUB_SCOPE = "repo read:user"

    // Redirect URI scheme (deep link)
    const val REDIRECT_URI = "filecleaner://oauth/callback"

    // PKCE state stored during flow
    private var pendingCodeVerifier: String? = null
    private var pendingProvider: ProviderType? = null

    data class OAuthConfig(
        val clientId: String,
        val clientSecret: String
    )

    data class OAuthTokenResult(
        val accessToken: String = "",
        val refreshToken: String = "",
        val error: String = ""
    ) {
        val isSuccess: Boolean get() = accessToken.isNotEmpty() && error.isEmpty()
    }

    /**
     * Get stored OAuth credentials from SharedPreferences.
     * Users configure their own OAuth app credentials in the setup dialog.
     */
    fun getConfig(context: Context, provider: ProviderType): OAuthConfig? {
        val prefs = context.getSharedPreferences("oauth_config", Context.MODE_PRIVATE)
        val prefix = provider.name.lowercase()
        val clientId = prefs.getString("${prefix}_client_id", null)
        val clientSecret = prefs.getString("${prefix}_client_secret", null)
        if (clientId.isNullOrBlank()) return null
        return OAuthConfig(clientId, clientSecret ?: "")
    }

    fun saveConfig(context: Context, provider: ProviderType, config: OAuthConfig) {
        val prefs = context.getSharedPreferences("oauth_config", Context.MODE_PRIVATE)
        val prefix = provider.name.lowercase()
        prefs.edit()
            .putString("${prefix}_client_id", config.clientId)
            .putString("${prefix}_client_secret", config.clientSecret)
            .apply()
    }

    /**
     * Launch the OAuth authorization flow in a Custom Chrome Tab.
     * After the user authorizes, they'll be redirected back to the app
     * via the REDIRECT_URI deep link.
     */
    fun launchAuthFlow(context: Context, provider: ProviderType, clientId: String) {
        pendingProvider = provider

        val authUrl = when (provider) {
            ProviderType.GOOGLE_DRIVE -> {
                // Generate PKCE code verifier and challenge
                val codeVerifier = generateCodeVerifier()
                pendingCodeVerifier = codeVerifier
                val codeChallenge = generateCodeChallenge(codeVerifier)

                "$GOOGLE_AUTH_URL?" +
                    "client_id=${enc(clientId)}" +
                    "&redirect_uri=${enc(REDIRECT_URI)}" +
                    "&response_type=code" +
                    "&scope=${enc(GOOGLE_SCOPE)}" +
                    "&code_challenge=${enc(codeChallenge)}" +
                    "&code_challenge_method=S256" +
                    "&access_type=offline" +
                    "&prompt=consent"
            }
            ProviderType.GITHUB -> {
                // GitHub uses state parameter for CSRF protection
                val state = generateState()
                pendingCodeVerifier = state

                "$GITHUB_AUTH_URL?" +
                    "client_id=${enc(clientId)}" +
                    "&redirect_uri=${enc(REDIRECT_URI)}" +
                    "&scope=${enc(GITHUB_SCOPE)}" +
                    "&state=${enc(state)}"
            }
            else -> return
        }

        launchAuthBrowser(context, authUrl)
    }

    /**
     * Build a Google OAuth authorization URL (for use with manual launch).
     * This variant reads stored config and uses PKCE.
     */
    fun buildGoogleAuthUrl(context: Context): String? {
        val config = getConfig(context, ProviderType.GOOGLE_DRIVE) ?: return null
        pendingProvider = ProviderType.GOOGLE_DRIVE

        val codeVerifier = generateCodeVerifier()
        pendingCodeVerifier = codeVerifier
        val codeChallenge = generateCodeChallenge(codeVerifier)

        return "$GOOGLE_AUTH_URL?" +
            "client_id=${enc(config.clientId)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&response_type=code" +
            "&scope=${enc(GOOGLE_SCOPE)}" +
            "&code_challenge=${enc(codeChallenge)}" +
            "&code_challenge_method=S256" +
            "&access_type=offline" +
            "&prompt=consent"
    }

    /**
     * Build a GitHub OAuth authorization URL (for use with manual launch).
     * This variant reads stored config.
     */
    fun buildGitHubAuthUrl(context: Context): String? {
        val config = getConfig(context, ProviderType.GITHUB) ?: return null
        pendingProvider = ProviderType.GITHUB

        val state = generateState()
        pendingCodeVerifier = state

        return "$GITHUB_AUTH_URL?" +
            "client_id=${enc(config.clientId)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&scope=${enc(GITHUB_SCOPE)}" +
            "&state=${enc(state)}"
    }

    /**
     * Exchange the authorization code for an access token.
     * Called when the app receives the OAuth redirect callback.
     */
    suspend fun exchangeCodeForToken(
        context: Context,
        code: String,
        provider: ProviderType? = null
    ): OAuthTokenResult = withContext(Dispatchers.IO) {
        val activeProvider = provider ?: pendingProvider
            ?: return@withContext OAuthTokenResult(error = "No pending OAuth provider")
        val config = getConfig(context, activeProvider)
            ?: return@withContext OAuthTokenResult(error = "OAuth not configured")

        try {
            when (activeProvider) {
                ProviderType.GOOGLE_DRIVE -> exchangeGoogleCode(code, config)
                ProviderType.GITHUB -> exchangeGitHubCode(code, config)
                else -> OAuthTokenResult(error = "Unsupported provider")
            }
        } catch (e: Exception) {
            OAuthTokenResult(error = e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            pendingCodeVerifier = null
            pendingProvider = null
        }
    }

    /**
     * Exchange a Google authorization code for an access token.
     * Uses PKCE code verifier for secure exchange without exposing client secret.
     */
    suspend fun exchangeGoogleCode(context: Context, code: String): OAuthTokenResult =
        withContext(Dispatchers.IO) {
            val config = getConfig(context, ProviderType.GOOGLE_DRIVE)
                ?: return@withContext OAuthTokenResult(error = "OAuth not configured")
            exchangeGoogleCode(code, config)
        }

    /**
     * Exchange a GitHub authorization code for an access token.
     */
    suspend fun exchangeGitHubCode(context: Context, code: String): OAuthTokenResult =
        withContext(Dispatchers.IO) {
            val config = getConfig(context, ProviderType.GITHUB)
                ?: return@withContext OAuthTokenResult(error = "OAuth not configured")
            exchangeGitHubCode(code, config)
        }

    fun getPendingProvider(): ProviderType? = pendingProvider

    /**
     * Launch a Custom Chrome Tab for OAuth authorization.
     * Falls back to regular browser if Custom Tabs not available.
     */
    fun launchAuthBrowser(context: Context, authUrl: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        } catch (_: Exception) {
            // Fallback to regular browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            context.startActivity(intent)
        }
    }

    /**
     * Parse the OAuth callback URI for the authorization code.
     * Supports both the new filecleaner:// scheme and the legacy com.filecleaner.app:// scheme.
     */
    fun parseCallbackCode(uri: Uri?): String? {
        if (uri == null) return null
        val scheme = uri.scheme ?: return null
        if (scheme != "filecleaner" && scheme != "com.filecleaner.app") return null
        return uri.getQueryParameter("code")
    }

    // ── Private helpers ─────────────────────────────────────────────

    private fun exchangeGoogleCode(code: String, config: OAuthConfig): OAuthTokenResult {
        val codeVerifier = pendingCodeVerifier
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(GOOGLE_TOKEN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            val params = buildString {
                append("code=${enc(code)}")
                append("&client_id=${enc(config.clientId)}")
                if (config.clientSecret.isNotBlank()) {
                    append("&client_secret=${enc(config.clientSecret)}")
                }
                append("&redirect_uri=${enc(REDIRECT_URI)}")
                append("&grant_type=authorization_code")
                if (codeVerifier != null) {
                    append("&code_verifier=${enc(codeVerifier)}")
                }
            }

            conn.outputStream.use { it.write(params.toByteArray()) }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val token = json.optString("access_token", "")
                val refresh = json.optString("refresh_token", "")
                if (token.isEmpty()) {
                    return OAuthTokenResult(error = json.optString("error_description", "No token"))
                }
                return OAuthTokenResult(accessToken = token, refreshToken = refresh)
            }
            val errorBody = try {
                conn.errorStream?.bufferedReader()?.readText() ?: "HTTP ${conn.responseCode}"
            } catch (_: Exception) {
                "HTTP ${conn.responseCode}"
            }
            return OAuthTokenResult(error = "HTTP ${conn.responseCode}: $errorBody")
        } finally {
            conn?.disconnect()
        }
    }

    private fun exchangeGitHubCode(code: String, config: OAuthConfig): OAuthTokenResult {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(GITHUB_TOKEN_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            val params = "client_id=${enc(config.clientId)}" +
                "&client_secret=${enc(config.clientSecret)}" +
                "&code=${enc(code)}" +
                "&redirect_uri=${enc(REDIRECT_URI)}"

            conn.outputStream.use { it.write(params.toByteArray()) }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val token = json.optString("access_token", "")
                if (token.isEmpty()) {
                    return OAuthTokenResult(
                        error = json.optString("error_description", "No token")
                    )
                }
                return OAuthTokenResult(accessToken = token)
            }
            return OAuthTokenResult(error = "HTTP ${conn.responseCode}")
        } finally {
            conn?.disconnect()
        }
    }

    // ── PKCE helpers ────────────────────────────────────────────────

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
