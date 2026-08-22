package com.qmxz.pilotbot.safety

import com.qmxz.pilotbot.navi.NaviState

interface DrivingLoadEstimator {
    fun estimate(state: NaviState?): DrivingLoadLevel
}

/**
 * M3 skeleton: two tiers only. Near-stationary traffic -> L0 (copilot stays quiet and only the
 * navigation broadcast speaks); otherwise L3 (active). Higher tiers arrive when the coordinator
 * fills jam metrics.
 */
class SimpleDrivingLoadEstimator : DrivingLoadEstimator {
    override fun estimate(state: NaviState?): DrivingLoadLevel = when {
        state == null -> DrivingLoadLevel.L3_ACTIVE
        state.currentSpeedKmh < SPEED_JAM_KMH -> DrivingLoadLevel.L0_SILENT
        else -> DrivingLoadLevel.L3_ACTIVE
    }

    companion object {
        const val SPEED_JAM_KMH = 15f
    }
}
