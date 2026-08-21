package com.qmxz.pilotbot.navi

/** Vendor-neutral representation of the next navigation manoeuvre. */
enum class TurnAction {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    KEEP_LEFT,
    KEEP_RIGHT,
    U_TURN,
    ENTER_ROUNDABOUT,
    EXIT_ROUNDABOUT,
    ARRIVE,
    UNKNOWN,
}
