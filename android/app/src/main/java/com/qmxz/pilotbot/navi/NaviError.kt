package com.qmxz.pilotbot.navi

/** A navigation-provider error that can safely be surfaced outside the SDK layer. */
data class NaviError(
    val code: Int,
    val message: String,
    val cause: Throwable? = null,
)
