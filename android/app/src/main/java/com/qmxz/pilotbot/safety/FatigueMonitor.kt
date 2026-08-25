package com.qmxz.pilotbot.safety

import com.qmxz.pilotbot.navi.NaviState
import java.util.Calendar

/**
 * Tracks driving duration, late-night driving, and congestion to provide proactive driver care.
 */
class FatigueMonitor(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val hourOfDayProvider: () -> Int = {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = clock()
        calendar.get(Calendar.HOUR_OF_DAY)
    },
    var onFatigueWarning: (minutes: Long) -> Unit = {},
    var onNightCare: () -> Unit = {},
    var onCongestionBoredom: () -> Unit = {},
) {
    private var navStartTimeMillis: Long? = null
    private val triggeredFatigueThresholds = mutableSetOf<Long>()
    private var nightCareTriggered = false
    private var congestionStartTimeMillis: Long? = null
    private var congestionBoredomTriggered = false
    private var lastKnownNavigating = false

    /**
     * Updates the monitor with the latest navigation snapshot and navigating state.
     */
    fun updateState(state: NaviState?, isNavigating: Boolean) {
        if (!isNavigating) {
            if (lastKnownNavigating) {
                reset()
            }
            return
        }

        val now = clock()

        if (!lastKnownNavigating || navStartTimeMillis == null) {
            navStartTimeMillis = now
            triggeredFatigueThresholds.clear()
            nightCareTriggered = false
            congestionStartTimeMillis = null
            congestionBoredomTriggered = false
        }
        lastKnownNavigating = true

        // 1. Continuous navigation duration & fatigue detection
        val start = navStartTimeMillis ?: now
        val elapsedMinutes = (now - start) / 60_000L
        for (threshold in FATIGUE_THRESHOLDS_MINUTES) {
            if (elapsedMinutes >= threshold && threshold !in triggeredFatigueThresholds) {
                triggeredFatigueThresholds.add(threshold)
                onFatigueWarning(threshold)
            }
        }

        // 2. Late-night driving detection (22:00 - 06:00)
        val hour = hourOfDayProvider()
        val isLateNight = hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR || (state?.isNight == true)
        if (isLateNight && !nightCareTriggered) {
            nightCareTriggered = true
            onNightCare()
        }

        // 3. Severe congestion duration detection (e.g. jam duration >= 10 mins or continuous crawl >= 10 mins)
        val isCongested = state != null && (
            state.jamDurationSeconds >= CONGESTION_MINUTES * 60 ||
            state.jamLengthMeters > 0 ||
            state.currentSpeedKmh < SPEED_JAM_KMH
        )

        if (isCongested) {
            if (congestionStartTimeMillis == null) {
                congestionStartTimeMillis = now
            }
            val jamDurationFromClock = (now - (congestionStartTimeMillis ?: now)) / 1000
            val jamDurationFromState = state?.jamDurationSeconds ?: 0
            val totalJamSeconds = maxOf(jamDurationFromClock, jamDurationFromState.toLong())

            if (totalJamSeconds >= CONGESTION_MINUTES * 60 && !congestionBoredomTriggered) {
                congestionBoredomTriggered = true
                onCongestionBoredom()
            }
        } else {
            // Cleared congestion
            congestionStartTimeMillis = null
            congestionBoredomTriggered = false
        }
    }

    /**
     * Resets driving session tracking state.
     */
    fun reset() {
        navStartTimeMillis = null
        triggeredFatigueThresholds.clear()
        nightCareTriggered = false
        congestionStartTimeMillis = null
        congestionBoredomTriggered = false
        lastKnownNavigating = false
    }

    companion object {
        val FATIGUE_THRESHOLDS_MINUTES = listOf(90L, 120L, 150L, 180L, 210L, 240L)
        const val NIGHT_START_HOUR = 22
        const val NIGHT_END_HOUR = 6
        const val CONGESTION_MINUTES = 10L
        const val SPEED_JAM_KMH = 15f
    }
}
