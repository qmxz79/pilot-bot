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
 * High-fidelity 60FPS Photorealistic Digital Human Renderer.
 * Utilizes pre-aligned photographic frame sprites (idle, blink, wink, speak_open, speak_wide)
 * combined with continuous sinusoidal breathing, speech head-nodding, state-based leaning,
 * and audio-driven lip-sync frame interpolation.
 */
class RealisticAvatarRenderer(private val context: Context) : AvatarRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    // Female photographic frame assets
    private var femaleIdle: Bitmap? = null
    private var femaleBlink: Bitmap? = null
    private var femaleWink: Bitmap? = null
    private var femaleSpeakOpen: Bitmap? = null
    private var femaleSpeakWide: Bitmap? = null

    // Male photographic frame assets
    private var maleIdle: Bitmap? = null
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
            femaleIdle = BitmapFactory.decodeResource(res, R.drawable.female_real_idle, opts)
            femaleBlink = BitmapFactory.decodeResource(res, R.drawable.female_real_blink, opts)
            femaleWink = BitmapFactory.decodeResource(res, R.drawable.female_real_wink, opts)
            femaleSpeakOpen = BitmapFactory.decodeResource(res, R.drawable.female_real_speak_open, opts)
            femaleSpeakWide = BitmapFactory.decodeResource(res, R.drawable.female_real_speak_wide, opts)

            maleIdle = BitmapFactory.decodeResource(res, R.drawable.male_real_idle, opts)
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

        // 2. Setup circular clipping mask for portrait
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 3. Clear, visible physical breathing & head nod/tilt transformation
        val breathScale = 1.0f + (frameData.breathingFactor - 0.5f) * 0.045f // 4.5% visible breathing scale
        val breathOffsetY = (frameData.breathingFactor - 0.5f) * 3.5f * u

        canvas.save()
        canvas.scale(breathScale, breathScale, cx, cy)
        canvas.translate(0f, breathOffsetY)
        canvas.rotate(frameData.bodyTiltDegrees, cx, cy + radius * 0.8f)

        // 4. Select the appropriate photographic frame according to real-time state & lip sync
        val activeBitmap = selectPhotorealBitmap(gender, frameData, lipState, lipOpenness)

        // 5. Draw active photorealistic human frame
        drawBitmapFrame(canvas, cx, cy, radius, activeBitmap, gender)

        canvas.restore()

        // 6. Draw circular luxury border rim
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
            return if (isFemale) femaleWink ?: femaleIdle else maleWink ?: maleIdle
        }

        // Priority 2: Blinking (organic blink cycle every 2.5~5s)
        if (frameData.blinkProgress > 0.35f) {
            return if (isFemale) femaleBlink ?: femaleIdle else maleBlink ?: maleIdle
        }

        // Priority 3: Audio-Driven Real-time Lip-Sync Speaking
        if (frameData.state == AvatarState.SPEAKING || lipOpenness > 0.08f) {
            return when {
                lipOpenness > 0.55f || lipState == LipState.WIDE_AO || lipState == LipState.FLAT_EI -> {
                    if (isFemale) femaleSpeakWide ?: femaleSpeakOpen ?: femaleIdle else maleSpeakWide ?: maleSpeakOpen ?: maleIdle
                }
                lipOpenness > 0.12f || lipState == LipState.HALF_OPEN || lipState == LipState.SLIGHT -> {
                    if (isFemale) femaleSpeakOpen ?: femaleIdle else maleSpeakOpen ?: maleIdle
                }
                else -> {
                    if (isFemale) femaleIdle else maleIdle
                }
            }
        }

        // Priority 4: Natural Idle resting
        return if (isFemale) femaleIdle else maleIdle
    }

    private fun drawBitmapFrame(canvas: Canvas, cx: Float, cy: Float, radius: Float, bmp: Bitmap?, gender: AvatarGender) {
        if (bmp != null && !bmp.isRecycled) {
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        } else {
            // Fallback fill if bitmap not ready
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
            AvatarState.IDLE -> Color.parseColor("#6366F1")      // Indigo
        }

        val haloAlpha = ((0.30f + breath * 0.30f) * 255).toInt().coerceIn(0, 255)
        val haloRadius = radius * (1.08f + breath * 0.05f)

        val gradient = RadialGradient(
            cx, cy, haloRadius,
            intArrayOf(Color.argb(haloAlpha, Color.red(haloColor), Color.green(haloColor), Color.blue(haloColor)), Color.TRANSPARENT),
            floatArrayOf(0.70f, 1.0f),
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
        paint.strokeWidth = 3.2f
        paint.color = rimColor
        canvas.drawCircle(cx, cy, radius - 1.6f, paint)
    }
}
