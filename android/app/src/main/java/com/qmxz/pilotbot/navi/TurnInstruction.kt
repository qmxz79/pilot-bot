package com.qmxz.pilotbot.navi

/** The next manoeuvre announced by the navigation provider. */
data class TurnInstruction(
    val action: TurnAction,
    val distanceMeters: Int,
)
