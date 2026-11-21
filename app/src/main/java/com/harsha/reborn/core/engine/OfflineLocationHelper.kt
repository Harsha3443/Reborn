package com.harsha.reborn.core.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class OfflineCoordinates(
    val latitude: Double,
    val longitude: Double,
)

@Singleton
class OfflineLocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasCoarseLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun getBestLastKnownCoordinates(): OfflineCoordinates? {
        if (!hasCoarseLocationPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        val providers = runCatching { lm.getProviders(true) }.getOrNull().orEmpty()
        var best: Location? = null
        for (provider in providers) {
            val candidate = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || candidate.time > best.time) {
                best = candidate
            }
        }
        return best?.let { OfflineCoordinates(latitude = it.latitude, longitude = it.longitude) }
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val radLat1 = Math.toRadians(lat1)
            val radLat2 = Math.toRadians(lat2)

            val a = sin(dLat / 2).pow(2) +
                cos(radLat1) * cos(radLat2) * sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
