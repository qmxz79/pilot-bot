package com.qmxz.pilotbot.enroute

import com.qmxz.pilotbot.navi.RoutePlan

/** M4 skeleton: returns nothing until a real geocoding/POI source is wired in. */
class NoopEnRouteDataSource : EnRouteDataSource {
    override suspend fun nearbyPoi(lat: Double, lng: Double, radiusMeters: Int): List<Poi> = emptyList()

    override suspend fun currentAdminArea(lat: Double, lng: Double): AdminArea = AdminArea("", "", "")

    override suspend fun prefetchAlongRoute(route: RoutePlan): Unit = Unit
}
