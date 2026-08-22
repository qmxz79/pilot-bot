package com.qmxz.pilotbot.safety

import com.qmxz.pilotbot.navi.NaviState

interface DrivingLoadEstimator {
    fun estimate(state: NaviState?): DrivingLoadLevel
}

/**
 * Four-tier heuristic using only NaviState fields the coordinator already fills (speed + next
 * turn). Jam metrics are always 0 in M0's NaviState, so they are not consulted here; when the
 * coordinator fills them, tighten L0/L2 with jam length.
 */
class SimpleDrivingLoadEstimator : DrivingLoadEstimator {
    override fun estimate(state: NaviState?): DrivingLoadLevel {
        if (state == null) return DrivingLoadLevel.L3_ACTIVE
        val speed = state.currentSpeedKmh
        val turnDistance = state.nextTurn?.distanceMeters
        return when {
            // 拥堵爬行：闭嘴，只留 SDK 导航播报
            speed < SPEED_JAM_KMH -> DrivingLoadLevel.L0_SILENT

            // 临近转向（复杂路口/变道密集）：只回应、不主动
            turnDistance != null && turnDistance < TIGHT_TURN_METERS -> DrivingLoadLevel.L1_RESTRAINED

            // 城市路段 / 轻度拥堵：可以说，但别啰嗦
            speed < URBAN_KMH -> DrivingLoadLevel.L2_MILD

            // 高速巡航：随便聊
            else -> DrivingLoadLevel.L3_ACTIVE
        }
    }

    companion object {
        const val SPEED_JAM_KMH = 15f
        const val URBAN_KMH = 60f
        const val TIGHT_TURN_METERS = 200
    }
}
