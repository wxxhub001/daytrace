package com.daytrace.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.domain.TimeUtil
import com.daytrace.ui.components.AppUsageRow
import com.daytrace.ui.components.EmptyHint
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.components.StatPill
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.DayViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(dayKey: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DayViewModel = viewModel(
        key = dayKey,
        factory = DayViewModel.factory(context.applicationContext, dayKey),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(dayKey) { vm.load() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(if (TimeUtil.isToday(dayKey)) "今天" else TimeUtil.formatDayLabel(dayKey))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dim.screen,
                end = Dim.screen,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Dim.gap),
        ) {
            item {
                SoftCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatPill(
                            value = TimeUtil.formatDuration(state.totalMs),
                            label = "总时长",
                            modifier = Modifier.weight(1.3f),
                        )
                        StatPill(
                            value = state.totalOpens.toString(),
                            label = "次打开",
                            modifier = Modifier.weight(1f),
                        )
                        StatPill(
                            value = state.items.size.toString(),
                            label = "个应用",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.items.isEmpty()) {
                item { EmptyHint(text = if (state.loading) "加载中…" else "这一天没有使用记录") }
            } else {
                items(state.items, key = { it.packageName }) { item ->
                    AppUsageRow(
                        item = item,
                        maxMs = state.items.firstOrNull()?.totalMs ?: 0L,
                        onClick = {},
                    )
                }
            }
        }
    }
}
