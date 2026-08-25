package com.qmxz.pilotbot.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private val builder = SimpleContextBuilder()

    @Test
    fun testClassifyTurnEvents() {
        assertEquals(EventType.TURN, SimpleContextBuilder.classify("前方300米右转进入建国门外大街"))
        assertEquals(EventType.TURN, SimpleContextBuilder.classify("请靠左侧车道行驶"))
        assertEquals(EventType.TURN, SimpleContextBuilder.classify("前方适时调头"))
    }

    @Test
    fun testClassifyCongestionEvents() {
        assertEquals(EventType.CONGESTION, SimpleContextBuilder.classify("前方2公里道路严重拥堵，预计通行10分钟"))
        assertEquals(EventType.CONGESTION, SimpleContextBuilder.classify("前方有交通事故，请谨慎驾驶"))
        assertEquals(EventType.CONGESTION, SimpleContextBuilder.classify("前方道路交通管制"))
    }

    @Test
    fun testClassifyArriveEvents() {
        assertEquals(EventType.ARRIVE, SimpleContextBuilder.classify("即将到达目的地，本次导航结束"))
        assertEquals(EventType.ARRIVE, SimpleContextBuilder.classify("您已抵达终点附近"))
    }

    @Test
    fun testClassifyGenericEvents() {
        assertEquals(EventType.GENERIC, SimpleContextBuilder.classify("沿当前道路继续直行"))
    }

    @Test
    fun testBuildContextBlock() {
        val event = builder.buildEvent("前方2公里拥堵")
        val block = builder.buildContextBlock(event)
        assertTrue(block.contains("导航刚播报：「前方2公里拥堵」"))
        assertTrue(block.contains("堵车"))
    }
}
