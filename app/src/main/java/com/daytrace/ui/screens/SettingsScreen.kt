package com.daytrace.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.domain.TimeUtil
import com.daytrace.service.AppAccessibilityService
import com.daytrace.service.ShizukuBridge
import com.daytrace.system.PermissionHelper
import com.daytrace.ui.components.SectionTitle
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.SettingsViewModel
import java.time.LocalDate
import kotlin.math.roundToInt
@Composable
fun SettingsScreen(onOpenPopupStyle: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context.applicationContext))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val permissionTick by vm.permissionTick.collectAsStateWithLifecycle()

    val snackbarState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val msg = message
        if (msg != null) {
            snackbarState.showSnackbar(msg)
            vm.clearMessage()
        }
    }

    var hasUsageAccess by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var hasNotification by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var accessibilityOn by remember { mutableStateOf(AppAccessibilityService.isEnabled(context)) }
    var shizukuOn by remember { mutableStateOf(ShizukuBridge.hasPermission()) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showSupportQr by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasUsageAccess = PermissionHelper.hasUsageAccess(context)
        hasNotification = PermissionHelper.hasNotificationPermission(context)
        accessibilityOn = AppAccessibilityService.isEnabled(context)
        shizukuOn = ShizukuBridge.hasPermission()
        vm.refreshPermissions()
    }

    LaunchedEffect(permissionTick) {
        accessibilityOn = AppAccessibilityService.isEnabled(context)
        shizukuOn = ShizukuBridge.hasPermission()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) vm.exportCsv(uri) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasNotification = granted }

    val minSessionLocal = remember(settings.minSessionMs) { mutableStateOf(settings.minSessionMs.toFloat()) }
    val mergeGapLocal = remember(settings.mergeGapMs) { mutableStateOf(settings.mergeGapMs.toFloat()) }
    val dndStartLocal = remember(settings.dndStartMinute) { mutableStateOf(settings.dndStartMinute.toFloat()) }
    val dndEndLocal = remember(settings.dndEndMinute) { mutableStateOf(settings.dndEndMinute.toFloat()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screen, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(Dim.gap),
        ) {
            Column {
                Text(
                    text = "偏好",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            SoftCard {
                SectionTitle(text = "权限")
                Spacer(Modifier.height(12.dp))
                PermissionLine(
                    title = "使用情况访问",
                    ok = hasUsageAccess,
                    action = if (hasUsageAccess) "重新打开" else "去授权",
                    onAction = { PermissionHelper.openUsageAccessSettings(context) },
                )
                Spacer(Modifier.height(12.dp))
                PermissionLine(
                    title = "通知",
                    ok = hasNotification,
                    action = if (hasNotification) "已开启" else "申请",
                    onAction = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
                Spacer(Modifier.height(12.dp))
                PermissionLine(
                    title = "无障碍（弹回桌面）",
                    ok = accessibilityOn,
                    action = if (accessibilityOn) "已开启" else "去开启",
                    onAction = { AppAccessibilityService.openSettings(context) },
                )
                Spacer(Modifier.height(12.dp))
                PermissionLine(
                    title = "Shizuku（关闭应用）",
                    ok = shizukuOn,
                    action = if (shizukuOn) "已授权" else "去安装",
                    onAction = { vm.requestShizukuPermission(REQ_SHIZUKU) },
                )
            }

            SoftCard {
                SectionTitle(text = "外观")
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPopupStyle() }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("弹窗样式", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "背景、图片、文案、字号都能改，带实时预览",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            SoftCard {
                SectionTitle(text = "提醒方式")
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    title = "精准模式",
                    subtitle = "前台服务每 15 秒采样，到点提醒准时；会常驻一条通知",
                    checked = settings.preciseModeEnabled,
                    onCheckedChange = { vm.setPreciseMode(it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "自动升级动作",
                    subtitle = "第 3 次起自动把你弹回桌面（关掉则一直只弹窗）",
                    checked = settings.escalationEnabled,
                    onCheckedChange = { vm.setEscalation(it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "免打扰时段",
                    subtitle = "这段时间内不提醒、也不把你退出应用",
                    checked = settings.dndEnabled,
                    onCheckedChange = { vm.setDnd(it, settings.dndStartMinute, settings.dndEndMinute) },
                )
                if (settings.dndEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "开始 ${minuteLabel(dndStartLocal.value.roundToInt())} · 结束 ${minuteLabel(dndEndLocal.value.roundToInt())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = dndStartLocal.value,
                        onValueChange = { dndStartLocal.value = it },
                        onValueChangeFinished = {
                            vm.setDnd(true, dndStartLocal.value.roundToInt(), settings.dndEndMinute)
                        },
                        valueRange = 0f..1439f,
                    )
                    Slider(
                        value = dndEndLocal.value,
                        onValueChange = { dndEndLocal.value = it },
                        onValueChangeFinished = {
                            vm.setDnd(true, settings.dndStartMinute, dndEndLocal.value.roundToInt())
                        },
                        valueRange = 0f..1439f,
                    )
                }
            }

            SoftCard {
                SectionTitle(text = "统计口径")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "只统计真正的前台停留：短暂切换、误触、系统弹窗都不会被算成一次打开。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "最短会话 ${(minSessionLocal.value / 1000f)} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = minSessionLocal.value,
                    onValueChange = { minSessionLocal.value = it },
                    onValueChangeFinished = { vm.setMinSessionMs(minSessionLocal.value.toLong()) },
                    valueRange = 0f..5000f,
                    steps = 9,
                )
                Text(
                    text = "合并间隔 ${(mergeGapLocal.value / 1000f)} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "离开后在这段时间内回来，仍算同一次打开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = mergeGapLocal.value,
                    onValueChange = { mergeGapLocal.value = it },
                    onValueChangeFinished = { vm.setMergeGapMs(mergeGapLocal.value.toLong()) },
                    valueRange = 0f..10000f,
                    steps = 19,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "隐藏系统应用",
                    subtitle = "不显示设置、相机等系统自带应用",
                    checked = settings.hideSystemApps,
                    onCheckedChange = { vm.setHideSystemApps(it) },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = "提醒总开关",
                    subtitle = "关掉后不再计时提醒，也不再把你退出应用",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        vm.setNotificationsEnabled(enabled)
                        if (enabled && !PermissionHelper.hasNotificationPermission(context)) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }

            SoftCard {
                SectionTitle(text = "数据")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "全部数据保存在本机，不联网、不上传。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(onClick = { vm.syncNow() }) { Text("立即同步") }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = {
                            exportLauncher.launch("daytrace-" + LocalDate.now().toString() + ".csv")
                        },
                    ) { Text("导出 CSV") }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("清空全部记录", color = MaterialTheme.colorScheme.error)
                }
            }

            SoftCard {
                SectionTitle(text = "支持一下")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "这个 App 没有广告、不联网、不收任何数据。如果它帮你省下了一些时间，可以请作者喝杯奶茶。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (com.daytrace.system.SupportLinks.hasAifadian) {
                        OutlinedButton(
                            onClick = { openUrl(context, com.daytrace.system.SupportLinks.AIFADIAN) },
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("爱发电") }
                    }
                    OutlinedButton(
                        onClick = { showSupportQr = true },
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("请我喝杯奶茶") }
                }
            }

            SoftCard {
                SectionTitle(text = "关于")
                Spacer(Modifier.height(10.dp))
                Text("刷够了没 1.9（DayTrace）", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "数据来源：Android UsageStatsManager（系统「数字健康」同款数据）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "最近同步：" +
                        if (settings.lastSyncTs > 0L) TimeUtil.formatDateTime(settings.lastSyncTs) else "尚未同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "隐私政策",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clickable { showPrivacy = true }
                        .padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSupportQr) {
        AlertDialog(
            onDismissRequest = { showSupportQr = false },
            title = { Text("赞赏码") },
            text = {
                val qr = remember { loadAssetBitmap(context, com.daytrace.system.SupportLinks.DONATE_QR_ASSET) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "微信赞赏码",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(14.dp),
                                ),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "微信扫一扫，随喜就好",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "还没有放赞赏码图片。\n\n把微信或支付宝的收款码另存为\napp/src/main/assets/donate_qr.png\n重新打包后这里就会显示。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSupportQr = false }) { Text("关闭") }
            },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "一句话：这个 App 不联网、不收集、不上传你的任何信息，所有数据只存在这台手机里。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "· 没有注册登录，不收集姓名、手机号、邮箱\n" +
                            "· 没有申请 INTERNET 权限，从系统层面就无法联网\n" +
                            "· 使用记录、限额、目标、弹窗样式都只存本机\n" +
                            "· 没有广告、统计、推送、崩溃上报类第三方 SDK\n" +
                            "· 无障碍服务不读取屏幕内容，只在到点时执行一次「回到桌面」\n" +
                            "· 数据可自行导出 CSV，可一键清空，卸载即彻底删除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "完整的隐私政策（含权限逐项说明）见随 App 发布的 PRIVACY.md。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部记录？") },
            text = { Text("会删除本机所有使用记录、提醒状态与同步进度，规则设置保留。此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        vm.clearAll()
                    },
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

private const val REQ_SHIZUKU = 43

private fun minuteLabel(minute: Int): String {
    val m = minute.coerceIn(0, 1439)
    val h = m / 60
    val mm = m % 60
    return (if (h < 10) "0$h" else "$h") + ":" + (if (mm < 10) "0$mm" else "$mm")
}

@Composable
private fun PermissionLine(
    title: String,
    ok: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (ok) "已开启" else "未开启",
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 只是跳浏览器，App 自身仍然没有 INTERNET 权限 */
private fun openUrl(context: Context, url: String) {
    val ok = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.isSuccess
    if (!ok) {
        Toast.makeText(context, "没有找到可以打开链接的应用", Toast.LENGTH_SHORT).show()
    }
}

/** 赞赏码放在 assets 里，没有就返回 null，界面会显示放置说明 */
private fun loadAssetBitmap(context: Context, name: String): Bitmap? = runCatching {
    context.assets.open(name).use { BitmapFactory.decodeStream(it) }
}.getOrNull()
