package com.daytrace.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE startTs >= :fromTs")
    suspend fun deleteFrom(fromTs: Long)

    @Query("SELECT * FROM sessions WHERE dayKey = :dayKey ORDER BY startTs ASC")
    suspend fun byDay(dayKey: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE dayKey >= :fromDayKey ORDER BY dayKey ASC, startTs ASC")
    suspend fun sinceDay(fromDayKey: String): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE packageName = :pkg AND startTs >= :fromTs AND startTs < :toTs ORDER BY startTs ASC")
    suspend fun byPackage(pkg: String, fromTs: Long, toTs: Long): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY startTs ASC")
    suspend fun all(): List<SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Query("SELECT MIN(startTs) FROM sessions")
    suspend fun earliestTs(): Long?
}

@Dao
interface LimitDao {

    @Query("SELECT * FROM limits ORDER BY label ASC")
    fun observeAll(): Flow<List<LimitEntity>>

    @Query("SELECT * FROM limits ORDER BY label ASC")
    suspend fun all(): List<LimitEntity>

    @Query("SELECT * FROM limits WHERE enabled = 1")
    suspend fun enabled(): List<LimitEntity>

    @Query("SELECT * FROM limits WHERE packageName = :pkg LIMIT 1")
    suspend fun find(pkg: String): LimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: LimitEntity)

    @Query("DELETE FROM limits WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("UPDATE limits SET lastNotifiedDay = :day, lastNotifiedAt = :ts WHERE packageName = :pkg")
    suspend fun markNotified(pkg: String, day: String, ts: Long)

    @Query("UPDATE limits SET lastNotifiedDay = NULL, lastNotifiedAt = 0 WHERE packageName = :pkg")
    suspend fun resetNotified(pkg: String)
}

@Dao
interface ReminderStateDao {

    @Query("SELECT * FROM reminder_state WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): ReminderStateEntity?

    @Query("SELECT * FROM reminder_state")
    suspend fun all(): List<ReminderStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: ReminderStateEntity)

    @Query("DELETE FROM reminder_state WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM reminder_state")
    suspend fun clear()
}
