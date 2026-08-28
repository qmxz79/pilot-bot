package com.qmxz.pilotbot.avatar.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.state.AvatarFrameData
import com.qmxz.pilotbot.avatar.state.AvatarState

/**
 * 2.5D High-fidelity Vector Renderer for Male Copilot "修然" (Handsome & Sunny Male Copilot).
 */
class MaleAvatarRenderer : AvatarRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun draw(
        canvas: Canvas,
        bounds: RectF,
        frameData: AvatarFrameData,
        lipState: LipState,
        lipOpenness: Float,
    ) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val size = bounds.width().coerceAtMost(bounds.height())
        val unit = size / 100.0f

        canvas.save()

        // 1. Subtle breathing float and head tilt
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * unit * 2.0f
        canvas.rotate(frameData.bodyTiltDegrees, cx, cy + 20 * unit)
        canvas.translate(0f, breathOffsetY)

        // 2. Soft background aura (Cool Blue / Indigo)
        drawAura(canvas, cx, cy, unit)

        // 3. Back Hair volume
        drawBackHair(canvas, cx, cy, unit)

        // 4. Body & Sharp Navy Jacket
        drawBody(canvas, cx, cy, unit, frameData.breathingFactor)

        // 5. Strong Neck & Collar
        drawNeck(canvas, cx, cy, unit)

        // 6. Masculine Jawline & Face Base
        drawFace(canvas, cx, cy, unit)

        // 7. Focused, Magnetic Eyes (Gaze, Blink & Wink)
        drawEyes(canvas, cx, cy, unit, frameData)

        // 8. Confident Eyebrows (Mood driven)
        drawEyebrows(canvas, cx, cy, unit, frameData.state)

        // 9. Defined Nose Bridge
        drawNose(canvas, cx, cy, unit)

        // 10. Dynamic Real-time Lip-Sync Mouth
        drawMouth(canvas, cx, cy, unit, lipState, lipOpenness)

        // 11. Stylish Front Layered Hair
        drawFrontHair(canvas, cx, cy, unit)

        canvas.restore()
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val haloRadius = 46 * u
        val gradient = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(Color.parseColor("#333B82F6"), Color.parseColor("#106366F1"), Color.TRANSPARENT),
            floatArrayOf(0.0f, 0.7f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = gradient
        canvas.drawCircle(cx, cy, haloRadius, paint)
    }

    private fun drawBackHair(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#18181B") // Jet Charcoal Black
        paint.style = Paint.Style.FILL

        path.reset()
        path.moveTo(cx - 26 * u, cy + 24 * u)
        path.cubicTo(cx - 30 * u, cy + 5 * u, cx - 28 * u, cy - 26 * u, cx, cy - 30 * u)
        path.cubicTo(cx + 28 * u, cy - 26 * u, cx + 30 * u, cy + 5 * u, cx + 26 * u, cy + 24 * u)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBody(canvas: Canvas, cx: Float, cy: Float, u: Float, breathing: Float) {
        // Deep Navy / Slate Smart Bomber Jacket
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#1E3A8A")
        paint.style = Paint.Style.FILL

        path.reset()
        val shoulderY = cy + 30 * u + (breathing * 1.5f * u)
        path.moveTo(cx - 38 * u, cy + 50 * u)
        path.cubicTo(cx - 34 * u, shoulderY, cx - 20 * u, cy + 25 * u, cx, cy + 27 * u)
        path.cubicTo(cx + 20 * u, cy + 25 * u, cx + 34 * u, shoulderY, cx + 38 * u, cy + 50 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Inner Clean White T-Shirt / Collar
        paint.color = Color.parseColor("#F1F5F9")
        path.reset()
        path.moveTo(cx - 12 * u, cy + 26 * u)
        path.lineTo(cx, cy + 38 * u)
        path.lineTo(cx + 12 * u, cy + 26 * u)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawNeck(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#F8CBA6")
        canvas.drawRect(cx - 8.5f * u, cy + 14 * u, cx + 8.5f * u, cy + 28 * u, paint)

        // Neck shadow
        paint.color = Color.parseColor("#E5AC82")
        canvas.drawRect(cx - 8.5f * u, cy + 14 * u, cx + 8.5f * u, cy + 18.5f * u, paint)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#FFDFC4") // Warm masculine skin tone
        paint.style = Paint.Style.FILL

        path.reset()
        path.moveTo(cx - 20 * u, cy - 8 * u)
        path.cubicTo(cx - 21 * u, cy + 8 * u, cx - 12 * u, cy + 21 * u, cx, cy + 23.5f * u)
        path.cubicTo(cx + 12 * u, cy + 21 * u, cx + 21 * u, cy + 8 * u, cx + 20 * u, cy - 8 * u)
        path.cubicTo(cx + 19 * u, cy - 25 * u, cx - 19 * u, cy - 25 * u, cx - 20 * u, cy - 8 * u)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, u: Float, frameData: AvatarFrameData) {
        val eyeSpacing = 9.8f * u
        val eyeY = cy + 2.0f * u
        val eyeRadiusX = 5.6f * u
        val eyeRadiusY = 5.8f * u

        val gazeDx = frameData.gazeX * 2.0f * u
        val gazeDy = frameData.gazeY * 1.5f * u

        // Left Eye
        drawSingleEye(canvas, cx - eyeSpacing, eyeY, eyeRadiusX, eyeRadiusY, u, gazeDx, gazeDy, frameData.blinkProgress, false)

        // Right Eye (can wink on tap)
        val rightBlink = if (frameData.isWinking) 1.0f else frameData.blinkProgress
        drawSingleEye(canvas, cx + eyeSpacing, eyeY, eyeRadiusX, eyeRadiusY, u, gazeDx, gazeDy, rightBlink, frameData.isWinking)
    }

    private fun drawSingleEye(
        canvas: Canvas,
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
        u: Float,
        gazeDx: Float,
        gazeDy: Float,
        blink: Float,
        isWink: Boolean,
    ) {
        paint.reset()
        paint.isAntiAlias = true

        if (blink > 0.75f || isWink) {
            paint.color = Color.parseColor("#18181B")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.6f * u
            paint.strokeCap = Paint.Cap.ROUND

            path.reset()
            path.moveTo(x - rx * 0.9f, y + 0.5f * u)
            path.cubicTo(x - rx * 0.3f, y - 1.5f * u, x + rx * 0.3f, y - 1.5f * u, x + rx * 0.9f, y + 0.5f * u)
            canvas.drawPath(path, paint)
            return
        }

        val currentRy = ry * (1.0f - blink).coerceAtLeast(0.15f)

        // Eye White
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(x - rx, y - currentRy, x + rx, y + currentRy), paint)

        // Deep Obsidian / Dark Slate Iris
        paint.color = Color.parseColor("#1E293B")
        val irisRx = 3.6f * u
        val irisRy = 3.8f * u * (1.0f - blink * 0.5f)
        canvas.drawOval(RectF(x + gazeDx - irisRx, y + gazeDy - irisRy, x + gazeDx + irisRx, y + gazeDy + irisRy), paint)

        // Pupil
        paint.color = Color.BLACK
        canvas.drawCircle(x + gazeDx, y + gazeDy, 1.9f * u, paint)

        // Clean Catchlight
        paint.color = Color.WHITE
        canvas.drawCircle(x + gazeDx - 1.2f * u, y + gazeDy - 1.2f * u, 1.1f * u, paint)

        // Upper Eyelid line
        paint.color = Color.parseColor("#18181B")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * u
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(x - rx * 1.1f, y - currentRy * 0.7f)
        path.cubicTo(x - rx * 0.2f, y - currentRy * 1.1f, x + rx * 0.5f, y - currentRy * 1.05f, x + rx * 1.1f, y - currentRy * 0.4f)
        canvas.drawPath(path, paint)
    }

    private fun drawEyebrows(canvas: Canvas, cx: Float, cy: Float, u: Float, state: AvatarState) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#18181B")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * u
        paint.strokeCap = Paint.Cap.ROUND

        val leftY = when (state) {
            AvatarState.LISTENING -> cy - 8.5f * u
            AvatarState.THINKING -> cy - 6.2f * u
            AvatarState.ALERT -> cy - 7.5f * u
            else -> cy - 7.0f * u
        }
        val rightY = when (state) {
            AvatarState.THINKING -> cy - 8.5f * u
            AvatarState.ALERT -> cy - 7.5f * u
            else -> cy - 7.0f * u
        }

        // Left Sword Eyebrow
        path.reset()
        path.moveTo(cx - 16 * u, leftY + 0.5f * u)
        path.lineTo(cx - 10 * u, leftY - 1.8f * u)
        path.lineTo(cx - 4 * u, leftY - 0.2f * u)
        canvas.drawPath(path, paint)

        // Right Sword Eyebrow
        path.reset()
        path.moveTo(cx + 4 * u, rightY - 0.2f * u)
        path.lineTo(cx + 10 * u, rightY - 1.8f * u)
        path.lineTo(cx + 16 * u, rightY + 0.5f * u)
        canvas.drawPath(path, paint)
    }

    private fun drawNose(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#E09E75")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * u
        paint.strokeCap = Paint.Cap.ROUND

        path.reset()
        path.moveTo(cx - 0.8f * u, cy + 3 * u)
        path.lineTo(cx + 1.2f * u, cy + 8.5f * u)
        path.lineTo(cx - 1.2f * u, cy + 10 * u)
        canvas.drawPath(path, paint)
    }

    private fun drawMouth(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        lipState: LipState,
        openness: Float,
    ) {
        val mouthY = cy + 16.5f * u
        val baseWidth = 6.8f * u * lipState.widthScale
        val openH = (openness * 6.0f * u).coerceAtLeast(0.0f)

        paint.reset()
        paint.isAntiAlias = true

        if (openness < 0.08f) {
            // Confident gentle smile
            paint.color = Color.parseColor("#BE185D")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f * u
            paint.strokeCap = Paint.Cap.ROUND

            path.reset()
            path.moveTo(cx - baseWidth, mouthY)
            path.cubicTo(cx - baseWidth * 0.3f, mouthY + 1.6f * u, cx + baseWidth * 0.3f, mouthY + 1.6f * u, cx + baseWidth, mouthY)
            canvas.drawPath(path, paint)
            return
        }

        // Open mouth cavity
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#881337")
        val mouthRect = RectF(cx - baseWidth, mouthY - openH * 0.25f, cx + baseWidth, mouthY + openH)
        canvas.drawRoundRect(mouthRect, 3.5f * u, 3.5f * u, paint)

        // Upper teeth
        paint.color = Color.WHITE
        val teethRect = RectF(cx - baseWidth * 0.65f, mouthY - openH * 0.2f, cx + baseWidth * 0.65f, mouthY + openH * 0.35f)
        canvas.drawRoundRect(teethRect, 1.8f * u, 1.8f * u, paint)

        // Lip outline
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#9F1239")
        paint.strokeWidth = 1.6f * u
        canvas.drawRoundRect(mouthRect, 3.5f * u, 3.5f * u, paint)
    }

    private fun drawFrontHair(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#27272A") // Modern layered Charcoal
        paint.style = Paint.Style.FILL

        // Dynamic Front Layered Hair
        path.reset()
        path.moveTo(cx - 24 * u, cy - 12 * u)
        path.cubicTo(cx - 22 * u, cy - 28 * u, cx, cy - 32 * u, cx + 22 * u, cy - 26 * u)
        path.lineTo(cx + 25 * u, cy - 10 * u)
        path.lineTo(cx + 17 * u, cy - 16 * u)
        path.lineTo(cx + 10 * u, cy - 7 * u) // Textured layer
        path.lineTo(cx + 2 * u, cy - 14 * u)
        path.lineTo(cx - 8 * u, cy - 6 * u)
        path.lineTo(cx - 16 * u, cy - 12 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Cool subtle highlight
        paint.color = Color.parseColor("#3394A3B8")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * u
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - 12 * u, cy - 24 * u)
        path.cubicTo(cx - 4 * u, cy - 28 * u, cx + 6 * u, cy - 27 * u, cx + 15 * u, cy - 22 * u)
        canvas.drawPath(path, paint)
    }
}
