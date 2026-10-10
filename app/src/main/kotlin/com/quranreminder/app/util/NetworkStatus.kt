package com.quranreminder.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.quranreminder.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * Fast "can we reach our API right now?" check, used before any network-only feature
 * (AI chat, recitation, language download) so offline users get an immediate,
 * clear message instead of waiting for long HTTP timeouts.
 */
object NetworkStatus {
    private const val PROBE_TIMEOUT_MS = 3_000

    suspend fun isServerReachable(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (hasNoActiveNetwork(context)) return@withContext false
        runCatching {
            val uri = URI(BuildConfig.SERVER_BASE_URL)
            val host = uri.host ?: return@runCatching false
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme.equals("https", ignoreCase = true) -> 443
                else -> 80
            }
            Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) }
            true
        }.getOrDefault(false)
    }

    /**
     * True only when the system positively reports no internet-capable network.
     * Requires ACCESS_NETWORK_STATE; without it we can't tell and fall through to the probe.
     */
    private fun hasNoActiveNetwork(context: Context): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        return runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }
}
