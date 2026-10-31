package com.daytrace.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.daytrace.domain.AggregateResult
import com.daytrace.domain.AppDetail
import com.daytrace.domain.AppUsageItem
import com.daytrace.domain.DayBucket
import com.daytrace.domain.DaySummary
import com.daytrace.domain.SessionView
import com.daytrace.domain.TimeUtil
import com.daytrace.domain.UsageAnalyzer
import com.daytrace.domain.UsageEventSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class UsageRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val eventSource: UsageEventSource,
    private val settings: SettingsStore,
) {

    private val pm: PackageManager = context.packageManager
    private val syncMutex = Mutex()
    private val metaCache = ConcurrentHashMap<String, AppMetaInfo>()

    private data class AppMetaInfo(
        val label: String,
        val isSystem: Boolean,
        val launchable: Boolean,
    )

    // ------------------------------------------------------------------ 同步

    /**
     * 从系统增量采集事件并落库。
     * 策略：每次重算"最近窗口"内的全部数据（系统事件保留期通常只有几天），
     * 旧的会话数据留在库里，形成长期历史。
     */
    suspend fun sync(): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) { doSync() }
    }

    private suspend fun doSync(): Boolean {
        if (!eventSource.hasUsageAccess()) return false

        val now = System.currentTimeMillis()
        val current = settings.current()
        val firstRun = current.lastSyncTs == 0L
        val windowMs = if (firstRun) INITIAL_IMPORT_MS else OVERLAP_MS
        val from = now - windowMs

        val raw = eventSource.query(from, now)
        if (raw.isEmpty()) {
            settings.setLastSync(now)
            return false
        }

        val earliest = raw.minOf { it.ts }
        // 只重写"系统仍能提供事件"的那部分，避免事件被系统清理后误删本地历史
        val deleteFrom = maxOf(from, earliest - 60_000L)

        val sessions = UsageAnalyzer.buildSessions(
            events = raw,
            rangeStart = from,
            rangeEnd = now,
            minSessionMs = current.minSessionMs,
            mergeGapMs = current.mergeGapMs,
            excludedPackages = excludedPackages(),
        )

        val zone = ZoneId.systemDefault()
        val rows = ArrayList<SessionEntity>(sessions.size + 4)
        for (session in sessions) {
            val meta = metaOf(session.pkg)
            for (part in UsageAnalyzer.splitByDay(session, zone)) {
                rows.add(
                    SessionEntity(
                        packageName = part.pkg,
                        label = meta.label,
                        isSystem = meta.isSystem,
                        launchable = meta.launchable,
                        startTs = part.startTs,
                        endTs = part.endTs,
                        dayKey = part.dayKey,
                        durationMs = part.durationMs,
                        opens = part.opens,
                    )
                )
            }
        }

        val dao = db.sessionDao()
        dao.deleteFrom(deleteFrom)
        if (rows.isNotEmpty()) dao.insertAll(rows)
        settings.setLastSync(now)
        return true
    }

    private fun excludedPackages(): Set<String> {
        val set = mutableSetOf(context.packageName, "com.android.systemui")
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { set.add(it) }
        } catch (t: Throwable) {
            // ignore
        }
        return set
    }

    private fun metaOf(pkg: String): AppMetaInfo = metaCache.getOrPut(pkg) {
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(info).toString()
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val launchable = pm.getLaunchIntentForPackage(pkg) != null ||
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg),
                    0,
                ).isNotEmpty()
            AppMetaInfo(label, isSystem, launchable)
        } catch (t: Throwable) {
            // 没有 QUERY_ALL_PACKAGES 时，非启动器可见的包查不到元信息：
            // 退化成包名末段，至少比一串 com.xxx.yyy 好读。
            AppMetaInfo(fallbackLabel(pkg), false, false)
        }
    }

    private fun fallbackLabel(pkg: String): String {
        val tail = pkg.substringAfterLast('.')
        return if (tail.isBlank()) pkg else tail.replaceFirstChar { it.uppercaseChar() }
    }

    // ------------------------------------------------------------------ 查询

    suspend fun dayAggregate(dayKey: String): AggregateResult {
        val s = settings.current()
        return aggregate(visible(db.sessionDao().byDay(dayKey), s))
    }

    suspend fun hourly(dayKey: String): List<Long> {
        val s = settings.current()
        val rows = visible(db.sessionDao().byDay(dayKey), s)
        val buckets = LongArray(24)
        val dayStart = TimeUtil.startOfDay(dayKey)
        val hourMs = 3_600_000L
        val dayEnd = dayStart + 24 * hourMs

        for (r in rows) {
            if (r.durationMs <= 0L) continue
            val from = maxOf(r.startTs, dayStart)
            val to = minOf(r.endTs, dayEnd)
            if (to <= from) {
                val idx = ((r.startTs - dayStart) / hourMs).toInt().coerceIn(0, 23)
                buckets[idx] += r.durationMs
                continue
            }
            val span = (to - from).coerceAtLeast(1L)
            val startHour = ((from - dayStart) / hourMs).toInt().coerceIn(0, 23)
            val endHour = ((to - 1 - dayStart) / hourMs).toInt().coerceIn(0, 23)
            for (h in startHour..endHour) {
                val hs = dayStart + h * hourMs
                val he = hs + hourMs
                val overlap = (minOf(to, he) - maxOf(from, hs)).coerceAtLeast(0L)
                if (overlap > 0L) buckets[h] += r.durationMs * overlap / span
            }
        }
        return buckets.toList()
    }

    suspend fun history(days: Int): List<DaySummary> {
        val s = settings.current()
        val keys = TimeUtil.recentDayKeys(days)
        val fromKey = keys.first()
        val rows = visible(db.sessionDao().sinceDay(fromKey), s)
        val grouped = LinkedHashMap<String, MutableList<SessionEntity>>()
        for (r in rows) grouped.getOrPut(r.dayKey) { ArrayList() }.add(r)

        return keys.map { key ->
            val list = grouped[key]
            if (list.isNullOrEmpty()) {
                DaySummary(key, 0L, 0, 0)
            } else {
                val agg = aggregate(list)
                DaySummary(key, agg.totalMs, agg.totalOpens, agg.appCount)
            }
        }.reversed()
    }

    suspend fun appDetail(pkg: String, fromTs: Long, toTs: Long): AppDetail {
        val rows = db.sessionDao().byPackage(pkg, fromTs, toTs)
        val totalMs = rows.sumOf { it.durationMs }
        val opens = rows.sumOf { it.opens }
        val label = rows.lastOrNull()?.label ?: metaOf(pkg).label
        val byDay = LinkedHashMap<String, Long>()
        for (r in rows) byDay[r.dayKey] = (byDay[r.dayKey] ?: 0L) + r.durationMs
        val buckets = byDay.entries
            .sortedBy { it.key }
            .map { DayBucket(it.key, it.value) }
        val sessions = rows.sortedByDescending { it.startTs }
            .take(60)
            .map { SessionView(it.startTs, it.endTs, it.durationMs) }
        return AppDetail(
            packageName = pkg,
            label = label,
            totalMs = totalMs,
            openCount = opens,
            avgMs = if (opens > 0) totalMs / opens else 0L,
            buckets = buckets,
            sessions = sessions,
        )
    }

    suspend fun appSessions(pkg: String, fromTs: Long, toTs: Long): List<SessionEntity> =
        db.sessionDao().byPackage(pkg, fromTs, toTs)

    suspend fun isHidden(pkg: String): Boolean = pkg in settings.current().hiddenPackages

    suspend fun setHidden(pkg: String, hidden: Boolean) {
        val cur = settings.current().hiddenPackages
        settings.setHiddenPackages(if (hidden) cur + pkg else cur - pkg)
    }

    suspend fun hideSystemApps(): Boolean = settings.current().hideSystemApps

    // ------------------------------------------------------------------ 维护

    suspend fun clearAll() {
        db.sessionDao().clearAll()
        settings.setLastSync(0L)
    }

    suspend fun exportedCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val rows = db.sessionDao().all()
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("日期,应用,包名,开始时间,结束时间,时长(秒),打开次数\n")
        for (r in rows) {
            sb.append(r.dayKey).append(',')
            sb.append(escapeCsv(r.label)).append(',')
            sb.append(r.packageName).append(',')
            sb.append(TimeUtil.formatDateTime(r.startTs, zone)).append(',')
            sb.append(TimeUtil.formatDateTime(r.endTs, zone)).append(',')
            sb.append(r.durationMs / 1000L).append(',')
            sb.append(r.opens).append('\n')
        }
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(sb.toString().toByteArray(Charsets.UTF_8))
        }
        rows.size
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"')) "\"" + value.replace("\"", "\"\"") + "\"" else value

    // ------------------------------------------------------------------ 内部

    private fun visible(rows: List<SessionEntity>, s: AppSettings): List<SessionEntity> =
        rows.filter { row ->
            row.launchable &&
                row.durationMs > 0L &&
                row.packageName !in s.hiddenPackages &&
                !(s.hideSystemApps && row.isSystem)
        }

    private fun aggregate(rows: List<SessionEntity>): AggregateResult {
        val map = LinkedHashMap<String, AppUsageItem>()
        for (r in rows) {
            val cur = map[r.packageName]
            map[r.packageName] = if (cur == null) {
                AppUsageItem(
                    packageName = r.packageName,
                    label = r.label,
                    isSystem = r.isSystem,
                    openCount = r.opens,
                    totalMs = r.durationMs,
                    lastUsedTs = r.endTs,
                )
            } else {
                cur.copy(
                    openCount = cur.openCount + r.opens,
                    totalMs = cur.totalMs + r.durationMs,
                    lastUsedTs = maxOf(cur.lastUsedTs, r.endTs),
                )
            }
        }
        val items = map.values.sortedByDescending { it.totalMs }
        return AggregateResult(
            items = items,
            totalMs = items.sumOf { it.totalMs },
            totalOpens = items.sumOf { it.openCount },
        )
    }

    companion object {
        /** 首次安装时回溯导入的天数（系统事件通常只保留几天） */
        private const val INITIAL_IMPORT_MS = 7L * 24 * 60 * 60 * 1000
        /** 每次同步重算的窗口，覆盖"跨零点"的会话 */
        private const val OVERLAP_MS = 36L * 60 * 60 * 1000

    }
}
