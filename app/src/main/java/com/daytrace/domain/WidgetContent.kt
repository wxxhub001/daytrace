package com.daytrace.domain

/** 小挂件用的纯数据 + 文案，便于单测（不碰 Android） */
data class TodayWidgetData(
    val totalText: String,
    val metaText: String,
    val barFraction: Float,
    val updatedText: String,
    val top: List<TopAppLine>,
)

data class TopAppLine(
    val packageName: String,
    val label: String,
    val timeText: String,
)

data class CountdownWidgetData(
    val hasGoal: Boolean,
    val title: String,
    val daysText: String,
    val dueText: String,
)

object WidgetContent {

    /** 进度条比例：和近 7 天日均比，没有历史就按 8 小时算满 */
    fun barFraction(todayMs: Long, avgMs: Long): Float {
        if (todayMs <= 0L) return 0f
        val base = if (avgMs > 0L) avgMs else 8 * 3_600_000L
        return (todayMs.toFloat() / base.toFloat()).coerceIn(0f, 1f)
    }

    fun metaLine(opens: Int, avgMs: Long): String {
        val avg = if (avgMs > 0L) "日均 ${TimeUtil.formatCompact(avgMs)}" else "还没有历史数据"
        return "打开 $opens 次 · $avg"
    }

    fun updatedLine(hour: Int, minute: Int): String =
        "截至 %02d:%02d".format(hour, minute)

    fun daysText(daysLeft: Int): String = when {
        daysLeft > 0 -> "还有 $daysLeft 天"
        daysLeft == 0 -> "就是今天"
        else -> "已过去 ${-daysLeft} 天"
    }

    fun bigNumber(daysLeft: Int): String = if (daysLeft > 0) "$daysLeft" else ""

    fun emptyCountdown(): CountdownWidgetData = CountdownWidgetData(
        hasGoal = false,
        title = "还没有倒计时目标",
        daysText = "点我添加",
        dueText = "在「目标」里新建一个",
    )
}