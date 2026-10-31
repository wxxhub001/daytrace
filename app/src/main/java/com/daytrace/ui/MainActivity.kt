package com.daytrace.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.daytrace.ServiceLocator
import com.daytrace.service.UsageMonitorService
import com.daytrace.ui.theme.DayTraceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 白底主题固定为浅色：状态栏/导航栏图标始终用深色，系统开深色模式也不跟着变
        val lightBars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = lightBars, navigationBarStyle = lightBars)

        // 打开 App 也刷新一次挂件：系统限制后台任务时，至少还能靠这一步追上
        com.daytrace.widget.WidgetUpdater.refreshAllAsync(applicationContext)

        // 只有首次创建时才处理通知跳转，避免旋转屏幕后重复导航
        val startPackage = if (savedInstanceState == null) {
            intent?.getStringExtra(EXTRA_PACKAGE)
        } else {
            null
        }
        val openGoals = savedInstanceState == null &&
            intent?.getBooleanExtra(EXTRA_OPEN_GOALS, false) == true

        // 精准模式开着就确保服务在跑（从界面启动，符合前台服务限制）
        lifecycleScope.launch {
            val container = ServiceLocator.get(applicationContext)
            val settings = container.settings.current()
            if (settings.preciseModeEnabled && !UsageMonitorService.running) {
                UsageMonitorService.start(applicationContext)
            }
        }

        setContent {
            DayTraceTheme {
                AppNav(startPackage = startPackage, openGoals = openGoals)
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
        const val EXTRA_OPEN_GOALS = "extra_open_goals"
    }
}
