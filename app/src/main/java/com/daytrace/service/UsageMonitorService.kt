package com.daytrace.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daytrace.R
import com.daytrace.ServiceLocator
import com.daytrace.domain.ForegroundTracker
import com.daytrace.system.Notifier
import com.daytrace.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 精准模式：低频（默认 30 秒）采样前台应用并计时，
 * 到点后可以真正做到"用了 10 分钟就提醒 / 就退出"。
 */
class UsageMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var lastPollTs = 0L
    private var lastIntervalSeconds = 0

    override fun onCreate() {
        super.onCreate()
        running = true
        val notifier = ServiceLocator.get(this).notifier
        notifier.ensureChannels()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        lastPollTs = System.currentTimeMillis() - 10_000L
        loopJob = scope.launch { loop() }
    }

    private suspend fun loop() {
        val container = ServiceLocator.get(this)
        val tracker = ForegroundTracker()
        while (scope.isActive) {
            try {
                val settings = container.settings.current()
                if (settings.sampleIntervalSeconds != lastIntervalSeconds) {
                    lastIntervalSeconds = settings.sampleIntervalSeconds
                }
                val now = System.currentTimeMillis()
                val events = container.eventSource.query(lastPollTs, now)
                tracker.consume(events)
                lastPollTs = now

                val snapshot = tracker.snapshot(now)
                if (snapshot != null) {
                    container.reminder.evaluate(snapshot.packageName, snapshot.usedMs)
                }
            } catch (t: Throwable) {
                // 单次采样失败不影响循环
            }
            delay(((lastIntervalSeconds.takeIf { it > 0 } ?: 30) * 1000L).coerceIn(10_000L, 120_000L))
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifier.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_daytrace)
            .setContentTitle("精准提醒运行中")
            .setContentText("到点会自动提醒你，点开可以关掉")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        lastIntervalSeconds = 0
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        loopJob?.cancel()
        scope.launch { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.daytrace.action.STOP_MONITOR"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, UsageMonitorService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                // 后台限制下启动失败时忽略
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, UsageMonitorService::class.java))
            } catch (t: Throwable) {
                // 忽略
            }
        }
    }
}
