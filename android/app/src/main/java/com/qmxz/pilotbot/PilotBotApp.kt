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
        // AMap V11 privacy compliance: must set both updatePrivacyShow and updatePrivacyAgree
        // before ANY SDK API call to enable full high-precision satellite GPS, network location,
        // POI search, and route calculation without error code 7 / 12 / 1806.
        try {
            NaviSetting.updatePrivacyShow(this, true, true)
            NaviSetting.updatePrivacyAgree(this, true)
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {}
    }
}
