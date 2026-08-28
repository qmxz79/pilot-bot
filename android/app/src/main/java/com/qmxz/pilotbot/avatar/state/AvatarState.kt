package com.qmxz.pilotbot.avatar.state

/**
 * Five primary vital sign & behavioral states of the virtual copilot:
 * - IDLE: Relaxed breathing, organic blinking (every 3-6s), slight curiosity glance at road;
 * - LISTENING: Leaning slightly forward, ears perked, attentive eyes watching driver;
 * - THINKING: Thinking eyes/eyebrows, subtle chin-tap contemplation gesture;
 * - SPEAKING: Natural body swaying with voice rhythm, conversational gaze, real-time lip sync;
 * - ALERT: Caring concerned facial expression, gentle waving gesture for fatigue/safety warnings.
 */
enum class AvatarState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ALERT,
}
