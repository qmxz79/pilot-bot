package com.qmxz.pilotbot.map

import android.os.Bundle
import android.view.ViewGroup
import com.qmxz.pilotbot.config.MapProvider
import com.qmxz.pilotbot.search.PlaceResult

/**
 * Coordinate data structure independent of specific map SDKs.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double,
) {
    val latitude: Double get() = lat
    val longitude: Double get() = lng
}

/**
 * Summary of a calculated navigation route.
 */
data class RouteSummary(
    val totalDistanceMeters: Long,
    val totalDurationSeconds: Long,
    val routeName: String,
    val polylinePoints: List<GeoPoint> = emptyList(),
)

/**
 * Universal map and navigation engine interface.
 * Abstracts over different underlying mapping providers (e.g. AMap for domestic China,
 * Google Maps for global / overseas navigation).
 */
interface MapEngine {
    val provider: MapProvider

    /**
     * Initializes the map engine UI inside the specified view container.
     */
    fun init(container: ViewGroup, savedInstanceState: Bundle?)

    /**
     * Updates the current user coordinate on the map.
     */
    fun updateLocation(lat: Double, lng: Double)

    /**
     * Searches for places / POIs by keyword with optional city constraint.
     */
    fun searchPlaces(keyword: String, city: String?, callback: (Result<List<PlaceResult>>) -> Unit)

    /**
     * Calculates driving route between [start] and [dest] coordinates.
     */
    fun calculateRoute(start: GeoPoint, dest: GeoPoint, callback: (Result<RouteSummary>) -> Unit)

    /**
     * Starts turn-by-turn navigation on the active route.
     */
    fun startNavigation()

    /**
     * Stops active navigation.
     */
    fun stopNavigation()

    /**
     * Lifecycle forwarders for activity/fragment lifecycle.
     */
    fun onResume()
    fun onPause()
    fun onDestroy()
}
