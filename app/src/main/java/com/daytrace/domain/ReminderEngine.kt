package com.daytrace.domain

import com.daytrace.data.ActionMode
import com.daytrace.data.AppSettings
import com.daytrace.data.LimitEntity
import com.daytrace.data.ReminderStateEntity

/** 到点动作，按"温和→强硬"排序（比较用的是 ordinal） */
enum class ReminderAction { NOTIFY, NOTIFY_VIBRATE, POPUP, POPUP_VIBRATE, HOME, HOME_LOCK, KILL }

enum class TriggerReason { SESSION_INTERVAL, LIMIT_TIME, LIMIT_OPENS, LIMIT_REPEAT, COOLDOWN_KICK }

data class ReminderContext(
    val now: Long,
    val dayKey: String,
    val minuteOfDay: Int,
    val packageName: String,
    /** 当前这次连续使用的时长 */
    val sessionUsedMs: Long,
    /** 今日累计使用时长 */
    val todayUsedMs: Long,
    val todayOpens: Int,
    val limit: LimitEntity,
    val state: ReminderStateEntity,
    val settings: AppSettings,
)

sealed interface ReminderDecision {
    data object Idle : ReminderDecision

    data class Fire(
        val reason: TriggerReason,
        val action: ReminderAction,
        val state: ReminderStateEntity,
        val cooldownUntil: Long,
    ) : ReminderDecision
}

/**
 * 纯函数提醒判定：不做任何 IO，方便单元测试。
 *
 * 规则：
 * 1. 冷却期内又打开该应用 → 立即再弹回（视为"已退出到桌面"的持续效果）
 * 2. 连续使用每 N 分钟提醒一次（会话口径）
 * 3. 当日首次超限提醒一次，之后每 M 分钟催办
 * 4. 白名单 / 免打扰 / 今天不再提醒 / 最小间隔 均直接跳过
 */
object ReminderEngine {

    const val MIN_GAP_MS = 3 * 60_000L
    const val COOLDOWN_MS = 3 * 60_000L
    const val SNOOZE_MS = 5 * 60_000L
    const val MAX_SNOOZE_PER_DAY = 2

