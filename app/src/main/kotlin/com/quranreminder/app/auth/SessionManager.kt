package com.quranreminder.app.auth

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Holds the signed-in user's session on this device and talks only to our API. */
class SessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank()

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

    var contentReadyLanguage: String?
        get() = prefs.getString(KEY_CONTENT_READY_LANGUAGE, null)
        private set(value) { prefs.edit().putString(KEY_CONTENT_READY_LANGUAGE, value).apply() }

    fun markContentReady(language: String) {
        contentReadyLanguage = language
    }

    suspend fun updateLanguage(language: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext Result.failure(IllegalStateException("Sessiya topilmadi"))
        runCatching {
            val currentName = prefs.getString(KEY_NAME, "") ?: ""
            val response = request(
                path = "/api/v1/profile",
                method = "PUT",
                body = mapOf("name" to currentName, "language" to language),
                token = token
            )
            val user = gson.fromJson(response, AuthUser::class.java)
            saveUser(user)
            user
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private suspend fun authenticate(path: String, body: Any): Result<AuthUser> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(path, "POST", body)
            val auth = gson.fromJson(response, AuthResponse::class.java)
            require(!auth.accessToken.isNullOrBlank() && auth.user != null) { "Server noto‘g‘ri javob qaytardi" }
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, auth.accessToken)
                .putString(KEY_REFRESH_TOKEN, auth.refreshToken)
                .apply()
            saveUser(auth.user)
            auth.user
        }
    }

    private fun request(path: String, method: String, body: Any, token: String? = null): String {
        val connection = (URL("${BuildConfig.SERVER_BASE_URL}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            doOutput = true
        }
        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(gson.toJson(body)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(connection.responseCode in 200..299) { apiError(response, connection.responseCode) }
        return response
    }

    private fun apiError(response: String, code: Int): String = runCatching {
        gson.fromJson(response, ApiError::class.java).error
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Server xatosi ($code)"

    private fun saveUser(user: AuthUser) {
        prefs.edit()
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_LANGUAGE, user.language)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "user_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_LANGUAGE = "language"
        const val KEY_CONTENT_READY_LANGUAGE = "content_ready_language"
    }
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
