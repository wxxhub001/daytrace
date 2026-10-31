package com.daytrace.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 只做一件事的无障碍服务：把用户"弹回桌面"（可选锁屏）。
 * 不读取窗口内容（canRetrieveWindowContent=false），不监听文本。
 */
class AppAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun goHome(): Boolean = try {
        performGlobalAction(GLOBAL_ACTION_HOME)
    } catch (t: Throwable) {
        false
    }

    fun lockScreen(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    } catch (t: Throwable) {
        false
    }

    companion object {
        @Volatile
        var instance: AppAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val expected = ComponentName(context, AppAccessibilityService::class.java)
                val raw = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: return false
                raw.split(':').any { ComponentName.unflattenFromString(it) == expected }
            } catch (t: Throwable) {
                false
            }
        }

        fun openSettings(context: Context) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (t: Throwable) {
                // 忽略
            }
        }
    }
}
