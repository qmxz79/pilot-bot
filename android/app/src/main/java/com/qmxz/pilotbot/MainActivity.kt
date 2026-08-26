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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import com.amap.api.navi.AMapNaviView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.qmxz.pilotbot.asr.AndroidSpeechToText
import com.qmxz.pilotbot.asr.SmartSpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.copilot.CopilotEngine
import com.qmxz.pilotbot.enroute.AmapEnRouteDataSource
import com.qmxz.pilotbot.llm.OpenAiCompatibleProvider
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
    private lateinit var naviView: AMapNaviView

    private lateinit var appConfig: AppConfig
    private lateinit var memoryStore: MemoryStore
    private lateinit var navigationProvider: AmapNavigationProvider
    private lateinit var copilot: CopilotEngine
    private lateinit var voiceController: VoiceController
    private lateinit var tts: SmartTextToSpeech
    private lateinit var smartStt: SmartSpeechToText
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
            statusText.text = getString(R.string.navi_error_format, error.code, error.message)
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

        // Top action buttons
        findViewById<MaterialButton>(R.id.searchButton).setOnClickListener { doSearch() }
        findViewById<MaterialButton>(R.id.memoryButton).setOnClickListener { showMemoryDialog() }
        findViewById<MaterialButton>(R.id.settingsButton).setOnClickListener { openSettings() }

        // Quick destination & POI shortcut buttons
        findViewById<MaterialButton>(R.id.quickHomeButton).setOnClickListener { handleUserUtterance("回家") }
        findViewById<MaterialButton>(R.id.quickCompanyButton).setOnClickListener { handleUserUtterance("去公司") }
        findViewById<MaterialButton>(R.id.quickGasButton).setOnClickListener { handleUserUtterance("附近加油站") }
        findViewById<MaterialButton>(R.id.quickChargeButton).setOnClickListener { handleUserUtterance("附近充电桩") }
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
        findViewById<MaterialButton>(R.id.simulateButton).setOnClickListener {
            copilot.speakAbout(getString(R.string.simulated_broadcast_text))
        }
        findViewById<MaterialButton>(R.id.simulateNarrationButton).setOnClickListener {
            copilot.narrate(getString(R.string.simulated_narration_text))
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
            onCopilotDone = { text -> appendTranscript(getString(R.string.transcript_copilot), text) },
            onSpeakingStart = { voiceController.onCopilotSpeakingStart() },
            onSpeakingEnd = { voiceController.onCopilotSpeakingEnd() },
        )

        voiceController = VoiceController(
            config = appConfig,
            speechToText = smartStt,
            copilot = copilot,
            onListeningState = { listening ->
                renderOnMain {
                    micButton.text = if (listening) "🎙️ 正在倾听中..." else getString(R.string.mic_button)
                }
            },
            onUserText = { text -> appendTranscript(getString(R.string.transcript_user), text) },
            onListenError = { msg ->
                renderOnMain {
                    roadNameText.text = "听不清：$msg"
                    Toast.makeText(this, "语音识别提示：$msg", Toast.LENGTH_SHORT).show()
                }
            },
            onUtterance = { utterance -> renderOnMain { handleUserUtterance(utterance) } },
        )

        placeSearch = PlaceSearch(applicationContext)
        enRoute = AmapEnRouteDataSource(applicationContext)
        enRoute.start(
            onAreaChanged = {},
            onFirstFix = { handleLocationFix(it) },
            onLocation = { loc -> updateLocationMarker(loc) },
        )

        navigationProvider = AmapNavigationProvider(applicationContext)
        navigationProvider.addListener(naviListener)

        // First-run guidance: jump straight into settings
        if (appConfig.consumeFirstLaunch()) {
            openSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        naviView.onResume()
        updateBubbleTag()
        refreshVoiceStatus()
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
                val cityCode = enRoute.latestLocation()?.cityCode
                placeSearch.search(dest, cityCode) { result ->
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
                    val cityCode = enRoute.latestLocation()?.cityCode
                    placeSearch.search(home, cityCode) { result ->
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
                    val cityCode = enRoute.latestLocation()?.cityCode
                    placeSearch.search(company, cityCode) { result ->
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
                val loc = enRoute.latestLocation()
                if (loc == null) {
                    copilot.speakDirect(getString(R.string.search_waiting_location))
                    return
                }
                copilot.speakDirect("正在搜索附近的 $kw…")
                placeSearch.searchAround(loc.latitude, loc.longitude, 3000, kw) { result ->
                    result.onSuccess { list ->
                        renderOnMain {
                            showAroundMarkers(list)
                            if (list.isNotEmpty()) {
                                val first = list.first()
                                copilot.speakDirect("附近找到 ${list.size} 个 $kw，最近的是 ${first.title}。")
                            } else {
                                copilot.speakDirect("附近 3 公里内没有找到 $kw。")
                            }
                        }
                    }.onFailure { e ->
                        renderOnMain {
                            copilot.speakDirect("搜索附近 $kw 失败：${e.message}")
                        }
                    }
                }
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
                    setOnClickListener {
                        searchResults.visibility = View.GONE
                        startNaviTo(place)
                    }
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
