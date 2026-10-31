package com.daytrace.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daytrace.R
import com.daytrace.data.LimitEntity
import com.daytrace.domain.ReminderAction
import com.daytrace.domain.ReminderEngine
import com.daytrace.domain.TimeUtil
import com.daytrace.domain.TriggerReason
import com.daytrace.receiver.ReminderActionReceiver
import com.daytrace.ui.MainActivity

class Notifier(private val context: Context) {

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                "使用提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "连续使用或超出限额时提醒你"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                "精准模式运行状态",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "开启精准提醒后常驻，用于按分钟级判断使用时长"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIMIT,
                "使用限额提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "应用使用时长或打开次数超过设定上限时提醒你"
            },
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyReminder(
        limit: LimitEntity,
        reason: TriggerReason,
        action: ReminderAction,
        sessionUsedMs: Long,
        todayUsedMs: Long,
        todayOpens: Int,
    ) {
        if (!canNotify()) return

        val headline = when (reason) {
            TriggerReason.SESSION_INTERVAL -> "已连续使用 ${TimeUtil.formatCompact(sessionUsedMs)}"
            TriggerReason.LIMIT_TIME -> "超出今日时长上限"
            TriggerReason.LIMIT_OPENS -> "超出今日次数上限"
            TriggerReason.LIMIT_REPEAT -> "今天用量偏高"
            TriggerReason.COOLDOWN_KICK -> "先休息一下吧"
        }
        val title = "${limit.label} · $headline"
        val body = when (reason) {
            TriggerReason.LIMIT_OPENS -> "今天已打开 $todayOpens 次，上限 ${limit.dailyOpens} 次。"
            else -> "今天累计 ${TimeUtil.formatCompact(todayUsedMs)}。休息一下，眼睛也需要喘口气。"
        }

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_PACKAGE, limit.packageName)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            limit.packageName.hashCode(),
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_daytrace)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)

        if (action >= ReminderAction.HOME) {
            builder.setSubText("已为你退出该应用")
        }

        builder.addAction(
            0,
            "今天不再提醒",
            broadcast(limit.packageName, ReminderActionReceiver.ACTION_SILENCE),
        )
        builder.addAction(
            0,
            "知道了",
            contentIntent,
        )

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(limit.packageName), builder.build())
        } catch (t: Throwable) {
            // 通知权限被收回时静默失败
        }
    }

    /** 提醒自检：立刻发一条示例提醒，用来确认整个通知链路是通的 */
    fun notifyTest() {
        if (!canNotify()) return
        val body = "能看到这条，说明提醒链路是通的。接下来把某个 App 的限额设成 1 分钟，再用它试试。"
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0x7A02,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_daytrace)
            .setContentTitle("刷够了没 · 测试提醒")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
        try {
            NotificationManagerCompat.from(context).notify(0x7A01, builder.build())
        } catch (t: Throwable) {
            // 忽略
        }
    }

    fun vibrate(ms: Long) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (t: Throwable) {
            // 忽略
        }
    }

    fun cancel(pkg: String) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId(pkg))
        } catch (t: Throwable) {
            // 忽略
        }
    }

    private fun broadcast(pkg: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderActionReceiver.EXTRA_PACKAGE, pkg)
        }
        return PendingIntent.getBroadcast(
            context,
            (pkg + action).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notificationId(pkg: String): Int = pkg.hashCode() and 0x0FFFFFFF

    companion object {
        const val CHANNEL_REMINDER = "reminder_alerts"
        const val CHANNEL_MONITOR = "monitor_status"
        const val CHANNEL_LIMIT = "limit_alerts"
    }
}
