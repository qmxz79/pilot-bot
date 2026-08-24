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
 * Real [EnRouteDataSource] backed by the Amap location SDK (already a dependency, same Android SDK
 * key). A continuously running location client reverse-geocodes province/city/district into the
 * cached [AdminArea]; crossing a province/city boundary fires [start]'s callback for narration.
 *
 * ponytail: [nearbyPoi] is empty because the location SDK provides no POI search; wiring POI
 * narration would need a separate Amap Web-service key. Add when en-route POI actually matters.
 */
class AmapEnRouteDataSource(context: Context) : EnRouteDataSource, AMapLocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val client: AMapLocationClient = AMapLocationClient(context.applicationContext).apply {
        setLocationListener(this@AmapEnRouteDataSource)
        setLocationOption(AMapLocationClientOption().apply {
            locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            interval = 10_000
            isNeedAddress = true
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

    /** Starts continuous location; [onAreaChanged] fires when province+city changes. */
    fun start(onAreaChanged: (AdminArea) -> Unit, onFirstFix: (AMapLocation) -> Unit = {}) {
        this.onAreaChanged = onAreaChanged
        this.onFirstFix = onFirstFix
        firstFix = true
        client.startLocation()
    }

    fun stop() {
        client.stopLocation()
        onAreaChanged = null
        onFirstFix = null
    }

    /** Latest full location fix (lat/lng + address), or null before the first successful fix. */
    fun latestLocation(): AMapLocation? = cachedLocation

    fun destroy() {
        stop()
        client.onDestroy()
        scope.cancel()
    }

    override fun onLocationChanged(location: AMapLocation?) {
        if (location == null || location.errorCode != 0) return
        cachedLocation = location
        val area = AdminArea(
            province = location.province ?: "",
            city = location.city ?: "",
            district = location.district ?: "",
        )
        val changed = firstFix || area.province != cachedArea.province || area.city != cachedArea.city
        val first = firstFix
        firstFix = false
        cachedArea = area
        if (first) {
            val callback = onFirstFix
            scope.launch { callback?.invoke(location) }
        }
        if (changed && area.province.isNotEmpty()) {
            Log.d(TAG, "Admin area: ${area.province}${area.city}${area.district}")
            val callback = onAreaChanged
            scope.launch { callback?.invoke(area) }
        }
    }

    override suspend fun nearbyPoi(lat: Double, lng: Double, radiusMeters: Int): List<Poi> = emptyList()

    override suspend fun currentAdminArea(lat: Double, lng: Double): AdminArea = cachedArea

    override suspend fun prefetchAlongRoute(route: RoutePlan): Unit = Unit

    private companion object {
        const val TAG = "AmapEnRoute"
    }
}
