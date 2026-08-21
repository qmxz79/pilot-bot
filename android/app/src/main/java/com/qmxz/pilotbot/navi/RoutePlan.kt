package com.qmxz.pilotbot.navi

/** A latitude/longitude coordinate independent of any particular map SDK. */
data class GeoPoint(
    val longitude: Double,
    val latitude: Double,
)

/**
 * Input and selected-route summary for one driving navigation request.
 *
 * [strategy] is an optional AMap driving strategy bitmask. A provider may substitute its
 * documented default when it is null. The route summary fields are filled after calculation.
 */
data class RoutePlan(
    val start: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val strategy: Int? = null,
    val selectedRouteId: Int? = null,
    val totalDistanceMeters: Int? = null,
    val totalTimeSeconds: Int? = null,
)
