package com.daytrace.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 目标类型 */
object GoalType {
    /** 每日屏幕时间上限：由使用记录自动判定，不需要手动打卡 */
    const val SCREEN_LIMIT = "SCREEN_LIMIT"

    /** 习惯目标：手动打卡 */
    const val CHECKIN = "CHECKIN"

    /** 指定日期倒计时：考试、纪念日、约定日，不判定每日完成 */
    const val COUNTDOWN = "COUNTDOWN"
}

object GoalPeriod {
    const val DAY = "DAY"
    const val WEEK = "WEEK"
}

/**
 * 一个长期目标。
 *
 * - [GoalType.SCREEN_LIMIT]：`targetMinutes` 为每日上限，达标由 [com.daytrace.domain.TimeUtil]
 *   汇总出的当日总时长自动判定；
 * - [GoalType.CHECKIN]：每 [period] 周期内打卡满 [targetCount] 次算达标。
 */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: String = GoalType.CHECKIN,
    /** SCREEN_LIMIT 用：每日上限（分钟） */
    val targetMinutes: Int = 0,
    /** CHECKIN 用：每个周期需要打卡的次数 */
    val targetCount: Int = 1,
    /** 周期：DAY / WEEK */
    val period: String = GoalPeriod.DAY,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val archived: Boolean = false,
    /** 当天"未完成"提醒去重用，避免一天内反复打扰 */
    val lastNotifiedDay: String? = null,
    /** COUNTDOWN 用：目标日期（ISO dayKey），其他类型为 null */
    val dueDayKey: String? = null,
)

/** 一次手动打卡。(goalId, dayKey) 唯一，同一天重复打卡只会覆盖 */
@Entity(tableName = "goal_checkins", primaryKeys = ["goalId", "dayKey"])
data class GoalCheckinEntity(
    val goalId: Long,
    val dayKey: String,
    val ts: Long,
    val note: String = "",
)

@Dao
interface GoalDao {

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY createdAt DESC")
    suspend fun active(): List<GoalEntity>

    @Query("SELECT * FROM goals ORDER BY archived ASC, createdAt DESC")
    suspend fun all(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun find(id: Long): GoalEntity?

    @Insert
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM goal_checkins WHERE dayKey >= :fromDayKey")
    suspend fun checkinsSince(fromDayKey: String): List<GoalCheckinEntity>

    @Query("SELECT * FROM goal_checkins WHERE goalId = :goalId")
    suspend fun checkinsOf(goalId: Long): List<GoalCheckinEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCheckin(row: GoalCheckinEntity)

    @Query("DELETE FROM goal_checkins WHERE goalId = :goalId AND dayKey = :dayKey")
    suspend fun deleteCheckin(goalId: Long, dayKey: String)

    @Query("DELETE FROM goal_checkins WHERE goalId = :goalId")
    suspend fun clearCheckins(goalId: Long)
}