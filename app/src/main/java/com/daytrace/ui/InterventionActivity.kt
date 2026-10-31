package com.daytrace.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daytrace.ServiceLocator
import com.daytrace.domain.ReminderEngine
import com.daytrace.domain.TimeUtil
import com.daytrace.service.AppAccessibilityService
import com.daytrace.ui.theme.DayTraceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 到点拦截页：全屏、倒计时、可"再给我 5 分钟" */
class InterventionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val sessionMs = intent.getLongExtra(EXTRA_SESSION_MS, 0L)
        val todayMs = intent.getLongExtra(EXTRA_TODAY_MS, 0L)

        setContent {
            DayTraceTheme {
                InterventionScreen(
                    packageName = pkg,
                    label = label,
                    sessionMs = sessionMs,
                    todayMs = todayMs,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_SESSION_MS = "extra_session_ms"
        const val EXTRA_TODAY_MS = "extra_today_ms"
    }
}

@Composable
private fun InterventionScreen(
    packageName: String,
    label: String,
    sessionMs: Long,
    todayMs: Long,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember { ServiceLocator.get(context.applicationContext) }
    var remain by remember { mutableIntStateOf(60) }
    var snoozeLeft by remember { mutableStateOf(ReminderEngine.MAX_SNOOZE_PER_DAY) }

    LaunchedEffect(packageName) {
        snoozeLeft = container.reminder.snoozeLeftToday(packageName)
    }

    LaunchedEffect(Unit) {
        while (remain > 0) {
            delay(1_000L)
            remain -= 1
        }
        AppAccessibilityService.instance?.goHome()
        onClose()
    }

    BackHandler { /* 拦截返回键 */ }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (sessionMs >= 60_000L) "已经用了 ${sessionMs / 60_000L} 分钟" else "先歇一下吧",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "让眼睛和大脑都休息一会儿",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "今天在$label 已累计 ${TimeUtil.formatCompact(todayMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            LinearProgressIndicator(
                progress = { remain / 60f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$remain 秒后自动回到桌面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        AppAccessibilityService.instance?.goHome()
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("回桌面") }

                OutlinedButton(
                    onClick = {
                        val pkg = packageName
                        scope.launch {
                            val ok = container.reminder.snooze(pkg)
                            if (ok) {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                    if (intent != null) {
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                } catch (t: Throwable) {
                                    // 忽略
                                }
                            }
                            onClose()
                        }
                    },
                    enabled = snoozeLeft > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (snoozeLeft > 0) "再来 5 分钟" else "今日已用完")
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.height(2.dp))
        }
    }
}
