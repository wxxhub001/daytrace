package com.daytrace.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daytrace.ServiceLocator
import com.daytrace.data.PopupStyle
import com.daytrace.domain.TimeUtil
import com.daytrace.service.AppAccessibilityService
import com.daytrace.ui.components.PopupCard
import com.daytrace.ui.theme.DayTraceTheme
import kotlinx.coroutines.launch

/**
 * 浮层弹窗提醒：到点时在别的应用上直接盖一张卡片，
 * 而不是缩进通知栏。样式来自设置（全局样式或这条规则单独定制的）。
 *
 * 需要悬浮窗权限或无障碍服务；都没有时由上层退回通知。
 */
class PopupReminderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        if (pkg.isBlank()) {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { pkg }
        val todayMs = intent.getLongExtra(EXTRA_TODAY_MS, 0L)
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, -1)

        setContent {
            DayTraceTheme {
                val context = LocalContext.current
                val container = remember { ServiceLocator.get(context) }
                val scope = rememberCoroutineScope()
                var style by remember { mutableStateOf<PopupStyle?>(null) }
                var snoozeLeft by remember { mutableIntStateOf(0) }

                LaunchedEffect(pkg) {
                    style = container.settings.current().styleFor(pkg)
                    snoozeLeft = container.reminder.snoozeLeftToday(pkg)
                }

                val exitApp: () -> Unit = {
                    val service = AppAccessibilityService.instance
                    if (service != null) {
                        service.goHome()
                    } else {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            },
                        )
                    }
                    finish()
                }

                val snooze: () -> Unit = {
                    scope.launch {
                        val ok = container.reminder.snooze(pkg)
                        if (!ok) {
                            Toast.makeText(context, "今天的延后次数用完了", Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                }

                val silence: () -> Unit = {
                    scope.launch {
                        container.reminder.silenceForDay(pkg)
                        finish()
                    }
                }

                val current = style
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (current == null) Color.Transparent else Color(0x99000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() },
                        )
                        .systemBarsPadding()
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (current != null) {
                        Box(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        ) {
                            PopupCard(
                                style = current,
                                appLabel = label,
                                packageName = pkg,
                                usedText = TimeUtil.formatCompact(todayMs),
                                limitText = if (limitMinutes > 0) {
                                    "上限 ${minutesText(limitMinutes)}"
                                } else {
                                    null
                                },
                                progress = if (limitMinutes > 0) {
                                    todayMs / (limitMinutes * 60_000f)
                                } else {
                                    1f
                                },
                                snoozeLeft = snoozeLeft,
                                onExit = exitApp,
                                onSnooze = snooze,
                                onSilence = silence,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 同一条规则再次触发时刷新内容，避免叠一摞弹窗
        setIntent(intent)
        recreate()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_popup_package"
        const val EXTRA_LABEL = "extra_popup_label"
        const val EXTRA_SESSION_MS = "extra_popup_session_ms"
        const val EXTRA_TODAY_MS = "extra_popup_today_ms"
        const val EXTRA_LIMIT_MINUTES = "extra_popup_limit_minutes"
    }
}

private fun minutesText(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}