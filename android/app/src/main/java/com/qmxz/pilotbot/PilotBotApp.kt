package com.qmxz.pilotbot

import android.app.Application
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.navi.NaviSetting
import com.amap.api.services.core.ServiceSettings

/** Application-level entry point for shared app initialization. */
class PilotBotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // AMap V11 privacy compliance: must be set before ANY SDK API call, otherwise the SDK
        // throws (AMapException "请先调用 updatePrivacyShow/updatePrivacyAgree").
        // Consent is deliberately deferred to PrivacyConsentActivity, after an explicit choice.
        NaviSetting.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
    }
}
