package com.qmxz.pilotbot.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyConsentTest {
    @Test fun gateRequiresExplicitConsentBeforeOpeningMain() {
        assertEquals(PrivacyGateState.SHOW_CONSENT, privacyGateState(false))
        assertEquals(PrivacyGateState.EXIT, privacyGateState(false, PrivacyGateAction.DECLINE))
        assertEquals(PrivacyGateState.OPEN_MAIN, privacyGateState(false, PrivacyGateAction.ACCEPT))
        assertEquals(PrivacyGateState.OPEN_MAIN, privacyGateState(true))
    }
}
