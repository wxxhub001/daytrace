package com.daytrace.ui.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daytrace.ServiceLocator
import com.daytrace.data.ActionMode
import com.daytrace.data.AppSettings
import com.daytrace.data.LimitEntity
import com.daytrace.domain.AppUsageItem
import com.daytrace.domain.DayBucket
import com.daytrace.domain.DaySummary
import com.daytrace.domain.SessionView
import com.daytrace.domain.TimeUtil
import com.daytrace.service.ShizukuBridge
import com.daytrace.service.UsageMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ 今日

data class TodayUiState(
    val loading: Boolean = true,
    val hasPermission: Boolean = true,
    val dayKey: String = "",
    val items: List<AppUsageItem> = emptyList(),
    val totalMs: Long = 0L,
    val totalOpens: Int = 0,
    val appCount: Int = 0,
    val hourly: List<Long> = List(24) { 0L },
    val sortByTime: Boolean = true,
    /** 昨日同一时刻之前的用量，用于"比昨天多/少"的对比 */
    val yesterdayMs: Long = -1L,
)

class TodayViewModel(private val container: ServiceLocator.Container) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState(dayKey = TimeUtil.todayKey()))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val hasPermission = container.eventSource.hasUsageAccess()
            if (!hasPermission) {
                _state.value = _state.value.copy(loading = false, hasPermission = false)
                return@launch
            }
            val current = _state.value
            if (current.items.isEmpty()) _state.value = current.copy(loading = true, hasPermission = true)

            container.repository.sync()
            container.reminder.checkCurrent()

            val dayKey = TimeUtil.todayKey()
            val aggregate = container.repository.dayAggregate(dayKey)
            val hourly = container.repository.hourly(dayKey)
            val sortByTime = _state.value.sortByTime
            val yesterday = TimeUtil.recentDayKeys(2).firstOrNull() ?: dayKey
            val yesterdayMs = if (yesterday == dayKey) -1L else container.repository.dayAggregate(yesterday).totalMs

            _state.value = TodayUiState(
                loading = false,
                hasPermission = true,
                dayKey = dayKey,
                items = sortItems(aggregate.items, sortByTime),
                totalMs = aggregate.totalMs,
                totalOpens = aggregate.totalOpens,
                appCount = aggregate.appCount,
                hourly = hourly,
                sortByTime = sortByTime,
                yesterdayMs = yesterdayMs,
            )
        }
    }

    fun setSortByTime(byTime: Boolean) {
        val current = _state.value
        _state.value = current.copy(
            sortByTime = byTime,
            items = sortItems(current.items, byTime),
        )
    }

    private fun sortItems(items: List<AppUsageItem>, byTime: Boolean): List<AppUsageItem> =
        if (byTime) items.sortedByDescending { it.totalMs } else items.sortedByDescending { it.openCount }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodayViewModel(ServiceLocator.get(app)) }
        }
    }
}

// ------------------------------------------------------------------ 应用详情

data class DetailUiState(
    val loading: Boolean = true,
    val packageName: String = "",
    val label: String = "",
    val totalMs: Long = 0L,
    val openCount: Int = 0,
    val avgMs: Long = 0L,
    val buckets: List<DayBucket> = emptyList(),
    val sessions: List<SessionView> = emptyList(),
    val rangeIndex: Int = 0,
    val isHidden: Boolean = false,
    val limit: LimitEntity? = null,
)

class DetailViewModel(
    private val container: ServiceLocator.Container,
    private val pkg: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState(packageName = pkg))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun load(rangeIndex: Int) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, rangeIndex = rangeIndex)
            container.repository.sync()
            val days = when (rangeIndex) {
                0 -> 1
                1 -> 7
                else -> 30
            }
            val keys = TimeUtil.recentDayKeys(days)
            val fromTs = TimeUtil.startOfDay(keys.first())
            val toTs = System.currentTimeMillis() + 1000L
            val detail = container.repository.appDetail(pkg, fromTs, toTs)
            val hidden = container.repository.isHidden(pkg)
            _state.value = DetailUiState(
                loading = false,
                packageName = pkg,
                label = detail.label,
                totalMs = detail.totalMs,
                openCount = detail.openCount,
                avgMs = detail.avgMs,
                buckets = detail.buckets,
                sessions = detail.sessions,
                rangeIndex = rangeIndex,
                isHidden = hidden,
                limit = container.database.limitDao().find(pkg),
            )
        }
    }

    fun toggleHidden() {
        viewModelScope.launch {
            val next = !_state.value.isHidden
            container.repository.setHidden(pkg, next)
            _state.value = _state.value.copy(isHidden = next)
        }
    }

    companion object {
        fun factory(app: Context, pkg: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(ServiceLocator.get(app), pkg) }
        }
    }
}

