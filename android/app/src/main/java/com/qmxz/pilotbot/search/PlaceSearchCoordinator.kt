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
        if (isGoogleActive()) {
            google.searchPlaces(keyword, null, reportingCallback)
        } else {
            amap.search(keyword, cityCode()) { amapResult ->
                amapResult.onSuccess { list ->
                    if (list.isNotEmpty()) {
                        reportingCallback(Result.success(list))
                    } else {
                        // If Amap found nothing, fall back to Google Maps search
                        google.searchPlaces(keyword, null) { googleResult ->
                            if (googleResult.isSuccess && googleResult.getOrNull()?.isNotEmpty() == true) {
                                reportingCallback(googleResult)
                            } else {
                                reportingCallback(Result.success(emptyList()))
                            }
                        }
                    }
                }.onFailure { amapErr ->
                    // If Amap fails (e.g. invalid signature, quota or overseas error), try Google
                    google.searchPlaces(keyword, null) { googleResult ->
                        if (googleResult.isSuccess && googleResult.getOrNull()?.isNotEmpty() == true) {
                            reportingCallback(googleResult)
                        } else {
                            reportingCallback(Result.failure(amapErr))
                        }
                    }
                }
            }
        }
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
