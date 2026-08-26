package com.qmxz.pilotbot.enroute

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Native Android OS GPS & Network location provider.
 *
 * Directly interfaces with device GPS chipsets (android.location.LocationManager) without
 * calling any third-party SDK servers (such as AMap or Baidu), consuming 0 tokens and 0 cloud API quota.
 * Ideal for global usage (e.g. Malaysia, Europe, Americas).
 */
class SystemGpsDataSource(private val context: Context) : LocationListener {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isRunning = false

    @Volatile
    private var latestLoc: Location? = null

    @Volatile
    private var onLocationCallback: ((Double, Double, String?) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (lat: Double, lng: Double, address: String?) -> Unit) {
        this.onLocationCallback = onLocation
        if (isRunning) return
        isRunning = true

        val lm = locationManager ?: return

        try {
            // Check last known location
            val gpsLast = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLast = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = when {
                gpsLast != null && netLast != null -> if (gpsLast.time > netLast.time) gpsLast else netLast
                gpsLast != null -> gpsLast
                else -> netLast
            }
            if (best != null) {
                latestLoc = best
                mainHandler.post {
                    onLocationCallback?.invoke(best.latitude, best.longitude, "全球 GPS 硬件定位")
                }
            }

            // Register continuous updates with minTime = 3000ms, minDistance = 2m
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 2f, this, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 5f, this, Looper.getMainLooper())
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

    override fun onLocationChanged(location: Location) {
        latestLoc = location
        onLocationCallback?.invoke(location.latitude, location.longitude, "全球 GPS 硬件定位")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}
}