// ------------------------------------------------------------------ 历史

data class HistoryUiState(
    val loading: Boolean = true,
    val days: List<DaySummary> = emptyList(),
    val maxMs: Long = 1L,
)

class HistoryViewModel(private val container: ServiceLocator.Container) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            container.repository.sync()
            val days = container.repository.history(30)
            _state.value = HistoryUiState(
                loading = false,
                days = days,
                maxMs = (days.maxOfOrNull { it.totalMs } ?: 0L).coerceAtLeast(1L),
            )
        }
    }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(ServiceLocator.get(app)) }
        }
    }
}

// ------------------------------------------------------------------ 某一天

data class DayUiState(
    val loading: Boolean = true,
    val dayKey: String = "",
    val items: List<AppUsageItem> = emptyList(),
    val totalMs: Long = 0L,
    val totalOpens: Int = 0,
)

class DayViewModel(
    private val container: ServiceLocator.Container,
    private val dayKey: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DayUiState(dayKey = dayKey))
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            container.repository.sync()
            val aggregate = container.repository.dayAggregate(dayKey)
            _state.value = DayUiState(
                loading = false,
                dayKey = dayKey,
                items = aggregate.items,
                totalMs = aggregate.totalMs,
                totalOpens = aggregate.totalOpens,
            )
        }
    }

    companion object {
        fun factory(app: Context, dayKey: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { DayViewModel(ServiceLocator.get(app), dayKey) }
        }
    }
}

// ------------------------------------------------------------------ 限额

data class LimitsUiState(
    val loading: Boolean = true,
    val limits: List<LimitEntity> = emptyList(),
    val usage: Map<String, AppUsageItem> = emptyMap(),
    val candidates: List<AppUsageItem> = emptyList(),
    val preciseMode: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val shizukuReady: Boolean = false,
)

class LimitsViewModel(
    private val container: ServiceLocator.Container,
    private val app: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(LimitsUiState())
    val state: StateFlow<LimitsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            container.repository.sync()
            val limitDao = container.database.limitDao()
            val limits = limitDao.all()
            val aggregate = container.repository.dayAggregate(TimeUtil.todayKey())
            val usage = aggregate.items.associateBy { it.packageName }
            val candidates = aggregate.items.filter { item -> limits.none { it.packageName == item.packageName } }
            val settings = container.settings.current()
            _state.value = LimitsUiState(
                loading = false,
                limits = limits,
                usage = usage,
                candidates = candidates,
                preciseMode = settings.preciseModeEnabled,
                accessibilityEnabled = com.daytrace.service.AppAccessibilityService.isEnabled(app),
                overlayEnabled = com.daytrace.system.PermissionHelper.hasOverlay(app),
                shizukuReady = ShizukuBridge.hasPermission(),
            )
        }
    }

    fun save(
        packageName: String,
        label: String,
        minutes: Int?,
        opens: Int?,
        remindEveryMinutes: Int?,
        repeatAfterLimitMinutes: Int?,
        actionMode: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            val existing = container.database.limitDao().find(packageName)
            container.database.limitDao().upsert(
                LimitEntity(
                    packageName = packageName,
                    label = label,
                    dailyMinutes = minutes,
                    dailyOpens = opens,
                    remindEveryMinutes = remindEveryMinutes,
                    repeatAfterLimitMinutes = repeatAfterLimitMinutes,
                    actionMode = actionMode,
                    enabled = enabled,
                    lastNotifiedDay = existing?.lastNotifiedDay,
                    lastNotifiedAt = existing?.lastNotifiedAt ?: 0L,
                ),
            )
            refresh()
        }
    }

    fun toggleEnabled(limit: LimitEntity) {
        viewModelScope.launch {
            container.database.limitDao().upsert(limit.copy(enabled = !limit.enabled))
            refresh()
        }
    }

    fun remove(packageName: String) {
        viewModelScope.launch {
            container.database.limitDao().delete(packageName)
            container.database.reminderDao().delete(packageName)
            refresh()
        }
    }

    fun enablePreciseMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setPreciseMode(enabled)
            if (enabled) UsageMonitorService.start(app) else UsageMonitorService.stop(app)
            refresh()
        }
    }

    /** 提醒自检：先真的弹一个窗（顺便验证悬浮窗/无障碍），弹不出来退回通知 */
    fun sendTestReminder() {
        val self = LimitEntity(
            packageName = app.packageName,
            label = "测试提醒",
            dailyMinutes = 1,
        )
        val shown = container.intervention.showPopup(self, sessionUsedMs = 0L, todayUsedMs = 90_000L)
        if (!shown) container.notifier.notifyTest()
    }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { LimitsViewModel(ServiceLocator.get(app), app) }
        }
    }
}

