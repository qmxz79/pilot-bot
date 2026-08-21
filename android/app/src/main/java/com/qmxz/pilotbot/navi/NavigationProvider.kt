package com.qmxz.pilotbot.navi

/**
 * Data source for one navigation session. Implementations hide vendor-specific SDK details
 * from the rest of the application.
 */
interface NavigationProvider {
    /** Calculates [route] and starts navigation after a successful calculation. */
    suspend fun startNavi(route: RoutePlan)

    /** Stops the current navigation session. */
    suspend fun stopNavi()

    /** True after navigation has actually started, rather than merely being calculated. */
    val isNavigating: Boolean

    fun addListener(listener: NaviEventListener)

    fun removeListener(listener: NaviEventListener)
}
