package com.qmxz.pilotbot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qmxz.pilotbot.privacy.PrivacyConsent
import com.qmxz.pilotbot.privacy.PrivacyGateState
import com.qmxz.pilotbot.privacy.privacyGateState

/** Mandatory first-run gate for AMap, location and navigation processing. */
class PrivacyConsentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (privacyGateState(PrivacyConsent.hasAccepted(this)) == PrivacyGateState.OPEN_MAIN) {
            openMain()
            return
        }
        setContentView(R.layout.activity_privacy_consent)
        findViewById<MaterialButton>(R.id.privacyPolicyButton).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("隐私政策")
                .setMessage("副驾伴侣仅在你点击“同意并继续”后初始化高德地图、定位与导航服务。定位信息用于路线规划、当前位置和沿途提示；录音仅在你主动启用语音功能时采集并发送至你配置的语音服务；模型、语音和地图 API Key 使用 Android Keystore 加密保存。应用不会将这些数据出售给第三方。你可以选择不同意并退出。")
                .setPositiveButton("我已了解", null)
                .show()
        }
        findViewById<MaterialButton>(R.id.privacyDeclineButton).setOnClickListener { finishAffinity() }
        findViewById<MaterialButton>(R.id.privacyAcceptButton).setOnClickListener {
            PrivacyConsent.accept(this)
            openMain()
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
