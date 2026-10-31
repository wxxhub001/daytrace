package com.daytrace.data

import com.daytrace.domain.GoalCalculator
import com.daytrace.domain.TimeUtil
import com.daytrace.system.GoalNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 小圆点上的一天 */
data class GoalDayMark(
    val dayKey: String,
    val started: Boolean,
    val done: Boolean,
    val valueMs: Long,
    val checked: Boolean,
)

/** 一个目标 + 它当前的进度，UI 直接消费 */
data class GoalProgress(
    val goal: GoalEntity,
    val todayDone: Boolean,
    val todayValueMs: Long,
    val weekCount: Int,
    val periodTarget: Int,
    val streak: Int,
    val ratio: Float,
    val marks: List<GoalDayMark>,
    /** 仅倒计时目标：距目标日还有多少天（0 = 今天，负数 = 已过期） */
    val daysLeft: Int? = null,
) {
    val isScreen: Boolean get() = goal.type == GoalType.SCREEN_LIMIT
    val isCountdown: Boolean get() = goal.type == GoalType.COUNTDOWN
    val todayChecked: Boolean get() = marks.lastOrNull()?.checked == true
}

/**
 * 目标模块的读写入口。
 *
 * 关键取舍：屏幕时间类的目标**不额外落库**，而是每次用已有的使用记录现算，
 * 这样不会出现"目标表和会话表对不上"的双写一致性问题，也不会重复占用空间。
 */
