package com.qmxz.pilotbot.safety

/** Driving-load tiers that decide how much the copilot should talk (DESIGN §5.5). */
enum class DrivingLoadLevel {
    /** Silent: heavy traffic / tight manoeuvres -> keep only the navigation broadcast. */
    L0_SILENT,

    /** Restrained: complex junctions -> answer but do not initiate. */
    L1_RESTRAINED,

    /** Mild: light congestion -> talk less and shorter. */
    L2_MILD,

    /** Active: highway cruise / simple road -> free conversation. */
    L3_ACTIVE,
}
