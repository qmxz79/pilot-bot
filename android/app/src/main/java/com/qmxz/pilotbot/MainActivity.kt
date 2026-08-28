package com.qmxz.pilotbot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.HapticFeedbackConstants
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
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
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.navi.AMapNaviView
import com.qmxz.pilotbot.map.GoogleMapEngine
import com.qmxz.pilotbot.map.GeoPoint as GlobalGeoPoint
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.qmxz.pilotbot.asr.AndroidSpeechToText
import com.qmxz.pilotbot.asr.SmartSpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.config.MapProvider
import com.qmxz.pilotbot.copilot.CopilotEngine
import com.qmxz.pilotbot.enroute.AmapEnRouteDataSource
import com.qmxz.pilotbot.enroute.SystemGpsDataSource
import com.qmxz.pilotbot.llm.OpenAiCompatibleProvider
import com.qmxz.pilotbot.voice.ConversationMode
import com.qmxz.pilotbot.memory.MemoryStore
import com.qmxz.pilotbot.memory.UserMemory
import com.qmxz.pilotbot.navi.AmapNavigationProvider
import com.qmxz.pilotbot.navi.GeoPoint
import com.qmxz.pilotbot.navi.NaviError
import com.qmxz.pilotbot.navi.NaviEventListener
import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.navi.RoutePlan
import com.qmxz.pilotbot.search.PlaceResult
import com.qmxz.pilotbot.search.PlaceSearch
import com.qmxz.pilotbot.tts.AndroidTextToSpeech
import com.qmxz.pilotbot.tts.SmartTextToSpeech
import com.qmxz.pilotbot.voice.VoiceController
import com.qmxz.pilotbot.voice.intent.VoiceIntent
import com.qmxz.pilotbot.voice.intent.VoiceIntentParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Driver Mode UI & Automotive Experience Lead:
 * Fullscreen AMap navigation + Driver big-card view + Copilot companion bubble + Full Voice Intent Dispatcher.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var roadNameText: TextView
    private lateinit var voiceStatus: TextView
    private lateinit var copilotBubbleTag: TextView
    private lateinit var copilotText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var transcriptScroll: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var micButton: MaterialButton
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var searchInput: EditText
    private lateinit var searchResults: View
    private lateinit var searchResultList: LinearLayout
    private lateinit var expandButton: MaterialButton
    private lateinit var panelBody: LinearLayout
    private lateinit var topBar: View
    private lateinit var bottomDriverCard: View
    private lateinit var mapFullscreenHint: View
    private lateinit var floatingMicButton: ExtendedFloatingActionButton
    private lateinit var naviView: AMapNaviView
    private lateinit var googleMapView: WebView

    private lateinit var appConfig: AppConfig
    private lateinit var memoryStore: MemoryStore
    private lateinit var navigationProvider: AmapNavigationProvider
    private lateinit var copilot: CopilotEngine
    private lateinit var voiceController: VoiceController
    private lateinit var tts: SmartTextToSpeech
    private lateinit var smartStt: SmartSpeechToText
    private lateinit var enRoute: AmapEnRouteDataSource
    private lateinit var systemGps: SystemGpsDataSource
    private lateinit var placeSearch: PlaceSearch
    private lateinit var googleMapEngine: GoogleMapEngine

    private var aMap: AMap? = null
    private var googleRoutePolyline: Polyline? = null
    private var locationMarker: Marker? = null
    private val aroundMarkers = mutableListOf<Marker>()
    private val transcript = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var destroyed = false

    private val naviListener = object : NaviEventListener {
        override fun onNaviStateChanged(state: NaviState) = renderOnMain {
            copilot.updateNaviState(state)
            val distStr = if (state.remainingDistanceMeters >= 1000) {
                String.format("%.1f 公里", state.remainingDistanceMeters / 1000.0)
            } else {
                "${state.remainingDistanceMeters} 米"
            }
            val timeMinutes = Math.max(1, state.remainingTimeSeconds / 60)
            val timeStr = if (timeMinutes >= 60) {
                "${timeMinutes / 60} 小时 ${timeMinutes % 60} 分钟"
            } else {
                "$timeMinutes 分钟"
            }

            statusText.text = "剩余 $distStr · 约 $timeStr"
            roadNameText.text = "当前道路：${state.currentRoadName ?: getString(R.string.unknown_road)}"
        }

        override fun onNaviText(text: String) = renderOnMain {
            roadNameText.text = getString(R.string.navi_text_format, text)
            copilot.speakAbout(text)
        }

        override fun onRouteCalculated(route: RoutePlan) = renderOnMain {
            val dist = (route.totalDistanceMeters ?: 0) / 1000.0
            val time = Math.max(1, (route.totalTimeSeconds ?: 0) / 60)
            statusText.text = String.format("路线已就绪：%.1f 公里 · 约 %d 分钟", dist, time)
        }

        override fun onArrived() = renderOnMain {
            statusText.setText(R.string.status_arrived)
            roadNameText.setText(R.string.nav_standby_road)
            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true
            stopButton.visibility = View.GONE
        }

        override fun onNaviError(error: NaviError) = renderOnMain {
            if (error.code == 3) {
                statusText.text = "当前处于海外（高德仅支持中国境内路网）"
                roadNameText.text = "可点击顶部「🗺️ 虚拟行车」体验国内导航解说"
                copilot.speakDirect("当前 GPS 位于海外，高德导航仅支持中国境内路网。你可以点击顶部的「虚拟行车」体验国内路线与副驾互动！")
            } else {
                statusText.text = getString(R.string.navi_error_format, error.code, error.message)
            }
            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true
            stopButton.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appConfig = AppConfig(applicationContext)
        memoryStore = MemoryStore(applicationContext)

        naviView = findViewById(R.id.naviView)
        naviView.onCreate(savedInstanceState)
        naviView.setNaviMode(AMapNaviView.CAR_UP_MODE)
        aMap = naviView.getMap()
        setupMapClick()

        googleMapView = findViewById(R.id.googleMapView)

        statusText = findViewById(R.id.statusText)
        roadNameText = findViewById(R.id.roadNameText)
        voiceStatus = findViewById(R.id.voiceStatus)
        copilotBubbleTag = findViewById(R.id.copilotBubbleTag)
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
        topBar = findViewById(R.id.topBar)
        bottomDriverCard = findViewById(R.id.bottomDriverCard)
        mapFullscreenHint = findViewById(R.id.mapFullscreenHint)
        floatingMicButton = findViewById(R.id.floatingMicButton)

        googleMapEngine = GoogleMapEngine(
            context = applicationContext,
            apiKeyProvider = { appConfig.googleMapsApiKey },
        )
        googleMapEngine.bindWebView(googleMapView) { toggleFullscreenMapMode() }

        // Real-time address auto-complete suggestions on search input typing
        var searchRunnable: Runnable? = null
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                searchRunnable?.let { mainHandler.removeCallbacks(it) }
                if (query.isEmpty()) {
                    searchResults.visibility = View.GONE
                    return
                }
                searchRunnable = Runnable {
                    performSearch(query) { result ->
                        result.onSuccess { list ->
                            renderOnMain { showSearchResults(list) }
                        }
                    }
                }
                mainHandler.postDelayed(searchRunnable!!, 300L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Top action buttons
        findViewById<MaterialButton>(R.id.searchButton).setOnClickListener { doSearch() }
        findViewById<MaterialButton>(R.id.memoryButton).setOnClickListener { showMemoryDialog() }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener { openSettings() }

        // Quick destination & POI shortcut buttons
        findViewById<MaterialButton>(R.id.quickHomeButton).setOnClickListener { handleUserUtterance("回家") }
        findViewById<MaterialButton>(R.id.quickCompanyButton).setOnClickListener { handleUserUtterance("去公司") }
        findViewById<MaterialButton>(R.id.quickGasButton).setOnClickListener { handleUserUtterance("附近加油站") }
        findViewById<MaterialButton>(R.id.quickChargeButton).setOnClickListener { handleUserUtterance("附近充电桩") }
        findViewById<MaterialButton>(R.id.simChinaNaviButton).setOnClickListener { startSimulatedChinaNavigation() }
        findViewById<MaterialButton>(R.id.locateButton).setOnClickListener { onLocateClick() }
        findViewById<MaterialButton>(R.id.aroundButton).setOnClickListener { onAroundClick() }

        // Quick dialogue test chips
        findViewById<MaterialButton>(R.id.quickChipHello).setOnClickListener { handleUserUtterance("你好！") }
        findViewById<MaterialButton>(R.id.quickChipWho).setOnClickListener { handleUserUtterance("你是谁？") }
        findViewById<MaterialButton>(R.id.quickChipJoke).setOnClickListener { handleUserUtterance("讲个笑话解解闷") }
        findViewById<MaterialButton>(R.id.quickChipEta).setOnClickListener { handleUserUtterance("还有多久能到目的地？") }
        findViewById<MaterialButton>(R.id.quickChipRemember).setOnClickListener { handleUserUtterance("记住我爱喝美式咖啡") }

        // Driver card action buttons
        micButton.setOnClickListener { onMicPressed() }
        floatingMicButton.setOnClickListener { onMicPressed() }
        mapFullscreenHint.setOnClickListener { toggleFullscreenMapMode() }
        startButton.setOnClickListener { requestLocationThenStartNavigation() }
        stopButton.setOnClickListener { stopCurrentNavigation() }
        expandButton.setOnClickListener { togglePanel() }
        findViewById<MaterialButton>(R.id.sendButton).setOnClickListener { sendChatText() }
        chatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendChatText()
                true
            } else {
                false
            }
        }
        findViewById<View>(R.id.copilotBubble).setOnClickListener {
            val endpoint = appConfig.endpoint
            if (endpoint.baseUrl.isBlank() || endpoint.apiKey.isBlank() || endpoint.model.isBlank()) {
                openSettings()
            }
        }

        updateBubbleTag()

        tts = SmartTextToSpeech(applicationContext, appConfig).apply {
            onStatusChanged = {
                renderOnMain { refreshVoiceStatus() }
            }
        }
        smartStt = SmartSpeechToText(applicationContext, appConfig)
        refreshVoiceStatus()

        copilot = CopilotEngine(
            config = appConfig,
            llm = OpenAiCompatibleProvider(),
            tts = tts,
            memoryStore = memoryStore,
            onCopilotText = { text -> renderOnMain { copilotText.text = text } },
            onCopilotDone = { text ->
                appendTranscript(getString(R.string.transcript_copilot), text)
                voiceController.onCopilotText(text)
            },
            onSpeakingStart = { voiceController.onCopilotSpeakingStart() },
            onSpeakingEnd = { voiceController.onCopilotSpeakingEnd() },
        )

        voiceController = VoiceController(
            config = appConfig,
            speechToText = smartStt,
            copilot = copilot,
            onListeningState = { listening ->
                renderOnMain { updateMicButtonAppearance(listening) }
            },
            onStatusUpdate = { status ->
                renderOnMain {
                    if (status.isNotEmpty()) {
                        copilotText.text = status
                    }
                }
            },
            onUserText = { text -> appendTranscript(getString(R.string.transcript_user), text) },
            onListenError = { msg ->
                renderOnMain {
                    roadNameText.text = "识别提示：$msg"
                    copilotText.text = "💡 语音提示：$msg"
                    Toast.makeText(this, "语音识别提示：$msg", Toast.LENGTH_LONG).show()
                    copilot.speakDirect("语音识别提示：$msg")
                }
            },
            onInterrupt = { triggerInterruptionFeedback() },
            onUtterance = { utterance -> renderOnMain { handleUserUtterance(utterance) } },
        )

        placeSearch = PlaceSearch(applicationContext)
        systemGps = SystemGpsDataSource(applicationContext)
        enRoute = AmapEnRouteDataSource(applicationContext)

        navigationProvider = AmapNavigationProvider(applicationContext)
        navigationProvider.addListener(naviListener)

        mountMapEngine()
        updateMicButtonAppearance()

        // First-run guidance: jump straight into settings
        if (appConfig.consumeFirstLaunch()) {
            openSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isGoogleMapsActive()) {
            naviView.onResume()
        }
        updateBubbleTag()
        refreshVoiceStatus()
        voiceController.startHandsFreeIfConfigured()
        updateMicButtonAppearance()
        mountMapEngine()
    }

    private fun isGoogleMapsActive(): Boolean =
        appConfig.mapProvider == MapProvider.GOOGLE || appConfig.mapProvider == MapProvider.GOOGLE_MAPS

    private fun performSearch(keyword: String, callback: (Result<List<PlaceResult>>) -> Unit) {
        if (isGoogleMapsActive()) {
            googleMapEngine.searchPlaces(keyword, null, callback)
        } else {
            placeSearch.search(keyword, enRoute.latestLocation()?.cityCode, callback)
        }
    }

    private fun performSearchNearby(keyword: String, callback: (Result<List<PlaceResult>>) -> Unit) {
        if (isGoogleMapsActive()) {
            googleMapEngine.searchPlaces(keyword, null, callback)
        } else {
            val loc = enRoute.latestLocation()
            if (loc != null) {
                placeSearch.searchAround(loc.latitude, loc.longitude, 3000, keyword, callback)
            } else {
                placeSearch.search(keyword, null, callback)
            }
        }
    }

    private fun mountMapEngine() {
        if (isGoogleMapsActive()) {
            // Completely stop AMap background location, navi, and view to consume 0 AMap tokens/quota
            enRoute.stop()
            suspend { navigationProvider.stopNavi() }.startCoroutine(handleCompletion)
            naviView.onPause()
            naviView.visibility = View.GONE

            // Display Google Map WebView and use device native GPS
            googleMapView.visibility = View.VISIBLE
            systemGps.start { lat, lng, addr ->
                renderOnMain {
                    googleMapEngine.updateLocation(lat, lng)
                    val disp = addr ?: "全球定位成功"
                    roadNameText.text = disp
                    statusText.text = "🌍 Google Maps 全球模式"
                    copilot.updateLocation(disp)
                }
            }

            statusText.text = "🌍 Google Maps 全球模式"
            val initialAddr = systemGps.latestAddress() ?: if (appConfig.googleMapsApiKey.isNotBlank()) "Google Maps 就绪 · 全球 GPS 卫星定位" else "Google Maps 模式 · 请在设置填入 Key"
            roadNameText.text = initialAddr
            systemGps.latestAddress()?.let { copilot.updateLocation(it) }
        } else {
            // Completely stop native GPS and Google Map updates
            systemGps.stop()
            googleMapEngine.stopNavigation()
            googleMapView.visibility = View.GONE

            // Resume AMap
            naviView.visibility = View.VISIBLE
            naviView.onResume()
            enRoute.start(
                onAreaChanged = {},
                onFirstFix = { handleLocationFix(it) },
                onLocation = { loc -> updateLocationMarker(loc) },
            )

            if (statusText.text.contains("Google Maps")) {
                statusText.setText(R.string.nav_standby_distance_time)
                roadNameText.setText(R.string.nav_standby_road)
            }
        }
    }

    private fun updateMicButtonAppearance(listening: Boolean = voiceController.isListening) {
        when (appConfig.conversationMode) {
            ConversationMode.FULL_DUPLEX -> {
                if (listening) {
                    micButton.text = "⚡ 全双工 (持续倾听中 · 随时说话/打断)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
                    floatingMicButton.text = "⚡ 全双工 (倾听中)"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))
                } else {
                    micButton.text = "⚡ 全双工 (点击开启倾听)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
                    floatingMicButton.text = "⚡ 全双工 (点击开启)"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#6366F1"))
                }
            }
            ConversationMode.CONTINUOUS -> {
                if (listening) {
                    micButton.text = "👂 连续对话 (免唤醒倾听中)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#10B981"))
                    floatingMicButton.text = "👂 连续对话 (倾听中)"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))
                } else {
                    micButton.text = "👂 连续对话 (点击开启)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
                    floatingMicButton.text = "👂 连续对话 (点击开启)"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#6366F1"))
                }
            }
            ConversationMode.WAKE_WORD -> {
                val wake = appConfig.wakeWord.ifBlank { "小伴" }
                if (listening) {
                    micButton.text = "🎙️ 唤醒模式 (呼唤「$wake」)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#2563EB"))
                    floatingMicButton.text = "🎙️ 呼唤「$wake」"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#2563EB"))
                } else {
                    micButton.text = "🎙️ 唤醒模式 (点击开启)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
                    floatingMicButton.text = "🎙️ 唤醒模式 (点击开启)"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#6366F1"))
                }
            }
            ConversationMode.PUSH_TO_TALK -> {
                if (listening) {
                    micButton.text = "🔴 正在倾听 (点击发送)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
                    floatingMicButton.text = "🔴 正在倾听..."
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#EF4444"))
                } else {
                    micButton.text = "🎙️ 按键说话 (点一下说一句)"
                    micButton.setBackgroundColor(android.graphics.Color.parseColor("#6366F1"))
                    floatingMicButton.text = "🎙️ 按键说话"
                    floatingMicButton.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#6366F1"))
                }
            }
        }
    }

    private fun triggerInterruptionFeedback() {
        renderOnMain {
            micButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            copilotText.text = getString(R.string.interrupted_copilot_hint)
            voiceStatus.text = "⚡ 即时打断"

            // Immediate interruption animation on copilot speech bubble
            val bubble = findViewById<View>(R.id.copilotBubble)
            bubble?.animate()
                ?.scaleX(1.04f)?.scaleY(1.04f)?.alpha(0.85f)
                ?.setDuration(120)
                ?.withEndAction {
                    bubble.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.alpha(1.0f)?.setDuration(120)?.start()
                }?.start()
        }
    }

    private fun updateBubbleTag() {
        val personaName = appConfig.currentPersona().name.ifBlank { "小伴" }
        copilotBubbleTag.text = "🤖 副驾「$personaName」说："
    }

    private fun refreshVoiceStatus() {
        voiceStatus.text = "${tts.status()} · ${smartStt.status()}"
    }

    /**
     * Complete Voice Intent Dispatcher:
     * - NavigateTo(dest) -> PlaceSearch.search -> start navigation
     * - GoHome / GoCompany -> MemoryStore -> PlaceSearch.search -> start navigation
     * - SearchNearby(kw) -> PlaceSearch.searchAround -> announce results
     * - RememberFact(fact) -> MemoryStore.addFact -> confirm
     * - Chat -> CopilotEngine.chat
     */
    private fun handleUserUtterance(utterance: String) {
        val intent = VoiceIntentParser.parse(utterance)
        when (intent) {
            is VoiceIntent.NavigateTo -> {
                val dest = intent.destination
                copilot.speakDirect("正在为你搜索前往 $dest 的路线…")
                performSearch(dest) { result ->
                    result.onSuccess { list ->
                        renderOnMain {
                            if (list.isNotEmpty()) {
                                val place = list.first()
                                copilot.speakDirect("找到目的地 ${place.title}，出发！")
                                startNaviTo(place)
                            } else {
                                copilot.speakDirect("没找到目的地 $dest，请换个地名试试。")
                            }
                        }
                    }.onFailure { e ->
                        renderOnMain {
                            copilot.speakDirect("搜索 $dest 失败：${e.message}")
                        }
                    }
                }
            }

            is VoiceIntent.GoHome -> {
                val home = memoryStore.getMemory().homeAddress
                if (home.isNotBlank()) {
                    copilot.speakDirect("好嘞，准备带你回家！正在规划前往 $home 的路线。")
                    performSearch(home) { result ->
                        result.onSuccess { list ->
                            renderOnMain {
                                if (list.isNotEmpty()) {
                                    startNaviTo(list.first())
                                } else {
                                    copilot.speakDirect("没有找到家地址 $home，请在记忆管理中核对。")
                                }
                            }
                        }.onFailure { e ->
                            renderOnMain {
                                copilot.speakDirect("搜索家地址失败：${e.message}")
                            }
                        }
                    }
                } else {
                    copilot.speakDirect(getString(R.string.home_not_set))
                    showMemoryDialog()
                }
            }

            is VoiceIntent.GoCompany -> {
                val company = memoryStore.getMemory().companyAddress
                if (company.isNotBlank()) {
                    copilot.speakDirect("收到，准备去公司！正在规划前往 $company 的路线。")
                    performSearch(company) { result ->
                        result.onSuccess { list ->
                            renderOnMain {
                                if (list.isNotEmpty()) {
                                    startNaviTo(list.first())
                                } else {
                                    copilot.speakDirect("没有找到公司地址 $company，请在记忆管理中核对。")
                                }
                            }
                        }.onFailure { e ->
                            renderOnMain {
                                copilot.speakDirect("搜索公司地址失败：${e.message}")
                            }
                        }
                    }
                } else {
                    copilot.speakDirect(getString(R.string.company_not_set))
                    showMemoryDialog()
                }
            }

            is VoiceIntent.SearchNearby -> {
                val kw = intent.keyword
                copilot.speakDirect("正在搜索附近的 $kw…")
                performSearchNearby(kw) { result ->
                    result.onSuccess { list ->
                        renderOnMain {
                            showAroundMarkers(list)
                            if (list.isNotEmpty()) {
                                val first = list.first()
                                copilot.speakDirect("附近找到 ${list.size} 个 $kw，最近的是 ${first.title}。")
                            } else {
                                copilot.speakDirect("附近没有找到 $kw。")
                            }
                        }
                    }.onFailure { e ->
                        renderOnMain {
                            copilot.speakDirect("搜索附近 $kw 失败：${e.message}")
                        }
                    }
                }
            }

            is VoiceIntent.WhereAmI -> {
                val addr = if (isGoogleMapsActive()) {
                    systemGps.latestAddress() ?: "GPS 卫星定位中"
                } else {
                    enRoute.latestLocation()?.address ?: "当前定位位置"
                }
                copilot.speakDirect("你现在在：$addr")
            }

            is VoiceIntent.RememberFact -> {
                val fact = intent.fact
                memoryStore.addFact(fact)
                copilot.speakDirect("好嘞，我记住了！")
            }

            is VoiceIntent.Chat -> {
                copilot.chat(intent.text)
            }
        }
    }

    /** Shows the Memory Management dialog to view and edit driver profile and preferences. */
    private fun showMemoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_memory_management, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .create()

        val currentMemory = memoryStore.getMemory()
        val userNameInput = view.findViewById<TextInputEditText>(R.id.memUserNameInput)
        val homeInput = view.findViewById<TextInputEditText>(R.id.memHomeInput)
        val companyInput = view.findViewById<TextInputEditText>(R.id.memCompanyInput)
        val prefContainer = view.findViewById<LinearLayout>(R.id.memPreferencesContainer)
        val newPrefInput = view.findViewById<EditText>(R.id.memNewPrefInput)
        val addPrefButton = view.findViewById<MaterialButton>(R.id.memAddPrefButton)
        val factsContainer = view.findViewById<LinearLayout>(R.id.memFactsContainer)
        val newFactInput = view.findViewById<EditText>(R.id.memNewFactInput)
        val addFactButton = view.findViewById<MaterialButton>(R.id.memAddFactButton)
        val saveButton = view.findViewById<MaterialButton>(R.id.memSaveButton)

        userNameInput.setText(currentMemory.userName)
        homeInput.setText(currentMemory.homeAddress)
        companyInput.setText(currentMemory.companyAddress)

        val workingPreferences = currentMemory.preferences.toMutableList()
        val workingFacts = currentMemory.facts.toMutableList()

        fun refreshPreferenceViews() {
            prefContainer.removeAllViews()
            if (workingPreferences.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "暂无偏好记录"
                    textSize = 12f
                    setTextColor(0xFF94A3B8.toInt())
                    setPadding(8, 4, 8, 4)
                }
                prefContainer.addView(empty)
            } else {
                workingPreferences.forEachIndexed { index, pref ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(4, 4, 4, 4)
                    }
                    val tv = TextView(this).apply {
                        text = "• $pref"
                        textSize = 14f
                        setTextColor(0xFF1E293B.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val delBtn = TextView(this).apply {
                        text = "✕"
                        textSize = 14f
                        setTextColor(0xFFEF4444.toInt())
                        setPadding(16, 0, 16, 0)
                        setOnClickListener {
                            workingPreferences.removeAt(index)
                            refreshPreferenceViews()
                        }
                    }
                    row.addView(tv)
                    row.addView(delBtn)
                    prefContainer.addView(row)
                }
            }
        }

        fun refreshFactViews() {
            factsContainer.removeAllViews()
            if (workingFacts.isEmpty()) {
                val empty = TextView(this).apply {
                    text = "暂无备忘点滴"
                    textSize = 12f
                    setTextColor(0xFF94A3B8.toInt())
                    setPadding(8, 4, 8, 4)
                }
                factsContainer.addView(empty)
            } else {
                workingFacts.forEachIndexed { index, fact ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(4, 4, 4, 4)
                    }
                    val tv = TextView(this).apply {
                        text = "• $fact"
                        textSize = 14f
                        setTextColor(0xFF1E293B.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val delBtn = TextView(this).apply {
                        text = "✕"
                        textSize = 14f
                        setTextColor(0xFFEF4444.toInt())
                        setPadding(16, 0, 16, 0)
                        setOnClickListener {
                            workingFacts.removeAt(index)
                            refreshFactViews()
                        }
                    }
                    row.addView(tv)
                    row.addView(delBtn)
                    factsContainer.addView(row)
                }
            }
        }

        addPrefButton.setOnClickListener {
            val text = newPrefInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                workingPreferences.add(text)
                newPrefInput.text?.clear()
                refreshPreferenceViews()
            }
        }

        addFactButton.setOnClickListener {
            val text = newFactInput.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                workingFacts.add(text)
                newFactInput.text?.clear()
                refreshFactViews()
            }
        }

        refreshPreferenceViews()
        refreshFactViews()

        saveButton.setOnClickListener {
            val updated = UserMemory(
                userName = userNameInput.text?.toString()?.trim().orEmpty(),
                homeAddress = homeInput.text?.toString()?.trim().orEmpty(),
                companyAddress = companyInput.text?.toString()?.trim().orEmpty(),
                preferences = workingPreferences,
                facts = workingFacts,
            )
            memoryStore.saveMemory(updated)
            Toast.makeText(this, R.string.memory_saved_toast, Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun handleLocationFix(location: AMapLocation) {
        val desc = location.address ?: "${location.latitude},${location.longitude}"
        copilot.updateLocation(desc)
        updateLocationMarker(location)
        aMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15f),
        )
    }

    private fun onLocateClick() {
        val loc = enRoute.latestLocation()
        if (loc == null) {
            statusText.text = getString(R.string.search_waiting_location)
            return
        }
        updateLocationMarker(loc)
        aMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16f))
        roadNameText.text = loc.address ?: "定位成功"
    }

    private fun updateLocationMarker(location: AMapLocation) {
        googleMapEngine.updateLocation(location.latitude, location.longitude)
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

    private fun onAroundClick() {
        handleUserUtterance("周边有什么")
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
    }

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
        map.setOnMapClickListener {
            toggleFullscreenMapMode()
        }
    }

    private fun toggleFullscreenMapMode() {
        val isCurrentlyHidden = bottomDriverCard.visibility == View.GONE
        if (isCurrentlyHidden) {
            // Restore full driver dashboard
            topBar.visibility = View.VISIBLE
            bottomDriverCard.visibility = View.VISIBLE
            mapFullscreenHint.visibility = View.GONE
            floatingMicButton.visibility = View.GONE
        } else {
            // Enter 100% full screen map view
            topBar.visibility = View.GONE
            bottomDriverCard.visibility = View.GONE
            searchResults.visibility = View.GONE
            mapFullscreenHint.visibility = View.VISIBLE
            floatingMicButton.visibility = View.VISIBLE
        }
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
        statusText.text = if (isGoogleMapsActive()) "Google 全球搜索中…" else getString(R.string.searching)
        searchResults.visibility = View.GONE
        performSearch(keyword) { result ->
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
            val empty = TextView(this).apply {
                text = "未找到匹配地址，请换个关键词试试"
                textSize = 13f
                setTextColor(android.graphics.Color.parseColor("#64748B"))
                setPadding(20, 16, 20, 16)
            }
            searchResultList.addView(empty)
        } else {
            results.forEach { place ->
                val itemView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 12, 16, 12)
                    isClickable = true
                    isFocusable = true
                    setBackgroundResource(android.R.drawable.list_selector_background)

                    val titleRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL

                        val icon = TextView(context).apply {
                            text = "📍 "
                            textSize = 14f
                        }
                        val titleText = TextView(context).apply {
                            text = place.title
                            textSize = 15f
                            setTextColor(android.graphics.Color.parseColor("#0F172A"))
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        addView(icon)
                        addView(titleText)
                    }

                    addView(titleRow)

                    if (place.snippet.isNotBlank()) {
                        val snippetText = TextView(context).apply {
                            text = place.snippet
                            textSize = 12f
                            setTextColor(android.graphics.Color.parseColor("#64748B"))
                            setPadding(24, 4, 0, 0)
                        }
                        addView(snippetText)
                    }

                    setOnClickListener {
                        searchInput.setText(place.title)
                        searchResults.visibility = View.GONE
                        startNaviTo(place)
                    }
                }
                searchResultList.addView(itemView)

                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply { setMargins(16, 0, 16, 0) }
                    setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))
                }
                searchResultList.addView(divider)
            }
        }
        searchResults.visibility = View.VISIBLE
    }

    private fun startNaviTo(place: PlaceResult) {
        if (isGoogleMapsActive()) {
            if (place.lat == 0.0 && place.lng == 0.0) {
                statusText.text = "正在获取 ${place.title} 的经纬度…"
                googleMapEngine.searchPlaces(place.title, null) { res ->
                    res.onSuccess { list ->
                        val matched = list.firstOrNull { it.lat != 0.0 && it.lng != 0.0 } ?: place
                        renderOnMain { startNaviTo(matched) }
                    }.onFailure {
                        renderOnMain {
                            statusText.text = "获取位置坐标失败"
                            copilot.speakDirect("无法获取目的地坐标")
                        }
                    }
                }
                return
            }

            val startLoc = systemGps.latestLocation()
            val startLat = startLoc?.latitude ?: enRoute.latestLocation()?.latitude ?: 3.1390
            val startLng = startLoc?.longitude ?: enRoute.latestLocation()?.longitude ?: 101.6869

            statusText.text = "Google Maps 正在规划路线…"
            googleMapEngine.calculateRoute(
                start = GlobalGeoPoint(lat = startLat, lng = startLng),
                dest = GlobalGeoPoint(lat = place.lat, lng = place.lng),
            ) { result ->
                result.onSuccess { route ->
                    renderOnMain {
                        val distKm = String.format("%.1f", route.totalDistanceMeters / 1000.0)
                        val mins = (route.totalDurationSeconds / 60).coerceAtLeast(1)
                        statusText.text = "Google 导航 · 剩余 $distKm 公里 · 约 $mins 分钟"
                        roadNameText.text = "目的地：${place.title} (${route.routeName})"
                        startButton.visibility = View.GONE
                        stopButton.visibility = View.VISIBLE
                        stopButton.isEnabled = true
                        copilot.speakDirect("已为您通过 Google Maps 规划好前往 ${place.title} 的路线，全程约 $distKm 公里，预计耗时 $mins 分钟。出发！")
                        googleMapEngine.startNavigation()
                        googleMapEngine.drawRoute(
                            route = route,
                            start = GlobalGeoPoint(startLat, startLng),
                            dest = GlobalGeoPoint(place.lat, place.lng),
                            destTitle = place.title,
                        )
                        drawGoogleRouteOnMap(route, place)
                    }
                }.onFailure { e ->
                    renderOnMain {
                        statusText.text = "Google 算路失败：${e.message}"
                        roadNameText.text = "请检查 Google Maps API Key"
                        copilot.speakDirect("Google 路线规划失败：${e.message}")
                    }
                }
            }
            return
        }

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

    private fun drawGoogleRouteOnMap(route: com.qmxz.pilotbot.map.RouteSummary, place: PlaceResult) {
        val map = aMap ?: return
        googleRoutePolyline?.remove()
        aroundMarkers.forEach { it.remove() }
        aroundMarkers.clear()

        val points = route.polylinePoints.map { LatLng(it.lat, it.lng) }
        if (points.isNotEmpty()) {
            googleRoutePolyline = map.addPolyline(
                PolylineOptions()
                    .addAll(points)
                    .color(android.graphics.Color.parseColor("#2563EB"))
                    .width(14f)
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lng), 14f))
        }

        val destMarker = map.addMarker(
            MarkerOptions()
                .position(LatLng(place.lat, place.lng))
                .title(place.title)
                .snippet(place.snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
        destMarker?.showInfoWindow()
    }

    private fun onMicPressed() {
        micButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION_REQUEST,
            )
            return
        }
        val apiKey = appConfig.endpoint.apiKey.trim()
        if (apiKey.isBlank() && !smartStt.isSystemAsrAvailable) {
            Toast.makeText(this, "💡 请先在右上角「设置」中填入 API Key 开启语音对话！", Toast.LENGTH_LONG).show()
            openSettings()
            return
        }
        voiceController.toggleMic()
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
        if (isGoogleMapsActive()) {
            startNaviTo(
                PlaceResult(
                    title = "吉隆坡双子塔 (KLCC)",
                    snippet = "Kuala Lumpur City Centre, Malaysia",
                    lat = 3.1578,
                    lng = 101.7123,
                )
            )
            return
        }
        val last = enRoute.latestLocation()
        if (last != null && last.latitude > 3.86 && last.latitude < 53.55 && last.longitude > 73.66 && last.longitude < 135.05) {
            beginNavigation(
                start = GeoPoint(longitude = last.longitude, latitude = last.latitude),
                destination = GeoPoint(longitude = last.longitude + 0.01, latitude = last.latitude + 0.01),
            )
        } else {
            startSimulatedChinaNavigation()
        }
    }

    private fun startSimulatedChinaNavigation() {
        copilot.speakDirect("启动北京经典路线虚拟行车：从天安门到奥林匹克公园，出发！")
        beginNavigation(
            start = GeoPoint(longitude = 116.3913, latitude = 39.9075),
            destination = GeoPoint(longitude = 116.397428, latitude = 39.99230),
        )
    }

    private fun beginNavigation(start: GeoPoint, destination: GeoPoint) {
        startButton.visibility = View.GONE
        stopButton.visibility = View.VISIBLE
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
        if (isGoogleMapsActive()) {
            googleMapEngine.stopNavigation()
            googleRoutePolyline?.remove()
            googleRoutePolyline = null
            stopButton.visibility = View.GONE
            startButton.visibility = View.VISIBLE
            startButton.isEnabled = true
            statusText.setText(R.string.nav_standby_distance_time)
            roadNameText.setText(R.string.status_navigation_stopped)
            copilot.speakDirect(getString(R.string.status_navigation_stopped))
            return
        }
        stopButton.visibility = View.GONE
        startButton.visibility = View.VISIBLE
        startButton.isEnabled = true
        statusText.setText(R.string.nav_standby_distance_time)
        roadNameText.setText(R.string.status_navigation_stopped)
        enRoute.stop()
        copilot.resetFatigueMonitor()
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
                    startButton.visibility = View.VISIBLE
                    startButton.isEnabled = true
                    stopButton.visibility = View.GONE
                }
            }
        }
    }

    private fun sendChatText() {
        val text = chatInput.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        appendTranscript(getString(R.string.transcript_user), text)
        chatInput.text?.clear()
        handleUserUtterance(text)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun togglePanel() {
        val collapsed = panelBody.visibility == View.GONE
        panelBody.visibility = if (collapsed) View.VISIBLE else View.GONE
        expandButton.text = if (collapsed) "▼ 收起" else "💬 更多"
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

    override fun onPause() {
        super.onPause()
        naviView.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        systemGps.stop()
        googleMapEngine.onDestroy()
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