class GoalRepository(
    private val db: AppDatabase,
    private val usage: UsageRepository,
    private val notifier: GoalNotifier,
) {

    fun observeActive(): Flow<List<GoalEntity>> = db.goalDao().observeActive()

    suspend fun progressList(): List<GoalProgress> = withContext(Dispatchers.IO) {
        val today = TimeUtil.todayKey()
        val goals = db.goalDao().active()
        if (goals.isEmpty()) return@withContext emptyList()

        val scanDays = SCAN_DAYS
        val window = GoalCalculator.lastDays(today, scanDays)
        val fromKey = window.firstOrNull() ?: today

        val checkinDays = db.goalDao().checkinsSince(fromKey)
            .groupBy { it.goalId }
            .mapValues { entry -> entry.value.map { it.dayKey }.toSet() }

        val totalByDay: Map<String, Long> = if (goals.any { it.type == GoalType.SCREEN_LIMIT }) {
            usage.history(scanDays).associate { it.dayKey to it.totalMs }
        } else {
            emptyMap()
        }

        val weekKeys = GoalCalculator.weekDays(today)
        val markKeys = GoalCalculator.lastDays(today, MARK_DAYS)

        goals.map { goal ->
            val createdKey = TimeUtil.dayKey(goal.createdAt)
            val checks = checkinDays[goal.id] ?: emptySet()
            val targetMs = goal.targetMinutes * 60_000L
            val isCountdown = goal.type == GoalType.COUNTDOWN

            val doneDays = HashSet<String>(scanDays)
            if (!isCountdown) {
                for (key in window) {
                    if (key < createdKey) continue
                    val done = when (goal.type) {
                        GoalType.SCREEN_LIMIT ->
                            GoalCalculator.screenLimitDone(totalByDay[key] ?: 0L, goal.targetMinutes)

                        else -> key in checks
                    }
                    if (done) doneDays.add(key)
                }
            }

            val todayMs = totalByDay[today] ?: 0L
            val weekCount = weekKeys.count { it in checks }
            val periodTarget = when {
                goal.type == GoalType.SCREEN_LIMIT -> goal.targetMinutes
                goal.period == GoalPeriod.WEEK -> goal.targetCount
                else -> goal.targetCount
            }

            val todayDone = when {
                isCountdown -> goal.dueDayKey != null && goal.dueDayKey <= today

                goal.type == GoalType.SCREEN_LIMIT ->
                    GoalCalculator.screenLimitDone(todayMs, goal.targetMinutes)

                goal.period == GoalPeriod.WEEK ->
                    GoalCalculator.checkinDone(weekCount, goal.targetCount)

                else -> today in checks
            }

            val ratio = when {
                isCountdown -> countdownRatio(createdKey, goal.dueDayKey, today)
                goal.type == GoalType.SCREEN_LIMIT -> GoalCalculator.ratio(todayMs, targetMs)
                goal.period == GoalPeriod.WEEK ->
                    GoalCalculator.ratio(weekCount.toLong(), goal.targetCount.toLong())

                else -> if (today in checks) 1f else 0f
            }

            GoalProgress(
                goal = goal,
                todayDone = todayDone,
                todayValueMs = todayMs,
                weekCount = weekCount,
                periodTarget = periodTarget,
                streak = if (isCountdown) 0 else GoalCalculator.streak(doneDays, today),
                ratio = ratio,
                marks = if (isCountdown) {
                    emptyList()
                } else {
                    markKeys.map { key ->
                        GoalDayMark(
                            dayKey = key,
                            started = key >= createdKey,
                            done = key in doneDays,
                            valueMs = totalByDay[key] ?: 0L,
                            checked = key in checks,
                        )
                    }
                },
                daysLeft = if (isCountdown) countdownDays(goal.dueDayKey, today) else null,
            )
        }
    }

    // ------------------------------------------------------------------ 写操作

    suspend fun addScreenLimit(title: String, targetMinutes: Int): Long =
        withContext(Dispatchers.IO) {
            db.goalDao().insert(
                GoalEntity(
                    title = title.trim().ifBlank { "屏幕时间上限" },
                    type = GoalType.SCREEN_LIMIT,
                    targetMinutes = targetMinutes.coerceIn(5, 24 * 60),
                    targetCount = 1,
                    period = GoalPeriod.DAY,
                ),
            )
        }

    suspend fun addCheckin(title: String, period: String, targetCount: Int): Long =
        withContext(Dispatchers.IO) {
            db.goalDao().insert(
                GoalEntity(
                    title = title.trim().ifBlank { "新习惯" },
                    type = GoalType.CHECKIN,
                    targetMinutes = 0,
                    targetCount = targetCount.coerceIn(1, 30),
                    period = if (period == GoalPeriod.WEEK) GoalPeriod.WEEK else GoalPeriod.DAY,
                ),
            )
        }

    /** 指定日期的倒计时目标 */
    suspend fun addCountdown(title: String, dueDayKey: String): Long =
        withContext(Dispatchers.IO) {
            db.goalDao().insert(
                GoalEntity(
                    title = title.trim().ifBlank { "重要日子" },
                    type = GoalType.COUNTDOWN,
                    targetMinutes = 0,
                    targetCount = 1,
                    period = GoalPeriod.DAY,
                    dueDayKey = dueDayKey,
                ),
            )
        }

    /** 目标日还剩几天；解析失败返回 null */
    private fun countdownDays(dueKey: String?, todayKey: String): Int? {
        val due = parseDate(dueKey) ?: return null
        val today = parseDate(todayKey) ?: return null
        return ChronoUnit.DAYS.between(today, due).toInt()
    }

    /** 倒计时进度：从创建日走到目标日的比例 */
    private fun countdownRatio(createdKey: String, dueKey: String?, todayKey: String): Float {
        val due = parseDate(dueKey) ?: return 0f
        val created = parseDate(createdKey) ?: return 0f
        val today = parseDate(todayKey) ?: return 0f
        val total = ChronoUnit.DAYS.between(created, due).coerceAtLeast(1L)
        val elapsed = ChronoUnit.DAYS.between(created, today).coerceIn(0L, total)
        return elapsed.toFloat() / total.toFloat()
    }

    private fun parseDate(key: String?): LocalDate? {
        if (key.isNullOrBlank()) return null
        return try {
            LocalDate.parse(key)
        } catch (t: Throwable) {
            null
        }
    }

    /** 今天打卡 / 取消打卡 */
    suspend fun toggleCheckin(goalId: Long): Boolean = withContext(Dispatchers.IO) {
        val today = TimeUtil.todayKey()
        val dao = db.goalDao()
        val exists = dao.checkinsOf(goalId).any { it.dayKey == today }
        if (exists) {
            dao.deleteCheckin(goalId, today)
            false
        } else {
            dao.putCheckin(
                GoalCheckinEntity(
                    goalId = goalId,
                    dayKey = today,
                    ts = System.currentTimeMillis(),
                ),
            )
            true
        }
    }

    suspend fun setArchived(goal: GoalEntity, archived: Boolean) = withContext(Dispatchers.IO) {
        db.goalDao().update(goal.copy(archived = archived))
    }

    suspend fun remove(goalId: Long) = withContext(Dispatchers.IO) {
        db.goalDao().clearCheckins(goalId)
        db.goalDao().delete(goalId)
    }

    // ------------------------------------------------------------------ 提醒

    /**
     * 白天兜底检查：9:00–22:00 之间每 3 小时催一次（一天最多 5 次），
     * 每次把当天所有未完成目标合并成一条通知。每个 3 小时时段内只提醒一次，
     * 靠 goals.lastNotifiedDay 记录 "日期#时段" 去重。
     */
    suspend fun checkAndNotify(force: Boolean = false) {
        if (!notifier.canNotify()) return
        val hour = LocalTime.now().hour
        if (!force && hour !in REMIND_FROM_HOUR..REMIND_UNTIL_HOUR) return

        val today = TimeUtil.todayKey()
        val token = if (force) "$today#now" else "$today#${hour / REMIND_EVERY_HOURS}"

        val pending = progressList().filter { progress ->
            // 倒计时目标不是"待办"，不需要催
            progress.goal.type != GoalType.COUNTDOWN &&
                !progress.todayDone &&
                progress.goal.lastNotifiedDay != token
        }
        if (pending.isEmpty()) return

        notifier.notifyUnfinished(pending.map { progress -> progress.goal.title to stateText(progress) })
        for (progress in pending) {
            db.goalDao().update(progress.goal.copy(lastNotifiedDay = token))
        }
    }

    private fun stateText(progress: GoalProgress): String {
        val goal = progress.goal
        return if (goal.type == GoalType.SCREEN_LIMIT) {
            "今天已用 ${TimeUtil.formatCompact(progress.todayValueMs)}（上限 ${goal.targetMinutes} 分钟）"
        } else if (goal.period == GoalPeriod.WEEK) {
            "本周 ${progress.weekCount}/${goal.targetCount} 次"
        } else {
            "今天还没打卡"
        }
    }

    companion object {
        /** 目标回溯窗口：连续天数与"历史达标"都在这段范围内计算 */
        private const val SCAN_DAYS = 120
        private const val MARK_DAYS = 7
        /** 目标催办窗口：早上 9 点到晚上 10 点 */
        private const val REMIND_FROM_HOUR = 9
        private const val REMIND_UNTIL_HOUR = 22
        /** 窗口内每 N 小时催一次 */
        private const val REMIND_EVERY_HOURS = 3
    }
}