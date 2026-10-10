package com.quranreminder.app.auth

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.ui.theme.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Holds the signed-in user's session on this device and talks only to our API.
 *
 * Offline-first contract: once signed in, the session is only ever dropped by an
 * explicit [signOut] or by a *definitive* 401 from `/auth/refresh`. Network errors,
 * timeouts and 5xx responses never sign the user out.
 */
class SessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    private val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()

    val displayName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""

    suspend fun signIn(email: String, password: String): Result<AuthUser> =
        authenticate("/api/v1/auth/login", AuthRequest(email = email, password = password))

    suspend fun signUp(name: String, email: String, password: String): Result<AuthUser> =
        authenticate("/api/v1/auth/register", AuthRequest(name = name, email = email, password = password))

    suspend fun signInWithGoogle(idToken: String): Result<AuthUser> =
        authenticate("/api/v1/auth/oauth/google", mapOf("id_token" to idToken))

    suspend fun requestPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { request("/api/v1/auth/password-reset/request", "POST", mapOf("email" to email)); Unit }
    }

    suspend fun confirmPasswordReset(token: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            request("/api/v1/auth/password-reset/confirm", "POST", mapOf("token" to token, "password" to newPassword))
            Unit
        }
    }

    /** Language whose Quran content is currently stored in the local database. Survives sign-out. */
    var contentReadyLanguage: String?
        get() = prefs.getString(KEY_CONTENT_READY_LANGUAGE, null)
        private set(value) { prefs.edit().putString(KEY_CONTENT_READY_LANGUAGE, value).apply() }

    fun markContentReady(language: String) {
        contentReadyLanguage = language
    }

    suspend fun updateLanguage(language: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        runCatching {
            val currentName = prefs.getString(KEY_NAME, "") ?: ""
            val response = withAuthorization { token ->
                request(
                    path = "/api/v1/profile",
                    method = "PUT",
                    body = mapOf("name" to currentName, "language" to language),
                    token = token
                )
            }
            val user = gson.fromJson(response, AuthUser::class.java)
            saveUser(user)
            user
        }
    }

    /**
     * Runs an authorized call with a valid access token. If [block] throws
     * [UnauthorizedException] (HTTP 401) the token is refreshed once and the call retried.
     * Throws [SessionException] with [CODE_NO_SESSION] / [CODE_SESSION_EXPIRED] when no
     * usable session exists.
     */
    suspend fun <T> withAuthorization(block: suspend (token: String) -> T): T {
        val token = validAccessToken() ?: throw SessionException(CODE_NO_SESSION)
        return try {
            block(token)
        } catch (unauthorized: UnauthorizedException) {
            when (val refreshed = refresh(failedToken = token)) {
                is RefreshOutcome.Refreshed -> block(refreshed.accessToken)
                RefreshOutcome.Rejected -> throw SessionException(CODE_SESSION_EXPIRED)
                is RefreshOutcome.Failed -> throw refreshed.error
            }
        }
    }

    /**
     * Returns an access token that is not known to be expired, refreshing proactively
     * when the JWT `exp` has passed. If refreshing fails for a network reason the stored
     * token is returned anyway (the server gets the final say); null only when there is
     * no session at all or the refresh token was definitively rejected.
     */
    suspend fun validAccessToken(): String? {
        val token = accessToken
        if (!token.isNullOrBlank() && !isExpired(token)) return token
        if (refreshToken.isNullOrBlank()) return token?.takeIf { it.isNotBlank() }
        return when (val outcome = refresh(failedToken = token)) {
            is RefreshOutcome.Refreshed -> outcome.accessToken
            RefreshOutcome.Rejected -> null
            is RefreshOutcome.Failed -> token?.takeIf { it.isNotBlank() }
        }
    }

    /** Refreshes the token pair. Concurrent callers share a single refresh. */
    suspend fun refresh(failedToken: String?): RefreshOutcome = refreshMutex.withLock {
        // Someone else already refreshed while we waited for the lock.
        val current = accessToken
        if (!current.isNullOrBlank() && current != failedToken && !isExpired(current)) {
            return@withLock RefreshOutcome.Refreshed(current)
        }
        val storedRefresh = refreshToken
        if (storedRefresh.isNullOrBlank()) {
            return@withLock RefreshOutcome.Failed(SessionException(CODE_NO_SESSION))
        }
        withContext(Dispatchers.IO) {
            try {
                val response = request("/api/v1/auth/refresh", "POST", mapOf("refresh_token" to storedRefresh))
                val auth = gson.fromJson(response, AuthResponse::class.java)
                if (auth.accessToken.isNullOrBlank()) {
                    RefreshOutcome.Failed(SessionException(CODE_BAD_RESPONSE))
                } else {
                    prefs.edit()
                        .putString(KEY_ACCESS_TOKEN, auth.accessToken)
                        .putString(KEY_REFRESH_TOKEN, auth.refreshToken ?: storedRefresh)
                        .apply()
                    auth.user?.let { saveUser(it) }
                    RefreshOutcome.Refreshed(auth.accessToken)
                }
            } catch (unauthorized: UnauthorizedException) {
                // Definitive: the refresh token is expired or revoked.
                clearSession()
                _sessionExpired.tryEmit(Unit)
                RefreshOutcome.Rejected
            } catch (error: Exception) {
                RefreshOutcome.Failed(error)
            }
        }
    }

    /**
     * Signs out locally right away and revokes the session on the server best-effort
     * in the background. Downloaded/bundled Quran content state is kept.
     */
    fun signOut() {
        val access = accessToken
        val refresh = refreshToken
        clearSession()
        if (access.isNullOrBlank() && refresh.isNullOrBlank()) return
        backgroundScope.launch {
            runCatching { request("/api/v1/auth/logout", "POST", null, access) }
                .onFailure { error ->
                    // Access token expired: trade the refresh token for a fresh pair and
                    // log that out, so the server revokes every refresh token.
                    if (error is UnauthorizedException && !refresh.isNullOrBlank()) {
                        runCatching {
                            val response = request("/api/v1/auth/refresh", "POST", mapOf("refresh_token" to refresh))
                            val fresh = gson.fromJson(response, AuthResponse::class.java).accessToken
                            if (!fresh.isNullOrBlank()) request("/api/v1/auth/logout", "POST", null, fresh)
                        }
                    }
                }
        }
    }

    private fun clearSession() {
        // The assistant conversation is personal: don't leave it for the next account.
        com.quranreminder.app.tadabbur.ChatHistoryStore(appContext).clear()
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_LANGUAGE)
            .apply()
    }

    private suspend fun authenticate(path: String, body: Any): Result<AuthUser> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(path, "POST", body)
            val auth = gson.fromJson(response, AuthResponse::class.java)
            if (auth.accessToken.isNullOrBlank() || auth.user == null) throw SessionException(CODE_BAD_RESPONSE)
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, auth.accessToken)
                .putString(KEY_REFRESH_TOKEN, auth.refreshToken)
                .apply()
            saveUser(auth.user)
            auth.user
        }
    }

    /**
     * Performs a JSON request. Throws [UnauthorizedException] on 401, [SessionException]
     * with [CODE_NETWORK] on connectivity failures and [CODE_SERVER] (plus the server's
     * own error text when present) on other non-2xx responses.
     */
    private fun request(path: String, method: String, body: Any?, token: String? = null): String {
        val connection = try {
            (URL("${BuildConfig.SERVER_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                doOutput = true
            }.also { conn ->
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(if (body == null) "{}" else gson.toJson(body)) }
            }
        } catch (io: IOException) {
            throw SessionException(CODE_NETWORK, cause = io)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED) throw UnauthorizedException(apiError(response))
            if (code !in 200..299) throw SessionException(CODE_SERVER, serverMessage = apiError(response))
            return response
        } catch (io: IOException) {
            // 401 and HTTP-level errors thrown above are IOExceptions too; only
            // genuine transport failures become "no internet".
            if (io is UnauthorizedException || io is SessionException) throw io
            throw SessionException(CODE_NETWORK, cause = io)
        } finally {
            connection.disconnect()
        }
    }

    private fun apiError(response: String): String? = runCatching {
        gson.fromJson(response, ApiError::class.java).error
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun saveUser(user: AuthUser) {
        prefs.edit()
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_LANGUAGE, user.language)
            .apply()
    }

    sealed interface RefreshOutcome {
        data class Refreshed(val accessToken: String) : RefreshOutcome
        /** The server definitively rejected the refresh token (401); the user is signed out. */
        data object Rejected : RefreshOutcome
        /** Transient failure (offline, timeout, 5xx); the session is kept. */
        data class Failed(val error: Exception) : RefreshOutcome
    }

    companion object {
        const val PREFERENCES = "user_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CONTENT_READY_LANGUAGE = "content_ready_language"

        /** Error codes are AppStrings keys so the UI can localize them. */
        const val CODE_NETWORK = "auth.error.network"
        const val CODE_BAD_RESPONSE = "auth.error.badResponse"
        const val CODE_SERVER = "auth.error.server"
        const val CODE_NO_SESSION = "auth.error.noSession"
        const val CODE_SESSION_EXPIRED = "auth.error.sessionExpired"

        /** Access-token expiry skew: treat tokens that expire within this window as expired. */
        private const val EXPIRY_SKEW_SECONDS = 30L

        private val refreshMutex = Mutex()
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits when a definitive 401 on refresh signed the user out; the UI routes to sign-in. */
        val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

        /** Reads the JWT `exp` claim without verifying it. Unknown format ⇒ not expired. */
        fun isExpired(jwt: String): Boolean = runCatching {
            val payload = jwt.split('.').getOrNull(1) ?: return false
            val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
            val exp = Gson().fromJson(json, JsonObject::class.java).get("exp")?.asLong ?: return false
            exp <= System.currentTimeMillis() / 1000 + EXPIRY_SKEW_SECONDS
        }.getOrDefault(false)
    }
}

/** HTTP 401 from an authorized call. */
class UnauthorizedException(message: String? = null) : IOException(message ?: "unauthorized")

/**
 * A session/network error whose [code] is an AppStrings key. [message] falls back to the
 * Uzbek Latin text so legacy `it.message` call sites still show something readable.
 */
class SessionException(
    val code: String,
    val serverMessage: String? = null,
    cause: Throwable? = null
) : IOException(serverMessage ?: AppStrings.uzLatin[code] ?: code, cause)

/** Localized, user-facing text for an auth failure, or null when there is nothing specific. */
fun Throwable.localizedAuthMessage(strings: Map<String, String>): String? = when (this) {
    is SessionException -> serverMessage ?: strings[code] ?: AppStrings.uzLatin[code]
    is UnauthorizedException -> message?.takeIf { it != "unauthorized" }
    else -> null
}

data class AuthRequest(val name: String = "", val email: String, val password: String)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null
)

data class AuthUser(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val language: String = ""
)

data class ApiError(val error: String = "")
