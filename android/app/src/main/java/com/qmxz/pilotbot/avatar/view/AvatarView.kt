package com.qmxz.pilotbot.avatar.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.lipsync.LipSyncEngine
import com.qmxz.pilotbot.avatar.state.AvatarGender
import com.qmxz.pilotbot.avatar.state.AvatarState
import com.qmxz.pilotbot.avatar.state.AvatarStateMachine

/**
 * 60 FPS Hardware-Accelerated View for displaying the expressive 2.5D Virtual Copilot.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), LipSyncEngine.LipSyncListener, Choreographer.FrameCallback {

    val stateMachine = AvatarStateMachine()

    private val femaleRenderer = FemaleAvatarRenderer()
    private val maleRenderer = MaleAvatarRenderer()
    private val boundsRect = RectF()

    private var activeLipSyncEngine: LipSyncEngine? = null
    private var currentLipState = LipState.CLOSED
    private var currentLipOpenness = 0.0f

    var avatarGender: AvatarGender = AvatarGender.FEMALE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var state: AvatarState
        get() = stateMachine.currentState
        set(value) {
            stateMachine.transitionTo(value)
        }

    private var isRunning = false
    private var onInteractiveClickListener: (() -> Unit)? = null

    init {
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
        currentLipState = state
        currentLipOpenness = rawOpenness
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimationLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimationLoop()
        activeLipSyncEngine?.removeListener(this)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startAnimationLoop()
        } else {
            stopAnimationLoop()
        }
    }

    private fun startAnimationLoop() {
        if (!isRunning) {
            isRunning = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopAnimationLoop() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (isRunning && isShown) {
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boundsRect.set(0f, 0f, width.toFloat(), height.toFloat())

        val frameData = stateMachine.updateFrame()
        val renderer = if (avatarGender == AvatarGender.FEMALE) femaleRenderer else maleRenderer

        renderer.draw(canvas, boundsRect, frameData, currentLipState, currentLipOpenness)
    }
}
