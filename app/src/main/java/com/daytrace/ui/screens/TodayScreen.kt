package com.daytrace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.domain.TimeUtil
import com.daytrace.system.PermissionHelper
import com.daytrace.ui.components.AppUsageRow
import com.daytrace.ui.components.EmptyHint
import com.daytrace.ui.components.HourCapsuleChart
import com.daytrace.ui.components.PermissionCard
import com.daytrace.ui.components.SectionTitle
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.components.StatPill
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.TodayUiState
import com.daytrace.ui.vm.TodayViewModel
import java.time.LocalTime

@Composable
fun TodayScreen(onOpenDetail: (String) -> Unit) {
    val context = LocalContext.current
    val vm: TodayViewModel = viewModel(factory = TodayViewModel.factory(context.applicationContext))
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
        item { TodayHeader(state.dayKey) }

        if (!state.hasPermission) {
            item { PermissionCard(onGrant = { PermissionHelper.openUsageAccessSettings(context) }) }
            item {
                Text(
                    text = "授权后回到这里会自动开始统计，历史数据可从系统已有记录里回溯导入 7 天。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item { HeroCard(state) }
            item {
                SoftCard {
                    SectionTitle(text = "24 小时分布")
                    Spacer(Modifier.height(14.dp))
                    HourCapsuleChart(values = state.hourly)
                }
            }
            item { ListHeader(state, onSortByTime = { vm.setSortByTime(true) }, onSortByCount = { vm.setSortByTime(false) }) }

            if (state.items.isEmpty()) {
                item { EmptyHint(text = if (state.loading) "正在统计…" else "今天还没有使用记录") }
            } else {
                items(state.items, key = { it.packageName }) { item ->
                    AppUsageRow(
                        item = item,
                        maxMs = state.items.firstOrNull()?.totalMs ?: 0L,
                        onClick = { onOpenDetail(item.packageName) },
                    )
                }
            }

            item {
                Text(
                    text = "数据来自系统使用情况统计，仅保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TodayHeader(dayKey: String) {
    Column {
        Text(
            text = greeting() ,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = TimeUtil.formatDayLabel(dayKey.ifEmpty { TimeUtil.todayKey() }),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 6 -> "夜深了"
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}

@Composable
private fun HeroCard(state: TodayUiState) {
    val shape = RoundedCornerShape(Dim.cardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(Dim.cardPadding),
    ) {
        Text(
            text = "今天已经使用",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = TimeUtil.formatDuration(state.totalMs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        DeltaLine(state)
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatPill(
                value = state.totalOpens.toString(),
                label = "次打开",
                modifier = Modifier.weight(1f),
            )
            StatPill(
                value = state.appCount.toString(),
                label = "个应用",
                modifier = Modifier.weight(1f),
            )
            Column(modifier = Modifier.weight(1.4f)) {
                Text(
                    text = "用得最多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.items.firstOrNull()?.label ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeltaLine(state: TodayUiState) {
    val yesterday = state.yesterdayMs
    if (yesterday < 0L) {
        Text(
            text = "开始记录后，明天就能看到对比",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val diff = state.totalMs - yesterday
    val icon = when {
        diff > 60_000L -> Icons.Rounded.TrendingUp
        diff < -60_000L -> Icons.Rounded.TrendingDown
        else -> Icons.Rounded.Remove
    }
    val text = when {
        diff > 60_000L -> "比昨天多 ${TimeUtil.formatCompact(diff)}"
        diff < -60_000L -> "比昨天少 ${TimeUtil.formatCompact(-diff)}"
        else -> "和昨天差不多"
    }
    val tint = when {
        diff > 60_000L -> MaterialTheme.colorScheme.error
        diff < -60_000L -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.height(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun ListHeader(
    state: TodayUiState,
    onSortByTime: () -> Unit,
    onSortByCount: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "应用明细",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        FilterChip(
            selected = state.sortByTime,
            onClick = onSortByTime,
            label = { Text("按时长") },
            shape = RoundedCornerShape(50),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = !state.sortByTime,
            onClick = onSortByCount,
            label = { Text("按次数") },
            shape = RoundedCornerShape(50),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}
