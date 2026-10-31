package com.daytrace

import com.daytrace.domain.GoalCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GoalCalculatorTest {

    private val today = "2026-10-26"

    // ------------------------------------------------------------------ 屏幕时间目标

    @Test
    fun `屏幕时间刚好到上限算达标`() {
        assertTrue(GoalCalculator.screenLimitDone(60 * 60_000L, 60))
    }

    @Test
    fun `屏幕时间超过上限不算达标`() {
        assertFalse(GoalCalculator.screenLimitDone(61 * 60_000L, 60))
    }

    @Test
    fun `非法上限不判定为达标`() {
        assertFalse(GoalCalculator.screenLimitDone(0L, 0))
        assertFalse(GoalCalculator.screenLimitDone(1_000L, -5))
    }

    @Test
    fun `进度比例会被截断在合理区间`() {
        assertEquals(0.5f, GoalCalculator.ratio(30, 60), 0.001f)
        assertEquals(1.4f, GoalCalculator.ratio(1_000, 60), 0.001f)
        assertEquals(0f, GoalCalculator.ratio(100, 0), 0.001f)
    }

    // ------------------------------------------------------------------ 连续天数

    @Test
    fun `没有任何记录时连续天数为零`() {
        assertEquals(0, GoalCalculator.streak(emptySet(), today))
    }

    @Test
    fun `今天还没过完不算断链`() {
        val done = setOf("2026-10-24", "2026-10-25")
        assertEquals(2, GoalCalculator.streak(done, today))
    }

    @Test
    fun `今天已达标会累计到今天`() {
        val done = setOf("2026-10-24", "2026-10-25", "2026-10-26")
        assertEquals(3, GoalCalculator.streak(done, today))
    }

    @Test
    fun `昨天没达标就断链`() {
        val done = setOf("2026-10-24")
        assertEquals(0, GoalCalculator.streak(done, today))
    }

    @Test
    fun `严格模式下今天未达标直接归零`() {
        val done = setOf("2026-10-24", "2026-10-25")
        assertEquals(0, GoalCalculator.streak(done, today, pendingTodayCounts = false))
    }

    @Test
    fun `跨月连续天数也能正确累计`() {
        val done = setOf("2026-09-30", "2026-10-01")
        assertEquals(2, GoalCalculator.streak(done, "2026-10-02"))
    }

    // ------------------------------------------------------------------ 打卡目标

    @Test
    fun `打卡次数达到目标即达标`() {
        assertTrue(GoalCalculator.checkinDone(3, 3))
        assertTrue(GoalCalculator.checkinDone(4, 3))
        assertFalse(GoalCalculator.checkinDone(2, 3))
        assertFalse(GoalCalculator.checkinDone(1, 0))
    }

    // ------------------------------------------------------------------ 日期窗口

    @Test
    fun `本周从周一开始且包含今天`() {
        val week = GoalCalculator.weekDays(today)
        assertEquals(today, week.last())
        assertEquals(DayOfWeek.MONDAY, LocalDate.parse(week.first()).dayOfWeek)
        assertEquals(LocalDate.parse(today).dayOfWeek.value, week.size)
    }

    @Test
    fun `最近七天按时间升序且包含今天`() {
        val days = GoalCalculator.lastDays(today, 7)
        assertEquals(7, days.size)
        assertEquals(today, days.last())
        assertEquals("2026-10-20", days.first())
    }

    @Test
    fun `星期几单字标签正确`() {
        assertEquals("一", GoalCalculator.weekdayLetter("2026-10-26"))
        assertEquals("日", GoalCalculator.weekdayLetter("2026-11-01"))
    }
}