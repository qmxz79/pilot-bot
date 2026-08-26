package com.qmxz.pilotbot.enroute

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Native Android OS GPS & Network location provider with offline/system reverse geocoding.
 *
 * Directly interfaces with device GPS chipsets (android.location.LocationManager) without
 * calling third-party proprietary location servers, consuming 0 tokens and 0 cloud API quota.
 * Automatically resolves coordinates into human-readable street & city names via Android system Geocoder.
 */
class SystemGpsDataSource(private val context: Context) : LocationListener {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isRunning = false

    @Volatile
    private var latestLoc: Location? = null

    @Volatile
    private var latestAddr: String? = null

    @Volatile
    private var onLocationCallback: ((Double, Double, String?) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (lat: Double, lng: Double, address: String?) -> Unit) {
        this.onLocationCallback = onLocation
        if (isRunning) return
        isRunning = true

        val lm = locationManager ?: return

        try {
            // 1. Immediately grab best cached location
            val gpsLast = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLast = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = when {
                gpsLast != null && netLast != null -> if (gpsLast.time > netLast.time) gpsLast else netLast
                gpsLast != null -> gpsLast
                else -> netLast
            }
            if (best != null) {
                latestLoc = best
                resolveAddressAsync(best.latitude, best.longitude) { addr ->
                    latestAddr = addr
                    mainHandler.post {
                        onLocationCallback?.invoke(best.latitude, best.longitude, addr)
                    }
                }
            }

            // 2. Request continuous location updates
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, this, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 5f, this, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            Log.w("SystemGpsDataSource", "Location permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.w("SystemGpsDataSource", "Failed to start GPS updates: ${e.message}")
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
        onLocationCallback = null
    }

    fun latestLocation(): Location? = latestLoc

    fun latestAddress(): String? = latestAddr

    override fun onLocationChanged(location: Location) {
        latestLoc = location
        resolveAddressAsync(location.latitude, location.longitude) { addr ->
            latestAddr = addr
            mainHandler.post {
                onLocationCallback?.invoke(location.latitude, location.longitude, addr)
            }
        }
    }

    private fun resolveAddressAsync(lat: Double, lng: Double, callback: (String) -> Unit) {
        scope.launch {
            val addr = try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        formatAddress(addresses[0])
                    } else {
                        "GPS (经度: ${String.format("%.4f", lng)}, 纬度: ${String.format("%.4f", lat)})"
                    }
                } else {
                    "GPS (经度: ${String.format("%.4f", lng)}, 纬度: ${String.format("%.4f", lat)})"
                }
            } catch (e: Exception) {
                "GPS (经度: ${String.format("%.4f", lng)}, 纬度: ${String.format("%.4f", lat)})"
            }
            callback(addr)
        }
    }

    private fun formatAddress(a: Address): String {
        val thoroughfare = a.thoroughfare ?: a.subThoroughfare ?: a.featureName
        val subLocality = a.subLocality ?: a.locality ?: a.adminArea
        val country = a.countryName ?: ""
        return buildList {
            if (!thoroughfare.isNullOrBlank()) add(thoroughfare)
            if (!subLocality.isNullOrBlank() && subLocality != thoroughfare) add(subLocality)
            if (country.isNotBlank() && !subLocality.orEmpty().contains(country)) add(country)
        }.joinToString(", ").ifBlank { a.getAddressLine(0) ?: "当前定位地点" }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}
