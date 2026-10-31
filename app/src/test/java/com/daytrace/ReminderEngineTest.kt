package com.daytrace

import com.daytrace.data.ActionMode
import com.daytrace.data.AppSettings
import com.daytrace.data.LimitEntity
import com.daytrace.data.ReminderStateEntity
import com.daytrace.domain.ReminderAction
import com.daytrace.domain.ReminderContext
import com.daytrace.domain.ReminderDecision
import com.daytrace.domain.ReminderEngine
import com.daytrace.domain.TriggerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {

    private val settings = AppSettings(
        whitelist = emptySet(),
        dndEnabled = false,
        escalationEnabled = true,
    )

    private fun limit(
        dailyMinutes: Int? = null,
        dailyOpens: Int? = null,
        remindEvery: Int? = 10,
        repeatEvery: Int? = 10,
        mode: String = ActionMode.AUTO,
        enabled: Boolean = true,
    ) = LimitEntity(
        packageName = PKG,
        label = "抖音",
        dailyMinutes = dailyMinutes,
        dailyOpens = dailyOpens,
        remindEveryMinutes = remindEvery,
        repeatAfterLimitMinutes = repeatEvery,
        actionMode = mode,
        enabled = enabled,
    )

    private fun context(
        now: Long = 1_000_000_000L,
        minuteOfDay: Int = 10 * 60,
        sessionUsedMs: Long = 10 * 60_000L,
        todayUsedMs: Long = 30 * 60_000L,
        todayOpens: Int = 5,
        limit: LimitEntity = limit(),
        state: ReminderStateEntity = ReminderStateEntity(packageName = PKG, dayKey = DAY),
        settings: AppSettings = this.settings,
    ) = ReminderContext(
        now = now,
        dayKey = DAY,
        minuteOfDay = minuteOfDay,
        packageName = PKG,
        sessionUsedMs = sessionUsedMs,
        todayUsedMs = todayUsedMs,
        todayOpens = todayOpens,
        limit = limit,
        state = state,
        settings = settings,
    )

    private fun fire(decision: ReminderDecision): ReminderDecision.Fire {
        assertTrue("期望触发，实际 $decision", decision is ReminderDecision.Fire)
        return decision as ReminderDecision.Fire
    }

    @Test
    fun firstIntervalTriggerShowsPopup() {
        val decision = fire(ReminderEngine.decide(context()))
        assertEquals(TriggerReason.SESSION_INTERVAL, decision.reason)
        assertEquals(ReminderAction.POPUP, decision.action)
        assertEquals(1, decision.state.triggerCountToday)
    }

    @Test
    fun secondTriggerVibratesWithPopup() {
        val decision = fire(
            ReminderEngine.decide(
                context(
                    state = ReminderStateEntity(
                        packageName = PKG,
                        dayKey = DAY,
                        triggerCountToday = 1,
                        thresholdFired = 10 * 60_000L,
                    ),
                    sessionUsedMs = 20 * 60_000L,
                ),
            ),
        )
        assertEquals(ReminderAction.POPUP_VIBRATE, decision.action)
        assertEquals(2, decision.state.triggerCountToday)
    }

    @Test
    fun popupModeAlwaysShowsPopup() {
        val limit = LimitEntity(
            packageName = PKG,
            label = "抖音",
            actionMode = ActionMode.POPUP,
        )
        assertEquals(ReminderAction.POPUP, ReminderEngine.resolveAction(limit, AppSettings(), 5))
    }

    @Test
    fun autoWithoutEscalationStillUsesPopup() {
        val limit = LimitEntity(
            packageName = PKG,
            label = "抖音",
            actionMode = ActionMode.AUTO,
        )
        val action = ReminderEngine.resolveAction(
            limit = limit,
            settings = AppSettings(escalationEnabled = false),
            triggerCount = 9,
        )
        assertEquals(ReminderAction.POPUP, action)
    }

    @Test
    fun thirdTriggerGoesHome() {
        val decision = fire(
            ReminderEngine.decide(
                context(
                    state = ReminderStateEntity(
                        packageName = PKG,
                        dayKey = DAY,
                        triggerCountToday = 2,
                        thresholdFired = 20 * 60_000L,
                    ),
                    sessionUsedMs = 30 * 60_000L,
                ),
            ),
        )
        assertEquals(ReminderAction.HOME, decision.action)
        assertTrue("回桌面后应进入冷却期", decision.cooldownUntil > 1_000_000_000L)
    }

    @Test
    fun cooldownKicksUserOutAgain() {
        val decision = fire(
            ReminderEngine.decide(
                context(
                    state = ReminderStateEntity(
                        packageName = PKG,
                        dayKey = DAY,
                        triggerCountToday = 3,
                        cooldownUntil = 1_000_000_000L + 60_000L,
                    ),
                    sessionUsedMs = 5_000L,
                ),
            ),
        )
        assertEquals(TriggerReason.COOLDOWN_KICK, decision.reason)
        assertEquals(ReminderAction.HOME, decision.action)
    }

    @Test
    fun whitelistedPackageIsIgnored() {
        val decision = ReminderEngine.decide(
            context(settings = settings.copy(whitelist = setOf(PKG))),
        )
        assertEquals(ReminderDecision.Idle, decision)
    }

    @Test
    fun doNotDisturbIsSilent() {
        val decision = ReminderEngine.decide(
            context(
                minuteOfDay = 23 * 60 + 30,
                settings = settings.copy(dndEnabled = true, dndStartMinute = 23 * 60, dndEndMinute = 7 * 60),
            ),
        )
        assertEquals(ReminderDecision.Idle, decision)
    }

    @Test
    fun dailyLimitFiresOnceThenRepeats() {
        val first = fire(
            ReminderEngine.decide(
                context(
                    limit = limit(dailyMinutes = 60, remindEvery = null),
                    todayUsedMs = 61 * 60_000L,
                ),
            ),
        )
        assertEquals(TriggerReason.LIMIT_TIME, first.reason)
        assertTrue(first.state.limitNotified)

        val repeat = fire(
            ReminderEngine.decide(
                context(
                    limit = limit(dailyMinutes = 60, remindEvery = null),
                    todayUsedMs = 70 * 60_000L,
                    state = first.state.copy(
                        lastLimitAt = 1_000_000_000L - 11 * 60_000L,
                        lastTriggerAt = 1_000_000_000L - 11 * 60_000L,
                    ),
                ),
            ),
        )
        assertEquals(TriggerReason.LIMIT_REPEAT, repeat.reason)
    }

    @Test
    fun explicitActionModeWins() {
        val decision = fire(
            ReminderEngine.decide(context(limit = limit(mode = ActionMode.LOCK))),
        )
        assertEquals(ReminderAction.HOME_LOCK, decision.action)
    }

    @Test
    fun sessionThresholdAdvancesByInterval() {
        // 已经连续用了 25 分钟，10 分钟一次的话下一个阈值应推进到 20 分钟
        val decision = fire(ReminderEngine.decide(context(sessionUsedMs = 25 * 60_000L)))
        assertEquals(20 * 60_000L, decision.state.thresholdFired)
    }

    @Test
    fun newDayResetsCounter() {
        val decision = fire(
            ReminderEngine.decide(
                context(
                    state = ReminderStateEntity(
                        packageName = PKG,
                        dayKey = "1999-01-01",
                        triggerCountToday = 7,
                        silencedForDay = true,
                    ),
                ),
            ),
        )
        assertEquals(1, decision.state.triggerCountToday)
        assertEquals(DAY, decision.state.dayKey)
    }

    private companion object {
        const val PKG = "com.example.app"
        const val DAY = "2026-10-22"
    }
}
