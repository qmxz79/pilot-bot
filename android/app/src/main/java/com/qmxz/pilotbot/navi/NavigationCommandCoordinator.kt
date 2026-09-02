package com.qmxz.pilotbot.navi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns asynchronous start/stop commands so UI controllers do not manage continuations. */
class NavigationCommandCoordinator(
    private val provider: NavigationProvider,
    private val onFailure: (Throwable) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    fun start(route: RoutePlan) = execute { provider.startNavi(route) }
    fun stop() = execute { provider.stopNavi() }
    fun close() = scope.cancel()

    private fun execute(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
    }
}
