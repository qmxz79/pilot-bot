package com.qmxz.pilotbot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.qmxz.pilotbot.asr.AndroidSpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.copilot.CopilotEngine
import com.qmxz.pilotbot.enroute.AmapEnRouteDataSource
import com.qmxz.pilotbot.llm.OpenAiCompatibleProvider
import com.qmxz.pilotbot.navi.AmapNavigationProvider
import com.qmxz.pilotbot.navi.GeoPoint
import com.qmxz.pilotbot.navi.NaviError
import com.qmxz.pilotbot.navi.NaviEventListener
import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.navi.RoutePlan
import com.qmxz.pilotbot.tts.AndroidTextToSpeech
import com.qmxz.pilotbot.voice.VoiceController
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Harness: real Amap navigation + M1 copilot loop + M2 voice chat (mode-selectable). */
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var copilotText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var micButton: MaterialButton
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var navigationProvider: AmapNavigationProvider
    private lateinit var copilot: CopilotEngine
    private lateinit var voiceController: VoiceController
    private lateinit var tts: AndroidTextToSpeech
    private lateinit var enRoute: AmapEnRouteDataSource
    private val transcript = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var destroyed = false

    private val naviListener = object : NaviEventListener {
        override fun onNaviStateChanged(state: NaviState) = renderOnMain {
            copilot.updateNaviState(state)
            statusText.text = getString(
                R.string.navi_state_format,
                state.remainingDistanceMeters,
                state.remainingTimeSeconds,
                state.currentRoadName ?: getString(R.string.unknown_road),
            )
        }

        override fun onNaviText(text: String) = renderOnMain {
            statusText.text = getString(R.string.navi_text_format, text)
            copilot.speakAbout(text)
        }

        override fun onRouteCalculated(route: RoutePlan) = renderOnMain {
            statusText.text = getString(
                R.string.route_calculated_format,
                route.totalDistanceMeters ?: 0,
                route.totalTimeSeconds ?: 0,
            )
        }

        override fun onArrived() = renderOnMain {
            statusText.setText(R.string.status_arrived)
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }

        override fun onNaviError(error: NaviError) = renderOnMain {
            statusText.text = getString(R.string.navi_error_format, error.code, error.message)
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        copilotText = findViewById(R.id.copilotText)
        transcriptText = findViewById(R.id.transcriptText)
        transcriptScroll = findViewById(R.id.transcriptScroll)
        micButton = findViewById(R.id.micButton)
        startButton = findViewById(R.id.startNavigationButton)
        stopButton = findViewById(R.id.stopNavigationButton)

        findViewById<MaterialButton>(R.id.simulateButton).setOnClickListener {
            copilot.speakAbout(getString(R.string.simulated_broadcast_text))
        }
        findViewById<MaterialButton>(R.id.simulateNarrationButton).setOnClickListener {
            copilot.narrate(getString(R.string.simulated_narration_text))
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        micButton.setOnClickListener { onMicPressed() }

        tts = AndroidTextToSpeech(applicationContext)
        copilot = CopilotEngine(
            config = AppConfig(applicationContext),
            llm = OpenAiCompatibleProvider(),
            tts = tts,
            onCopilotText = { text -> renderOnMain { copilotText.text = text } },
            onCopilotDone = { text -> appendTranscript(getString(R.string.transcript_copilot), text) },
            onSpeakingStart = { voiceController.onCopilotSpeakingStart() },
            onSpeakingEnd = { voiceController.onCopilotSpeakingEnd() },
        )
        voiceController = VoiceController(
            config = AppConfig(applicationContext),
            speechToText = AndroidSpeechToText(applicationContext),
            copilot = copilot,
            onListeningState = { listening ->
                renderOnMain {
                    micButton.text = getString(
                        if (listening) R.string.mic_button_listening else R.string.mic_button,
                    )
                }
            },
            onUserText = { text -> appendTranscript(getString(R.string.transcript_user), text) },
        )

        enRoute = AmapEnRouteDataSource(applicationContext)

        navigationProvider = AmapNavigationProvider(applicationContext)
        navigationProvider.addListener(naviListener)
        startButton.setOnClickListener { requestLocationThenStartNavigation() }
        stopButton.setOnClickListener { stopCurrentNavigation() }
    }

    private fun onMicPressed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            voiceController.toggleMic()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST,
            )
        }
    }

    private fun requestLocationThenStartNavigation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startTestNavigation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST,
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    startTestNavigation()
                } else {
                    statusText.setText(R.string.location_permission_required)
                }
            }
            MIC_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    voiceController.toggleMic()
                } else {
                    statusText.setText(R.string.mic_permission_required)
                }
            }
        }
    }

    private fun startTestNavigation() {
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.setText(R.string.status_calculating_route)
        val route = RoutePlan(
            start = GeoPoint(longitude = 116.3913, latitude = 39.9075),
            destination = GeoPoint(longitude = 116.397428, latitude = 39.90923),
        )
        suspend { navigationProvider.startNavi(route) }.startCoroutine(handleCompletion)
        enRoute.start { area ->
            copilot.narrate(
                getString(R.string.narration_area_format, area.province, area.city),
            )
        }
    }

    private fun stopCurrentNavigation() {
        stopButton.isEnabled = false
        startButton.isEnabled = true
        statusText.setText(R.string.status_navigation_stopped)
        enRoute.stop()
        suspend { navigationProvider.stopNavi() }.startCoroutine(handleCompletion)
    }

    private val handleCompletion = object : Continuation<Unit> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.exceptionOrNull()?.let { error ->
                renderOnMain {
                    statusText.text = getString(
                        R.string.navi_error_format,
                        -1,
                        error.message ?: error.javaClass.simpleName,
                    )
                    startButton.isEnabled = true
                    stopButton.isEnabled = false
                }
            }
        }
    }

    private fun appendTranscript(role: String, text: String) {
        renderOnMain {
            transcript.append(role).append('：').append(text).append('\n')
            transcriptText.text = transcript.toString()
            transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun renderOnMain(block: () -> Unit) {
        mainHandler.post {
            if (!destroyed && !isFinishing && !isDestroyed) block()
        }
    }

    override fun onDestroy() {
        destroyed = true
        mainHandler.removeCallbacksAndMessages(null)
        navigationProvider.removeListener(naviListener)
        enRoute.destroy()
        voiceController.shutdown()
        copilot.close()
        tts.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val LOCATION_PERMISSION_REQUEST = 1001
        const val MIC_PERMISSION_REQUEST = 1002
    }
}
