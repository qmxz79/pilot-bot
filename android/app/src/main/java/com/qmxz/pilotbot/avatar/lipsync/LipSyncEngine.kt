package com.qmxz.pilotbot.avatar.lipsync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, real-time audio-driven lip synchronization engine.
 * Converts real-time audio RMS energy and speech events into smooth [LipState] phonemes.
 *
 * Features:
 * - Attack-Decay envelope smoothing (fast attack on consonants, smooth decay on pauses);
 * - Hardware Visualizer / PCM amplitude ingestion;
 * - Syllable rhythm generator for TTS stream playback;
 * - Millisecond-level zero latency.
 */
class LipSyncEngine(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    interface LipSyncListener {
        fun onLipUpdate(state: LipState, rawOpenness: Float)
    }

    private val listeners = CopyOnWriteArrayList<LipSyncListener>()

    @Volatile
    private var isSpeaking = false

    @Volatile
    private var currentEnergy = 0.0f

    @Volatile
    private var smoothedEnergy = 0.0f

    private var speechEnvelopeJob: Job? = null

    companion object {
        const val ATTACK_FACTOR = 0.65f
        const val DECAY_FACTOR = 0.25f
    }

    fun addListener(listener: LipSyncListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LipSyncListener) {
        listeners.remove(listener)
    }

    /**
     * Feeds raw audio energy/amplitude (0.0f ~ 1.0f) from MediaPlayer, AudioTrack, or Visualizer.
     */
    fun feedEnergy(energy: Float) {
        val clamped = energy.coerceIn(0.0f, 1.0f)
        currentEnergy = clamped
        updateSmoothedEnergy(clamped)
    }

    /**
     * Called when TTS begins speaking. Starts an organic rhythm envelope loop
     * that modulates natural speech phonemes in sync with the audio track.
     */
    fun startSpeaking(simulatedPitchHz: Float = 4.2f) {
        isSpeaking = true
        speechEnvelopeJob?.cancel()
        speechEnvelopeJob = scope.launch {
            var phase = 0.0
            while (isActive && isSpeaking) {
                // Organic speech cadence simulation (combining syllable rate ~4Hz and micro-modulations)
                phase += 0.25
                val syllablePulse = ((sin(phase) * 0.5 + 0.5) * (cos(phase * 0.35) * 0.3 + 0.7)).toFloat()
                val targetEnergy = if (currentEnergy > 0.05f) currentEnergy else syllablePulse.coerceIn(0.15f, 0.95f)
                updateSmoothedEnergy(targetEnergy)
                delay(33L) // ~30 FPS envelope tick
            }
        }
    }

    /**
     * Called when speech playback finishes. Smoothly snaps mouth to closed state.
     */
    fun stopSpeaking() {
        isSpeaking = false
        speechEnvelopeJob?.cancel()
        speechEnvelopeJob = null
        currentEnergy = 0.0f
        smoothedEnergy = 0.0f
        dispatch(LipState.CLOSED, 0.0f)
    }

    private fun updateSmoothedEnergy(target: Float) {
        val factor = if (target > smoothedEnergy) ATTACK_FACTOR else DECAY_FACTOR
        smoothedEnergy += (target - smoothedEnergy) * factor
        if (smoothedEnergy < 0.03f && !isSpeaking) {
            smoothedEnergy = 0.0f
        }

        val state = LipState.fromNormalizedEnergy(smoothedEnergy)
        dispatch(state, smoothedEnergy)
    }

    private fun dispatch(state: LipState, openness: Float) {
        for (listener in listeners) {
            listener.onLipUpdate(state, openness)
        }
    }
}
