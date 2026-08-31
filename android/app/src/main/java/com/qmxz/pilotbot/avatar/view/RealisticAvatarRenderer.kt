package com.qmxz.pilotbot.avatar.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
 * 100% Native High-Fidelity 60FPS Photorealistic Avatar Renderer.
 * Directly renders real-human photography with 60FPS continuous physical breathing,
 * smooth eyelid blinking, speaking mouth dynamics, and glowing smart status ring.
 */
class RealisticAvatarRenderer(private val context: Context) : AvatarRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    // Real-human portrait assets
    private var femalePhoto: Bitmap? = null
    private var femaleBlink: Bitmap? = null
    private var femaleWink: Bitmap? = null
    private var femaleSpeakOpen: Bitmap? = null
    private var femaleSpeakWide: Bitmap? = null

    private var malePhoto: Bitmap? = null
    private var maleBlink: Bitmap? = null
    private var maleWink: Bitmap? = null
    private var maleSpeakOpen: Bitmap? = null
    private var maleSpeakWide: Bitmap? = null

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
            val res = context.resources
            femalePhoto = BitmapFactory.decodeResource(res, R.drawable.avatar_female_real, opts)
                ?: BitmapFactory.decodeResource(res, R.drawable.female_real_idle, opts)
            femaleBlink = BitmapFactory.decodeResource(res, R.drawable.female_real_blink, opts)
            femaleWink = BitmapFactory.decodeResource(res, R.drawable.female_real_wink, opts)
            femaleSpeakOpen = BitmapFactory.decodeResource(res, R.drawable.female_real_speak_open, opts)
            femaleSpeakWide = BitmapFactory.decodeResource(res, R.drawable.female_real_speak_wide, opts)

            malePhoto = BitmapFactory.decodeResource(res, R.drawable.avatar_male_real, opts)
                ?: BitmapFactory.decodeResource(res, R.drawable.male_real_idle, opts)
            maleBlink = BitmapFactory.decodeResource(res, R.drawable.male_real_blink, opts)
            maleWink = BitmapFactory.decodeResource(res, R.drawable.male_real_wink, opts)
            maleSpeakOpen = BitmapFactory.decodeResource(res, R.drawable.male_real_speak_open, opts)
            maleSpeakWide = BitmapFactory.decodeResource(res, R.drawable.male_real_speak_wide, opts)
        }
    }

    override fun draw(
        canvas: Canvas,
        bounds: RectF,
        frameData: AvatarFrameData,
        lipState: LipState,
        lipOpenness: Float,
    ) {
        drawWithGender(canvas, bounds, frameData, lipState, lipOpenness, AvatarGender.FEMALE)
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
        val radius = (size / 2.0f) * 0.92f
        val u = size / 100.0f

        canvas.save()

        // 1. Draw outer dynamic breathing status glow halo
        drawStatusHalo(canvas, cx, cy, radius, frameData.state, frameData.breathingFactor)

        // 2. Setup circular clipping mask for high-definition portrait
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 3. 60 FPS Physical Breathing & Body Bobbing
        val breathScale = 1.0f + (frameData.breathingFactor - 0.5f) * 0.035f
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * 2.5f * u

        canvas.save()
        canvas.scale(breathScale, breathScale, cx, cy)
        canvas.translate(0f, breathOffsetY)
        canvas.rotate(frameData.bodyTiltDegrees * 0.7f, cx, cy + radius * 0.8f)

        // 4. Select the appropriate photographic frame
        val activeBitmap = selectPhotorealBitmap(gender, frameData, lipState, lipOpenness)

        // 5. Draw the crystal-clear real human photo frame
        drawBitmapFrame(canvas, cx, cy, radius, activeBitmap, gender)

        canvas.restore()

        // 6. Draw circular luxury border rim on top
        drawBorderRim(canvas, cx, cy, radius, frameData.state)

        canvas.restore()
    }

    private fun selectPhotorealBitmap(
        gender: AvatarGender,
        frameData: AvatarFrameData,
        lipState: LipState,
        lipOpenness: Float,
    ): Bitmap? {
        val isFemale = (gender == AvatarGender.FEMALE)

        // Priority 1: Interactive wink on tap
        if (frameData.isWinking) {
            val winkBmp = if (isFemale) femaleWink else maleWink
            if (winkBmp != null) return winkBmp
        }

        // Priority 2: Blinking (organic blink cycle every 2.5~4.5s)
        if (frameData.blinkProgress > 0.35f) {
            val blinkBmp = if (isFemale) femaleBlink else maleBlink
            if (blinkBmp != null) return blinkBmp
        }

        // Priority 3: Audio-Driven Real-time Lip-Sync Speaking
        if (frameData.state == AvatarState.SPEAKING || lipOpenness > 0.08f) {
            if (lipOpenness > 0.50f || lipState == LipState.WIDE_AO || lipState == LipState.FLAT_EI) {
                val wideBmp = if (isFemale) femaleSpeakWide else maleSpeakWide
                if (wideBmp != null) return wideBmp
            }
            if (lipOpenness > 0.12f || lipState == LipState.HALF_OPEN || lipState == LipState.SLIGHT) {
                val openBmp = if (isFemale) femaleSpeakOpen else maleSpeakOpen
                if (openBmp != null) return openBmp
            }
        }

        // Priority 4: Natural Real-human portrait
        return if (isFemale) femalePhoto else malePhoto
    }

    private fun drawBitmapFrame(canvas: Canvas, cx: Float, cy: Float, radius: Float, bmp: Bitmap?, gender: AvatarGender) {
        if (bmp != null && !bmp.isRecycled) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.isDither = true
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        } else {
            // High quality fallback skin gradient
            paint.reset()
            paint.isAntiAlias = true
            paint.color = if (gender == AvatarGender.FEMALE) Color.parseColor("#FED7AA") else Color.parseColor("#FDE68A")
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawStatusHalo(canvas: Canvas, cx: Float, cy: Float, radius: Float, state: AvatarState, breath: Float) {
        val haloColor = when (state) {
            AvatarState.SPEAKING -> Color.parseColor("#8B5CF6")  // Electric Purple
            AvatarState.LISTENING -> Color.parseColor("#10B981") // Emerald Green
            AvatarState.THINKING -> Color.parseColor("#38BDF8")  // Sky Blue
            AvatarState.ALERT -> Color.parseColor("#F97316")     // Coral Orange
            AvatarState.IDLE -> Color.parseColor("#6366F1")      // Indigo
        }

        val haloAlpha = ((0.28f + breath * 0.28f) * 255).toInt().coerceIn(0, 255)
        val haloRadius = radius * (1.08f + breath * 0.05f)

        val gradient = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(Color.argb(haloAlpha, Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor)), Color.TRANSPARENT),
            floatArrayOf(0.72f, 1.0f),
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
            AvatarState.IDLE -> Color.parseColor("#CBD5E1")
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.0f
        paint.color = rimColor
        canvas.drawCircle(cx, cy, radius - 1.5f, paint)
    }
}
