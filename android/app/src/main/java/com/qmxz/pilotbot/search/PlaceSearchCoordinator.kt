package com.qmxz.pilotbot.search

import com.qmxz.pilotbot.map.GoogleMapEngine

/** Chooses the active map provider and centralizes place-search failure reporting. */
class PlaceSearchCoordinator(
    private val isGoogleActive: () -> Boolean,
    private val google: GoogleMapEngine,
    private val amap: PlaceSearch,
    private val cityCode: () -> String?,
    private val location: () -> Coordinates?,
    private val onFailure: (Throwable) -> Unit,
) {
    data class Coordinates(val latitude: Double, val longitude: Double)

    fun search(keyword: String, callback: (Result<List<PlaceResult>>) -> Unit) {
        val reportingCallback: (Result<List<PlaceResult>>) -> Unit = { result ->
            result.exceptionOrNull()?.let(onFailure)
            callback(result)
        }
        if (isGoogleActive()) google.searchPlaces(keyword, null, reportingCallback)
        else amap.search(keyword, cityCode(), reportingCallback)
    }

    fun searchNearby(keyword: String, callback: (Result<List<PlaceResult>>) -> Unit) {
        val reportingCallback: (Result<List<PlaceResult>>) -> Unit = { result ->
            result.exceptionOrNull()?.let(onFailure)
            callback(result)
        }
        if (isGoogleActive()) {
            google.searchPlaces(keyword, null, reportingCallback)
        } else {
            location()?.let { amap.searchAround(it.latitude, it.longitude, 3000, keyword, reportingCallback) }
                ?: amap.search(keyword, null, reportingCallback)
        }
    }
}
