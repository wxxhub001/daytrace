package com.daytrace.domain

import java.time.Instant
import java.time.ZoneId

/**
 * 核心算法：把 UsageEvents 的原始事件流，还原成"一次打开 = 一段前台会话"。
 *
 * 严格模式（strict）规则：
 *  1. 同一包连续 RESUMED 视为应用内部 Activity 跳转，不新增一次打开；
 *  2. PAUSED 之后若在 mergeGapMs 内重新 RESUMED，视为同一次打开（点通知回来、系统弹窗）；
 *  3. 会话时长 = 各段真实前台时间之和，不包含中间离开的间隙；
 *  4. 短于 minSessionMs 的会话直接丢弃（误触、闪退、权限弹窗）；
 *  5. 熄屏 / 锁屏 / 关机事件会立即结束当前会话，避免把息屏时间算成使用时间。
 */
object UsageAnalyzer {

    private class OpenSession(
        var pkg: String,
        var startTs: Long,
        var endTs: Long,
        var durationMs: Long,
        var markTs: Long,
        var paused: Boolean,
    )

    fun buildSessions(
        events: List<RawEvent>,
        rangeStart: Long,
        rangeEnd: Long,
        minSessionMs: Long,
        mergeGapMs: Long,
        excludedPackages: Set<String> = emptySet(),
    ): List<RawSession> {
        if (events.isEmpty()) return emptyList()

        val sorted = events.sortedBy { it.ts }
        val result = ArrayList<RawSession>()
        var cur: OpenSession? = null

        fun commit() {
            val s = cur ?: return
            cur = null
            if (s.durationMs <= 0L) return
            if (s.durationMs < minSessionMs) return
            if (s.pkg in excludedPackages) return
            result.add(RawSession(s.pkg, s.startTs, s.endTs, s.durationMs))
        }

        fun open(pkg: String, ts: Long) {
            val last = result.lastOrNull()
            if (last != null && last.pkg == pkg && ts - last.endTs in 0..mergeGapMs) {
                // 与上一段合并：算同一次打开，开始时间保持最初的
                result.removeAt(result.size - 1)
                cur = OpenSession(pkg, last.startTs, ts, last.durationMs, ts, false)
            } else {
                cur = OpenSession(pkg, ts, ts, 0L, ts, false)
            }
        }

        fun accumulate(ts: Long) {
            val s = cur ?: return
            if (s.paused) return
            s.durationMs += (ts - s.markTs).coerceAtLeast(0L)
            s.markTs = ts
            s.endTs = ts
        }

        for (e in sorted) {
            when (e.type) {
                EventType.RESUMED -> {
                    val s = cur
                    when {
                        s == null -> open(e.pkg, e.ts)
                        s.pkg == e.pkg && s.paused -> {
                            if (e.ts - s.markTs <= mergeGapMs) {
                                s.paused = false
                                s.markTs = e.ts
                                s.endTs = e.ts
                            } else {
                                commit()
                                open(e.pkg, e.ts)
                            }
                        }
                        s.pkg == e.pkg -> accumulate(e.ts)
                        else -> {
                            accumulate(e.ts)
                            commit()
                            open(e.pkg, e.ts)
                        }
                    }
                }

                EventType.PAUSED, EventType.STOPPED -> {
                    val s = cur
                    if (s != null && s.pkg == e.pkg && !s.paused) {
                        accumulate(e.ts)
                        s.paused = true
                    }
                }

                EventType.SCREEN_NON_INTERACTIVE,
                EventType.KEYGUARD_SHOWN,
                EventType.DEVICE_SHUTDOWN -> {
                    if (cur != null) {
                        accumulate(e.ts)
                        commit()
                    }
                }
            }
        }

        // 仍在进行中的会话统计到 rangeEnd
        if (cur != null) {
            accumulate(maxOf(rangeEnd, cur!!.markTs))
            commit()
        }
        return result
    }

    /**
     * 按本地自然日切分。跨零点的会话会拆成多段：
     * 只有包含"起点"的那一段计 1 次打开，后续片段 opens = 0，
     * 这样"今日打开次数"不会因为通宵挂机而虚增。
     */
    fun splitByDay(session: RawSession, zone: ZoneId): List<DayPart> {
        val start = session.startTs
        val end = session.endTs
        val dayKeyOfStart = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().toString()

        if (end <= start || session.durationMs <= 0L) {
            return listOf(DayPart(session.pkg, start, maxOf(end, start), session.durationMs, dayKeyOfStart, 1))
        }

        val parts = ArrayList<DayPart>(2)
        var cursor = start
        var remaining = session.durationMs
        val totalSpan = end - start
        var first = true
        var guard = 0

        while (cursor < end && guard < 64) {
            guard++
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segEnd = minOf(end, nextDayStart)
            val segLen = (segEnd - cursor).coerceAtLeast(1L)
            val segDuration = if (segEnd >= end || totalSpan <= 0L) {
                remaining
            } else {
                (session.durationMs * segLen) / totalSpan
            }
            remaining -= segDuration
            parts.add(
                DayPart(
                    pkg = session.pkg,
                    startTs = cursor,
                    endTs = segEnd,
                    durationMs = segDuration.coerceAtLeast(0L),
                    dayKey = date.toString(),
                    opens = if (first) 1 else 0,
                )
            )
            first = false
            cursor = segEnd
        }
        return parts
    }
}
