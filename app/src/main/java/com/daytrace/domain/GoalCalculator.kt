package com.daytrace.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 目标判定的纯逻辑，不依赖 Android，可直接单元测试。
 *
 * 约定：dayKey 一律是 ISO 日期字符串（如 "2026-10-26"），字符串比较即时间先后。
 */
object GoalCalculator {

    /** 打卡目标允许的最长连续追溯天数，防止异常数据导致死循环 */
    private const val MAX_WALK = 3650

    /** 屏幕时间目标：当日总时长不超过上限即达标（上限 <= 0 视为无效配置） */
    fun screenLimitDone(usedMs: Long, targetMinutes: Int): Boolean =
        targetMinutes > 0 && usedMs <= targetMinutes * 60_000L

    /** 打卡目标：周期内次数够了即达标 */
    fun checkinDone(count: Int, targetCount: Int): Boolean =
        targetCount > 0 && count >= targetCount

    /** 进度比例，用于进度条；超过 100% 时保留一点超出量，方便视觉上变红 */
    fun ratio(used: Long, target: Long): Float {
        if (target <= 0L) return 0f
        return (used.toDouble() / target.toDouble()).toFloat().coerceIn(0f, 1.4f)
    }

    /**
     * 连续达标天数：从今天往回数。
     *
     * [pendingTodayCounts] = true 表示"今天还没过完"，今天尚未达标不算断链，
     * 从昨天继续往回数；设为 false 则严格从今天开始算。
     */
    fun streak(
        doneDays: Set<String>,
        todayKey: String,
        pendingTodayCounts: Boolean = true,
    ): Int {
        var day = parseOrNull(todayKey) ?: return 0
        var count = 0
        if (day.toString() in doneDays) {
            count++
            day = day.minusDays(1)
        } else if (pendingTodayCounts) {
            day = day.minusDays(1)
        } else {
            return 0
        }
        var guard = 0
        while (guard++ < MAX_WALK && day.toString() in doneDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** 最近 [count] 天的 dayKey（含今天），升序：最早 → 今天 */
    fun lastDays(todayKey: String, count: Int): List<String> {
        val today = parseOrNull(todayKey) ?: return emptyList()
        if (count <= 0) return emptyList()
        return (count - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }

    /** 本周（周一起算）到今天的 dayKey 列表，升序 */
    fun weekDays(todayKey: String): List<String> {
        val today = parseOrNull(todayKey) ?: return emptyList()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val out = ArrayList<String>(7)
        var cursor = monday
        while (!cursor.isAfter(today)) {
            out.add(cursor.toString())
            cursor = cursor.plusDays(1)
        }
        return out
    }

    /** 一周几到周几的中文单字，用于小圆点标签 */
    fun weekdayLetter(dayKey: String): String {
        val day = parseOrNull(dayKey) ?: return ""
        return when (day.dayOfWeek) {
            DayOfWeek.MONDAY -> "一"
            DayOfWeek.TUESDAY -> "二"
            DayOfWeek.WEDNESDAY -> "三"
            DayOfWeek.THURSDAY -> "四"
            DayOfWeek.FRIDAY -> "五"
            DayOfWeek.SATURDAY -> "六"
            DayOfWeek.SUNDAY -> "日"
        }
    }

    private fun parseOrNull(dayKey: String): LocalDate? = try {
        if (dayKey.isBlank()) null else LocalDate.parse(dayKey)
    } catch (t: Throwable) {
        null
    }
}