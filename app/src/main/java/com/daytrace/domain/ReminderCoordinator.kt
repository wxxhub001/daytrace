package com.daytrace.domain

import com.daytrace.data.AppDatabase
import com.daytrace.data.ReminderStateEntity
import com.daytrace.data.SettingsStore
import com.daytrace.system.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/**
 * 提醒调度：把"当前前台应用 / 用法数据 / 配置"送进 ReminderEngine，
 * 决定是否提醒以及执行什么动作。
 */
class ReminderCoordinator(
    private val db: AppDatabase,
    private val repository: com.daytrace.data.UsageRepository,
    private val settings: SettingsStore,
    private val eventSource: UsageEventSource,
    private val notifier: Notifier,
    private val intervention: InterventionController,
) {

    private val mutex = Mutex()
    private var cacheDayKey: String = ""
    private var cacheAt: Long = 0L
    private var cacheAggregate: AggregateResult? = null

    /** 精准模式：每采样一次就调用一次 */
    suspend fun evaluate(pkg: String, sessionUsedMs: Long) {
        mutex.withLock {
            val limit = db.limitDao().find(pkg) ?: return
            val current = settings.current()
            val now = System.currentTimeMillis()
            val aggregate = aggregateFor(current)
            val usage = aggregate.items.firstOrNull { it.packageName == pkg }
            val state = db.reminderDao().get(pkg) ?: ReminderStateEntity(packageName = pkg)
            val dayKey = TimeUtil.todayKey()

            val ctx = ReminderContext(
                now = now,
                dayKey = dayKey,
                minuteOfDay = minuteOfDay(now),
                packageName = pkg,
                sessionUsedMs = sessionUsedMs,
                todayUsedMs = usage?.totalMs ?: 0L,
                todayOpens = usage?.openCount ?: 0,
                limit = limit,
                state = state,
                settings = current,
            )

            val decision = ReminderEngine.decide(ctx)
            if (decision !is ReminderDecision.Fire) return

            val saved = decision.state.copy(
                cooldownUntil = maxOf(decision.cooldownUntil, decision.state.cooldownUntil),
            )
            db.reminderDao().put(saved)
            db.limitDao().markNotified(pkg, dayKey, now)

            val isPopup = decision.action == ReminderAction.POPUP ||
                decision.action == ReminderAction.POPUP_VIBRATE
            val popupShown = isPopup && intervention.showPopup(
                limit = limit,
                sessionUsedMs = sessionUsedMs,
                todayUsedMs = ctx.todayUsedMs,
            )

            if (decision.reason != TriggerReason.COOLDOWN_KICK) {
                // 弹窗已经抢到注意力就不再叠通知；弹窗没弹出来才退回通知，别让提醒静默丢失
                if (!popupShown) {
                    notifier.notifyReminder(
                        limit = limit,
                        reason = decision.reason,
                        action = decision.action,
                        sessionUsedMs = sessionUsedMs,
                        todayUsedMs = ctx.todayUsedMs,
                        todayOpens = ctx.todayOpens,
                    )
                }
                if (decision.action == ReminderAction.NOTIFY_VIBRATE ||
                    decision.action == ReminderAction.POPUP_VIBRATE
                ) {
                    notifier.vibrate(300L)
                }
            }

            if (popupShown) {
                // 清掉当天遗留的"已超限"通知，避免弹窗和通知同时挂着
                notifier.cancel(pkg)
            } else {
                intervention.perform(
                    action = decision.action,
                    limit = limit,
                    sessionUsedMs = sessionUsedMs,
                    todayUsedMs = ctx.todayUsedMs,
                )
            }
        }
    }

    /** 标准模式：WorkManager / 打开 App 时检查当前前台应用 */
    suspend fun checkCurrent() {
        val now = System.currentTimeMillis()
        val dayStart = TimeUtil.startOfDay(TimeUtil.todayKey())
        val events = eventSource.query(dayStart, now)
        if (events.isEmpty()) return
        val sessions = UsageAnalyzer.buildSessions(
            events = events,
            rangeStart = dayStart,
            rangeEnd = now,
            minSessionMs = 0L,
            mergeGapMs = 3_000L,
            excludedPackages = emptySet(),
        )
        val last = sessions.lastOrNull() ?: return
        if (now - last.endTs > 120_000L) return
        evaluate(last.pkg, last.durationMs)
    }

    /** 通知上的"今天不再提醒" */
    suspend fun silenceForDay(pkg: String) {
        val state = db.reminderDao().get(pkg) ?: ReminderStateEntity(packageName = pkg)
        db.reminderDao().put(
            state.copy(
                packageName = pkg,
                dayKey = TimeUtil.todayKey(),
                silencedForDay = true,
                cooldownUntil = 0L,
            ),
        )
        notifier.cancel(pkg)
    }

    /** 拦截页上的"再给我 5 分钟" */
    suspend fun snooze(pkg: String): Boolean {
        val state = db.reminderDao().get(pkg) ?: ReminderStateEntity(packageName = pkg)
        val today = TimeUtil.todayKey()
        val count = if (state.dayKey == today) state.snoozeCountToday else 0
        if (count >= ReminderEngine.MAX_SNOOZE_PER_DAY) return false
        db.reminderDao().put(
            state.copy(
                packageName = pkg,
                dayKey = today,
                snoozeCountToday = count + 1,
                snoozeUntil = System.currentTimeMillis() + ReminderEngine.SNOOZE_MS,
                cooldownUntil = 0L,
            ),
        )
        notifier.cancel(pkg)
        return true
    }

    suspend fun snoozeLeftToday(pkg: String): Int {
        val state = db.reminderDao().get(pkg) ?: return ReminderEngine.MAX_SNOOZE_PER_DAY
        val today = TimeUtil.todayKey()
        val used = if (state.dayKey == today) state.snoozeCountToday else 0
        return (ReminderEngine.MAX_SNOOZE_PER_DAY - used).coerceAtLeast(0)
    }

    suspend fun stateOf(pkg: String): ReminderStateEntity? = db.reminderDao().get(pkg)

    suspend fun clearAll() {
        db.reminderDao().clear()
    }

    private suspend fun aggregateFor(current: com.daytrace.data.AppSettings): AggregateResult {
        val now = System.currentTimeMillis()
        val dayKey = TimeUtil.todayKey()
        val cached = cacheAggregate
        if (cached != null && cacheDayKey == dayKey && now - cacheAt < 20_000L) return cached
        val fresh = repository.dayAggregate(dayKey)
        cacheAggregate = fresh
        cacheDayKey = dayKey
        cacheAt = now
        return fresh
    }

    private fun minuteOfDay(now: Long): Int {
        val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalTime()
        return time.hour * 60 + time.minute
    }
}
