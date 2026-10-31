package com.daytrace.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一次"打开"在一段自然日内的切片。
 * (packageName, startTs) 唯一，同步时用 REPLACE 幂等重写最近窗口的数据。
 */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["packageName", "startTs"], unique = true),
        Index(value = ["dayKey"]),
        Index(value = ["startTs"]),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val launchable: Boolean,
    val startTs: Long,
    val endTs: Long,
    val dayKey: String,
    val durationMs: Long,
    /** 这一段是否代表打开动作本身（跨天片段为 0） */
    val opens: Int,
)

/** 到点动作 */
object ActionMode {
    /** 按当日触发次数自动升级：1 弹窗 → 2 弹窗+震动 → 3+ 弹回桌面 */
    const val AUTO = "AUTO"
    /** 直接把提醒做成浮层弹窗（需要悬浮窗权限） */
    const val POPUP = "POPUP"
    const val NOTIFY = "NOTIFY"
    const val HOME = "HOME"
    const val LOCK = "LOCK"
    const val KILL = "KILL"
}

/** 提醒与限额配置 */
@Entity(tableName = "limits")
data class LimitEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    /** null = 不限时长 */
    val dailyMinutes: Int? = null,
    /** null = 不限次数 */
    val dailyOpens: Int? = null,
    /** 连续使用每 N 分钟提醒一次，null = 不提醒 */
    val remindEveryMinutes: Int? = 10,
    /** 超过当日上限后每 N 分钟再提醒，null = 只提醒一次 */
    val repeatAfterLimitMinutes: Int? = 10,
    @ColumnInfo(defaultValue = "AUTO")
    val actionMode: String = ActionMode.AUTO,
    val enabled: Boolean = true,
    val lastNotifiedDay: String? = null,
    val lastNotifiedAt: Long = 0L,
)

/** 提醒去重 / 升级状态，进程被杀也不重复打扰 */
@Entity(tableName = "reminder_state")
data class ReminderStateEntity(
    @PrimaryKey val packageName: String,
    val dayKey: String = "",
    /** 当日已触发次数，决定升级梯度 */
    val triggerCountToday: Int = 0,
    val lastTriggerAt: Long = 0L,
    /** 当日限额是否已提醒过 */
    val limitNotified: Boolean = false,
    val lastLimitAt: Long = 0L,
    /** 本会话已提醒到的累计用量（毫秒） */
    val thresholdFired: Long = 0L,
    /** 冷却截止时间：期间再次打开该应用会被立刻弹回 */
    val cooldownUntil: Long = 0L,
    /** "再给我 5 分钟" */
    val snoozeUntil: Long = 0L,
    val snoozeCountToday: Int = 0,
    /** 今天不再提醒 */
    val silencedForDay: Boolean = false,
)
