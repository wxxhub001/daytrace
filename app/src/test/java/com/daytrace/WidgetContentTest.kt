package com.daytrace

import com.daytrace.domain.WidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetContentTest {

    private val hour = 3_600_000L

    @Test
    fun barFractionIsZeroWithoutUsage() {
        assertEquals(0f, WidgetContent.barFraction(0L, 3 * hour), 0.001f)
    }

    @Test
    fun barFractionReachesFullAtAverage() {
        assertEquals(1f, WidgetContent.barFraction(3 * hour, 3 * hour), 0.001f)
    }

    @Test
    fun barFractionCapsAtOneWhenOverAverage() {
        assertEquals(1f, WidgetContent.barFraction(10 * hour, 3 * hour), 0.001f)
    }

    @Test
    fun barFractionFallsBackToEightHours() {
        assertEquals(0.5f, WidgetContent.barFraction(4 * hour, 0L), 0.001f)
        assertEquals(1f, WidgetContent.barFraction(12 * hour, 0L), 0.001f)
    }

    @Test
    fun metaLineShowsOpensAndAverage() {
        assertEquals("打开 37 次 · 日均 4小时0分", WidgetContent.metaLine(37, 4 * hour))
    }

    @Test
    fun metaLineHandlesMissingHistory() {
        assertEquals("打开 5 次 · 还没有历史数据", WidgetContent.metaLine(5, 0L))
    }

    @Test
    fun daysTextCoversAllThreeCases() {
        assertEquals("还有 43 天", WidgetContent.daysText(43))
        assertEquals("就是今天", WidgetContent.daysText(0))
        assertEquals("已过去 3 天", WidgetContent.daysText(-3))
    }

    @Test
    fun bigNumberOnlyForFutureCountdown() {
        assertEquals("43", WidgetContent.bigNumber(43))
        assertEquals("", WidgetContent.bigNumber(0))
        assertEquals("", WidgetContent.bigNumber(-2))
    }

    @Test
    fun emptyCountdownPromptsCreation() {
        val empty = WidgetContent.emptyCountdown()
        assertFalse(empty.hasGoal)
        assertEquals("还没有倒计时目标", empty.title)
        assertEquals("点我添加", empty.daysText)
    }
}