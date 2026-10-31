package com.daytrace.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionEntity::class,
        LimitEntity::class,
        ReminderStateEntity::class,
        GoalEntity::class,
        GoalCheckinEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun limitDao(): LimitDao
    abstract fun reminderDao(): ReminderStateDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val DB_NAME = "daytrace.db"

        /** v1.0 → v1.1：新增提醒规则字段与提醒状态表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE limits ADD COLUMN remindEveryMinutes INTEGER")
                db.execSQL("ALTER TABLE limits ADD COLUMN repeatAfterLimitMinutes INTEGER")
                db.execSQL("ALTER TABLE limits ADD COLUMN actionMode TEXT NOT NULL DEFAULT 'AUTO'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS reminder_state (" +
                        "packageName TEXT NOT NULL, " +
                        "dayKey TEXT NOT NULL, " +
                        "triggerCountToday INTEGER NOT NULL, " +
                        "lastTriggerAt INTEGER NOT NULL, " +
                        "limitNotified INTEGER NOT NULL, " +
                        "lastLimitAt INTEGER NOT NULL, " +
                        "thresholdFired INTEGER NOT NULL, " +
                        "cooldownUntil INTEGER NOT NULL, " +
                        "snoozeUntil INTEGER NOT NULL, " +
                        "snoozeCountToday INTEGER NOT NULL, " +
                        "silencedForDay INTEGER NOT NULL, " +
                        "PRIMARY KEY(packageName))"
                )
            }
        }

        /** v1.1 → v1.2：新增长期目标与打卡记录 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "targetMinutes INTEGER NOT NULL, " +
                        "targetCount INTEGER NOT NULL, " +
                        "period TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "archived INTEGER NOT NULL, " +
                        "lastNotifiedDay TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS goal_checkins (" +
                        "goalId INTEGER NOT NULL, " +
                        "dayKey TEXT NOT NULL, " +
                        "ts INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "PRIMARY KEY(goalId, dayKey))"
                )
            }
        }

        /** v1.2 → v1.3：目标支持指定日期倒计时 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE goals ADD COLUMN dueDayKey TEXT")
            }
        }
    }
}
