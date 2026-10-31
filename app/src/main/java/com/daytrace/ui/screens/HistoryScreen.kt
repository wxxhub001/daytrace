package com.daytrace.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.domain.DaySummary
import com.daytrace.domain.TimeUtil
import com.daytrace.ui.components.EmptyHint
import com.daytrace.ui.components.GradientBar
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.HistoryViewModel

@Composable
fun HistoryScreen(onOpenDay: (String) -> Unit) {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(context.applicationContext))
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Dim.screen,
            end = Dim.screen,
            top = 22.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dim.gap),
    ) {
        item {
            Column {
                Text(
                    text = "长期记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "近 30 天",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "历史保存在本地数据库，系统清理事件也不会丢",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.days.isEmpty()) {
            item { EmptyHint(text = if (state.loading) "加载中…" else "还没有历史数据") }
        } else {
            items(state.days, key = { it.dayKey }) { day ->
                DayRow(day = day, maxMs = state.maxMs, onClick = { onOpenDay(day.dayKey) })
            }
        }
    }
}

@Composable
private fun DayRow(day: DaySummary, maxMs: Long, onClick: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (TimeUtil.isToday(day.dayKey)) "今天" else TimeUtil.formatDayLabel(day.dayKey),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                GradientBar(
                    progress = if (maxMs <= 0L) 0f else day.totalMs.toFloat() / maxMs.toFloat(),
                    height = 6.dp,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = TimeUtil.formatCompact(day.totalMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${day.openCount} 次 · ${day.appCount} 个应用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
