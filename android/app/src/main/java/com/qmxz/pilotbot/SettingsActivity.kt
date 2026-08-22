package com.qmxz.pilotbot

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona
import com.qmxz.pilotbot.persona.PersonaStore
import com.qmxz.pilotbot.voice.ConversationMode

/** Edits model endpoint, persona preset, and voice mode; persisted to SharedPreferences. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var baseUrl: TextInputEditText
    private lateinit var apiKey: TextInputEditText
    private lateinit var model: TextInputEditText
    private lateinit var personaSpinner: Spinner
    private lateinit var name: TextInputEditText
    private lateinit var tone: TextInputEditText
    private lateinit var catchphrase: TextInputEditText
    private lateinit var modeRadioGroup: RadioGroup
    private lateinit var wakeWord: TextInputEditText

    private val presetIds: List<String> =
        listOf(PersonaStore.CUSTOM_ID) + PersonaStore.BUILTINS.map { it.id }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val config = AppConfig(applicationContext)
        baseUrl = findViewById(R.id.baseUrlInput)
        apiKey = findViewById(R.id.apiKeyInput)
        model = findViewById(R.id.modelInput)
        personaSpinner = findViewById(R.id.personaSpinner)
        name = findViewById(R.id.nameInput)
        tone = findViewById(R.id.toneInput)
        catchphrase = findViewById(R.id.catchphraseInput)
        modeRadioGroup = findViewById(R.id.modeRadioGroup)
        wakeWord = findViewById(R.id.wakeWordInput)

        config.endpoint.let {
            baseUrl.setText(it.baseUrl)
            apiKey.setText(it.apiKey)
            model.setText(it.model)
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
        "calm" -> getString(R.string.preset_calm)
        "sarcastic" -> getString(R.string.preset_sarcastic)
        else -> getString(R.string.preset_custom)
    }
}
