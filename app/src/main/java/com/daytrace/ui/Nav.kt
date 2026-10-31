package com.daytrace.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daytrace.ui.screens.DayScreen
import com.daytrace.ui.screens.DetailScreen
import com.daytrace.ui.screens.GoalsScreen
import com.daytrace.ui.screens.HistoryScreen
import com.daytrace.ui.screens.LimitsScreen
import com.daytrace.ui.screens.PopupStyleScreen
import com.daytrace.ui.screens.SettingsScreen
import com.daytrace.ui.screens.TodayScreen

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AppNav(startPackage: String? = null, openGoals: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val tabs = listOf(
        TabItem("today", "今日", Icons.Rounded.Today),
        TabItem("goals", "目标", Icons.Rounded.Eco),
        TabItem("history", "历史", Icons.Rounded.History),
        TabItem("limits", "提醒", Icons.Rounded.Timer),
        TabItem("settings", "我的", Icons.Rounded.Settings),
    )

    LaunchedEffect(startPackage, openGoals) {
        if (!startPackage.isNullOrBlank()) {
            navController.navigate("detail/" + startPackage)
        } else if (openGoals) {
            navController.navigate("goals") {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                FreshNavBar(
                    tabs = tabs,
                    current = route ?: "today",
                    onSelect = { tab ->
                        if (route != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                TodayScreen(onOpenDetail = { pkg -> navController.navigate("detail/" + pkg) })
            }
            composable("goals") {
                GoalsScreen()
            }
            composable("history") {
                HistoryScreen(onOpenDay = { dayKey -> navController.navigate("day/" + dayKey) })
            }
            composable("limits") {
                LimitsScreen(
                    onEditPopupStyle = { pkg -> navController.navigate("popup_style/$pkg") },
                )
            }
            composable("settings") {
                SettingsScreen(onOpenPopupStyle = { navController.navigate("popup_style") })
            }
            composable("popup_style") {
                PopupStyleScreen(onBack = { navController.popBackStack() })
            }
            composable("popup_style/{pkg}") { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                PopupStyleScreen(
                    packageName = pkg,
                    appLabel = rememberAppLabel(pkg),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("detail/{pkg}") { entry ->
                val pkg = entry.arguments?.getString("pkg").orEmpty()
                DetailScreen(packageName = pkg, onBack = { navController.popBackStack() })
            }
            composable("day/{dayKey}") { entry ->
                val dayKey = entry.arguments?.getString("dayKey").orEmpty()
                DayScreen(dayKey = dayKey, onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun rememberAppLabel(packageName: String): String? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName.isBlank()) {
            null
        } else {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }
}

@Composable
private fun FreshNavBar(
    tabs: List<TabItem>,
    current: String,
    onSelect: (TabItem) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 26.dp, vertical = 10.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
