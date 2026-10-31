package com.daytrace.domain

/** UsageEvents 中我们关心的事件类型（常量值与系统 UsageEvents.Event 保持一致） */
object EventType {
    const val RESUMED = 1                 // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
    const val PAUSED = 2                  // ACTIVITY_PAUSED / MOVE_TO_BACKGROUND
    const val STOPPED = 23                // ACTIVITY_STOPPED (API 29+)
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_HIDDEN = 17
    const val KEYGUARD_SHOWN = 18
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27
}

/** 从系统读到的原始事件 */
data class RawEvent(val ts: Long, val pkg: String, val type: Int)

/** 配对后的一段前台会话 */
data class RawSession(
    val pkg: String,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
)

/** 按自然日切分后的片段（跨零点的会话会被拆成多段） */
data class DayPart(
    val pkg: String,
    val startTs: Long,
    val endTs: Long,
    val durationMs: Long,
    val dayKey: String,
    val opens: Int,
)

/** 单个应用在一段区间内的汇总 */
data class AppUsageItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val openCount: Int,
    val totalMs: Long,
    val lastUsedTs: Long,
)

data class AggregateResult(
    val items: List<AppUsageItem>,
    val totalMs: Long,
    val totalOpens: Int,
) {
    val appCount: Int get() = items.size
}

data class DaySummary(
    val dayKey: String,
    val totalMs: Long,
    val openCount: Int,
    val appCount: Int,
)

data class DayBucket(val dayKey: String, val durationMs: Long)

data class SessionView(val startTs: Long, val endTs: Long, val durationMs: Long)

data class AppDetail(
    val packageName: String,
    val label: String,
    val totalMs: Long,
    val openCount: Int,
    val avgMs: Long,
    val buckets: List<DayBucket>,
    val sessions: List<SessionView>,
)

enum class LimitKind { TIME, OPENS }
