package com.qmxz.pilotbot.avatar.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.state.AvatarFrameData
import com.qmxz.pilotbot.avatar.state.AvatarState

/**
 * 2.5D High-fidelity Vector Renderer for Female Copilot "心怡" (Sweet & Intellectual Female Copilot).
 */
class FemaleAvatarRenderer : AvatarRenderer {
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
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * unit * 2.2f
        canvas.rotate(frameData.bodyTiltDegrees, cx, cy + 20 * unit)
        canvas.translate(0f, breathOffsetY)

        // 2. Soft background glow circle / halo
        drawAura(canvas, cx, cy, unit)

        // 3. Back Hair
        drawBackHair(canvas, cx, cy, unit)

        // 4. Body & Stylish Jacket
        drawBody(canvas, cx, cy, unit, frameData.breathingFactor)

        // 5. Neck & Chin Shadow
        drawNeck(canvas, cx, cy, unit)

        // 6. Face Base & Blushes
        drawFace(canvas, cx, cy, unit)

        // 7. Expressive Eyes & Eyelids (with Gaze & Blink/Wink)
        drawEyes(canvas, cx, cy, unit, frameData)

        // 8. Dynamic Eyebrows (Mood driven)
        drawEyebrows(canvas, cx, cy, unit, frameData.state)

        // 9. Nose
        drawNose(canvas, cx, cy, unit)

        // 10. Dynamic Real-time Lip-Sync Mouth
        drawMouth(canvas, cx, cy, unit, lipState, lipOpenness)

        // 11. Front Bangs & Chic Hair Strands
        drawFrontHair(canvas, cx, cy, unit)

