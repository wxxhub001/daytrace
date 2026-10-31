package com.daytrace.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtil {

    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val weekFormatter = DateTimeFormatter.ofPattern("EEE", Locale.CHINA)
    private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    fun dayKey(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate().toString()

    fun todayKey(zone: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zone).toString()

    fun startOfDay(dayKey: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(dayKey).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(dayKey: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(dayKey).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** 返回最近 count 天（含今天）的 dayKey，升序 */
    fun recentDayKeys(count: Int, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val today = LocalDate.now(zone)
        return (count - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }

    fun formatDuration(ms: Long): String {
        if (ms < 1000L) return "0 秒"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 && m > 0 -> "${h} 小时 ${m} 分"
            h > 0 -> "${h} 小时"
            m > 0 && s > 0 -> "${m} 分 ${s} 秒"
            m > 0 -> "${m} 分"
            else -> "${s} 秒"
        }
    }

    /** 紧凑格式，列表里用 */
    fun formatCompact(ms: Long): String {
        if (ms < 60_000L) return "${(ms / 1000).coerceAtLeast(0)}秒"
        val totalMin = ms / 60_000L
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}小时${m}分" else "${m}分"
    }

    fun formatClock(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clockFormatter.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun formatDateTime(ts: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        fullFormatter.format(Instant.ofEpochMilli(ts).atZone(zone))

    fun formatDayLabel(dayKey: String): String {
        val date = LocalDate.parse(dayKey)
        return dateFormatter.format(date) + " " + weekFormatter.format(date)
    }

    fun isToday(dayKey: String): Boolean = dayKey == todayKey()
}
