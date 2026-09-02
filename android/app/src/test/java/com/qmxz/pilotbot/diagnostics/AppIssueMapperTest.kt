package com.qmxz.pilotbot.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIssueMapperTest {
    @Test fun mapsCommonNetworkAndCredentialErrorsWithoutLeakingSecrets() {
        assertEquals(AppIssue.Kind.AUTH, AppIssueMapper.fromThrowable(AppIssue.Area.LLM, Exception("HTTP 401: key=secret")).kind)
        assertEquals(AppIssue.Kind.NETWORK, AppIssueMapper.fromThrowable(AppIssue.Area.ASR, Exception("timeout")).kind)
        assertEquals(AppIssue.Kind.RATE_LIMIT, AppIssueMapper.fromThrowable(AppIssue.Area.TTS, Exception("HTTP 429")).kind)
        assertEquals(AppIssue.Kind.CONFIGURATION, AppIssueMapper.fromThrowable(AppIssue.Area.LLM, Exception("HTTP 404")).kind)
    }

    @Test fun diagnosticsAreBoundedAndRedactBearerTokens() {
        val log = DiagnosticLog(maxEntries = 1)
        log.record(AppIssue(AppIssue.Area.LLM, AppIssue.Kind.AUTH, "", "Bearer sk-secret api_key=another-secret"))
        log.record(AppIssue(AppIssue.Area.ASR, AppIssue.Kind.NETWORK, "", "timeout"))
        assertEquals(1, log.entries().size)
        assertEquals("timeout", log.entries().single().message)
        org.junit.Assert.assertTrue(DiagnosticLog.redact("Bearer sk-secret").contains("***"))
    }
}
