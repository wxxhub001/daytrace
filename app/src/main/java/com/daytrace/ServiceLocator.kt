package com.daytrace

import android.content.Context
import androidx.room.Room
import com.daytrace.data.AppDatabase
import com.daytrace.data.SettingsStore
import com.daytrace.data.UsageRepository
import com.daytrace.domain.InterventionController
import com.daytrace.domain.ReminderCoordinator
import com.daytrace.domain.UsageEventSource
import com.daytrace.system.Notifier

/** 极简手动依赖注入容器（不引入 Hilt，减少构建复杂度） */
object ServiceLocator {

    @Volatile
    private var instance: Container? = null

    fun get(context: Context): Container =
        instance ?: synchronized(this) {
            instance ?: Container(context.applicationContext).also { instance = it }
        }

    class Container(app: Context) {
        val database: AppDatabase = Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            AppDatabase.DB_NAME,
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
            )
            .build()

        val settings = SettingsStore(app)
        val eventSource = UsageEventSource(app)
        val notifier = Notifier(app)
        val goalNotifier = com.daytrace.system.GoalNotifier(app)
        val repository = UsageRepository(app, database, eventSource, settings)
        val goalRepository = com.daytrace.data.GoalRepository(database, repository, goalNotifier)
        val intervention = InterventionController(app)
        val reminder = ReminderCoordinator(
            db = database,
            repository = repository,
            settings = settings,
            eventSource = eventSource,
            notifier = notifier,
            intervention = intervention,
        )
    }
}
