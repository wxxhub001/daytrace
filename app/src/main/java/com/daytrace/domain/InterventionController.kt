package com.daytrace.domain

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.daytrace.data.LimitEntity
import com.daytrace.service.AppAccessibilityService
import com.daytrace.service.ShizukuBridge
import com.daytrace.ui.InterventionActivity
import com.daytrace.ui.PopupReminderActivity

/** 到点动作的执行者：通知之外的强制性行为都在这里 */
class InterventionController(private val context: Context) {

    fun accessibilityEnabled(): Boolean = AppAccessibilityService.instance != null

    fun shizukuReady(): Boolean = ShizukuBridge.hasPermission()

    fun perform(
        action: ReminderAction,
        limit: LimitEntity,
        sessionUsedMs: Long,
        todayUsedMs: Long,
    ): Boolean {
        val service = AppAccessibilityService.instance
        return when (action) {
            ReminderAction.NOTIFY, ReminderAction.NOTIFY_VIBRATE,
            ReminderAction.POPUP, ReminderAction.POPUP_VIBRATE -> false

            ReminderAction.KILL -> when {
                ShizukuBridge.forceStop(context, limit.packageName) -> true
                service != null -> {
                    service.goHome()
                    showIntervention(limit, sessionUsedMs, todayUsedMs)
                    true
                }
                else -> false
            }

            ReminderAction.HOME -> {
                if (service == null) {
                    showIntervention(limit, sessionUsedMs, todayUsedMs)
                    false
                } else {
                    service.goHome()
                    showIntervention(limit, sessionUsedMs, todayUsedMs)
                    true
                }
            }

            ReminderAction.HOME_LOCK -> {
                if (service == null) {
                    showIntervention(limit, sessionUsedMs, todayUsedMs)
                    false
                } else {
                    service.goHome()
                    service.lockScreen()
                    true
                }
            }
        }
    }

    /**
     * 浮层弹窗提醒：需要悬浮窗权限，没有就直接返回 false，
     * 让调用方退回通知，保证提醒不会静默丢失。
     */
    fun showPopup(limit: LimitEntity, sessionUsedMs: Long, todayUsedMs: Long): Boolean {
        // 悬浮窗和无障碍都能豁免「后台启动 Activity」限制，任一在场就能弹
        val allowed = Settings.canDrawOverlays(context) || AppAccessibilityService.instance != null
        if (!allowed) return false
        val intent = Intent(context, PopupReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(PopupReminderActivity.EXTRA_PACKAGE, limit.packageName)
            putExtra(PopupReminderActivity.EXTRA_LABEL, limit.label)
            putExtra(PopupReminderActivity.EXTRA_SESSION_MS, sessionUsedMs)
            putExtra(PopupReminderActivity.EXTRA_TODAY_MS, todayUsedMs)
            putExtra(PopupReminderActivity.EXTRA_LIMIT_MINUTES, limit.dailyMinutes ?: -1)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 全屏拦截页；无障碍未开启时也允许尝试一次（可能被系统拦下） */
    fun showIntervention(limit: LimitEntity, sessionUsedMs: Long, todayUsedMs: Long) {
        val intent = Intent(context, InterventionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterventionActivity.EXTRA_PACKAGE, limit.packageName)
            putExtra(InterventionActivity.EXTRA_LABEL, limit.label)
            putExtra(InterventionActivity.EXTRA_SESSION_MS, sessionUsedMs)
            putExtra(InterventionActivity.EXTRA_TODAY_MS, todayUsedMs)
        }
        try {
            context.startActivity(intent)
        } catch (t: Throwable) {
            // 后台启动被拦时忽略，至少还有通知
        }
    }
}
