package com.daytrace.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daytrace.R
import com.daytrace.ui.MainActivity

/** 目标未完成的温和提醒（每天最多一次） */
class GoalNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GOAL,
                "长期目标提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "晚上提醒你还有目标没有完成"
                setShowBadge(true)
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

    /**
     * 把当天没完成的目标合并成一条通知，避免多个目标连发。
     * [items] 为 (目标标题, 状态描述)。
     */
    fun notifyUnfinished(items: List<Pair<String, String>>) {
        if (items.isEmpty() || !canNotify()) return

        val title = if (items.size == 1) {
            "「${items.first().first}」今天还没完成"
        } else {
            "还有 ${items.size} 个目标没完成"
        }
        val body = items.joinToString("\n") { (name, state) -> "· $name：$state" }

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_GOALS, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_GOAL)
            .setSmallIcon(R.drawable.ic_stat_daytrace)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "去打卡", contentIntent)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (t: Throwable) {
            // 通知权限被收回时静默失败
        }
    }

    companion object {
        const val CHANNEL_GOAL = "daytrace.goal.reminder"
        private const val NOTIFICATION_ID = 0x93A1
        private const val REQUEST_CODE = 0x93A2
    }
}