        canvas.restore()
    }

    private fun drawAura(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        val haloRadius = 46 * u
        val gradient = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(Color.parseColor("#33EC4899"), Color.parseColor("#108B5CF6"), Color.TRANSPARENT),
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
        paint.color = Color.parseColor("#3E2314") // Dark chestnut
        paint.style = Paint.Style.FILL

        path.reset()
        path.moveTo(cx - 30 * u, cy + 45 * u)
        path.cubicTo(cx - 34 * u, cy + 10 * u, cx - 32 * u, cy - 25 * u, cx, cy - 32 * u)
        path.cubicTo(cx + 32 * u, cy - 25 * u, cx + 34 * u, cy + 10 * u, cx + 30 * u, cy + 45 * u)
        path.lineTo(cx - 30 * u, cy + 45 * u)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBody(canvas: Canvas, cx: Float, cy: Float, u: Float, breathing: Float) {
        // Modern Teal / Coral Jacket
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#0D9488")
        paint.style = Paint.Style.FILL

        path.reset()
        val shoulderY = cy + 30 * u + (breathing * 1.5f * u)
        path.moveTo(cx - 36 * u, cy + 50 * u)
        path.cubicTo(cx - 32 * u, shoulderY, cx - 18 * u, cy + 26 * u, cx, cy + 28 * u)
        path.cubicTo(cx + 18 * u, cy + 26 * u, cx + 32 * u, shoulderY, cx + 36 * u, cy + 50 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Inner White V-Neck Collar
        paint.color = Color.parseColor("#F8FAFC")
        path.reset()
        path.moveTo(cx - 10 * u, cy + 27 * u)
        path.lineTo(cx, cy + 40 * u)
        path.lineTo(cx + 10 * u, cy + 27 * u)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawNeck(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#FCD5B5")
        canvas.drawRect(cx - 7 * u, cy + 15 * u, cx + 7 * u, cy + 28 * u, paint)

        // Neck shadow
        paint.color = Color.parseColor("#EAB892")
        canvas.drawRect(cx - 7 * u, cy + 15 * u, cx + 7 * u, cy + 19 * u, paint)
    }

    private fun drawFace(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#FFE4CD") // Fair glowing skin tone
        paint.style = Paint.Style.FILL

        path.reset()
        path.moveTo(cx - 21 * u, cy - 5 * u)
        path.cubicTo(cx - 22 * u, cy + 10 * u, cx - 14 * u, cy + 22 * u, cx, cy + 24 * u)
        path.cubicTo(cx + 14 * u, cy + 22 * u, cx + 22 * u, cy + 10 * u, cx + 21 * u, cy - 5 * u)
        path.cubicTo(cx + 20 * u, cy - 25 * u, cx - 20 * u, cy - 25 * u, cx - 21 * u, cy - 5 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Soft blushes
        paint.color = Color.parseColor("#44F43F5E")
        canvas.drawOval(RectF(cx - 19 * u, cy + 7 * u, cx - 9 * u, cy + 13 * u), paint)
        canvas.drawOval(RectF(cx + 9 * u, cy + 7 * u, cx + 19 * u, cy + 13 * u), paint)
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, u: Float, frameData: AvatarFrameData) {
        val eyeSpacing = 9.5f * u
        val eyeY = cy + 2.5f * u
        val eyeRadiusX = 5.5f * u
        val eyeRadiusY = 6.8f * u

        val gazeDx = frameData.gazeX * 2.0f * u
        val gazeDy = frameData.gazeY * 1.5f * u

        // Left Eye (or wink)
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
            // Closed eye curved line / happy eye arc
            paint.color = Color.parseColor("#2E180E")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f * u
            paint.strokeCap = Paint.Cap.ROUND

            path.reset()
            path.moveTo(x - rx * 0.9f, y)
            path.cubicTo(x - rx * 0.3f, y - 2 * u, x + rx * 0.3f, y - 2 * u, x + rx * 0.9f, y)
            canvas.drawPath(path, paint)
            return
        }

        val currentRy = ry * (1.0f - blink).coerceAtLeast(0.15f)

        // Eye white
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(x - rx, y - currentRy, x + rx, y + currentRy), paint)

        // Iris (warm deep amber-hazel)
        paint.color = Color.parseColor("#4A2810")
        val irisRx = 3.6f * u
        val irisRy = 4.4f * u * (1.0f - blink * 0.5f)
        canvas.drawOval(RectF(x + gazeDx - irisRx, y + gazeDy - irisRy, x + gazeDx + irisRx, y + gazeDy + irisRy), paint)

        // Pupil (Deep dark)
        paint.color = Color.parseColor("#1A0C05")
        canvas.drawCircle(x + gazeDx, y + gazeDy, 2.0f * u, paint)

        // Double Catchlight / Sparkle
        paint.color = Color.WHITE
        canvas.drawCircle(x + gazeDx - 1.2f * u, y + gazeDy - 1.4f * u, 1.2f * u, paint)
        canvas.drawCircle(x + gazeDx + 1.2f * u, y + gazeDy + 1.2f * u, 0.6f * u, paint)

        // Eyelash Line
        paint.color = Color.parseColor("#2E180E")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * u
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(x - rx * 1.1f, y - currentRy * 0.8f)
        path.cubicTo(x - rx * 0.2f, y - currentRy * 1.15f, x + rx * 0.6f, y - currentRy * 1.1f, x + rx * 1.15f, y - currentRy * 0.5f)
        canvas.drawPath(path, paint)
    }

    private fun drawEyebrows(canvas: Canvas, cx: Float, cy: Float, u: Float, state: AvatarState) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#4A2E1B")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * u
        paint.strokeCap = Paint.Cap.ROUND

        val leftY = when (state) {
            AvatarState.LISTENING -> cy - 8.5f * u // Raised
            AvatarState.THINKING -> cy - 6.0f * u
            AvatarState.ALERT -> cy - 7.5f * u
            else -> cy - 7.0f * u
        }
        val rightY = when (state) {
            AvatarState.THINKING -> cy - 8.5f * u // One eyebrow raised
            AvatarState.ALERT -> cy - 7.5f * u
            else -> cy - 7.0f * u
        }

        // Left eyebrow
        path.reset()
        path.moveTo(cx - 15 * u, leftY + 1 * u)
        path.cubicTo(cx - 11 * u, leftY - 1.5f * u, cx - 7 * u, leftY - 1 * u, cx - 4 * u, leftY + 1 * u)
        canvas.drawPath(path, paint)

        // Right eyebrow
        path.reset()
        path.moveTo(cx + 4 * u, rightY + 1 * u)
        path.cubicTo(cx + 7 * u, rightY - 1 * u, cx + 11 * u, rightY - 1.5f * u, cx + 15 * u, rightY + 1 * u)
        canvas.drawPath(path, paint)
    }

    private fun drawNose(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#DFA079")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + 9.5f * u, 1.1f * u, paint)
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
        val baseWidth = 6.5f * u * lipState.widthScale
        val openH = (openness * 6.5f * u).coerceAtLeast(0.0f)

        paint.reset()
        paint.isAntiAlias = true

        if (openness < 0.08f) {
            // Closed smiling mouth
            paint.color = Color.parseColor("#E11D48") // Rose red lips
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f * u
            paint.strokeCap = Paint.Cap.ROUND

            path.reset()
            path.moveTo(cx - baseWidth, mouthY)
            path.cubicTo(cx - baseWidth * 0.4f, mouthY + 1.8f * u, cx + baseWidth * 0.4f, mouthY + 1.8f * u, cx + baseWidth, mouthY)
            canvas.drawPath(path, paint)
            return
        }

        // Open mouth cavity (Inner cavity)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#881337") // Deep dark ruby
        val mouthRect = RectF(cx - baseWidth, mouthY - openH * 0.3f, cx + baseWidth, mouthY + openH)
        canvas.drawRoundRect(mouthRect, 4 * u, 4 * u, paint)

        // Upper teeth
        paint.color = Color.WHITE
        val teethRect = RectF(cx - baseWidth * 0.6f, mouthY - openH * 0.25f, cx + baseWidth * 0.6f, mouthY + openH * 0.35f)
        canvas.drawRoundRect(teethRect, 2 * u, 2 * u, paint)

        // Tongue (cute pink bottom)
        if (openH > 2.5f * u) {
            paint.color = Color.parseColor("#FB7185")
            canvas.drawCircle(cx, mouthY + openH * 0.75f, baseWidth * 0.5f, paint)
        }

        // Lip outline & color
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#E11D48")
        paint.strokeWidth = 1.6f * u
        canvas.drawRoundRect(mouthRect, 4 * u, 4 * u, paint)
    }

    private fun drawFrontHair(canvas: Canvas, cx: Float, cy: Float, u: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#4A2E1B")
        paint.style = Paint.Style.FILL

        // Left Side Swept Bangs
        path.reset()
        path.moveTo(cx - 24 * u, cy - 10 * u)
        path.cubicTo(cx - 20 * u, cy - 26 * u, cx, cy - 28 * u, cx + 18 * u, cy - 18 * u)
        path.cubicTo(cx + 8 * u, cy - 12 * u, cx - 8 * u, cy - 10 * u, cx - 18 * u, cy - 4 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Right side soft strand
        path.reset()
        path.moveTo(cx + 15 * u, cy - 22 * u)
        path.cubicTo(cx + 25 * u, cy - 12 * u, cx + 24 * u, cy + 8 * u, cx + 21 * u, cy + 18 * u)
        path.cubicTo(cx + 25 * u, cy + 4 * u, cx + 26 * u, cy - 14 * u, cx + 15 * u, cy - 22 * u)
        path.close()
        canvas.drawPath(path, paint)

        // Hair highlight shine
        paint.color = Color.parseColor("#33FFFFFF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * u
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - 14 * u, cy - 22 * u)
        path.cubicTo(cx - 6 * u, cy - 25 * u, cx + 6 * u, cy - 24 * u, cx + 12 * u, cy - 18 * u)
        canvas.drawPath(path, paint)
    }
}
