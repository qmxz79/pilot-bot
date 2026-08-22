package com.qmxz.pilotbot.enroute

import com.qmxz.pilotbot.navi.RoutePlan

data class Poi(
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
)

data class AdminArea(
    val province: String,
    val city: String,
    val district: String,
)

/** Along-the-route data source for en-route narration (DESIGN §5.6). */
interface EnRouteDataSource {
    suspend fun nearbyPoi(lat: Double, lng: Double, radiusMeters: Int): List<Poi>

    suspend fun currentAdminArea(lat: Double, lng: Double): AdminArea

    suspend fun prefetchAlongRoute(route: RoutePlan)
}
