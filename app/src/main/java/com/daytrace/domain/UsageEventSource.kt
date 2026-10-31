package com.daytrace.domain

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * 对系统 UsageStatsManager 的薄封装。
 * 数据源：系统"数字健康"用的同一份数据，无需 root。
 */
class UsageEventSource(private val context: Context) {

    private val usageStats: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** 是否已获得"使用情况访问权限"（PACKAGE_USAGE_STATS，需用户在系统设置中手动开启） */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (appOps != null) {
            val mode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        context.packageName,
                    )
                }
            } catch (t: Throwable) {
                AppOpsManager.MODE_ERRORED
            }
            if (mode == AppOpsManager.MODE_ALLOWED) return true
        }
        // 某些 ROM 上 AppOps 返回值不可靠，用一次真实查询做兜底
        return try {
            val now = System.currentTimeMillis()
            usageStats?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
                ?.isNotEmpty() == true
        } catch (t: Throwable) {
            false
        }
    }

    fun query(startTs: Long, endTs: Long): List<RawEvent> {
        val manager = usageStats ?: return emptyList()
        val events = try {
            manager.queryEvents(startTs, endTs)
        } catch (t: Throwable) {
            return emptyList()
        } ?: return emptyList()

        val buffer = UsageEvents.Event()
        val out = ArrayList<RawEvent>(512)
        while (events.hasNextEvent()) {
            events.getNextEvent(buffer)
            if (!isInteresting(buffer.eventType)) continue
            val pkg = buffer.packageName ?: ""
            out.add(RawEvent(buffer.timeStamp, pkg, buffer.eventType))
        }
        return out
    }

    private fun isInteresting(type: Int): Boolean = when (type) {
        EventType.RESUMED,
        EventType.PAUSED,
        EventType.STOPPED,
        EventType.SCREEN_NON_INTERACTIVE,
        EventType.KEYGUARD_SHOWN,
        EventType.DEVICE_SHUTDOWN -> true
        else -> false
    }
}
