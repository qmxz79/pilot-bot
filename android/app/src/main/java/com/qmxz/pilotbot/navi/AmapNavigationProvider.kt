package com.qmxz.pilotbot.navi

import android.content.Context

/**
 * Thin session facade over the process-wide [NaviSessionCoordinator].
 *
 * It has no direct AMapNavi reference and never registers an SDK listener. The coordinator owns
 * both for the application process, filters callbacks by its active generation, and forwards only
 * the current application's events to listeners registered here.
 */
class AmapNavigationProvider(context: Context) : NavigationProvider {
    private val coordinator = NaviSessionCoordinator.getInstance(context.applicationContext)

    override val isNavigating: Boolean
        get() = coordinator.isNavigating

    override suspend fun startNavi(route: RoutePlan) {
        coordinator.start(route)
    }

    override suspend fun stopNavi() {
        coordinator.stop()
    }

    override fun addListener(listener: NaviEventListener) {
        coordinator.addListener(listener)
    }

    override fun removeListener(listener: NaviEventListener) {
        coordinator.removeListener(listener)
    }

    /** Removes application listeners; it does not remove the process-wide SDK listener. */
    fun close() {
        coordinator.close()
    }
}
