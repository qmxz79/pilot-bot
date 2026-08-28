package com.qmxz.pilotbot.avatar.view

import android.graphics.Canvas
import android.graphics.RectF
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.state.AvatarFrameData

/**
 * Common interface for avatar character graphic renderers.
 */
interface AvatarRenderer {
    /**
     * Renders the complete avatar within the specified [bounds].
     * @param canvas Hardware accelerated drawing canvas.
     * @param bounds Target rectangular viewport.
     * @param frameData Current animation frame (breathing, blink, gaze, tilt).
     * @param lipState Current phoneme lip state.
     * @param lipOpenness Continuous mouth opening degree (0.0f ~ 1.0f).
     */
    fun draw(
        canvas: Canvas,
        bounds: RectF,
        frameData: AvatarFrameData,
        lipState: LipState,
        lipOpenness: Float,
    )
}