// ------------------------------------------------------------------ 设置

class SettingsViewModel(
    private val container: ServiceLocator.Container,
    private val app: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 让 Compose 在权限状态变化后重新读取 */
    private val _permissionTick = MutableStateFlow(0)
    val permissionTick: StateFlow<Int> = _permissionTick.asStateFlow()

    fun refreshPermissions() { _permissionTick.value = _permissionTick.value + 1 }

    fun clearMessage() { _message.value = null }

    fun setMinSessionMs(value: Long) = viewModelScope.launch {
        container.settings.setMinSessionMs(value)
    }

    fun setMergeGapMs(value: Long) = viewModelScope.launch {
        container.settings.setMergeGapMs(value)
    }

    fun setHideSystemApps(value: Boolean) = viewModelScope.launch {
        container.settings.setHideSystemApps(value)
    }

    fun setNotificationsEnabled(value: Boolean) = viewModelScope.launch {
        container.settings.setNotificationsEnabled(value)
    }

    fun setEscalation(value: Boolean) = viewModelScope.launch {
        container.settings.setEscalation(value)
    }

    fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        container.settings.setDnd(enabled, startMinute, endMinute)
    }

    fun setWhitelist(value: Set<String>) = viewModelScope.launch {
        container.settings.setWhitelist(value)
    }

    fun setSampleInterval(seconds: Int) = viewModelScope.launch {
        container.settings.setSampleInterval(seconds)
    }

    fun setPreciseMode(enabled: Boolean) = viewModelScope.launch {
        container.settings.setPreciseMode(enabled)
        if (enabled) UsageMonitorService.start(app) else UsageMonitorService.stop(app)
        _permissionTick.value = _permissionTick.value + 1
    }

    fun requestShizukuPermission(code: Int) {
        val ok = ShizukuBridge.requestPermission(code)
        _message.value = if (ok) "已向 Shizuku 发出授权请求" else "请先安装并启动 Shizuku"
    }

    fun syncNow() = viewModelScope.launch {
        val ok = container.repository.sync()
        container.reminder.checkCurrent()
        _message.value = if (ok) "已从系统补采最新数据" else "没有新数据，或权限未开启"
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        val count = container.repository.exportedCsv(uri)
        _message.value = "已导出 $count 条记录"
    }

    fun clearAll() = viewModelScope.launch {
        container.repository.clearAll()
        container.reminder.clearAll()
        _message.value = "本地记录已清空"
    }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(ServiceLocator.get(app), app) }
        }
    }
}

/** 供界面直接使用的动作名 */
val ActionModeOptions: List<Pair<String, String>> = listOf(
    ActionMode.AUTO to "自动升级（弹窗 → 震动 → 弹回桌面）",
    ActionMode.POPUP to "弹窗提醒",
    ActionMode.NOTIFY to "静默通知",
    ActionMode.HOME to "弹回桌面",
    ActionMode.LOCK to "回桌面并锁屏",
    ActionMode.KILL to "关闭应用*",
)
