package com.quranreminder.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.quranreminder.app.BuildConfig
import com.quranreminder.app.auth.SessionManager
import com.quranreminder.app.auth.UnauthorizedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** What the profile page shows; mirrors GET /api/v1/profile. */
data class Profile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val language: String = "",
    @SerializedName("birth_year") val birthYear: Int? = null,
    val gender: String? = null,
    @SerializedName("quran_level") val quranLevel: String? = null,
    val goal: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

/**
 * The signed-in user's profile and photo. Cached on the phone so the page and the avatar
 * show offline; the server is the source of truth.
 */
object ProfileRepository {
    private const val PREFS = "profile"
    private const val KEY_JSON = "profile_json"

    private val gson = GsonBuilder().serializeNulls().create()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    /** Bumped whenever the local photo file changes, so images reload. */
    private val _avatarVersion = MutableStateFlow(0)
    val avatarVersion: StateFlow<Int> = _avatarVersion.asStateFlow()

    fun avatarFile(context: Context) = File(context.filesDir, "profile/avatar.jpg")

    /** Loads the cached profile (no network). */
    fun load(context: Context): Profile? {
        _profile.value?.let { return it }
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null)
        val cached = json?.let { runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull() }
        _profile.value = cached
        return cached
    }

    private fun store(context: Context, profile: Profile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, gson.toJson(profile)).apply()
        _profile.value = profile
        SessionManager(context).cacheName(profile.name)
    }

    /** Clears everything on sign-out. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        avatarFile(context).delete()
        _profile.value = null
        _avatarVersion.value++
    }

    suspend fun refresh(context: Context): Result<Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val previous = load(context)
            val text = SessionManager(context).withAuthorization { token ->
                execute(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/profile").get(), token)
            }
            val fresh = gson.fromJson(text, Profile::class.java)
            store(context, fresh)
            syncAvatar(context, previous?.avatarUrl, fresh.avatarUrl)
            fresh
        }
    }

    /** Changes only the given fields; a null value clears that field. */
    suspend fun update(context: Context, fields: Map<String, Any?>): Result<Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val body = gson.toJson(fields).toRequestBody(jsonType)
            val text = SessionManager(context).withAuthorization { token ->
                execute(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/profile").patch(body), token)
            }
            gson.fromJson(text, Profile::class.java).also { store(context, it) }
        }
    }

    /** Square-crops, scales to 512 px and uploads the picked photo. */
    suspend fun uploadAvatar(context: Context, uri: Uri): Result<Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val jpeg = runCatching { prepareAvatar(context, uri) }
                .onFailure { android.util.Log.w("Profile", "could not prepare the photo", it) }
                .getOrThrow()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "avatar.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
                .build()
            val text = SessionManager(context).withAuthorization { token ->
                execute(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/profile/avatar").post(body), token)
            }
            val fresh = gson.fromJson(text, Profile::class.java)
            avatarFile(context).apply { parentFile?.mkdirs() }.writeBytes(jpeg)
            _avatarVersion.value++
            store(context, fresh)
            fresh
        }
    }

    suspend fun removeAvatar(context: Context): Result<Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val text = SessionManager(context).withAuthorization { token ->
                execute(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}/api/v1/profile/avatar").delete(), token)
            }
            avatarFile(context).delete()
            _avatarVersion.value++
            gson.fromJson(text, Profile::class.java).also { store(context, it) }
        }
    }

    /** Downloads the photo when the server's version differs from the cached one. */
    private suspend fun syncAvatar(context: Context, old: String?, new: String?) {
        val file = avatarFile(context)
        if (new == null) {
            if (file.exists()) {
                file.delete()
                _avatarVersion.value++
            }
            return
        }
        if (new == old && file.exists()) return
        val bytes = SessionManager(context).withAuthorization { token ->
            http.newCall(Request.Builder().url("${BuildConfig.SERVER_BASE_URL}$new").header("Authorization", "Bearer $token").build())
                .execute().use { response ->
                    if (response.code == 401) throw UnauthorizedException()
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body?.bytes() ?: throw IOException("empty photo")
                }
        }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        _avatarVersion.value++
    }

    private fun execute(builder: Request.Builder, token: String): String {
        http.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) throw UnauthorizedException()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return text
        }
    }

    private fun prepareAvatar(context: Context, uri: Uri): ByteArray {
        val decoded = decodeScaled(context, uri)
        val side = min(decoded.width, decoded.height)
        val scale = min(1f, TARGET.toFloat() / side)
        val matrix = Matrix().apply { postScale(scale, scale) }
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
        if (square !== decoded) decoded.recycle()
        return ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 88, out)
            square.recycle()
            out.toByteArray()
        }
    }

    /** Decodes the photo near [TARGET] px on its short side, upright. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            // ImageDecoder reads HEIC/HEIF camera photos too and applies the EXIF rotation.
            val source = android.graphics.ImageDecoder.createSource(resolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val f = TARGET.toFloat() / min(w, h)
                if (f < 1f) decoder.setTargetSize(maxOf(1, (w * f).toInt()), maxOf(1, (h * f).toInt()))
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // (With inJustDecodeBounds decodeStream always returns null; only the options are filled.)
        (resolver.openInputStream(uri) ?: throw IOException("cannot open photo")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("unsupported image")
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= TARGET) sample *= 2
        val bitmap = (resolver.openInputStream(uri) ?: throw IOException("cannot open photo")).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("cannot decode photo")
        // Camera photos are stored sideways with an EXIF rotation flag.
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private const val TARGET = 512
}
