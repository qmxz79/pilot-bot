package com.qmxz.pilotbot.avatar

import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.lipsync.LipSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LipSyncEngineTest {

    @Test
    fun testLipStatePhonemeMapping() {
        assertEquals(LipState.CLOSED, LipState.fromNormalizedEnergy(0.0f))
        assertEquals(LipState.CLOSED, LipState.fromNormalizedEnergy(0.05f))
        assertEquals(LipState.SLIGHT, LipState.fromNormalizedEnergy(0.15f))
        assertEquals(LipState.HALF_OPEN, LipState.fromNormalizedEnergy(0.40f))
        assertEquals(LipState.FLAT_EI, LipState.fromNormalizedEnergy(0.70f))
        assertEquals(LipState.WIDE_AO, LipState.fromNormalizedEnergy(0.95f))
    }

    @Test
    fun testLipSyncEngineEnergyFeedAndDecay() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = LipSyncEngine(testScope)

        var lastState = LipState.CLOSED
        var lastOpenness = 0.0f
        engine.addListener(object : LipSyncEngine.LipSyncListener {
            override fun onLipUpdate(state: LipState, rawOpenness: Float) {
                lastState = state
                lastOpenness = rawOpenness
            }
        })

        // Feed strong speech energy
        engine.feedEnergy(0.9f)
        assertTrue(lastOpenness > 0.4f)
        assertTrue(lastState != LipState.CLOSED)

        // Stop speaking -> returns to closed
        engine.stopSpeaking()
        assertEquals(LipState.CLOSED, lastState)
        assertEquals(0.0f, lastOpenness, 0.001f)

        testScope.cancel()
    }

    @Test
    fun testLipSyncEngineSpeakingRhythm() {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = LipSyncEngine(testScope)

        var updatesCount = 0
        engine.addListener(object : LipSyncEngine.LipSyncListener {
            override fun onLipUpdate(state: LipState, rawOpenness: Float) {
                updatesCount++
            }
        })

        engine.startSpeaking()
        Thread.sleep(120L)
        assertTrue("Expected multiple cadence updates during speech", updatesCount > 0)

        engine.stopSpeaking()
        testScope.cancel()
    }
}
