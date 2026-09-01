package com.qmxz.pilotbot

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.config.MapProvider
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona
import com.qmxz.pilotbot.persona.PersonaStore
import com.qmxz.pilotbot.voice.ConversationMode

/** Edits model endpoint, persona preset, and voice mode; persisted to SharedPreferences. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var baseUrl: TextInputEditText
    private lateinit var apiKey: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var avatarPreview: com.qmxz.pilotbot.avatar.view.AvatarView
    private lateinit var avatarRadioGroup: RadioGroup
    private lateinit var avatarFemaleRadio: RadioButton
    private lateinit var avatarMaleRadio: RadioButton
    private lateinit var personaSpinner: Spinner
    private lateinit var name: TextInputEditText
    private lateinit var tone: TextInputEditText
    private lateinit var catchphrase: TextInputEditText
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var wakeWord: TextInputEditText
    private lateinit var mapProviderRadioGroup: RadioGroup
    private lateinit var mapProviderAmap: View
    private lateinit var mapProviderGoogle: View
    private lateinit var googleMapsApiKey: TextInputEditText

    private val presetIds: List<String> =
        listOf(PersonaStore.CUSTOM_ID) + PersonaStore.BUILTINS.map { it.id }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Probe installed TTS engines + default engine (instance-only in API 34; the probe is
        // throwaway and independent of engine init success).
        val probe = TextToSpeech(applicationContext) {}
        val hasTtsEngine = probe.getEngines().isNotEmpty()
        val hasTtsDefault = !probe.getDefaultEngine().isNullOrEmpty()
        runCatching { probe.shutdown() }

        val ttsWarning = findViewById<View>(R.id.ttsWarning)
        if (hasTtsEngine && hasTtsDefault) {
            ttsWarning.visibility = View.GONE
        } else {
            ttsWarning.visibility = View.VISIBLE
            findViewById<MaterialButton>(R.id.ttsWarningButton).setOnClickListener {
                startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            }
        }

        val config = AppConfig(applicationContext)
        findViewById<View>(R.id.asrWarning).visibility =
            if (config.endpoint.apiKey.isNotBlank() || SpeechRecognizer.isRecognitionAvailable(this)) View.GONE else View.VISIBLE
        avatarPreview = findViewById(R.id.settingsAvatarPreview)
        avatarRadioGroup = findViewById(R.id.avatarRadioGroup)
        avatarFemaleRadio = findViewById(R.id.avatarFemaleRadio)
        avatarMaleRadio = findViewById(R.id.avatarMaleRadio)
        baseUrl = findViewById(R.id.baseUrlInput)
        apiKey = findViewById(R.id.apiKeyInput)
        model = findViewById(R.id.modelInput)
        personaSpinner = findViewById(R.id.personaSpinner)
        name = findViewById(R.id.nameInput)
        tone = findViewById(R.id.toneInput)
        catchphrase = findViewById(R.id.catchphraseInput)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        wakeWord = findViewById(R.id.wakeWordInput)
        mapProviderRadioGroup = findViewById(R.id.mapProviderRadioGroup)
        mapProviderAmap = findViewById(R.id.mapProviderAmap)
        mapProviderGoogle = findViewById(R.id.mapProviderGoogle)
        googleMapsApiKey = findViewById(R.id.googleMapsApiKeyInput)

        // Setup Avatar Selection & Live Preview
        avatarPreview.avatarGender = config.avatarGender
        if (config.avatarGender == com.qmxz.pilotbot.avatar.state.AvatarGender.MALE) {
            avatarMaleRadio.isChecked = true
        } else {
            avatarFemaleRadio.isChecked = true
        }
        avatarRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val gender = if (checkedId == R.id.avatarMaleRadio) {
                com.qmxz.pilotbot.avatar.state.AvatarGender.MALE
            } else {
                com.qmxz.pilotbot.avatar.state.AvatarGender.FEMALE
            }
            avatarPreview.avatarGender = gender
        }

        config.endpoint.let {
            baseUrl.setText(it.baseUrl)
            apiKey.setText(it.apiKey)
            model.setText(it.model)
        }

        // Quick model preset button click handlers
        findViewById<MaterialButton>(R.id.presetDeepSeekBtn).setOnClickListener {
            baseUrl.setText("https://api.deepseek.com/v1")
            model.setText("deepseek-chat")
            Toast.makeText(this, "已填入 DeepSeek 配置。提示：如需语音对讲，推荐使用「⚡ 硅基流动」（含官方 DeepSeek-V3 + 免费语音识别）！", Toast.LENGTH_LONG).show()
        }
        findViewById<MaterialButton>(R.id.presetSiliconFlowBtn).setOnClickListener {
            baseUrl.setText("https://api.siliconflow.cn/v1")
            model.setText("deepseek-ai/DeepSeek-V3")
            Toast.makeText(this, "已填入 硅基流动（包含 DeepSeek-V3 大模型 + 极速语音识别），填入 Key 即可全功能使用！", Toast.LENGTH_LONG).show()
        }
        findViewById<MaterialButton>(R.id.presetQwenBtn).setOnClickListener {
            baseUrl.setText("https://dashscope.aliyuncs.com/compatible-mode/v1")
            model.setText("qwen-plus")
            Toast.makeText(this, "已填入 通义千问 配置，请填入 API Key", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.presetZhipuBtn).setOnClickListener {
            baseUrl.setText("https://open.bigmodel.cn/api/paas/v4")
            model.setText("glm-4-flash")
            Toast.makeText(this, "已填入 智谱GLM 配置，请填入 API Key", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.presetKimiBtn).setOnClickListener {
            baseUrl.setText("https://api.moonshot.cn/v1")
            model.setText("moonshot-v1-8k")
            Toast.makeText(this, "已填入 Kimi 配置，请填入 API Key", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.presetOpenAiBtn).setOnClickListener {
            baseUrl.setText("https://api.openai.com/v1")
            model.setText("gpt-4o-mini")
            Toast.makeText(this, "已填入 OpenAI 配置，请填入 API Key", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.presetOllamaBtn).setOnClickListener {
            baseUrl.setText("http://192.168.1.100:11434/v1")
            model.setText("qwen2.5:7b")
            Toast.makeText(this, "已填入 Ollama 配置，请修改 IP 为局域网地址", Toast.LENGTH_SHORT).show()
        }

        personaSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            presetIds.map(::presetLabel),
        )
        personaSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyPreset(presetIds[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        personaSpinner.setSelection(presetIds.indexOf(config.personaId).coerceAtLeast(0), false)
        applyPreset(config.personaId)

        when (config.conversationMode) {
            ConversationMode.PUSH_TO_TALK -> modeRadioGroup.check(R.id.modePushToTalk)
            ConversationMode.CONTINUOUS -> modeRadioGroup.check(R.id.modeContinuous)
            ConversationMode.WAKE_WORD -> modeRadioGroup.check(R.id.modeWakeWord)
            ConversationMode.FULL_DUPLEX -> modeRadioGroup.check(R.id.modeFullDuplex)
        }
        wakeWord.setText(config.wakeWord)

        when (config.mapProvider) {
            MapProvider.GOOGLE, MapProvider.GOOGLE_MAPS -> mapProviderRadioGroup.check(R.id.mapProviderGoogle)
            else -> mapProviderRadioGroup.check(R.id.mapProviderAmap)
        }
        googleMapsApiKey.setText(config.googleMapsApiKey)

        val voiceRadioGroup = findViewById<RadioGroup>(R.id.voiceRadioGroup)
        when (config.ttsVoice) {
            "voiceSweetFemale" -> voiceRadioGroup.check(R.id.voiceSweetFemaleRadio)
            "voiceLivelyFemale" -> voiceRadioGroup.check(R.id.voiceLivelyFemaleRadio)
            "voiceSunnyMale" -> voiceRadioGroup.check(R.id.voiceSunnyMaleRadio)
            "voiceCalmMale" -> voiceRadioGroup.check(R.id.voiceCalmMaleRadio)
            else -> voiceRadioGroup.check(R.id.voiceAutoRadio)
        }

        findViewById<MaterialButton>(R.id.saveSettingsButton).setOnClickListener {
            val selectedId = presetIds[personaSpinner.selectedItemPosition]
            config.personaId = selectedId
            if (selectedId == PersonaStore.CUSTOM_ID) {
                config.persona = Persona(
                    id = PersonaStore.CUSTOM_ID,
                    name = name.text?.toString()?.trim().orEmpty(),
                    tone = tone.text?.toString()?.trim().orEmpty(),
                    catchphrase = catchphrase.text?.toString()?.trim().orEmpty(),
                )
            }
            config.endpoint = LlmEndpoint(
                baseUrl = baseUrl.text?.toString()?.trim().orEmpty(),
                apiKey = apiKey.text?.toString()?.trim().orEmpty(),
                model = model.text?.toString()?.trim().orEmpty(),
            )
            config.conversationMode = when (modeRadioGroup.checkedRadioButtonId) {
                R.id.modeContinuous -> ConversationMode.CONTINUOUS
                R.id.modeWakeWord -> ConversationMode.WAKE_WORD
                R.id.modeFullDuplex -> ConversationMode.FULL_DUPLEX
                else -> ConversationMode.PUSH_TO_TALK
            }
            config.wakeWord = wakeWord.text?.toString()?.trim().orEmpty()
            config.mapProvider = when (mapProviderRadioGroup.checkedRadioButtonId) {
                R.id.mapProviderGoogle -> MapProvider.GOOGLE
                else -> MapProvider.AMAP
            }
            config.avatarGender = if (avatarRadioGroup.checkedRadioButtonId == R.id.avatarMaleRadio) {
                com.qmxz.pilotbot.avatar.state.AvatarGender.MALE
            } else {
                com.qmxz.pilotbot.avatar.state.AvatarGender.FEMALE
            }
            config.ttsVoice = when (voiceRadioGroup.checkedRadioButtonId) {
                R.id.voiceSweetFemaleRadio -> "voiceSweetFemale"
                R.id.voiceLivelyFemaleRadio -> "voiceLivelyFemale"
                R.id.voiceSunnyMaleRadio -> "voiceSunnyMale"
                R.id.voiceCalmMaleRadio -> "voiceCalmMale"
                else -> "auto"
            }
            config.googleMapsApiKey = googleMapsApiKey.text?.toString()?.trim().orEmpty()
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }

        findViewById<MaterialButton>(R.id.sharePersonaButton).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, config.currentPersona().toJson())
            }
            startActivity(Intent.createChooser(send, getString(R.string.share_persona)))
        }

        findViewById<MaterialButton>(R.id.importPersonaButton).setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            val imported = text?.let { Persona.fromJson(it) }
            if (imported != null) {
                config.persona = imported.copy(id = PersonaStore.CUSTOM_ID)
                config.personaId = PersonaStore.CUSTOM_ID
                personaSpinner.setSelection(presetIds.indexOf(PersonaStore.CUSTOM_ID).coerceAtLeast(0), true)
                name.setText(imported.name)
                tone.setText(imported.tone)
                catchphrase.setText(imported.catchphrase)
                applyPreset(PersonaStore.CUSTOM_ID)
                Toast.makeText(this, R.string.import_persona_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.import_persona_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** A built-in preset fills and locks the fields; the custom slot makes them editable. */
    private fun applyPreset(id: String) {
        val editable = id == PersonaStore.CUSTOM_ID
        name.isEnabled = editable
        tone.isEnabled = editable
        catchphrase.isEnabled = editable
        if (!editable) {
            PersonaStore.find(id)?.let {
                name.setText(it.name)
                tone.setText(it.tone)
                catchphrase.setText(it.catchphrase)
            }
        }
    }

    private fun presetLabel(id: String): String = when (id) {
        PersonaStore.CUSTOM_ID -> getString(R.string.preset_custom)
        "cheerful" -> getString(R.string.preset_cheerful)
        "humorous" -> getString(R.string.preset_humorous)
        "calm" -> getString(R.string.preset_calm)
        "sarcastic" -> getString(R.string.preset_sarcastic)
        else -> getString(R.string.preset_custom)
    }
}
