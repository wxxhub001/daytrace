package com.daytrace.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.data.ActionMode
import com.daytrace.data.LimitEntity
import com.daytrace.domain.AppUsageItem
import com.daytrace.domain.TimeUtil
import com.daytrace.service.AppAccessibilityService
import com.daytrace.service.ShizukuBridge
import com.daytrace.ui.components.AppIcon
import com.daytrace.ui.components.EmptyHint
import com.daytrace.ui.components.GradientBar
import com.daytrace.ui.components.SectionTitle
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.ActionModeOptions
import com.daytrace.ui.vm.LimitsUiState
import com.daytrace.ui.vm.LimitsViewModel

@Composable
fun LimitsScreen(onEditPopupStyle: (String) -> Unit = {}) {
    val context = LocalContext.current
    val vm: LimitsViewModel = viewModel(factory = LimitsViewModel.factory(context.applicationContext))
    val state by vm.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LimitEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickerOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("添加") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dim.screen,
                end = Dim.screen,
                top = 22.dp,
                bottom = 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dim.gap),
        ) {
            item {
                Column {
                    Text(
                        text = "提醒规则",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "限额与提醒",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "默认每连续使用 10 分钟提醒一次，超限后每 10 分钟催办；" +
                            "动作可设为只提醒或直接把你弹回桌面。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                ModeCard(
                    state = state,
                    onPreciseChange = { vm.enablePreciseMode(it) },
                    onOpenAccessibility = { AppAccessibilityService.openSettings(context) },
                    onOpenOverlay = {
                        com.daytrace.system.PermissionHelper.openOverlaySettings(context)
                    },
                    onRequestShizuku = { ShizukuBridge.requestPermission(REQ_SHIZUKU) },
                    onTestReminder = { vm.sendTestReminder() },
                )
            }

            if (state.limits.isEmpty()) {
                item { EmptyHint(text = if (state.loading) "加载中…" else "还没有提醒规则，点右下角添加") }
            } else {
                items(state.limits, key = { it.packageName }) { limit ->
                    LimitRow(
                        limit = limit,
                        usage = state.usage[limit.packageName],
                        onClick = { editing = limit },
                        onToggle = { vm.toggleEnabled(limit) },
                    )
                }
            }

            item {
                Text(
                    text = "*「关闭应用」需要安装 Shizuku 并授权；未授权时会自动降级为弹回桌面。" +
                        "「弹回桌面」需要开启无障碍服务；「拦截页浮层」需要悬浮窗权限，未开时仍会把你弹回桌面。" +
                        "所有动作只作用于你设了限额的那一个应用，不会影响其他应用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (pickerOpen) {
        AppPickerDialog(
            candidates = state.candidates,
            onDismiss = { pickerOpen = false },
            onPick = { item ->
                pickerOpen = false
                editing = LimitEntity(packageName = item.packageName, label = item.label)
            },
        )
    }

    val target = editing
    if (target != null) {
        LimitEditorDialog(
            limit = target,
            usage = state.usage[target.packageName],
            onDismiss = { editing = null },
            onEditPopupStyle = {
                editing = null
                onEditPopupStyle(target.packageName)
            },
            onDelete = {
                vm.remove(target.packageName)
                editing = null
            },
            onSave = { minutes, opens, remind, repeat, mode, enabled ->
                vm.save(
                    packageName = target.packageName,
                    label = target.label,
                    minutes = minutes,
                    opens = opens,
                    remindEveryMinutes = remind,
                    repeatAfterLimitMinutes = repeat,
                    actionMode = mode,
                    enabled = enabled,
                )
                editing = null
            },
        )
    }
}

private const val REQ_SHIZUKU = 42

@Composable
private fun ModeCard(
    state: LimitsUiState,
    onPreciseChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestShizuku: () -> Unit,
    onTestReminder: () -> Unit,
) {
    SoftCard {
        SectionTitle(text = "运行方式")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("精准模式", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "前台服务每 15 秒采样一次，可精确到分钟；关闭后用系统兜底，误差可达 15 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = state.preciseMode, onCheckedChange = onPreciseChange)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))

        StatusLine(
            title = "弹回桌面",
            subtitle = "需要无障碍服务",
            ok = state.accessibilityEnabled,
            actionLabel = if (state.accessibilityEnabled) "已开启" else "去开启",
            onAction = onOpenAccessibility,
        )

        Spacer(Modifier.height(10.dp))

        StatusLine(
            title = "弹窗 / 拦截页浮层",
            subtitle = "需要悬浮窗权限，否则到点弹不出来",
            ok = state.overlayEnabled,
            actionLabel = if (state.overlayEnabled) "已开启" else "去开启",
            onAction = onOpenOverlay,
        )

        Spacer(Modifier.height(10.dp))

        StatusLine(
            title = "关闭应用",
            subtitle = "需要 Shizuku（adb 授权）",
            ok = state.shizukuReady,
            actionLabel = if (state.shizukuReady) "已授权" else "授权",
            onAction = onRequestShizuku,
        )

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onTestReminder, modifier = Modifier.align(Alignment.Start)) {
            Text("测试一下弹窗")
        }
    }
}

@Composable
private fun StatusLine(
    title: String,
    subtitle: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ok) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun LimitRow(
    limit: LimitEntity,
    usage: AppUsageItem?,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val usedMs = usage?.totalMs ?: 0L
    val usedOpens = usage?.openCount ?: 0

    val timeRatio = limit.dailyMinutes?.takeIf { it > 0 }?.let {
        usedMs.toFloat() / (it * 60_000L).toFloat()
    }
    val opensRatio = limit.dailyOpens?.takeIf { it > 0 }?.let {
        usedOpens.toFloat() / it.toFloat()
    }
    val ratio = (timeRatio ?: opensRatio ?: 0f).coerceIn(0f, 1f)
    val exceeded = (timeRatio ?: 0f) >= 1f || (opensRatio ?: 0f) >= 1f

    SoftCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = limit.packageName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = limit.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (exceeded) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "已超限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!limit.enabled) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "已停用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                GradientBar(progress = ratio, height = 6.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ruleSummary(limit, usedMs, usedOpens),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = limit.enabled, onCheckedChange = { onToggle() })
        }
    }
}

