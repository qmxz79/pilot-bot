package com.qmxz.pilotbot.navi

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.SimpleNaviListener
import com.amap.api.navi.enums.IconType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-wide owner of AMapNavi and its single SDK listener.
 *
 * SDK listener registration is deliberately stable for the process lifetime: session switches do
 * not remove/re-add it, so an old SDK callback cannot be transferred to a newly registered SDK
 * listener. Application listeners are dispatched only for the current coordinator generation.
 *
 * Non-overlapping route requests are a hard M0 constraint. Stop during route calculation waits in
 * WAIT_FOR_OLD_TERMINAL for the old route terminal callback; stop during START_PENDING waits in
 * WAIT_FOR_START_ACK for the old start acknowledgement. This relies on the SDK contract that an
 * accepted calculateDriveRoute reports its result through AMapNaviListener, and that an accepted
 * startNavi stopped immediately still reports onStartNavi. If device testing leaves either wait
 * permanently blocked, treat it as an SDK-contract finding and recover through an explicit
 * reset/restart flow rather than silently allowing an overlapping request.
 *
 * NAVIGATING stop returns directly to IDLE because AMapNaviListener has no navigation-stop callback
 * and its callbacks carry no session ID. Delayed old state/text/arrival callbacks could therefore
 * be indistinguishable from a new session at this SDK boundary. M0 must test this residual risk;
 * if mixing occurs, add a measured, configurable drain delay instead of claiming static isolation.
 *
 * M0 device experiments: calculate -> immediate stop -> start; START_PENDING stop -> immediate
 * start with delayed old ACK; NAVIGATING stop -> immediate start with delayed state/text/arrival;
 * record callback types/timing, verify GPS actually stops, and verify callback thread.
 */
class NaviSessionCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val navi = AMapNavi.getInstance(appContext)
    private val listeners = CopyOnWriteArraySet<NaviEventListener>()
    private val routeLock = Any()

    /** All fields below are protected by [routeLock]. */
    private var phase = Phase.IDLE
    private var currentGeneration = 0L
    private var inFlightGeneration: Long? = null
    private var startRequestedGeneration: Long? = null
    // Retained only while the coordinator owns the route session.
    private var activeRoute: RoutePlan? = null

    val isNavigating: Boolean
        get() = synchronized(routeLock) { phase == Phase.NAVIGATING }

    fun addListener(listener: NaviEventListener) {
        listeners += listener
    }

    fun removeListener(listener: NaviEventListener) {
        listeners -= listener
    }

    /** Removes app listeners only. The process SDK listener intentionally remains registered. */
    fun close() {
        listeners.clear()
    }

    fun start(route: RoutePlan) {
        val generation = synchronized(routeLock) {
            when (phase) {
                Phase.IDLE -> {
                    currentGeneration += 1
                    inFlightGeneration = currentGeneration
                    activeRoute = route
                    phase = Phase.ROUTE_CALCULATING
                    currentGeneration
                }

                Phase.WAIT_FOR_OLD_TERMINAL,
                Phase.WAIT_FOR_START_ACK,
                -> {
                    dispatchBusyError("正在停止上一段导航，请稍候")
                    return
                }

                else -> {
                    dispatchBusyError("已有导航请求正在处理中")
                    return
                }
            }
        }

        val accepted = navi.calculateDriveRoute(
            listOf(route.start.toAmapLatLng()),
            listOf(route.destination.toAmapLatLng()),
            route.waypoints.map { it.toAmapLatLng() },
            route.strategy ?: defaultStrategy(),
        )
        if (!accepted) {
            synchronized(routeLock) {
                if (phase == Phase.ROUTE_CALCULATING && inFlightGeneration == generation) {
                    phase = Phase.IDLE
                    inFlightGeneration = null
                    activeRoute = null
                }
            }
            dispatchForGeneration(
                generation,
                NaviError(CALCULATION_REQUEST_ERROR, "AMap rejected the route request"),
            )
        }
    }

    /**
     * Stops the SDK. Only an in-flight route calculation enters WAIT_FOR_OLD_TERMINAL: that is the
     * only phase whose old terminal callback could otherwise be mistaken for a future request.
     */
    fun stop() {
        synchronized(routeLock) {
            when (phase) {
                Phase.IDLE,
                Phase.WAIT_FOR_OLD_TERMINAL,
                Phase.WAIT_FOR_START_ACK,
                -> return

                Phase.ROUTE_CALCULATING -> {
                    currentGeneration += 1 // invalidates callbacks from the stopped calculation
                    inFlightGeneration = null
                    startRequestedGeneration = null
                    phase = Phase.WAIT_FOR_OLD_TERMINAL
                }

                Phase.START_PENDING -> {
                    // startNavi was accepted, but its untagged ACK may still arrive. Hold the
                    // coordinator until that old ACK is consumed so it cannot start a new session.
                    currentGeneration += 1
                    inFlightGeneration = null
                    startRequestedGeneration = null
                    activeRoute = null
                    phase = Phase.WAIT_FOR_START_ACK
                }

                Phase.NAVIGATING -> {
                    // AMapNaviListener exposes no navigation-stop terminal callback to await.
                    // This residual late-callback risk is documented and measured in M0 tests.
                    currentGeneration += 1
                    inFlightGeneration = null
                    startRequestedGeneration = null
                    activeRoute = null
                    phase = Phase.IDLE
                }
            }
        }
        navi.stopNavi()
        navi.stopGPS()
    }

    private val amapListener = object : SimpleNaviListener() {
        override fun onInitNaviFailure() {
            val generation = synchronized(routeLock) {
                val current = inFlightGeneration
                if (phase != Phase.ROUTE_CALCULATING || current == null || current != currentGeneration) {
                    return
                }
                phase = Phase.IDLE
                inFlightGeneration = null
                activeRoute = null
                current
            }
            dispatchForGeneration(generation, NaviError(INIT_ERROR, "AMap navigation initialization failed"))
        }

        override fun onCalculateRouteSuccess(result: AMapCalcRouteResult) {
            val routeGeneration = synchronized(routeLock) {
                when (phase) {
                    Phase.WAIT_FOR_OLD_TERMINAL -> {
                        completeOldTerminalWaitLocked()
                        return
                    }

                    Phase.ROUTE_CALCULATING -> {
                        val generation = inFlightGeneration
                        if (generation == null || generation != currentGeneration) return
                        phase = Phase.START_PENDING
                        startRequestedGeneration = generation
                        generation
                    }

                    else -> return
                }
            }

            val selectedRouteId = result.routeid.firstOrNull()
            val path = selectedRouteId?.let { navi.naviPaths[it] } ?: navi.naviPath
            val calculatedRoute = synchronized(routeLock) {
                activeRoute?.takeIf { routeGeneration == currentGeneration }?.copy(
                    selectedRouteId = selectedRouteId,
                    totalDistanceMeters = path?.allLength,
                    totalTimeSeconds = path?.allTime,
                )
            } ?: return
            dispatchForGeneration(routeGeneration) { it.onRouteCalculated(calculatedRoute) }

            // Keep phase validation and SDK start in one lock region: stop() either invalidates
            // this generation before SDK start is issued, or runs immediately afterwards and stops
            // it. The SDK start result is asynchronous through onStartNavi.
            val rejectedBySdk = synchronized(routeLock) {
                if (phase != Phase.START_PENDING || startRequestedGeneration != routeGeneration) {
                    false
                } else if (navi.startNavi(AMapNavi.GPSNaviMode)) {
                    false
                } else {
                    phase = Phase.IDLE
                    inFlightGeneration = null
                    startRequestedGeneration = null
                    activeRoute = null
                    true
                }
            }
            if (rejectedBySdk) {
                dispatchForGeneration(
                    routeGeneration,
                    NaviError(START_ERROR, "AMap rejected the navigation start request"),
                )
            }
        }

        override fun onCalculateRouteFailure(result: AMapCalcRouteResult) {
            val generation = synchronized(routeLock) {
                when (phase) {
                    Phase.WAIT_FOR_OLD_TERMINAL -> {
                        completeOldTerminalWaitLocked()
                        return
                    }

                    Phase.ROUTE_CALCULATING -> {
                        val current = inFlightGeneration
                        if (current == null || current != currentGeneration) return
                        phase = Phase.IDLE
                        inFlightGeneration = null
                        activeRoute = null
                        current
                    }

                    else -> return
                }
            }
            dispatchForGeneration(
                generation,
                NaviError(result.errorCode, result.errorDescription ?: "AMap route calculation failed"),
            )
        }

        override fun onStartNavi(type: Int) {
            val result = synchronized(routeLock) {
                when {
                    phase == Phase.WAIT_FOR_START_ACK -> {
                        // Consume the old untagged ACK without entering navigation or forwarding.
                        phase = Phase.IDLE
                        inFlightGeneration = null
                        startRequestedGeneration = null
                        activeRoute = null
                        StartAckResult.OLD_ACK_CONSUMED
                    }

                    phase == Phase.START_PENDING &&
                        startRequestedGeneration != null &&
                        startRequestedGeneration == currentGeneration -> {
                        phase = Phase.NAVIGATING
                        startRequestedGeneration = null
                        StartAckResult.ACCEPTED
                    }

                    else -> StartAckResult.IGNORED
                }
            }
            if (result == StartAckResult.IGNORED) {
                Log.w(TAG, "Ignoring onStartNavi outside current START_PENDING session")
            }
        }

        override fun onNaviInfoUpdate(info: NaviInfo) {
            val generation = navigatingGeneration() ?: return
            dispatchForGeneration(generation) { it.onNaviStateChanged(info.toNaviState()) }
        }

        override fun onGetNavigationText(type: Int, text: String) {
            val generation = navigatingGeneration() ?: return
            if (text.isNotBlank()) dispatchForGeneration(generation) { it.onNaviText(text) }
        }

        override fun onArriveDestination() {
            val generation = synchronized(routeLock) {
                if (phase != Phase.NAVIGATING || inFlightGeneration != currentGeneration) return
                phase = Phase.IDLE
                activeRoute = null
                inFlightGeneration.also { inFlightGeneration = null }
            } ?: return
            dispatchForGeneration(generation) { it.onArrived() }
        }
    }

    init {
        // Exactly one SDK listener is installed for this process coordinator, after listener creation.
        navi.addAMapNaviListener(amapListener)
    }

    private fun navigatingGeneration(): Long? = synchronized(routeLock) {
        inFlightGeneration?.takeIf { phase == Phase.NAVIGATING && it == currentGeneration }
    }

    /** Consumes either old route terminal callback while stop is waiting for it. */
    private fun completeOldTerminalWaitLocked() {
        phase = Phase.IDLE
        inFlightGeneration = null
        startRequestedGeneration = null
        activeRoute = null
    }

    /** Main-thread dispatch rechecks generation so queued old UI events are discarded. */
    private fun dispatchForGeneration(generation: Long, block: (NaviEventListener) -> Unit) {
        mainHandler.post {
            val valid = synchronized(routeLock) {
                generation == currentGeneration &&
                    phase != Phase.WAIT_FOR_OLD_TERMINAL &&
                    phase != Phase.WAIT_FOR_START_ACK
            }
            if (valid) listeners.forEach(block)
        }
    }

    private fun dispatchForGeneration(generation: Long, error: NaviError) {
        dispatchForGeneration(generation) { it.onNaviError(error) }
    }

    /**
     * BUSY is a synchronous rejection of the caller's new request, not an event from an old SDK
     * session, so it intentionally bypasses generation filtering while still dispatching on main.
     */
    private fun dispatchBusyError(message: String) {
        val error = NaviError(CALCULATION_BUSY_ERROR, message)
        mainHandler.post { listeners.forEach { it.onNaviError(error) } }
    }

    private fun GeoPoint.toAmapLatLng(): NaviLatLng = NaviLatLng(latitude, longitude)

    private fun defaultStrategy(): Int = AMapNavi.strategyConvert(true, false, false, false, false)

    private fun NaviInfo.toNaviState(): NaviState = NaviState(
        remainingDistanceMeters = pathRetainDistance,
        remainingTimeSeconds = pathRetainTime,
        currentRoadName = currentRoadName,
        currentSpeedKmh = currentSpeed.toFloat(),
        nextTurn = curStepRetainDistance.takeIf { it >= 0 }?.let {
            TurnInstruction(iconType.toTurnAction(), it)
        },
        jamLengthMeters = 0,
        jamDurationSeconds = 0,
        isNight = (appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
    )

    private fun Int.toTurnAction(): TurnAction = when (this) {
        IconType.LEFT, IconType.LEFT_BACK -> TurnAction.TURN_LEFT
        IconType.RIGHT, IconType.RIGHT_BACK -> TurnAction.TURN_RIGHT
        IconType.LEFT_FRONT, IconType.MERGE_LEFT -> TurnAction.KEEP_LEFT
        IconType.RIGHT_FRONT, IconType.MERGE_RIGHT -> TurnAction.KEEP_RIGHT
        IconType.LEFT_TURN_AROUND -> TurnAction.U_TURN
        IconType.STRAIGHT -> TurnAction.STRAIGHT
        IconType.ENTER_ROUNDABOUT -> TurnAction.ENTER_ROUNDABOUT
        IconType.OUT_ROUNDABOUT -> TurnAction.EXIT_ROUNDABOUT
        IconType.ARRIVED_DESTINATION -> TurnAction.ARRIVE
        else -> TurnAction.UNKNOWN
    }

    private enum class Phase {
        IDLE,
        ROUTE_CALCULATING,
        START_PENDING,
        NAVIGATING,
        WAIT_FOR_OLD_TERMINAL,
        WAIT_FOR_START_ACK,
    }

    private enum class StartAckResult {
        ACCEPTED,
        OLD_ACK_CONSUMED,
        IGNORED,
    }

    companion object {
        private const val TAG = "NaviSessionCoordinator"
        private const val INIT_ERROR = -1001
        private const val CALCULATION_REQUEST_ERROR = -1002
        private const val CALCULATION_BUSY_ERROR = -1003
        private const val START_ERROR = -1004

        @Volatile
        private var instance: NaviSessionCoordinator? = null

        fun getInstance(context: Context): NaviSessionCoordinator = instance ?: synchronized(this) {
            instance ?: NaviSessionCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
