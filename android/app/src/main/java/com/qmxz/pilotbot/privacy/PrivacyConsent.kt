package com.qmxz.pilotbot.privacy

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.navi.NaviSetting
import com.amap.api.services.core.ServiceSettings

enum class PrivacyGateState { SHOW_CONSENT, OPEN_MAIN, EXIT }

/** Pure state policy so the first-run gate remains independently testable. */
fun privacyGateState(hasAccepted: Boolean, action: PrivacyGateAction? = null): PrivacyGateState = when {
    hasAccepted -> PrivacyGateState.OPEN_MAIN
    action == PrivacyGateAction.ACCEPT -> PrivacyGateState.OPEN_MAIN
    action == PrivacyGateAction.DECLINE -> PrivacyGateState.EXIT
    else -> PrivacyGateState.SHOW_CONSENT
}

enum class PrivacyGateAction { ACCEPT, DECLINE }

object PrivacyConsent {
    private const val PREFS = "pilot_bot_privacy"
    private const val KEY_ACCEPTED = "amap_privacy_accepted"

    fun hasAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)

    /** Applies the user's explicit consent before any AMap component is initialized. */
    fun accept(context: Context) {
        val appContext = context.applicationContext
        NaviSetting.updatePrivacyAgree(appContext, true)
        MapsInitializer.updatePrivacyAgree(appContext, true)
        AMapLocationClient.updatePrivacyAgree(appContext, true)
        ServiceSettings.updatePrivacyAgree(appContext, true)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACCEPTED, true).apply()
    }
}