    fun decide(ctx: ReminderContext): ReminderDecision {
        if (!ctx.limit.enabled) return ReminderDecision.Idle
        if (!ctx.settings.notificationsEnabled) return ReminderDecision.Idle
        if (ctx.packageName in ctx.settings.whitelist) return ReminderDecision.Idle
        if (ctx.settings.inDoNotDisturb(ctx.minuteOfDay)) return ReminderDecision.Idle

        var state = ctx.state
        if (state.dayKey != ctx.dayKey) {
            state = state.copy(
                dayKey = ctx.dayKey,
                triggerCountToday = 0,
                limitNotified = false,
                lastLimitAt = 0L,
                thresholdFired = 0L,
                cooldownUntil = 0L,
                snoozeUntil = 0L,
                snoozeCountToday = 0,
                silencedForDay = false,
            )
        }
        if (state.silencedForDay) return ReminderDecision.Idle
        if (state.snoozeUntil > ctx.now) return ReminderDecision.Idle
        if (ctx.now - state.lastTriggerAt < MIN_GAP_MS) return ReminderDecision.Idle

        val nextAction = resolveAction(ctx.limit, ctx.settings, state.triggerCountToday + 1)

        // 冷却期内用户又打开了这个应用：不计数、不通知，直接再弹一次
        if (state.cooldownUntil > ctx.now &&
            ctx.sessionUsedMs >= 1_000L &&
            nextAction >= ReminderAction.HOME
        ) {
            return ReminderDecision.Fire(
                reason = TriggerReason.COOLDOWN_KICK,
                action = nextAction,
                state = state,
                cooldownUntil = ctx.now + COOLDOWN_MS,
            )
        }

        // 1) 连续使用周期提醒
        val every = ctx.limit.remindEveryMinutes
        if (every != null && every > 0) {
            val interval = every * 60_000L
            if (ctx.sessionUsedMs >= state.thresholdFired + interval) {
                val nextThreshold = (ctx.sessionUsedMs / interval) * interval
                val s = state.copy(
                    thresholdFired = nextThreshold,
                    triggerCountToday = state.triggerCountToday + 1,
                    lastTriggerAt = ctx.now,
                )
                val action = resolveAction(ctx.limit, ctx.settings, s.triggerCountToday)
                return ReminderDecision.Fire(
                    reason = TriggerReason.SESSION_INTERVAL,
                    action = action,
                    state = s,
                    cooldownUntil = if (action >= ReminderAction.HOME) ctx.now + COOLDOWN_MS else 0L,
                )
            }
        }

        // 2) 当日限额
        val limitMs = ctx.limit.dailyMinutes?.toLong()?.times(60_000L)
        val overTime = limitMs != null && ctx.todayUsedMs >= limitMs
        val overOpens = ctx.limit.dailyOpens?.let { ctx.todayOpens >= it } ?: false
        if (!overTime && !overOpens) return ReminderDecision.Idle

        val reason = if (overTime) TriggerReason.LIMIT_TIME else TriggerReason.LIMIT_OPENS

        if (!state.limitNotified) {
            val s = state.copy(
                limitNotified = true,
                lastLimitAt = ctx.now,
                lastTriggerAt = ctx.now,
                triggerCountToday = state.triggerCountToday + 1,
            )
            val action = resolveAction(ctx.limit, ctx.settings, s.triggerCountToday)
            return ReminderDecision.Fire(
                reason = reason,
                action = action,
                state = s,
                cooldownUntil = if (action >= ReminderAction.HOME) ctx.now + COOLDOWN_MS else 0L,
            )
        }

        val repeat = ctx.limit.repeatAfterLimitMinutes
        if (repeat != null && repeat > 0 && ctx.now - state.lastLimitAt >= repeat * 60_000L) {
            val s = state.copy(
                lastLimitAt = ctx.now,
                lastTriggerAt = ctx.now,
                triggerCountToday = state.triggerCountToday + 1,
            )
            val action = resolveAction(ctx.limit, ctx.settings, s.triggerCountToday)
            return ReminderDecision.Fire(
                reason = TriggerReason.LIMIT_REPEAT,
                action = action,
                state = s,
                cooldownUntil = if (action >= ReminderAction.HOME) ctx.now + COOLDOWN_MS else 0L,
            )
        }

        return ReminderDecision.Idle
    }

    /** AUTO 模式下按当日触发次数升级：1 弹窗 → 2 弹窗+震动 → 3+ 弹回桌面 */
    fun resolveAction(limit: LimitEntity, settings: AppSettings, triggerCount: Int): ReminderAction =
        when (limit.actionMode) {
            ActionMode.NOTIFY -> ReminderAction.NOTIFY
            ActionMode.POPUP -> ReminderAction.POPUP
            ActionMode.HOME -> ReminderAction.HOME
            ActionMode.LOCK -> ReminderAction.HOME_LOCK
            ActionMode.KILL -> ReminderAction.KILL
            else -> when {
                !settings.escalationEnabled -> ReminderAction.POPUP
                triggerCount <= 1 -> ReminderAction.POPUP
                triggerCount == 2 -> ReminderAction.POPUP_VIBRATE
                else -> ReminderAction.HOME
            }
        }

    fun describe(reason: TriggerReason, usedMs: Long, todayMs: Long, limit: LimitEntity): String =
        when (reason) {
            TriggerReason.SESSION_INTERVAL ->
                "连续使用了 ${TimeUtil.formatCompact(usedMs)}，休息一下吧"
            TriggerReason.LIMIT_TIME ->
                "已超出今日 ${limit.dailyMinutes} 分钟上限（今天 ${TimeUtil.formatCompact(todayMs)}）"
            TriggerReason.LIMIT_OPENS ->
                "已超出今日 ${limit.dailyOpens} 次上限（已打开 ${limit.dailyOpens} 次以上）"
            TriggerReason.LIMIT_REPEAT ->
                "今天已用 ${TimeUtil.formatCompact(todayMs)}，超时提醒"
            TriggerReason.COOLDOWN_KICK ->
                "刚刚提醒过，先休息一下吧"
        }
}
