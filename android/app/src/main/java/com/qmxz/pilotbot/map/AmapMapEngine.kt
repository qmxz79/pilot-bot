package com.qmxz.pilotbot.map

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.navi.AMapNaviView
import com.qmxz.pilotbot.config.MapProvider
import com.qmxz.pilotbot.navi.AmapNavigationProvider
import com.qmxz.pilotbot.navi.NaviError
import com.qmxz.pilotbot.navi.NaviEventListener
import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.navi.RoutePlan
import com.qmxz.pilotbot.search.PlaceResult
import com.qmxz.pilotbot.search.PlaceSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * AMap (AutoNavi / 高德地图) implementation of [MapEngine].
 * Integrates AMap SDK, [AMapNaviView], and [AmapNavigationProvider] for domestic China navigation.
 */
class AmapMapEngine(
    private val context: Context,
    val navigationProvider: AmapNavigationProvider = AmapNavigationProvider(context),
    private val placeSearch: PlaceSearch = PlaceSearch(context),
) : MapEngine {

    override val provider: MapProvider = MapProvider.AMAP

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var naviView: AMapNaviView? = null
    private var pendingRouteCallback: ((Result<RouteSummary>) -> Unit)? = null
    private var lastCalculatedRoute: RoutePlan? = null

    private val naviEventListener = object : NaviEventListener {
        override fun onRouteCalculated(route: RoutePlan) {
            lastCalculatedRoute = route
            val callback = pendingRouteCallback ?: return
            pendingRouteCallback = null

            val summary = RouteSummary(
                totalDistanceMeters = route.totalDistanceMeters?.toLong() ?: 0L,
                totalDurationSeconds = route.totalTimeSeconds?.toLong() ?: 0L,
                routeName = "高德推荐路线",
                polylinePoints = listOf(
                    GeoPoint(lat = route.start.latitude, lng = route.start.longitude),
                    GeoPoint(lat = route.destination.latitude, lng = route.destination.longitude),
                ),
            )
            mainHandler.post { callback(Result.success(summary)) }
        }

        override fun onNaviError(error: NaviError) {
            val callback = pendingRouteCallback ?: return
            pendingRouteCallback = null
            mainHandler.post {
                callback(Result.failure(IllegalStateException("高德算路错误 [${error.code}]: ${error.message}")))
            }
        }

        override fun onNaviStateChanged(state: NaviState) {}
        override fun onNaviText(text: String) {}
        override fun onArrived() {}
    }

    override fun init(container: ViewGroup, savedInstanceState: Bundle?) {
        navigationProvider.addListener(naviEventListener)

        val existingView = (0 until container.childCount)
            .map { container.getChildAt(it) }
            .filterIsInstance<AMapNaviView>()
            .firstOrNull()

        val view = existingView ?: AMapNaviView(container.context).also {
            container.addView(
                it,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        naviView = view
        view.onCreate(savedInstanceState)
    }

    override fun updateLocation(lat: Double, lng: Double) {
        val view = naviView ?: return
        view.map?.animateCamera(
            CameraUpdateFactory.newLatLng(LatLng(lat, lng)),
        )
    }

    override fun searchPlaces(
        keyword: String,
        city: String?,
        callback: (Result<List<PlaceResult>>) -> Unit,
    ) {
        placeSearch.search(keyword, city, callback)
    }

    override fun calculateRoute(
        start: GeoPoint,
        dest: GeoPoint,
        callback: (Result<RouteSummary>) -> Unit,
    ) {
        this.pendingRouteCallback = callback
        val routePlan = RoutePlan(
            start = com.qmxz.pilotbot.navi.GeoPoint(longitude = start.lng, latitude = start.lat),
            destination = com.qmxz.pilotbot.navi.GeoPoint(longitude = dest.lng, latitude = dest.lat),
        )
        scope.launch {
            try {
                navigationProvider.startNavi(routePlan)
            } catch (e: Exception) {
                pendingRouteCallback = null
                mainHandler.post { callback(Result.failure(e)) }
            }
        }
    }

    override fun startNavigation() {
        val route = lastCalculatedRoute ?: return
        scope.launch {
            runCatching {
                navigationProvider.startNavi(route)
            }
        }
    }

    override fun stopNavigation() {
        scope.launch {
            runCatching {
                navigationProvider.stopNavi()
            }
        }
    }

    override fun onResume() {
        naviView?.onResume()
    }

    override fun onPause() {
        naviView?.onPause()
    }

    override fun onDestroy() {
        navigationProvider.removeListener(naviEventListener)
        naviView?.onDestroy()
        naviView = null
        scope.cancel()
    }
}
