package com.qmxz.pilotbot.enroute

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.qmxz.pilotbot.navi.RoutePlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * High-Precision Real-Time [EnRouteDataSource] backed by Amap Location SDK.
 * Uses satellite GPS + cell/wifi multi-source fusion for lane-level accuracy.
 */
class AmapEnRouteDataSource(context: Context) : EnRouteDataSource, AMapLocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appContext = context.applicationContext

    private val client: AMapLocationClient = AMapLocationClient(appContext).apply {
        setLocationListener(this@AmapEnRouteDataSource)
        setLocationOption(AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = 2000L
            isNeedAddress = true
            isOnceLocation = false
            isSensorEnable = true
            isWifiScan = true
            isMockEnable = false
            httpTimeOut = 15000L
            locationPurpose = AMapLocationClientOption.AMapLocationPurpose.Transport
        })
    }

    @Volatile
    private var cachedArea: AdminArea = AdminArea("", "", "")

    @Volatile
    private var cachedLocation: AMapLocation? = null

    @Volatile
    private var firstFix = true

    @Volatile
    private var onAreaChanged: ((AdminArea) -> Unit)? = null

    @Volatile
    private var onFirstFix: ((AMapLocation) -> Unit)? = null

    @Volatile
    private var onLocation: ((AMapLocation) -> Unit)? = null

    @Volatile
    private var onError: ((Int, String) -> Unit)? = null

    override suspend fun nearbyPoi(lat: Double, lng: Double, radiusMeters: Int): List<Poi> = emptyList()

    override suspend fun currentAdminArea(lat: Double, lng: Double): AdminArea = cachedArea

    override suspend fun prefetchAlongRoute(route: RoutePlan) {}

    /** Starts continuous high-precision location tracking. */
    fun start(
        onAreaChanged: (AdminArea) -> Unit,
        onFirstFix: (AMapLocation) -> Unit = {},
        onLocation: (AMapLocation) -> Unit = {},
        onError: (Int, String) -> Unit = { _, _ -> },
    ) {
        this.onAreaChanged = onAreaChanged
        this.onFirstFix = onFirstFix
        this.onLocation = onLocation
        this.onError = onError
        firstFix = true
        try {
            client.startLocation()
        } catch (e: Throwable) {
            Log.e("AMapLocation", "Failed to start Amap location client: ${e.message}")
        }
    }

    fun stop() {
        try {
            client.stopLocation()
        } catch (_: Throwable) {}
        onAreaChanged = null
        onFirstFix = null
        onLocation = null
        onError = null
    }

    /** Latest full location fix (lat/lng + address), or null before the first successful fix. */
    fun latestLocation(): AMapLocation? = cachedLocation

    fun destroy() {
        stop()
        try {
            client.onDestroy()
        } catch (_: Throwable) {}
        scope.cancel()
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location == null) return
        if (location.errorCode != 0) {
            Log.w("AMapLocation", "Location fix error: code=${location.errorCode}, info=${location.errorInfo}")
            val errCb = onError
            scope.launch { errCb?.invoke(location.errorCode, location.errorInfo ?: "未知定位错误") }
            return
        }

        cachedLocation = location
        val area = AdminArea(
            province = location.province.orEmpty(),
            city = location.city.orEmpty(),
            district = location.district.orEmpty(),
        )
        val changed = firstFix || area.province != cachedArea.province || area.city != cachedArea.city
        val first = firstFix
        firstFix = false
        cachedArea = area

        val everyFix = onLocation
        scope.launch { everyFix?.invoke(location) }

        if (first) {
            val firstCb = onFirstFix
            scope.launch { firstCb?.invoke(location) }
        }
        if (changed && area.province.isNotBlank()) {
            val areaCb = onAreaChanged
            scope.launch { areaCb?.invoke(area) }
        }
    }
}
