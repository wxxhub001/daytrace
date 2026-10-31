package com.daytrace.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.data.PopupBgMode
import com.daytrace.data.PopupPresets
import com.daytrace.data.PopupStyle
import com.daytrace.data.PopupTextMode
import com.daytrace.domain.PopupFormat
import com.daytrace.system.PopupBackground
import com.daytrace.ui.components.PopupCard
import com.daytrace.ui.components.PopupPreviewSample
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.PopupStyleViewModel
import kotlinx.coroutines.launch

/**
 * 弹窗样式编辑。packageName 为 null 时编辑全局样式，
 * 否则编辑"这条规则"单独定制的样式。
 *
 * 上方是实时预览，用的是和真实弹窗完全相同的 PopupCard。
 */
@Composable
fun PopupStyleScreen(
    packageName: String? = null,
    appLabel: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: PopupStyleViewModel = viewModel(
        factory = PopupStyleViewModel.factory(context.applicationContext, packageName),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var hexText by remember { mutableStateOf(styleHex(state.style.bgColor)) }
    val style = state.style

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = PopupBackground.saveFromUri(context, uri)
                if (path != null) {
                    vm.attachImage(path)
                } else {
                    Toast.makeText(context, "这张图读不出来，换一张试试", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Dim.screen, end = Dim.screen, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(Dim.gap),
    ) {
        // 顶部
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("返回")
            }
        }
        Column {
            Text(
                text = "弹窗样式",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (state.perApp) {
                    val name = appLabel?.takeIf { it.isNotBlank() } ?: packageName.orEmpty()
                    if (state.hasOverride) "$name · 已单独定制" else "$name · 当前跟随全局"
                } else {
                    "全局样式，所有规则默认用它"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        // 实时预览
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
        ) {
            PopupCard(
                style = style,
                appLabel = PopupPreviewSample.LABEL,
                packageName = PopupPreviewSample.PACKAGE,
                usedText = PopupPreviewSample.USED,
                limitText = PopupPreviewSample.LIMIT,
                progress = PopupPreviewSample.PROGRESS,
                snoozeLeft = PopupPreviewSample.SNOOZE_LEFT,
                onExit = {},
                onSnooze = {},
                onSilence = {},
            )
        }

        // 预设
        SoftCard {
            SectionLabel("预设主题")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PopupPresets.ALL.forEach { preset ->
                    val selected = preset.id == style.presetId
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                vm.update { PopupPresets.apply(preset, it) }
                                hexText = styleHex(preset.style.bgColor)
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(preset.style.bgColor))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        // 背景
        SoftCard {
            SectionLabel("背景")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = style.bgMode == PopupBgMode.COLOR,
                    onClick = { vm.update { it.copy(bgMode = PopupBgMode.COLOR) } },
                    label = { Text("纯色") },
                )
                FilterChip(
                    selected = style.bgMode == PopupBgMode.IMAGE,
                    onClick = {
                        vm.update { it.copy(bgMode = PopupBgMode.IMAGE) }
                    },
                    label = { Text("图片") },
                )
            }

            Spacer(Modifier.height(12.dp))
            if (style.bgMode == PopupBgMode.COLOR) {
                ColorSwatches(
                    colors = BG_SWATCHES,
                    selected = style.bgColor,
                    onSelect = { color ->
                        vm.update { it.copy(bgColor = color) }
                        hexText = styleHex(color)
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { hexText = it.take(9) },
                        label = { Text("色值") },
                        placeholder = { Text("#FFFFFF") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val parsed = parseHex(hexText)
                            if (parsed != null) {
                                vm.update { it.copy(bgColor = parsed) }
                            } else {
                                Toast.makeText(context, "色值格式： #RRGGBB", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text("应用") }
                }
            } else {
                if (style.bgImagePath.isNullOrBlank()) {
                    Text(
                        text = "还没选图。选一张后会自动压到 1600px 存到 App 里，原图删了也不影响。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "已选择一张图片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.clearImage() }) { Text("移除") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("选择图片") }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "压暗",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(style.scrim * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Slider(
                    value = style.scrim,
                    onValueChange = { vm.update { s -> s.copy(scrim = it) } },
                    valueRange = 0f..0.7f,
                )
                Text(
                    text = "图片越花，压暗越要多一点，不然白字读不清。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 文案
        SoftCard {
            SectionLabel("文案")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = style.title,
                onValueChange = { vm.update { s -> s.copy(title = it.take(30)) } },
                label = { Text("标题") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = style.bodyText,
                onValueChange = { vm.update { s -> s.copy(bodyText = it.take(120)) } },
                label = { Text("正文") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PopupFormat.TOKENS.forEach { token ->
                    Text(
                        text = token,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                vm.update { s -> s.copy(bodyText = (s.bodyText + token).take(120)) }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "{app} 应用名 · {used} 已用 · {limit} 上限 · {left} 剩余 · {opens} 打开次数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = style.exitLabel,
                onValueChange = { vm.update { s -> s.copy(exitLabel = it.take(12)) } },
                label = { Text("主按钮") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = style.snoozeLabel,
                    onValueChange = { vm.update { s -> s.copy(snoozeLabel = it.take(12)) } },
                    label = { Text("延后按钮") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = style.silenceLabel,
                    onValueChange = { vm.update { s -> s.copy(silenceLabel = it.take(12)) } },
                    label = { Text("静音按钮") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "字号",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(style.textScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Slider(
                value = style.textScale,
                onValueChange = { vm.update { s -> s.copy(textScale = it) } },
                valueRange = 0.85f..1.35f,
            )
        }

        // 颜色
        SoftCard {
            SectionLabel("文字与按钮颜色")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = style.textMode == PopupTextMode.AUTO,
                    onClick = { vm.update { it.copy(textMode = PopupTextMode.AUTO) } },
                    label = { Text("自动（推荐）") },
                )
                FilterChip(
                    selected = style.textMode == PopupTextMode.CUSTOM,
                    onClick = {
                        vm.update {
                            it.copy(
                                textMode = PopupTextMode.CUSTOM,
                                textColor = PopupFormat.suggestedTextColor(it.bgColor),
                            )
                        }
                    },
                    label = { Text("自定义") },
                )
            }

            if (style.textMode == PopupTextMode.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                ColorSwatches(
                    colors = TEXT_SWATCHES,
                    selected = style.textColor,
                    onSelect = { color -> vm.update { it.copy(textColor = color) } },
                )
                if (!PopupFormat.isReadable(style.bgColor, style.textColor)) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "这组配色对比度只有 %.1f:1，弹出来会看不清".format(
                                PopupFormat.contrastRatio(style.bgColor, style.textColor),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                vm.update {
                                    it.copy(textColor = PopupFormat.suggestedTextColor(it.bgColor))
                                }
                            },
                        ) { Text("一键修正") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "主按钮颜色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ColorSwatches(
                colors = ACCENT_SWATCHES,
                selected = style.accentColor,
                onSelect = { color -> vm.update { it.copy(accentColor = color) } },
            )
        }

        // 底部操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    vm.save()
                    Toast.makeText(
                        context,
                        if (state.perApp) "已保存这条规则的样式" else "已保存全局样式",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) { Text("保存") }

            if (state.perApp) {
                TextButton(onClick = { vm.resetToGlobal() }) { Text("改回跟随全局") }
            } else {
                TextButton(onClick = { vm.resetToFactoryDefault() }) { Text("恢复默认") }
            }
        }

        if (state.saved) {
            Text(
                text = "改动已写入设置。想马上看效果，去「提醒 → 运行方式 → 测试一下弹窗」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ColorSwatches(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(6).forEach { rowColors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(
                                width = if (color == selected) 2.dp else 1.dp,
                                color = if (color == selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            )
                            .clickable { onSelect(color) },
                    )
                }
            }
        }
    }
}

private val BG_SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFFFFF6DA.toInt(), 0xFFF7F4EC.toInt(),
    0xFFE8F7EE.toInt(), 0xFFE3F0FF.toInt(), 0xFFFFE4D6.toInt(),
    0xFFFDE8EF.toInt(), 0xFFF1F1F3.toInt(), 0xFF191919.toInt(),
    0xFF2B2B2E.toInt(), 0xFF1E3A27.toInt(), 0xFF3A2E1F.toInt(),
)

private val ACCENT_SWATCHES = listOf(
    0xFFFFC300.toInt(), 0xFFFF9F1C.toInt(), 0xFF3FAE6A.toInt(),
    0xFF3D7DFF.toInt(), 0xFFE85D75.toInt(), 0xFF8A7B5C.toInt(),
    0xFF7C5CFF.toInt(), 0xFF191919.toInt(),
)

private val TEXT_SWATCHES = listOf(
    0xFF191919.toInt(), 0xFFFFFFFF.toInt(), 0xFF4A3600.toInt(),
    0xFF14532D.toInt(), 0xFF1E3A8A.toInt(), 0xFF7F1D1D.toInt(),
)

private fun styleHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val raw = text.trim().removePrefix("#")
    if (raw.length != 6) return null
    val value = raw.toLongOrNull(16) ?: return null
    return (0xFF000000L or value).toInt()
}