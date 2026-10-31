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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.data.GoalDayMark
import com.daytrace.data.GoalEntity
import com.daytrace.data.GoalPeriod
import com.daytrace.data.GoalProgress
import com.daytrace.data.GoalType
import com.daytrace.domain.GoalCalculator
import com.daytrace.domain.TimeUtil
import com.daytrace.ui.components.GradientBar
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.GoalsUiState
import com.daytrace.ui.vm.GoalsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun GoalsScreen() {
    val context = LocalContext.current
    val vm: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(context.applicationContext))
    val state by vm.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

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
        item { GoalsHeader(onAdd = { showCreate = true }) }

        if (state.goals.isNotEmpty()) {
            item { GoalsSummary(state) }
        }

        if (state.goals.isEmpty() && !state.loading) {
            item { EmptyGoals(onAdd = { showCreate = true }) }
        } else {
            items(state.goals, key = { it.goal.id }) { progress ->
                GoalCard(
                    progress = progress,
                    onToggleCheckin = { vm.toggleCheckin(progress.goal.id) },
                    onArchive = { vm.setArchived(progress.goal, true) },
                    onDelete = { vm.remove(progress.goal.id) },
                )
            }
        }

        item {
            Text(
                text = "屏幕时间目标由使用记录自动判定；习惯目标需要手动打卡；" +
                    "倒计时只负责数日子。9:00–22:00 之间，没完成的目标每 3 小时提醒你一次。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    if (showCreate) {
        CreateGoalDialog(
            onDismiss = { showCreate = false },
            onCreateScreen = { title, minutes ->
                vm.createScreenLimit(title, minutes)
                showCreate = false
            },
            onCreateCheckin = { title, period, count ->
                vm.createCheckin(title, period, count)
                showCreate = false
            },
            onCreateCountdown = { title, dueDayKey ->
                vm.createCountdown(title, dueDayKey)
                showCreate = false
            },
        )
    }
}

@Composable
private fun GoalsHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "目标",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "把想坚持的事，交给时间",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("新建")
        }
    }
}

@Composable
private fun GoalsSummary(state: GoalsUiState) {
    SoftCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今天达标 ${state.doneToday} / ${state.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summaryHint(state.doneToday, state.total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientBar(
            progress = if (state.total == 0) 0f else state.doneToday.toFloat() / state.total,
        )
    }
}

private fun summaryHint(done: Int, total: Int): String = when {
    total == 0 -> "先给自己定一个小目标吧"
    done >= total -> "全部达成，今天可以安心休息了"
    done == 0 -> "还没开始，现在正是好时候"
    else -> "还差 ${total - done} 个，慢慢来"
}

@Composable
private fun EmptyGoals(onAdd: () -> Unit) {
    SoftCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "还没有目标",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "可以设一条\"每天屏幕时间不超过 3 小时\"，\n或者给某个重要日子倒数，每天看一眼还剩几天。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) {
                Text("新建第一个目标")
            }
        }
    }
}