private fun ruleSummary(limit: LimitEntity, usedMs: Long, usedOpens: Int): String = buildString {
    val parts = mutableListOf<String>()
    limit.dailyMinutes?.let { parts += "今日 ${TimeUtil.formatCompact(usedMs)} / $it 分" }
    limit.dailyOpens?.let { parts += "$usedOpens / $it 次" }
    append(parts.joinToString("　"))
    if (parts.isNotEmpty()) append(" · ")
    append(
        when (val every = limit.remindEveryMinutes) {
            null -> "不周期提醒"
            else -> "每 $every 分钟提醒"
        },
    )
    val repeat = limit.repeatAfterLimitMinutes
    if (repeat != null) append(" · 超限每 $repeat 分钟催办")
    append(" · " + (ActionModeOptions.firstOrNull { it.first == limit.actionMode }?.second ?: "自动升级"))
}

@Composable
private fun AppPickerDialog(
    candidates: List<AppUsageItem>,
    onDismiss: () -> Unit,
    onPick: (AppUsageItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, candidates) {
        if (query.isBlank()) candidates
        else candidates.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索今天用过的应用") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        text = "今天还没有可添加的应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = { it.packageName }) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(packageName = item.packageName, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.openCount} 次 · ${TimeUtil.formatCompact(item.totalMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun LimitEditorDialog(
    limit: LimitEntity,
    usage: AppUsageItem?,
    onDismiss: () -> Unit,
    onEditPopupStyle: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Int?, Int?, Int?, Int?, String, Boolean) -> Unit,
) {
    var minutesText by remember { mutableStateOf(limit.dailyMinutes?.toString() ?: "") }
    var opensText by remember { mutableStateOf(limit.dailyOpens?.toString() ?: "") }
    var remind by remember { mutableStateOf(limit.remindEveryMinutes) }
    var repeat by remember { mutableStateOf(limit.repeatAfterLimitMinutes) }
    var mode by remember { mutableStateOf(limit.actionMode) }
    var enabled by remember { mutableStateOf(limit.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = limit.packageName, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(limit.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        minutesText.toIntOrNull(),
                        opensText.toIntOrNull(),
                        remind,
                        repeat,
                        mode,
                        enabled,
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (usage != null) {
                    Text(
                        text = "今天已用 ${TimeUtil.formatCompact(usage.totalMs)} · ${usage.openCount} 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("每日时长上限（分钟，留空不限）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = opensText,
                    onValueChange = { opensText = it.filter { c -> c.isDigit() } },
                    label = { Text("每日打开次数上限（留空不限）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("连续使用每 N 分钟提醒", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf(
                        null to "关闭",
                        5 to "5",
                        10 to "10",
                        15 to "15",
                        20 to "20",
                        30 to "30",
                        60 to "60",
                    ),
                    selected = remind,
                    onSelect = { remind = it },
                )

                Spacer(Modifier.height(14.dp))
                Text("超限后每 N 分钟催办", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = listOf(
                        null to "只提醒一次",
                        5 to "5",
                        10 to "10",
                        15 to "15",
                        30 to "30",
                    ),
                    selected = repeat,
                    onSelect = { repeat = it },
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "弹窗样式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEditPopupStyle) { Text("单独定制") }
                }

                Spacer(Modifier.height(12.dp))
                Text("到点动作", style = MaterialTheme.typography.bodyMedium)
                ActionModeOptions.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = entry.first }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == entry.first, onClick = { mode = entry.first })
                        Text(entry.second, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (mode != ActionMode.NOTIFY) {
                    Text(
                        text = "提示：弹窗和踢出都需要开启精准模式才准时生效；" +
                            "弹窗还要悬浮窗权限，踢出还要无障碍服务。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onDelete) {
                    Text("删除这条规则", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun ChipRow(
    options: List<Pair<Int?, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { entry ->
            FilterChip(
                selected = selected == entry.first,
                onClick = { onSelect(entry.first) },
                label = { Text(entry.second) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
