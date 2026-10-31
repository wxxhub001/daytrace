package com.daytrace.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.daytrace.domain.UsageEventSource

object PermissionHelper {

    fun hasUsageAccess(context: Context): Boolean = UsageEventSource(context).hasUsageAccess()

    fun openUsageAccessSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName)),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (t: Throwable) {
                // 尝试下一个入口
            }
        }
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            // 放弃
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 悬浮窗权限。Android 10+ 禁止后台直接启动 Activity，
     * 持有该权限是从前台服务弹出全屏拦截页的官方豁免条件之一；
     * 没有它时拦截页可能弹不出来，但"弹回桌面"仍然有效。
     */
    fun hasOverlay(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (t: Throwable) {
        false
    }

    fun openOverlaySettings(context: Context) {
        val candidates = listOf(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.packageName),
            ),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (t: Throwable) {
                // 尝试下一个入口
            }
        }
    }
}
