package com.qmxz.pilotbot.safety

import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.navi.TurnAction
import com.qmxz.pilotbot.navi.TurnInstruction
import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingLoadEstimatorTest {

    private val estimator = SimpleDrivingLoadEstimator()

    @Test
    fun testNullStateDefaultsToActive() {
        assertEquals(DrivingLoadLevel.L3_ACTIVE, estimator.estimate(null))
    }

    @Test
    fun testLowSpeedTrafficJamReturnsSilent() {
        val state = NaviState(
            remainingDistanceMeters = 5000,
            remainingTimeSeconds = 600,
            currentRoadName = "二环辅路",
            currentSpeedKmh = 10f,
            nextTurn = TurnInstruction(TurnAction.STRAIGHT, 1000),
            jamLengthMeters = 0,
            jamDurationSeconds = 0,
            isNight = false,
        )
        assertEquals(DrivingLoadLevel.L0_SILENT, estimator.estimate(state))
    }

    @Test
    fun testTightTurnReturnsRestrained() {
        val state = NaviState(
            remainingDistanceMeters = 5000,
            remainingTimeSeconds = 600,
            currentRoadName = "主路",
            currentSpeedKmh = 35f,
            nextTurn = TurnInstruction(TurnAction.TURN_RIGHT, 150),
            jamLengthMeters = 0,
            jamDurationSeconds = 0,
            isNight = false,
        )
        assertEquals(DrivingLoadLevel.L1_RESTRAINED, estimator.estimate(state))
    }

    @Test
    fun testUrbanSpeedReturnsMild() {
        val state = NaviState(
            remainingDistanceMeters = 5000,
            remainingTimeSeconds = 600,
            currentRoadName = "城市主干道",
            currentSpeedKmh = 45f,
            nextTurn = TurnInstruction(TurnAction.STRAIGHT, 800),
            jamLengthMeters = 0,
            jamDurationSeconds = 0,
            isNight = false,
        )
        assertEquals(DrivingLoadLevel.L2_MILD, estimator.estimate(state))
    }

    @Test
    fun testHighwaySpeedReturnsActive() {
        val state = NaviState(
            remainingDistanceMeters = 50000,
            remainingTimeSeconds = 2000,
            currentRoadName = "京承高速",
            currentSpeedKmh = 90f,
            nextTurn = TurnInstruction(TurnAction.STRAIGHT, 5000),
            jamLengthMeters = 0,
            jamDurationSeconds = 0,
            isNight = false,
        )
        assertEquals(DrivingLoadLevel.L3_ACTIVE, estimator.estimate(state))
    }
}
