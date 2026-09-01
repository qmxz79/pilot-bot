package com.qmxz.pilotbot.avatar.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.qmxz.pilotbot.avatar.lipsync.LipState
import com.qmxz.pilotbot.avatar.lipsync.LipSyncEngine
import com.qmxz.pilotbot.avatar.state.AvatarGender
import com.qmxz.pilotbot.avatar.state.AvatarState
import com.qmxz.pilotbot.avatar.state.AvatarStateMachine

/**
 * 60 FPS Hardware-Accelerated 3D Interactive Digital Human View.
 * Loads and renders genuine 3D rigged character models with real-time 3D
 * skeletal dynamics, speech cadence nodding, and glance tracking.
 */
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), LipSyncEngine.LipSyncListener {

    val stateMachine = AvatarStateMachine()
    private val webView = WebView(context)

    private var activeLipSyncEngine: LipSyncEngine? = null
    private var isPageLoaded = false
    private var onInteractiveClickListener: (() -> Unit)? = null

    var avatarGender: AvatarGender = AvatarGender.FEMALE
        set(value) {
            field = value
            sendJsCommand("window.setAvatarGender('${if (value == AvatarGender.MALE) "male" else "female"}')")
        }

    var state: AvatarState
        get() = stateMachine.currentState
        set(value) {
            stateMachine.transitionTo(value)
            sendJsCommand("window.setAvatarState('${value.name.lowercase()}')")
        }

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        webView.addJavascriptInterface(AvatarJsBridge(), "AndroidBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
                sendJsCommand("window.setAvatarGender('${if (avatarGender == AvatarGender.MALE) "male" else "female"}')")
                sendJsCommand("window.setAvatarState('${state.name.lowercase()}')")
            }
        }

        webView.loadUrl("file:///android_asset/avatar/avatar_3d.html")
        addView(webView)
    }

    private fun sendJsCommand(script: String) {
        post {
            if (isPageLoaded) {
                webView.evaluateJavascript(script, null)
            }
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
        sendJsCommand("window.setLipOpenness($rawOpenness)")
    }

    fun triggerWink() {
        sendJsCommand("window.triggerWink()")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activeLipSyncEngine?.removeListener(this)
    }

    inner class AvatarJsBridge {
        @JavascriptInterface
        fun onAvatarClicked() {
            post {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                stateMachine.triggerInteractiveReaction()
                onInteractiveClickListener?.invoke()
            }
        }
    }
}
