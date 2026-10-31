package com.daytrace.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daytrace.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 通知上的「今天不再提醒」等操作 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = ServiceLocator.get(context)
                when (action) {
                    ACTION_SILENCE -> container.reminder.silenceForDay(pkg)
                }
            } catch (t: Throwable) {
                // 忽略
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SILENCE = "com.daytrace.action.SILENCE"
        const val EXTRA_PACKAGE = "extra_package"
    }
}
