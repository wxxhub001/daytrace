package com.daytrace

import com.daytrace.domain.EventType
import com.daytrace.domain.RawEvent
import com.daytrace.domain.RawSession
import com.daytrace.domain.UsageAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 核心算法单元测试。运行：./gradlew :app:testDebugUnitTest
 * 这里固化了"严格模式"的判定规则，改算法前先跑一遍。
 */
class UsageAnalyzerTest {

    private val minSession = 1_500L
    private val mergeGap = 3_000L
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun resumed(ts: Long, pkg: String) = RawEvent(ts, pkg, EventType.RESUMED)
    private fun paused(ts: Long, pkg: String) = RawEvent(ts, pkg, EventType.PAUSED)

    private fun build(events: List<RawEvent>) = UsageAnalyzer.buildSessions(
        events = events,
        rangeStart = 0L,
        rangeEnd = 1_000_000L,
        minSessionMs = minSession,
        mergeGapMs = mergeGap,
        excludedPackages = emptySet(),
    )

    @Test
    fun threeSeparateOpens() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"), paused(5_000, "app.a"),
                resumed(60_000, "app.b"), paused(70_000, "app.b"),
                resumed(120_000, "app.a"), paused(130_000, "app.a"),
            )
        )
        assertEquals(3, sessions.size)
        assertEquals(listOf("app.a", "app.b", "app.a"), sessions.map { it.pkg })
        assertEquals(5_000L, sessions[0].durationMs)
        assertEquals(10_000L, sessions[1].durationMs)
    }

    @Test
    fun internalActivitySwitchIsOneOpen() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"),
                resumed(2_000, "app.a"),
                resumed(4_000, "app.a"),
                paused(6_000, "app.a"),
            )
        )
        assertEquals(1, sessions.size)
        assertEquals(6_000L, sessions[0].durationMs)
    }

    @Test
    fun quickReturnMergesIntoOneOpen() {
        // 暂停 2 秒后回来（例如点了通知 / 系统弹窗），仍算同一次
        val sessions = build(
            listOf(
                resumed(0, "app.a"), paused(3_000, "app.a"),
                resumed(5_000, "app.a"), paused(8_000, "app.a"),
            )
        )
        assertEquals(1, sessions.size)
        assertEquals(6_000L, sessions[0].durationMs)
        assertEquals(0L, sessions[0].startTs)
        assertEquals(8_000L, sessions[0].endTs)
    }

    @Test
    fun longGapCreatesNewOpen() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"), paused(3_000, "app.a"),
                resumed(60_000, "app.a"), paused(63_000, "app.a"),
            )
        )
        assertEquals(2, sessions.size)
    }

    @Test
    fun tinySessionIsDropped() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"), paused(400, "app.a"),
            )
        )
        assertEquals(0, sessions.size)
    }

    @Test
    fun screenOffEndsSession() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"),
                RawEvent(10_000, "", EventType.SCREEN_NON_INTERACTIVE),
                resumed(60_000, "app.b"),
                paused(70_000, "app.b"),
            )
        )
        assertEquals(2, sessions.size)
        assertEquals(10_000L, sessions[0].durationMs)
        assertEquals(10_000L, sessions[1].durationMs)
    }

    @Test
    fun switchingAppClosesPrevious() {
        val sessions = build(
            listOf(
                resumed(0, "app.a"),
                resumed(5_000, "app.b"),
                paused(9_000, "app.b"),
            )
        )
        assertEquals(2, sessions.size)
        assertEquals(5_000L, sessions[0].durationMs)
        assertEquals(4_000L, sessions[1].durationMs)
    }

    @Test
    fun ongoingSessionCountsUntilNow() {
        val sessions = build(listOf(resumed(0, "app.a")))
        assertEquals(1, sessions.size)
        assertEquals(1_000_000L, sessions[0].durationMs)
    }

    @Test
    fun excludedPackagesAreIgnored() {
        val sessions = UsageAnalyzer.buildSessions(
            events = listOf(resumed(0, "launcher"), paused(5_000, "launcher")),
            rangeStart = 0L,
            rangeEnd = 10_000L,
            minSessionMs = minSession,
            mergeGapMs = mergeGap,
            excludedPackages = setOf("launcher"),
        )
        assertEquals(0, sessions.size)
    }

    @Test
    fun crossMidnightIsSplitWithSingleOpen() {
        val start = OffsetDateTime.parse("2024-06-01T23:50:00+08:00").toInstant().toEpochMilli()
        val end = start + 20 * 60_000L
        val session = RawSession("app.a", start, end, 20 * 60_000L)

        val parts = UsageAnalyzer.splitByDay(session, zone)

        assertEquals(2, parts.size)
        assertEquals("2024-06-01", parts[0].dayKey)
        assertEquals("2024-06-02", parts[1].dayKey)
        assertEquals(1, parts[0].opens)
        assertEquals(0, parts[1].opens)
        assertEquals(10 * 60_000L, parts[0].durationMs)
        assertEquals(10 * 60_000L, parts[1].durationMs)
        assertEquals(20 * 60_000L, parts[0].durationMs + parts[1].durationMs)
    }
}
