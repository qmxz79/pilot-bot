package com.qmxz.pilotbot.safety

import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.navi.TurnAction
import com.qmxz.pilotbot.navi.TurnInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FatigueMonitorTest {

    private var currentTime = 1_000_000_000L
    private var currentHour = 14

    private fun createDefaultState(
        speed: Float = 60f,
        jamDurationSeconds: Int = 0,
        jamLengthMeters: Int = 0,
        isNight: Boolean = false,
    ) = NaviState(
        remainingDistanceMeters = 10000,
        remainingTimeSeconds = 600,
        currentRoadName = "主干道",
        currentSpeedKmh = speed,
        nextTurn = TurnInstruction(TurnAction.STRAIGHT, 1000),
        jamLengthMeters = jamLengthMeters,
        jamDurationSeconds = jamDurationSeconds,
        isNight = isNight,
    )

    @Test
    fun testFatigueWarningTriggeredAtMilestones() {
        val warnings = mutableListOf<Long>()
        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onFatigueWarning = { warnings.add(it) },
        )

        val state = createDefaultState(speed = 80f)

        // Start navigation
        monitor.updateState(state, isNavigating = true)
        assertTrue(warnings.isEmpty())

        // Advance 89 minutes
        currentTime += 89 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertTrue(warnings.isEmpty())

        // Reach 90 minutes
        currentTime += 1 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(listOf(90L), warnings)

        // Advance to 105 minutes (should not repeat 90)
        currentTime += 15 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(listOf(90L), warnings)

        // Reach 120 minutes
        currentTime += 15 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(listOf(90L, 120L), warnings)
    }

    @Test
    fun testNightCareTriggeredDuringLateNightHours() {
        var nightCareCount = 0
        currentHour = 23 // 23:00 (11 PM)

        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onNightCare = { nightCareCount++ },
        )

        val state = createDefaultState()

        // Start navigation at night
        monitor.updateState(state, isNavigating = true)
        assertEquals(1, nightCareCount)

        // Subsequent updates in the same session do not re-trigger
        currentTime += 5 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(1, nightCareCount)
    }

    @Test
    fun testNightCareTriggeredEarlyMorning() {
        var nightCareCount = 0
        currentHour = 4 // 04:00 AM

        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onNightCare = { nightCareCount++ },
        )

        monitor.updateState(createDefaultState(), isNavigating = true)
        assertEquals(1, nightCareCount)
    }

    @Test
    fun testDaytimeDoesNotTriggerNightCareUnlessStateIsNight() {
        var nightCareCount = 0
        currentHour = 14 // 14:00 (2 PM)

        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onNightCare = { nightCareCount++ },
        )

        // Daytime update
        monitor.updateState(createDefaultState(isNight = false), isNavigating = true)
        assertEquals(0, nightCareCount)

        // When state explicitly indicates night
        monitor.updateState(createDefaultState(isNight = true), isNavigating = true)
        assertEquals(1, nightCareCount)
    }

    @Test
    fun testCongestionBoredomTriggeredAfterTenMinutes() {
        var boredomCount = 0
        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onCongestionBoredom = { boredomCount++ },
        )

        val crawlingState = createDefaultState(speed = 10f)

        // Navigation starts
        monitor.updateState(crawlingState, isNavigating = true)
        assertEquals(0, boredomCount)

        // Crawling for 9 minutes
        currentTime += 9 * 60_000L
        monitor.updateState(crawlingState, isNavigating = true)
        assertEquals(0, boredomCount)

        // Reaching 10 minutes
        currentTime += 1 * 60_000L
        monitor.updateState(crawlingState, isNavigating = true)
        assertEquals(1, boredomCount)

        // Continued crawl does not trigger again
        currentTime += 5 * 60_000L
        monitor.updateState(crawlingState, isNavigating = true)
        assertEquals(1, boredomCount)

        // Road clears
        val normalState = createDefaultState(speed = 60f)
        monitor.updateState(normalState, isNavigating = true)

        // Stuck in another jam for 10 minutes
        currentTime += 1 * 60_000L
        monitor.updateState(crawlingState, isNavigating = true)
        currentTime += 10 * 60_000L
        monitor.updateState(crawlingState, isNavigating = true)
        assertEquals(2, boredomCount)
    }

    @Test
    fun testCongestionBoredomTriggeredByNaviStateJamDuration() {
        var boredomCount = 0
        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onCongestionBoredom = { boredomCount++ },
        )

        val jamState = createDefaultState(speed = 8f, jamDurationSeconds = 650)
        monitor.updateState(jamState, isNavigating = true)
        assertEquals(1, boredomCount)
    }

    @Test
    fun testResetClearsContinuousDrivingTracking() {
        val warnings = mutableListOf<Long>()
        val monitor = FatigueMonitor(
            clock = { currentTime },
            hourOfDayProvider = { currentHour },
            onFatigueWarning = { warnings.add(it) },
        )

        val state = createDefaultState(speed = 80f)

        monitor.updateState(state, isNavigating = true)
        currentTime += 95 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(listOf(90L), warnings)

        // Navigation stops / resets
        monitor.reset()
        warnings.clear()

        // New navigation session starts
        currentTime += 10 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertTrue(warnings.isEmpty())

        // 80 minutes into new session -> no warning yet
        currentTime += 80 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertTrue(warnings.isEmpty())

        // 90 minutes into new session -> warns again
        currentTime += 10 * 60_000L
        monitor.updateState(state, isNavigating = true)
        assertEquals(listOf(90L), warnings)
    }
}
