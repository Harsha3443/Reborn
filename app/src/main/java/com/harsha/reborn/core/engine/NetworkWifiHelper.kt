package com.harsha.reborn.core.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class WifiStateSnapshot(
    val isWifiConnected: Boolean = false,
    val ssid: String? = null,
)

@Singleton
class NetworkWifiHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getWifiSnapshot(): WifiStateSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return WifiStateSnapshot()

        val isWifi = runCatching {
            val active = cm.activeNetwork ?: return@runCatching false
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)

        if (!isWifi) {
            return WifiStateSnapshot(isWifiConnected = false, ssid = null)
        }

        val ssid = readCurrentSsidIfPermitted()
        return WifiStateSnapshot(isWifiConnected = true, ssid = ssid)
    }

    @Suppress("DEPRECATION")
    private fun readCurrentSsidIfPermitted(): String? {
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) return null

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        val raw = runCatching { wifiManager.connectionInfo?.ssid }.getOrNull().orEmpty()
        return normalizeSsid(raw)
    }

    companion object {
        fun normalizeSsid(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim().removePrefix("\"").removeSuffix("\"").trim()
            if (trimmed.isEmpty() ||
                trimmed.equals("<unknown ssid>", ignoreCase = true) ||
                trimmed.equals("0x", ignoreCase = true)
            ) {
                return null
            }
            return trimmed
        }
    }
}
