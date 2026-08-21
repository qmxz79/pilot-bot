package com.qmxz.pilotbot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona

/** Edits the model endpoint and the single copilot persona; persisted to SharedPreferences. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val config = AppConfig(applicationContext)
        val baseUrl = findViewById<TextInputEditText>(R.id.baseUrlInput)
        val apiKey = findViewById<TextInputEditText>(R.id.apiKeyInput)
        val model = findViewById<TextInputEditText>(R.id.modelInput)
        val name = findViewById<TextInputEditText>(R.id.nameInput)
        val tone = findViewById<TextInputEditText>(R.id.toneInput)
        val catchphrase = findViewById<TextInputEditText>(R.id.catchphraseInput)

        config.endpoint.let {
            baseUrl.setText(it.baseUrl)
            apiKey.setText(it.apiKey)
            model.setText(it.model)
        }
        config.persona.let {
            name.setText(it.name)
            tone.setText(it.tone)
            catchphrase.setText(it.catchphrase)
        }

        findViewById<MaterialButton>(R.id.saveSettingsButton).setOnClickListener {
            config.endpoint = LlmEndpoint(
                baseUrl = baseUrl.text?.toString()?.trim().orEmpty(),
                apiKey = apiKey.text?.toString()?.trim().orEmpty(),
                model = model.text?.toString()?.trim().orEmpty(),
            )
            config.persona = Persona(
                name = name.text?.toString()?.trim().orEmpty(),
                tone = tone.text?.toString()?.trim().orEmpty(),
                catchphrase = catchphrase.text?.toString()?.trim().orEmpty(),
            )
            setResult(RESULT_OK)
            finish()
        }
    }
}
