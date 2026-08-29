package com.qmxz.pilotbot.avatar.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import com.qmxz.pilotbot.R
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.state.AvatarFrameData
import com.qmxz.pilotbot.avatar.state.AvatarGender
import com.qmxz.pilotbot.avatar.state.AvatarState

/**
 * Photorealistic 2.5D Digital Human Renderer.
 * Combines high-resolution real human portrait photography with 60FPS dynamic
 * breathing, organic eyelid blinking, and real-time audio-driven lip-sync mouth morphing.
 */
class RealisticAvatarRenderer(private val context: Context) : AvatarRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val mouthPath = Path()
    private val eyelidPath = Path()

    private var femaleBitmap: Bitmap? = null
    private var maleBitmap: Bitmap? = null

    private val srcRect = Rect()
    private val dstRect = RectF()

    init {
        loadBitmaps()
    }

    private fun loadBitmaps() {
        runCatching {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            femaleBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.avatar_female_real, opts)
            maleBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.avatar_male_real, opts)
        }
    }

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
        val radius = (size / 2.0f) * 0.94f
        val u = size / 100.0f

        canvas.save()

        // 1. Draw outer status breathing glow halo
        drawStatusHalo(canvas, cx, cy, radius, frameData.state, frameData.breathingFactor)

        // 2. Setup circular clipping mask for portrait
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 3. Subtle physical breathing & head tilt transformation
        val breathScale = 1.0f + (frameData.breathingFactor - 0.5f) * 0.025f
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * 1.5f * u

        canvas.save()
        canvas.scale(breathScale, breathScale, cx, cy)
        canvas.translate(0f, breathOffsetY)
        canvas.rotate(frameData.bodyTiltDegrees * 0.7f, cx, cy + radius * 0.8f)

        // 4. Draw base photorealistic human portrait
        val currentGender = if (frameData.bodyTiltDegrees < -900f) AvatarGender.MALE else null // placeholder check
        drawPortraitBitmap(canvas, cx, cy, radius, currentGender ?: AvatarGender.FEMALE)

        // 5. Draw realistic organic blinking / winking eyelids over eyes
        drawRealisticEyelids(canvas, cx, cy, u, frameData)

        // 6. Draw real-time audio lip-sync mouth morphing
        drawRealisticLipSync(canvas, cx, cy, u, lipState, lipOpenness, frameData.state)

        canvas.restore()

        // 7. Draw circular luxury border rim on top
        drawBorderRim(canvas, cx, cy, radius, frameData.state)

        canvas.restore()
    }

    fun drawWithGender(
        canvas: Canvas,
        bounds: RectF,
        frameData: AvatarFrameData,
        lipState: LipState,
        lipOpenness: Float,
        gender: AvatarGender,
    ) {
        val cx = bounds.centerX()
        val cy = bounds.centerY()
        val size = bounds.width().coerceAtMost(bounds.height())
        val radius = (size / 2.0f) * 0.94f
        val u = size / 100.0f

        canvas.save()

        // 1. Draw outer status breathing glow halo
        drawStatusHalo(canvas, cx, cy, radius, frameData.state, frameData.breathingFactor)

        // 2. Setup circular clipping mask
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 3. Subtle physical breathing & head tilt
        val breathScale = 1.0f + (frameData.breathingFactor - 0.5f) * 0.025f
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * 1.5f * u

        canvas.save()
        canvas.scale(breathScale, breathScale, cx, cy)
        canvas.translate(0f, breathOffsetY)
        canvas.rotate(frameData.bodyTiltDegrees * 0.7f, cx, cy + radius * 0.8f)

        // 4. Draw base portrait bitmap
        drawPortraitBitmap(canvas, cx, cy, radius, gender)

        // 5. Draw realistic blinking eyelids
        drawRealisticEyelids(canvas, cx, cy, u, frameData)

        // 6. Draw real-time audio lip-sync mouth morphing
        drawRealisticLipSync(canvas, cx, cy, u, lipState, lipOpenness, frameData.state)

        canvas.restore()

        // 7. Draw circular border rim
        drawBorderRim(canvas, cx, cy, radius, frameData.state)

        canvas.restore()
    }

    private fun drawPortraitBitmap(canvas: Canvas, cx: Float, cy: Float, radius: Float, gender: AvatarGender) {
        val bmp = if (gender == AvatarGender.FEMALE) femaleBitmap else maleBitmap
        if (bmp != null && !bmp.isRecycled) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        } else {
            // Fallback fill if bitmap loading failed
            paint.reset()
            paint.color = if (gender == AvatarGender.FEMALE) Color.parseColor("#FFE4CD") else Color.parseColor("#FFDFC4")
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawStatusHalo(canvas: Canvas, cx: Float, cy: Float, radius: Float, state: AvatarState, breath: Float) {
        val haloColor = when (state) {
            AvatarState.SPEAKING -> Color.parseColor("#8B5CF6")  // Electric Purple
            AvatarState.LISTENING -> Color.parseColor("#10B981") // Emerald Green
            AvatarState.THINKING -> Color.parseColor("#38BDF8")  // Sky Blue
            AvatarState.ALERT -> Color.parseColor("#F97316")     // Coral Orange
            AvatarState.IDLE -> Color.parseColor("#64748B")      // Slate Grey
        }

        val haloAlpha = ((0.25f + breath * 0.25f) * 255).toInt().coerceIn(0, 255)
        val haloRadius = radius * (1.06f + breath * 0.04f)

        val gradient = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(Color.argb(haloAlpha, Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor)), Color.TRANSPARENT),
            floatArrayOf(0.75f, 1.0f),
            Shader.TileMode.CLAMP
        )
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = gradient
        canvas.drawCircle(cx, cy, haloRadius, paint)
    }

    private fun drawBorderRim(canvas: Canvas, cx: Float, cy: Float, radius: Float, state: AvatarState) {
        val rimColor = when (state) {
            AvatarState.SPEAKING -> Color.parseColor("#A78BFA")
            AvatarState.LISTENING -> Color.parseColor("#34D399")
            AvatarState.THINKING -> Color.parseColor("#60A5FA")
            AvatarState.ALERT -> Color.parseColor("#FB923C")
            AvatarState.IDLE -> Color.parseColor("#E2E8F0")
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.8f
        paint.color = rimColor
        canvas.drawCircle(cx, cy, radius - 1.4f, paint)
    }

    private fun drawRealisticEyelids(canvas: Canvas, cx: Float, cy: Float, u: Float, frameData: AvatarFrameData) {
        val blink = frameData.blinkProgress
        val isWink = frameData.isWinking

        if (blink < 0.15f && !isWink) return

        // Calibrated eye centers on 512x512 photo face
        val leftEyeX = cx - 11.5f * u
        val rightEyeX = cx + 11.5f * u
        val eyeY = cy - 13.0f * u
        val eyeRadiusX = 7.5f * u
        val eyeRadiusY = 5.5f * u

        // Left Eye Eyelid
        if (blink >= 0.15f) {
            drawSingleEyelid(canvas, leftEyeX, eyeY, eyeRadiusX, eyeRadiusY, u, blink)
        }

        // Right Eye (or Wink)
        val rightBlink = if (isWink) 1.0f else blink
        if (rightBlink >= 0.15f) {
            drawSingleEyelid(canvas, rightEyeX, eyeY, eyeRadiusX, eyeRadiusY, u, rightBlink)
        }
    }

    private fun drawSingleEyelid(
        canvas: Canvas,
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
        u: Float,
        progress: Float,
    ) {
        paint.reset()
        paint.isAntiAlias = true

        val coverH = (ry * 2.0f * progress).coerceAtMost(ry * 2.2f)

        // Realistic skin-tone gradient for eyelid fold
        eyelidPath.reset()
        val topY = y - ry
        val bottomY = topY + coverH
        eyelidPath.moveTo(x - rx, y)
        eyelidPath.cubicTo(x - rx * 0.4f, topY - 1.5f * u, x + rx * 0.4f, topY - 1.5f * u, x + rx, y)
        eyelidPath.cubicTo(x + rx * 0.4f, bottomY, x - rx * 0.4f, bottomY, x - rx, y)
        eyelidPath.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E8B99B") // Warm natural eyelid skin tone
        paint.alpha = (progress * 240).toInt().coerceIn(0, 255)
        canvas.drawPath(eyelidPath, paint)

        // Delicate dark eyelash arc
        if (progress > 0.6f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.0f * u
            paint.color = Color.parseColor("#2E1911")
            paint.alpha = ((progress - 0.6f) / 0.4f * 230).toInt().coerceIn(0, 230)
            paint.strokeCap = Paint.Cap.ROUND

            val lashPath = Path()
            lashPath.moveTo(x - rx * 0.95f, bottomY - 0.5f * u)
            lashPath.cubicTo(x - rx * 0.3f, bottomY + 1.2f * u, x + rx * 0.3f, bottomY + 1.2f * u, x + rx * 0.95f, bottomY - 0.5f * u)
            canvas.drawPath(lashPath, paint)
        }
    }

    private fun drawRealisticLipSync(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        u: Float,
        lipState: LipState,
        openness: Float,
        state: AvatarState,
    ) {
        if (openness < 0.08f) {
            // Mouth is resting at the natural smile in the photo; no overlay needed
            return
        }

        // Calibrated mouth coordinates on photo face
        val mouthY = cy + 2.0f * u
        val baseWidth = 11.0f * u * lipState.widthScale
        val openHeight = (openness * 7.5f * u).coerceAtLeast(0.5f)

        paint.reset()
        paint.isAntiAlias = true

        mouthPath.reset()
        val topMouthY = mouthY - openHeight * 0.3f
        val bottomMouthY = mouthY + openHeight * 0.7f

        // Natural curved upper & lower lip boundary
        mouthPath.moveTo(cx - baseWidth, mouthY)
        mouthPath.cubicTo(cx - baseWidth * 0.4f, topMouthY, cx + baseWidth * 0.4f, topMouthY, cx + baseWidth, mouthY)
        mouthPath.cubicTo(cx + baseWidth * 0.4f, bottomMouthY, cx - baseWidth * 0.4f, bottomMouthY, cx - baseWidth, mouthY)
        mouthPath.close()

        // 1. Dark inner mouth cavity
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4A0D18") // Realistic deep oral cavity shadow
        canvas.drawPath(mouthPath, paint)

        // 2. Upper realistic clean white teeth row
        if (openHeight > 1.2f * u) {
            paint.color = Color.parseColor("#FAF5F0")
            val teethRect = RectF(cx - baseWidth * 0.55f, topMouthY + 0.2f * u, cx + baseWidth * 0.55f, topMouthY + openHeight * 0.42f)
            canvas.drawRoundRect(teethRect, 1.8f * u, 1.8f * u, paint)
        }

        // 3. Lower soft pink tongue curve
        if (openHeight > 2.8f * u) {
            paint.color = Color.parseColor("#E17B87")
            canvas.drawCircle(cx, bottomMouthY - openHeight * 0.15f, baseWidth * 0.38f, paint)
        }

        // 4. Photorealistic glossy lip contour blend
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * u
        paint.color = Color.parseColor("#BE185D")
        paint.alpha = (openness * 200).toInt().coerceIn(0, 200)
        canvas.drawPath(mouthPath, paint)
    }
}
