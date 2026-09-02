package com.qmxz.pilotbot.navi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationCommandCoordinatorTest {
    @Test fun delegatesCommandsAndSurfacesFailuresOutsideUi() {
        val provider = FakeProvider()
        var failure: Throwable? = null
        val coordinator = NavigationCommandCoordinator(provider, { failure = it }, CoroutineScope(Dispatchers.Unconfined))
        coordinator.start(RoutePlan(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)))
        coordinator.stop()
        assertEquals(1, provider.starts)
        assertEquals(1, provider.stops)
        org.junit.Assert.assertEquals(null, failure)
    }

    @Test fun reportsProviderFailure() {
        val provider = FakeProvider(failStart = true)
        var failure: Throwable? = null
        NavigationCommandCoordinator(provider, { failure = it }, CoroutineScope(Dispatchers.Unconfined))
            .start(RoutePlan(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0)))
        org.junit.Assert.assertTrue(failure?.message?.contains("start failed") == true)
    }

    private class FakeProvider(private val failStart: Boolean = false) : NavigationProvider {
        var starts = 0; var stops = 0
        override val isNavigating: Boolean = false
        override suspend fun startNavi(route: RoutePlan) { starts++; if (failStart) error("start failed") }
        override suspend fun stopNavi() { stops++ }
        override fun addListener(listener: NaviEventListener) {}
        override fun removeListener(listener: NaviEventListener) {}
    }
}
