package com.daytrace.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daytrace.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * 标准模式的后台兜底：同步数据 + 检查当前前台应用是否该提醒。
 * WorkManager 最小时隙 15 分钟且受 Doze 影响，
 * 需要分钟级精度请开启"精准模式"（前台服务）。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val container = ServiceLocator.get(applicationContext)
            container.repository.sync()
            container.reminder.checkCurrent()
            // 晚上兜底提醒当天还没完成的目标（每个目标每天最多一次）
            container.goalRepository.checkAndNotify()
            // 数据变了才推一次挂件
            com.daytrace.widget.WidgetUpdater.refreshAll(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}

object WorkScheduler {

    private const val UNIQUE_SYNC = "daytrace.periodic.sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
