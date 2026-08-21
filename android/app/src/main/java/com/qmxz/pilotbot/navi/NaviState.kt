package com.qmxz.pilotbot.navi

/** Navigation status snapshot aggregated from a navigation SDK callback. */
data class NaviState(
    val remainingDistanceMeters: Int,
    val remainingTimeSeconds: Int,
    val currentRoadName: String?,
    val currentSpeedKmh: Float,
    val nextTurn: TurnInstruction?,
    val jamLengthMeters: Int,
    val jamDurationSeconds: Int,
    val isNight: Boolean,
)
