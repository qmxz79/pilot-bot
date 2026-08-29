package com.qmxz.pilotbot.avatar.state

import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin

/**
 * Manages the dynamic life signs and animation clock for the avatar:
 * - Breathing motion calculation (0.0f ~ 1.0f);
 * - Organic blinking state (0.0f = eyes fully open, 1.0f = eyes closed);
 * - Eye gaze offset (X, Y);
 * - State transition smoothing.
 */
class AvatarStateMachine {
    interface StateListener {
        fun onStateChanged(newState: AvatarState)
    }

    private val listeners = CopyOnWriteArrayList<StateListener>()
    private val random = Random()

    var currentState: AvatarState = AvatarState.IDLE
        private set

    // Animation timestamps & cycle parameters
    private var stateStartTime = System.currentTimeMillis()
    private var nextBlinkTime = System.currentTimeMillis() + randomBlinkInterval()
    private var isBlinking = false
    private var blinkProgress = 0.0f // 0.0 -> 1.0 -> 0.0

    private var nextGlanceTime = System.currentTimeMillis() + randomGlanceInterval()
    private var targetGazeX = 0.0f
    private var targetGazeY = 0.0f
    private var currentGazeX = 0.0f
    private var currentGazeY = 0.0f

    private var interactiveWinkUntil = 0L

    fun addListener(listener: StateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: StateListener) {
        listeners.remove(listener)
    }

    fun transitionTo(newState: AvatarState) {
        if (currentState != newState) {
            currentState = newState
            stateStartTime = System.currentTimeMillis()
            for (l in listeners) {
                l.onStateChanged(newState)
            }
        }
    }

    /** Triggers a playful wink or interactive reaction on tap. */
    fun triggerInteractiveReaction() {
        interactiveWinkUntil = System.currentTimeMillis() + 800L
    }

    /**
     * Updates internal physics and vital sign clocks. Call every frame (~60 FPS).
     * Returns an [AvatarFrameData] snapshot.
     */
    fun updateFrame(currentTimeMs: Long = System.currentTimeMillis()): AvatarFrameData {
        // 1. Breathing motion (smooth sine wave, period ~3.6s)
        val breathPhase = (currentTimeMs % 3600L) / 3600.0 * 2.0 * Math.PI
        val breathingFactor = ((sin(breathPhase) + 1.0) / 2.0).toFloat()

        // 2. Blinking logic
        val isInteractiveWink = currentTimeMs < interactiveWinkUntil
        if (currentTimeMs >= nextBlinkTime && !isBlinking) {
            isBlinking = true
        }

        if (isBlinking) {
            val blinkDuration = 240L
            val elapsed = (currentTimeMs - nextBlinkTime).coerceAtLeast(0L)
            if (elapsed < blinkDuration / 2) {
                blinkProgress = (elapsed.toFloat() / (blinkDuration / 2)).coerceIn(0f, 1f)
            } else if (elapsed < blinkDuration) {
                blinkProgress = (1.0f - (elapsed - blinkDuration / 2).toFloat() / (blinkDuration / 2)).coerceIn(0f, 1f)
            } else {
                isBlinking = false
                blinkProgress = 0.0f
                nextBlinkTime = currentTimeMs + randomBlinkInterval()
            }
        }

        // 3. Glance / Gaze tracking
        if (currentTimeMs >= nextGlanceTime) {
            if (currentState == AvatarState.IDLE) {
                targetGazeX = (random.nextFloat() - 0.5f) * 0.8f
                targetGazeY = (random.nextFloat() - 0.5f) * 0.4f
            } else if (currentState == AvatarState.LISTENING) {
                targetGazeX = -0.3f // Gaze towards driver
                targetGazeY = 0.1f
            } else if (currentState == AvatarState.THINKING) {
                targetGazeX = 0.4f // Looking slightly up and to side
                targetGazeY = -0.4f
            } else {
                targetGazeX = 0.0f
                targetGazeY = 0.0f
            }
            nextGlanceTime = currentTimeMs + randomGlanceInterval()
        }

        // Smooth gaze interpolation
        currentGazeX += (targetGazeX - currentGazeX) * 0.12f
        currentGazeY += (targetGazeY - currentGazeY) * 0.12f

        // Head tilt and body lean based on state
        val bodyTilt = when (currentState) {
            AvatarState.LISTENING -> -4.0f // Leaning towards driver
            AvatarState.THINKING -> 3.5f
            AvatarState.SPEAKING -> sin((currentTimeMs % 800L) / 800.0 * 2.0 * Math.PI).toFloat() * 2.5f // Energetic speaking head nod
            AvatarState.ALERT -> 0.0f
            AvatarState.IDLE -> sin((currentTimeMs % 3200L) / 3200.0 * 2.0 * Math.PI).toFloat() * 1.5f
        }

        return AvatarFrameData(
            state = currentState,
            breathingFactor = breathingFactor,
            blinkProgress = blinkProgress,
            isWinking = isInteractiveWink,
            gazeX = currentGazeX,
            gazeY = currentGazeY,
            bodyTiltDegrees = bodyTilt,
        )
    }

    private fun randomBlinkInterval(): Long = 1600L + random.nextInt(2400)

    private fun randomGlanceInterval(): Long = 3000L + random.nextInt(3500)
}

data class AvatarFrameData(
    val state: AvatarState,
    val breathingFactor: Float, // 0.0f ~ 1.0f (0 = exhaled, 1 = inhaled)
    val blinkProgress: Float,   // 0.0f = fully open, 1.0f = fully closed
    val isWinking: Boolean,
    val gazeX: Float,           // -1.0f (left) ~ 1.0f (right)
    val gazeY: Float,           // -1.0f (up) ~ 1.0f (down)
    val bodyTiltDegrees: Float,
)
