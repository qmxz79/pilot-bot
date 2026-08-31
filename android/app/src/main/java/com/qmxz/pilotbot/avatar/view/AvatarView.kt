package com.qmxz.pilotbot.avatar.view

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.lipsync.LipSyncEngine
import com.qmxz.pilotbot.avatar.state.AvatarGender
import com.qmxz.pilotbot.avatar.state.AvatarState
import com.qmxz.pilotbot.avatar.state.AvatarStateMachine

/**
 * 100% Native High-Definition Live-Action Video Avatar View.
 * Plays continuous real-human video loops (breathing, organic blinking, speaking lip-sync)
 * powered by Android's hardware-accelerated TextureView and MediaPlayer.
 * Zero WebView, Zero Black Play Buttons, 100% Real Live Actor Quality!
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener, LipSyncEngine.LipSyncListener {

    val stateMachine = AvatarStateMachine()

    private val textureView = TextureView(context)
    private val overlayView = StatusOverlayView(context)

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isSurfaceAvailable = false
    private var currentPlayingAsset: String? = null

    private var activeLipSyncEngine: LipSyncEngine? = null
    private var onInteractiveClickListener: (() -> Unit)? = null

    var avatarGender: AvatarGender = AvatarGender.FEMALE
        set(value) {
            if (field != value) {
                field = value
                updateVideoSource()
            }
        }

    var state: AvatarState
        get() = stateMachine.currentState
        set(value) {
            stateMachine.transitionTo(value)
            overlayView.setStatus(value)
            updateVideoSource()
        }

    init {
        setupViews()
    }

    private fun setupViews() {
        // Circular clipping for TextureView
        textureView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        textureView.surfaceTextureListener = this
        textureView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        textureView.clipToOutline = true
        addView(textureView)

        // Overlay for status glow & circular border
        overlayView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(overlayView)

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            stateMachine.triggerInteractiveReaction()
            onInteractiveClickListener?.invoke()
        }
    }

    fun setOnInteractiveClickListener(listener: () -> Unit) {
        onInteractiveClickListener = listener
    }

    fun attachLipSyncEngine(engine: LipSyncEngine) {
        activeLipSyncEngine?.removeListener(this)
        activeLipSyncEngine = engine
        engine.addListener(this)
    }

    override fun onLipUpdate(state: LipState, rawOpenness: Float) {
        // Live audio energy updates can trigger speaking video state
        if (rawOpenness > 0.15f && this.state != AvatarState.SPEAKING) {
            this.state = AvatarState.SPEAKING
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(surfaceTexture)
        isSurfaceAvailable = true
        playCurrentVideo()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        isSurfaceAvailable = false
        releaseMediaPlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}

    private fun updateVideoSource() {
        if (!isSurfaceAvailable) return
        val isSpeaking = (state == AvatarState.SPEAKING)
        val isFemale = (avatarGender == AvatarGender.FEMALE)

        val targetAsset = when {
            isFemale && isSpeaking -> "avatar/female_speaking.mp4"
            isFemale && !isSpeaking -> "avatar/female_idle.mp4"
            !isFemale && isSpeaking -> "avatar/male_speaking.mp4"
            else -> "avatar/male_idle.mp4"
        }

        if (targetAsset != currentPlayingAsset) {
            currentPlayingAsset = targetAsset
            playVideoAsset(targetAsset)
        }
    }

    private fun playCurrentVideo() {
        val isSpeaking = (state == AvatarState.SPEAKING)
        val isFemale = (avatarGender == AvatarGender.FEMALE)
        val asset = if (isFemale) {
            if (isSpeaking) "avatar/female_speaking.mp4" else "avatar/female_idle.mp4"
        } else {
            if (isSpeaking) "avatar/male_speaking.mp4" else "avatar/male_idle.mp4"
        }
        currentPlayingAsset = asset
        playVideoAsset(asset)
    }

    private fun playVideoAsset(assetPath: String) {
        try {
            val s = surface ?: return
            val afd: AssetFileDescriptor = context.assets.openFd(assetPath)

            val mp = mediaPlayer ?: MediaPlayer().also {
                mediaPlayer = it
            }

            mp.reset()
            mp.setSurface(s)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.prepareAsync()
            mp.setOnPreparedListener { player ->
                player.start()
            }
        } catch (_: Exception) {}
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
            currentPlayingAsset = null
        } catch (_: Exception) {}
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isSurfaceAvailable && mediaPlayer == null) {
            playCurrentVideo()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseMediaPlayer()
        activeLipSyncEngine?.removeListener(this)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            if (isSurfaceAvailable && (mediaPlayer == null || mediaPlayer?.isPlaying == false)) {
                playCurrentVideo()
            }
        } else {
            mediaPlayer?.pause()
        }
    }

    /** Overlay view for luxury circular cockpit border & smart status halo */
    private class StatusOverlayView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val boundsRect = RectF()
        private var rimColor = Color.parseColor("#CBD5E1")

        fun setStatus(state: AvatarState) {
            rimColor = when (state) {
                AvatarState.SPEAKING -> Color.parseColor("#A78BFA")
                AvatarState.LISTENING -> Color.parseColor("#34D399")
                AvatarState.THINKING -> Color.parseColor("#60A5FA")
                AvatarState.ALERT -> Color.parseColor("#FB923C")
                AvatarState.IDLE -> Color.parseColor("#CBD5E1")
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            boundsRect.set(0f, 0f, width.toFloat(), height.toFloat())
            val cx = boundsRect.centerX()
            val cy = boundsRect.centerY()
            val radius = (boundsRect.width().coerceAtMost(boundsRect.height()) / 2.0f)

            // Luxury metallic/glow circular rim
            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = rimColor
            canvas.drawCircle(cx, cy, radius - 1.75f, paint)
        }
    }
}
