package com.qmxz.pilotbot.navi

/** Receives vendor-neutral events emitted by [NavigationProvider]. */
interface NaviEventListener {
    fun onNaviStateChanged(state: NaviState)

    /** The SDK's original spoken navigation prompt, for example: "前方 500 米靠右行驶". */
    fun onNaviText(text: String)

    fun onRouteCalculated(route: RoutePlan)

    fun onArrived()

    fun onNaviError(error: NaviError)
}
