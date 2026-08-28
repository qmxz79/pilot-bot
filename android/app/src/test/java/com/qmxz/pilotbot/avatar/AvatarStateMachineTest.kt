package com.qmxz.pilotbot.avatar

import com.qmxz.pilotbot.avatar.state.AvatarGender
import com.qmxz.pilotbot.avatar.state.AvatarState
import com.qmxz.pilotbot.avatar.state.AvatarStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarStateMachineTest {

    @Test
    fun testAvatarGenderDefinitions() {
        assertEquals("心怡", AvatarGender.FEMALE.characterName)
        assertEquals("修然", AvatarGender.MALE.characterName)
        assertEquals(AvatarGender.FEMALE, AvatarGender.fromName("female"))
        assertEquals(AvatarGender.MALE, AvatarGender.fromName("MALE"))
        assertEquals(AvatarGender.FEMALE, AvatarGender.fromName("unknown"))
    }

    @Test
    fun testStateMachineTransitions() {
        val stateMachine = AvatarStateMachine()
        assertEquals(AvatarState.IDLE, stateMachine.currentState)

        var notifiedState = AvatarState.IDLE
        stateMachine.addListener(object : AvatarStateMachine.StateListener {
            override fun onStateChanged(newState: AvatarState) {
                notifiedState = newState
            }
        })

        stateMachine.transitionTo(AvatarState.LISTENING)
        assertEquals(AvatarState.LISTENING, stateMachine.currentState)
        assertEquals(AvatarState.LISTENING, notifiedState)

        stateMachine.transitionTo(AvatarState.THINKING)
        assertEquals(AvatarState.THINKING, stateMachine.currentState)

        stateMachine.transitionTo(AvatarState.SPEAKING)
        assertEquals(AvatarState.SPEAKING, stateMachine.currentState)

        stateMachine.transitionTo(AvatarState.ALERT)
        assertEquals(AvatarState.ALERT, stateMachine.currentState)
    }

    @Test
    fun testFrameDataCalculations() {
        val stateMachine = AvatarStateMachine()
        val frame = stateMachine.updateFrame(1000L)

        assertNotNull(frame)
        assertEquals(AvatarState.IDLE, frame.state)
        assertTrue(frame.breathingFactor in 0.0f..1.0f)
        assertTrue(frame.blinkProgress in 0.0f..1.0f)
        assertTrue(frame.gazeX in -1.5f..1.5f)
        assertTrue(frame.gazeY in -1.5f..1.5f)
    }

    @Test
    fun testInteractiveWinkReaction() {
        val stateMachine = AvatarStateMachine()
        stateMachine.triggerInteractiveReaction()
        val frame = stateMachine.updateFrame(System.currentTimeMillis())
        assertTrue("Expected isWinking to be true immediately after trigger", frame.isWinking)
    }
}