@Composable
private fun GoalCard(
    progress: GoalProgress,
    onToggleCheckin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val goal = progress.goal
    var confirmDelete by remember { mutableStateOf(false) }

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        progress.isCountdown -> Icons.Rounded.Event
                        progress.isScreen -> Icons.Rounded.Timelapse
                        else -> Icons.Rounded.Eco
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = goalSubtitle(goal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(progress)
        }

        Spacer(Modifier.height(14.dp))

        if (progress.isCountdown) {
            CountdownBlock(progress)
        } else {
            GradientBar(progress = progress.ratio)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = progressText(progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress.streak > 0) {
                    Text(
                        text = "连续 ${progress.streak} 天达标",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (progress.marks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                DayDots(progress.marks)
            }

            if (!progress.isScreen) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onToggleCheckin,
                    enabled = !progress.todayChecked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (progress.todayChecked) "今天已打卡" else "打卡")
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onArchive) {
                Text("归档", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个目标？") },
            text = { Text("\"${goal.title}\"的打卡记录会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CountdownBlock(progress: GoalProgress) {
    val left = progress.daysLeft ?: 0
    Row(verticalAlignment = Alignment.Bottom) {
        when {
            left > 0 -> {
                Text(
                    text = "还有",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$left",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            left == 0 -> Text(
                text = "就是今天",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )

            else -> Text(
                text = "已过去 ${-left} 天",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    GradientBar(progress = progress.ratio)
}

@Composable
private fun StatusPill(progress: GoalProgress) {
    val label: String
    val bg: Color
    val fg: Color
    when {
        progress.isCountdown && progress.todayDone -> {
            label = "已到期"
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
        }

        progress.isCountdown -> {
            label = "进行中"
            bg = MaterialTheme.colorScheme.secondaryContainer
            fg = MaterialTheme.colorScheme.onSecondaryContainer
        }

        progress.todayDone -> {
            label = "今日达标"
            bg = MaterialTheme.colorScheme.tertiaryContainer
            fg = MaterialTheme.colorScheme.onTertiaryContainer
        }

        else -> {
            label = "待完成"
            bg = MaterialTheme.colorScheme.primaryContainer
            fg = MaterialTheme.colorScheme.onPrimaryContainer
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DayDots(marks: List<GoalDayMark>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        marks.forEach { mark ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fill = when {
                    !mark.started -> Color.Transparent
                    mark.done -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            width = 1.dp,
                            color = if (mark.started) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (mark.done) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = GoalCalculator.weekdayLetter(mark.dayKey),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 新建目标

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGoalDialog(
    onDismiss: () -> Unit,
    onCreateScreen: (String, Int) -> Unit,
    onCreateCheckin: (String, String, Int) -> Unit,
    onCreateCountdown: (String, String) -> Unit,
) {
    var mode by remember { mutableStateOf(GoalType.SCREEN_LIMIT) }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(180) }
    var weekly by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(1) }
    var dueDayKey by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
    var pickerOpen by remember { mutableStateOf(false) }

    val isScreen = mode == GoalType.SCREEN_LIMIT
    val isCheckin = mode == GoalType.CHECKIN

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(
                    text = "新建目标",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isScreen,
                        onClick = { mode = GoalType.SCREEN_LIMIT },
                        label = { Text("屏幕时间") },
                    )
                    FilterChip(
                        selected = isCheckin,
                        onClick = { mode = GoalType.CHECKIN },
                        label = { Text("习惯打卡") },
                    )
                    FilterChip(
                        selected = mode == GoalType.COUNTDOWN,
                        onClick = { mode = GoalType.COUNTDOWN },
                        label = { Text("倒计时") },
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(24) },
                    label = {
                        Text(
                            when {
                                isScreen -> "目标名称"
                                isCheckin -> "习惯名称"
                                else -> "在等什么"
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when {
                                isScreen -> "比如：每天不超过 3 小时"
                                isCheckin -> "比如：睡前不刷手机"
                                else -> "比如：期末考试"
                            },
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isScreen) {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "每天上限",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatMinutes(minutes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = (it / 15f).roundToInt() * 15 },
                        valueRange = 15f..600f,
                        steps = 38,
                    )
                    Text(
                        text = "低于这个时长就算达标，可随时调整。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isCheckin) {
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !weekly,
                            onClick = { weekly = false },
                            label = { Text("每天") },
                        )
                        FilterChip(
                            selected = weekly,
                            onClick = { weekly = true },
                            label = { Text("每周") },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (weekly) "每周打卡次数" else "每天打卡次数",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { count = (count - 1).coerceAtLeast(1) },
                                enabled = count > 1,
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "减少")
                            }
                            Text(
                                text = "$count 次",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            IconButton(
                                onClick = { count = (count + 1).coerceAtMost(if (weekly) 30 else 10) },
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "增加")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "目标日期",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatDayKey(dueDayKey),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = leftDaysText(dueDayKey),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { pickerOpen = true }) { Text("选日期") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 100).forEach { days ->
                            val key = LocalDate.now().plusDays(days.toLong()).toString()
                            FilterChip(
                                selected = dueDayKey == key,
                                onClick = { dueDayKey = key },
                                label = { Text("$days 天后") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            when {
                                isScreen -> onCreateScreen(
                                    title.ifBlank { "屏幕时间上限" },
                                    minutes,
                                )

                                isCheckin -> onCreateCheckin(
                                    title.ifBlank { "新习惯" },
                                    if (weekly) GoalPeriod.WEEK else GoalPeriod.DAY,
                                    count,
                                )

                                else -> onCreateCountdown(
                                    title.ifBlank { "重要日子" },
                                    dueDayKey,
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        val initial = runCatching {
            LocalDate.parse(dueDayKey).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dueDayKey = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        pickerOpen = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ------------------------------------------------------------------ 文案

private fun goalSubtitle(goal: GoalEntity): String = when {
    goal.type == GoalType.SCREEN_LIMIT -> "每天屏幕时间 ≤ ${formatMinutes(goal.targetMinutes)}"
    goal.type == GoalType.COUNTDOWN -> "目标日 ${formatDayKey(goal.dueDayKey ?: "")}"
    goal.period == GoalPeriod.WEEK -> "每周 ${goal.targetCount} 次"
    goal.targetCount > 1 -> "每天 ${goal.targetCount} 次"
    else -> "每天打卡"
}

private fun progressText(progress: GoalProgress): String {
    val goal = progress.goal
    return when {
        goal.type == GoalType.SCREEN_LIMIT ->
            "今天 ${TimeUtil.formatCompact(progress.todayValueMs)} / ${formatMinutes(goal.targetMinutes)}"

        goal.period == GoalPeriod.WEEK ->
            "本周 ${progress.weekCount}/${goal.targetCount} 次"

        progress.todayChecked -> "今天已打卡"
        else -> "今天还没打卡"
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0 分钟"
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}

/** ISO dayKey → "11月25日 周二" */
private fun formatDayKey(dayKey: String): String {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
    val week = GoalCalculator.weekdayLetter(dayKey)
    return "${date.monthValue}月${date.dayOfMonth}日 周$week"
}

/** 目标日相对今天还有多久 */
private fun leftDaysText(dayKey: String): String {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return ""
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)
    return when {
        days > 0 -> "还有 $days 天"
        days == 0L -> "就是今天"
        else -> "已过去 ${-days} 天"
    }
}