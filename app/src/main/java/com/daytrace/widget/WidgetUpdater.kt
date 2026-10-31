package com.daytrace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.daytrace.R
import com.daytrace.ServiceLocator
import com.daytrace.data.GoalType
import com.daytrace.domain.TimeUtil
import com.daytrace.domain.WidgetContent
import com.daytrace.system.AppIconLoader
import com.daytrace.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 小挂件的刷新入口。
 *
 * 只在"数据变了"的时候被调用（同步完成后、打开 App 时、系统轮询时），
 * 不做定时无脑重建，省电也不闪屏。
 */
object WidgetUpdater {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** 给非 suspend 的地方用（同步完成、Activity 启动） */
    fun refreshAllAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            try {
                refreshAll(app)
            } catch (t: Throwable) {
                // 挂件更新失败不该影响主流程
            }
        }
    }

    suspend fun refreshAll(context: Context) = withContext(Dispatchers.IO) {
        updateToday(context)
        updateCountdown(context)
    }

    // ------------------------------------------------------------------ 今日概览

    suspend fun updateToday(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val container = ServiceLocator.get(context)
        val todayKey = TimeUtil.todayKey()
        val today = container.repository.dayAggregate(todayKey)
        val history = container.repository.history(7)
        val previous = history.filter { it.dayKey != todayKey }
        val avgMs = if (previous.isEmpty()) 0L else previous.sumOf { it.totalMs } / previous.size

        val now = java.util.Calendar.getInstance()
        val data = com.daytrace.domain.TodayWidgetData(
            totalText = TimeUtil.formatCompact(today.totalMs),
            metaText = WidgetContent.metaLine(today.totalOpens, avgMs),
            barFraction = WidgetContent.barFraction(today.totalMs, avgMs),
            updatedText = WidgetContent.updatedLine(
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
            ),
            top = emptyList(),
        )

        val top = today.items.sortedByDescending { it.totalMs }.take(3)

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_total, data.totalText)
            views.setTextViewText(R.id.widget_meta, data.metaText)
            views.setTextViewText(R.id.widget_updated, data.updatedText)
            views.setProgressBar(R.id.widget_bar, 100, (data.barFraction * 100).toInt(), false)
            views.setOnClickPendingIntent(R.id.widget_root, openApp(context))

            val compact = isCompact(manager, id)
            if (compact || top.isEmpty()) {
                views.setViewVisibility(R.id.widget_top_container, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_top_container, View.VISIBLE)
                views.removeAllViews(R.id.widget_top_container)
                for (item in top) {
                    views.addView(R.id.widget_top_container, buildRow(context, item.packageName, item.label, item.totalMs))
                }
            }
            manager.updateAppWidget(id, views)
        }
    }

    private suspend fun buildRow(
        context: Context,
        packageName: String,
        label: String,
        totalMs: Long,
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_today_row)
        row.setTextViewText(R.id.row_label, label)
        row.setTextViewText(R.id.row_time, TimeUtil.formatCompact(totalMs))
        val icon: Bitmap? = try {
            AppIconLoader.load(context, packageName, sizePx = 96)?.asAndroidBitmap()
        } catch (t: Throwable) {
            null
        }
        if (icon != null) {
            row.setImageViewBitmap(R.id.row_icon, icon)
        } else {
            row.setViewVisibility(R.id.row_icon, View.GONE)
        }
        return row
    }

    private fun isCompact(manager: AppWidgetManager, id: Int): Boolean {
        val options = manager.getAppWidgetOptions(id)
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        return minHeight in 1..119
    }

    // ------------------------------------------------------------------ 倒计时

    suspend fun updateCountdown(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val container = ServiceLocator.get(context)
        val goals = container.database.goalDao().active()
            .filter { it.type == GoalType.COUNTDOWN && !it.dueDayKey.isNullOrBlank() }

        val today = LocalDate.now()
        val next = goals
            .mapNotNull { goal ->
                val due = runCatching { LocalDate.parse(goal.dueDayKey) }.getOrNull() ?: return@mapNotNull null
                val days = ChronoUnit.DAYS.between(today, due).toInt()
                goal to days
            }
            .sortedBy { it.second }
            .firstOrNull { it.second >= 0 }
            ?: goals.mapNotNull { goal ->
                val due = runCatching { LocalDate.parse(goal.dueDayKey) }.getOrNull() ?: return@mapNotNull null
                goal to ChronoUnit.DAYS.between(today, due).toInt()
            }.maxByOrNull { it.second }

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_countdown)
            if (next == null) {
                val empty = WidgetContent.emptyCountdown()
                views.setTextViewText(R.id.countdown_title, empty.title)
                views.setTextViewText(R.id.countdown_days, "")
                views.setTextViewText(R.id.countdown_hint, empty.daysText)
                views.setTextViewText(R.id.countdown_due, empty.dueText)
            } else {
                val (goal, days) = next
                views.setTextViewText(R.id.countdown_title, goal.title)
                views.setTextViewText(R.id.countdown_days, WidgetContent.bigNumber(days))
                views.setTextViewText(R.id.countdown_hint, WidgetContent.daysText(days))
                views.setTextViewText(
                    R.id.countdown_due,
                    goal.dueDayKey?.let { formatDue(it) }.orEmpty(),
                )
            }
            views.setOnClickPendingIntent(R.id.countdown_root, openGoals(context))
            manager.updateAppWidget(id, views)
        }
    }

    private fun formatDue(dayKey: String): String {
        val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
        return "${date.monthValue}月${date.dayOfMonth}日"
    }

    // ------------------------------------------------------------------ 点击跳转

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0x7B01,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openGoals(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_GOALS, true)
        }
        return PendingIntent.getActivity(
            context,
            0x7B02,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}