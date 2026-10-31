package com.daytrace.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "daytrace_settings")

/** 免打扰时段用"当天第几分钟"表示，例如 23:00 → 1380 */
data class AppSettings(
    /** 短于该时长的会话不算一次打开（毫秒） */
    val minSessionMs: Long = 1500L,
    /** 离开后在此时间内回来，仍算同一次打开（毫秒） */
    val mergeGapMs: Long = 3000L,
    val hideSystemApps: Boolean = false,
    val hiddenPackages: Set<String> = emptySet(),
    val notificationsEnabled: Boolean = true,
    /** 精准模式：前台服务每 N 秒采样，可支撑"到点退出应用" */
    val preciseModeEnabled: Boolean = false,
    val sampleIntervalSeconds: Int = 15,
    /** 到点是否按次数自动升级（弹窗 → 弹窗+震动 → 弹回桌面） */
    val escalationEnabled: Boolean = true,
    val dndEnabled: Boolean = true,
    val dndStartMinute: Int = 23 * 60,
    val dndEndMinute: Int = 7 * 60,
    /** 白名单：完全不提醒也不强制 */
    val whitelist: Set<String> = DEFAULT_WHITELIST,
    val lastSyncTs: Long = 0L,
    /** 到点弹窗的全局样式 */
    val popupStyle: PopupStyle = PopupStyle(),
    /** 单条规则单独定制的弹窗样式（包名 → 样式） */
    val popupStyleOverrides: Map<String, PopupStyle> = emptyMap(),
) {
    /** 某个应用实际生效的弹窗样式：有单独定制就用它，否则用全局 */
    fun styleFor(packageName: String?): PopupStyle =
        packageName?.let { popupStyleOverrides[it] } ?: popupStyle

    fun hasPopupOverride(packageName: String?): Boolean =
        packageName != null && popupStyleOverrides.containsKey(packageName)
    fun inDoNotDisturb(minuteOfDay: Int): Boolean {
        if (!dndEnabled) return false
        val start = dndStartMinute
        val end = dndEndMinute
        if (start == end) return false
        return if (start < end) minuteOfDay in start until end
        else minuteOfDay >= start || minuteOfDay < end
    }

    companion object {
        val DEFAULT_WHITELIST: Set<String> = setOf(
            // 电话 / 相机 / 地图导航
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.incallui",
            "com.android.camera",
            "com.android.camera2",
            "com.google.android.GoogleCamera",
            "com.autonavi.minimap",
            "com.baidu.BaiduMap",
            "com.google.android.apps.maps",
            // 支付相关，避免打断付款
            "com.eg.android.AlipayGphone",
            "com.tencent.mm",
        )
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val MIN_SESSION = longPreferencesKey("min_session_ms")
        val MERGE_GAP = longPreferencesKey("merge_gap_ms")
        val HIDE_SYSTEM = booleanPreferencesKey("hide_system_apps")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val PRECISE = booleanPreferencesKey("precise_mode")
        val SAMPLE_INTERVAL = intPreferencesKey("sample_interval_seconds")
        val ESCALATION = booleanPreferencesKey("escalation_enabled")
        val DND_ENABLED = booleanPreferencesKey("dnd_enabled")
        val DND_START = intPreferencesKey("dnd_start_minute")
        val DND_END = intPreferencesKey("dnd_end_minute")
        val WHITELIST = stringSetPreferencesKey("whitelist")
        val LAST_SYNC = longPreferencesKey("last_sync_ts")
        val POPUP_STYLE = stringPreferencesKey("popup_style")
        val POPUP_STYLE_MAP = stringPreferencesKey("popup_style_overrides")
    }

    val flow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            AppSettings(
                minSessionMs = p[Keys.MIN_SESSION] ?: 1500L,
                mergeGapMs = p[Keys.MERGE_GAP] ?: 3000L,
                hideSystemApps = p[Keys.HIDE_SYSTEM] ?: false,
                hiddenPackages = p[Keys.HIDDEN_PACKAGES] ?: emptySet(),
                notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
                preciseModeEnabled = p[Keys.PRECISE] ?: false,
                sampleIntervalSeconds = p[Keys.SAMPLE_INTERVAL] ?: 15,
                escalationEnabled = p[Keys.ESCALATION] ?: true,
                dndEnabled = p[Keys.DND_ENABLED] ?: true,
                dndStartMinute = p[Keys.DND_START] ?: 23 * 60,
                dndEndMinute = p[Keys.DND_END] ?: 7 * 60,
                whitelist = p[Keys.WHITELIST] ?: AppSettings.DEFAULT_WHITELIST,
                lastSyncTs = p[Keys.LAST_SYNC] ?: 0L,
                popupStyle = PopupStyle.decode(p[Keys.POPUP_STYLE]) ?: PopupStyle(),
                popupStyleOverrides = PopupStyle.decodeMap(p[Keys.POPUP_STYLE_MAP]),
            )
        }

    suspend fun current(): AppSettings = flow.first()

    suspend fun setMinSessionMs(value: Long) {
        context.settingsDataStore.edit { it[Keys.MIN_SESSION] = value.coerceIn(0L, 10_000L) }
    }

    suspend fun setMergeGapMs(value: Long) {
        context.settingsDataStore.edit { it[Keys.MERGE_GAP] = value.coerceIn(0L, 30_000L) }
    }

    suspend fun setHideSystemApps(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIDE_SYSTEM] = value }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.NOTIFICATIONS] = value }
    }

    suspend fun setPreciseMode(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PRECISE] = value }
    }

    suspend fun setSampleInterval(value: Int) {
        context.settingsDataStore.edit { it[Keys.SAMPLE_INTERVAL] = value.coerceIn(10, 120) }
    }

    suspend fun setEscalation(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ESCALATION] = value }
    }

    suspend fun setDnd(enabled: Boolean, startMinute: Int, endMinute: Int) {
        context.settingsDataStore.edit {
            it[Keys.DND_ENABLED] = enabled
            it[Keys.DND_START] = startMinute.coerceIn(0, 1439)
            it[Keys.DND_END] = endMinute.coerceIn(0, 1439)
        }
    }

    suspend fun setWhitelist(value: Set<String>) {
        context.settingsDataStore.edit { it[Keys.WHITELIST] = value }
    }

    suspend fun setHiddenPackages(value: Set<String>) {
        context.settingsDataStore.edit { it[Keys.HIDDEN_PACKAGES] = value }
    }

    suspend fun setLastSync(ts: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_SYNC] = ts }
    }

    suspend fun setPopupStyle(style: PopupStyle) {
        context.settingsDataStore.edit { it[Keys.POPUP_STYLE] = style.encode() }
    }

    /** 保存单条规则的样式；传 null 表示恢复跟随全局 */
    suspend fun setPopupStyleOverride(packageName: String, style: PopupStyle?) {
        context.settingsDataStore.edit { prefs ->
            val current = PopupStyle.decodeMap(prefs[Keys.POPUP_STYLE_MAP])
            val next = if (style == null) current - packageName else current + (packageName to style)
            if (next.isEmpty()) {
                prefs.remove(Keys.POPUP_STYLE_MAP)
            } else {
                prefs[Keys.POPUP_STYLE_MAP] = PopupStyle.encodeMap(next)
            }
        }
    }
}
