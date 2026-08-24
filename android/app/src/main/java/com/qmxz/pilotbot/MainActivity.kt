package com.qmxz.pilotbot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amap.api.location.AMapLocation
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.navi.AMapNaviView
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
import com.qmxz.pilotbot.search.PlaceResult
import com.qmxz.pilotbot.search.PlaceSearch
import com.qmxz.pilotbot.tts.AndroidTextToSpeech
import com.qmxz.pilotbot.voice.VoiceController
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Harness: fullscreen map + nav + M1 copilot loop + M2 voice chat + destination search. */
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var voiceStatus: TextView
    private lateinit var copilotText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var micButton: MaterialButton
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var searchInput: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var searchResultList: LinearLayout
    private lateinit var expandButton: MaterialButton
    private lateinit var panelBody: LinearLayout
    private lateinit var naviView: AMapNaviView
    private lateinit var navigationProvider: AmapNavigationProvider
    private lateinit var copilot: CopilotEngine
    private lateinit var voiceController: VoiceController
    private lateinit var tts: AndroidTextToSpeech
    private lateinit var enRoute: AmapEnRouteDataSource
    private lateinit var placeSearch: PlaceSearch
    private var aMap: AMap? = null
    private var locationMarker: Marker? = null
    private val aroundMarkers = mutableListOf<Marker>()
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

        naviView = findViewById(R.id.naviView)
        naviView.onCreate(savedInstanceState)
        naviView.setNaviMode(AMapNaviView.CAR_UP_MODE)
        aMap = naviView.getMap()
        setupMapClick()

        statusText = findViewById(R.id.statusText)
        voiceStatus = findViewById(R.id.voiceStatus)
        copilotText = findViewById(R.id.copilotText)
        transcriptText = findViewById(R.id.transcriptText)
        transcriptScroll = findViewById(R.id.transcriptScroll)
        chatInput = findViewById(R.id.chatInput)
        micButton = findViewById(R.id.micButton)
        startButton = findViewById(R.id.startNavigationButton)
        stopButton = findViewById(R.id.stopNavigationButton)
        searchInput = findViewById(R.id.searchInput)
        searchResults = findViewById(R.id.searchResults)
        searchResultList = findViewById(R.id.searchResultList)
        expandButton = findViewById(R.id.expandButton)
        panelBody = findViewById(R.id.panelBody)

        findViewById<MaterialButton>(R.id.searchButton).setOnClickListener { doSearch() }
        findViewById<MaterialButton>(R.id.locateButton).setOnClickListener { onLocateClick() }
        findViewById<MaterialButton>(R.id.aroundButton).setOnClickListener { onAroundClick() }
        findViewById<MaterialButton>(R.id.simulateButton).setOnClickListener {
            copilot.speakAbout(getString(R.string.simulated_broadcast_text))
        }
        findViewById<MaterialButton>(R.id.simulateNarrationButton).setOnClickListener {
            copilot.narrate(getString(R.string.simulated_narration_text))
        }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener { openSettings() }
        micButton.setOnClickListener { onMicPressed() }
        findViewById<MaterialButton>(R.id.sendButton).setOnClickListener { sendChatText() }
        expandButton.setOnClickListener { togglePanel() }

        tts = AndroidTextToSpeech(applicationContext)
        refreshVoiceStatus()
        mainHandler.postDelayed({ refreshVoiceStatus() }, 1500L)
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
            onListenError = { msg -> renderOnMain { statusText.text = "听不清：$msg" } },
        )

        placeSearch = PlaceSearch(applicationContext)
        enRoute = AmapEnRouteDataSource(applicationContext)
        // Locate immediately on launch (map + copilot know the real position).
        enRoute.start(
            onAreaChanged = {},
            onFirstFix = { handleLocationFix(it) },
            onLocation = { loc -> updateLocationMarker(loc) },
        )

        navigationProvider = AmapNavigationProvider(applicationContext)
        navigationProvider.addListener(naviListener)
        startButton.setOnClickListener { requestLocationThenStartNavigation() }
        stopButton.setOnClickListener { stopCurrentNavigation() }

        // First-run guidance: jump straight into settings to wire model/persona/voice mode.
        if (AppConfig(applicationContext).consumeFirstLaunch()) {
            openSettings()
        }
    }

    private fun refreshVoiceStatus() {
        val asrOk = SpeechRecognizer.isRecognitionAvailable(this)
        voiceStatus.text = "${tts.status()} · 识别:${if (asrOk) "可用" else "不可用"}"
    }

    private fun handleLocationFix(location: AMapLocation) {
        val desc = location.address ?: "${location.latitude},${location.longitude}"
        copilot.updateLocation(desc)
        updateLocationMarker(location)
        aMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f),
        )
    }

    /** 「定位」：把相机移到当前位置并显示/更新大头针。 */
    private fun onLocateClick() {
        val loc = enRoute.latestLocation()
        if (loc == null) {
            statusText.text = getString(R.string.search_waiting_location)
            return
        }
        updateLocationMarker(loc)
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
        statusText.text = loc.address ?: "定位成功"
    }

    /** 每次定位 fix 更新大头针位置（复用同一 marker，避免堆叠）。 */
    private fun updateLocationMarker(location: AMapLocation) {
        val map = aMap ?: return
        val latLng = LatLng(location.latitude, location.longitude)
        val existing = locationMarker
        if (existing != null) {
            existing.position = latLng
        } else {
            locationMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(location.address ?: "当前位置")
                    .icon(BitmapDescriptorFactory.defaultMarker()),
            )
        }
    }

    /** 「周边」：搜当前位置 500m 内 POI，散落大头针。 */
    private fun onAroundClick() {
        val loc = enRoute.latestLocation()
        if (loc == null) {
            statusText.text = getString(R.string.search_waiting_location)
            return
        }
        statusText.text = getString(R.string.searching)
        placeSearch.searchAround(loc.latitude, loc.longitude, 500) { result ->
            result.onSuccess { list ->
                renderOnMain { showAroundMarkers(list) }
            }.onFailure { e ->
                renderOnMain { statusText.text = "周边搜索失败：${e.message}" }
            }
        }
    }

    private fun showAroundMarkers(results: List<PlaceResult>) {
        val map = aMap ?: return
        aroundMarkers.forEach { it.remove() }
        aroundMarkers.clear()
        results.forEach { place ->
            aroundMarkers += map.addMarker(
                MarkerOptions()
                    .position(LatLng(place.lat, place.lng))
                    .title(place.title)
                    .snippet(place.snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker()),
            )
        }
        statusText.text = "附近找到 ${results.size} 个地点，点大头针看详情"
    }

    /** Marker 点击显示气泡（title + snippet）。 */
    private fun setupMapClick() {
        val map = aMap ?: return
        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
        map.setInfoWindowAdapter(object : AMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View = makeInfoView(marker)

            override fun getInfoContents(marker: Marker): View = makeInfoView(marker)
        })
    }

    private fun makeInfoView(marker: Marker): View = TextView(this).apply {
        text = "${marker.title ?: ""}\n${marker.snippet ?: ""}"
        setPadding(16, 10, 16, 10)
        setTextColor(android.graphics.Color.BLACK)
        background = android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
    }

    private fun doSearch() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        if (keyword.isEmpty()) return
        statusText.text = getString(R.string.searching)
        searchResults.visibility = View.GONE
        placeSearch.search(keyword, enRoute.latestLocation()?.cityCode) { result ->
            result.onSuccess { list ->
                renderOnMain { showSearchResults(list) }
            }.onFailure { e ->
                renderOnMain {
                    statusText.text = "搜索失败：${e.message}"
                    searchResults.visibility = View.GONE
                }
            }
        }
    }

    private fun showSearchResults(results: List<PlaceResult>) {
        searchResultList.removeAllViews()
        if (results.isEmpty()) {
            val empty = TextView(this).apply { text = getString(R.string.search_no_result) }
            searchResultList.addView(empty)
        } else {
            results.forEach { place ->
                val row = TextView(this).apply {
                    text = "${place.title}\n  ${place.snippet}"
                    textSize = 14f
                    setPadding(12, 8, 12, 8)
                    setOnClickListener { startNaviTo(place) }
                }
                searchResultList.addView(row)
            }
        }
        searchResults.visibility = View.VISIBLE
    }

    private fun startNaviTo(place: PlaceResult) {
        val start = enRoute.latestLocation()
        if (start == null) {
            statusText.text = getString(R.string.search_waiting_location)
            return
        }
        beginNavigation(
            start = GeoPoint(start.longitude, start.latitude),
            destination = GeoPoint(place.lng, place.lat),
        )
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
        beginNavigation(
            start = GeoPoint(longitude = 116.3913, latitude = 39.9075),
            destination = GeoPoint(longitude = 116.397428, latitude = 39.90923),
        )
    }

    private fun beginNavigation(start: GeoPoint, destination: GeoPoint) {
        startButton.isEnabled = false
        stopButton.isEnabled = true
        statusText.setText(R.string.status_calculating_route)
        val route = RoutePlan(start = start, destination = destination)
        suspend { navigationProvider.startNavi(route) }.startCoroutine(handleCompletion)
        enRoute.start(
            onAreaChanged = { area ->
                copilot.narrate(
                    getString(R.string.narration_area_format, area.province, area.city),
                )
            },
            onFirstFix = { handleLocationFix(it) },
            onLocation = { loc -> updateLocationMarker(loc) },
        )
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

    private fun sendChatText() {
        val text = chatInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        appendTranscript(getString(R.string.transcript_user), text)
        chatInput.text?.clear()
        copilot.chat(text)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun togglePanel() {
        val collapsed = panelBody.visibility == View.GONE
        panelBody.visibility = if (collapsed) View.VISIBLE else View.GONE
        expandButton.text = getString(if (collapsed) R.string.collapse_panel else R.string.expand_panel)
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

    override fun onResume() {
        super.onResume()
        naviView.onResume()
    }

    override fun onPause() {
        super.onPause()
        naviView.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        naviView.onDestroy()
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
