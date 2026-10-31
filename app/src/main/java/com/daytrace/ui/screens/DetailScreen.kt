package com.daytrace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daytrace.domain.DayBucket
import com.daytrace.domain.TimeUtil
import com.daytrace.ui.components.AppIcon
import com.daytrace.ui.components.SectionTitle
import com.daytrace.ui.components.SoftCard
import com.daytrace.ui.components.StatPill
import com.daytrace.ui.theme.Dim
import com.daytrace.ui.vm.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: DetailViewModel = viewModel(
        key = packageName,
        factory = DetailViewModel.factory(context.applicationContext, packageName),
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(packageName) { vm.load(0) }

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
                    Text(
                        text = state.label.ifEmpty { packageName },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dim.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dim.gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = packageName, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.label.ifEmpty { packageName },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("今日", "近 7 天", "近 30 天").forEachIndexed { index, label ->
                    FilterChip(
                        selected = state.rangeIndex == index,
                        onClick = { vm.load(index) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            SoftCard {
                Text(
                    text = "使用时长",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = TimeUtil.formatDuration(state.totalMs),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatPill(
                        value = state.openCount.toString(),
                        label = "次打开",
                        modifier = Modifier.weight(1f),
                    )
                    StatPill(
                        value = TimeUtil.formatCompact(state.avgMs),
                        label = "平均每次",
                        modifier = Modifier.weight(1f),
                    )
                    StatPill(
                        value = state.sessions.firstOrNull()?.let { TimeUtil.formatClock(it.startTs) } ?: "—",
                        label = "最近使用",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.buckets.isNotEmpty()) {
                SoftCard {
                    SectionTitle(text = "每日分布")
                    Spacer(Modifier.height(14.dp))
                    DayBars(buckets = state.buckets)
                }
            }

            if (state.sessions.isNotEmpty()) {
                SoftCard {
                    SectionTitle(text = "最近会话")
                    Spacer(Modifier.height(6.dp))
                    state.sessions.take(12).forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = TimeUtil.formatClock(session.startTs) + " – " +
                                    TimeUtil.formatClock(session.endTs),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = TimeUtil.formatCompact(session.durationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("从统计中隐藏", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "隐藏后该应用不再出现在列表和总额里",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.isHidden, onCheckedChange = { vm.toggleHidden() })
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DayBars(buckets: List<DayBucket>) {
    val max = (buckets.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        buckets.takeLast(30).forEach { bucket ->
            val fraction = if (bucket.durationMs <= 0L) 0.04f
            else (bucket.durationMs.toFloat() / max.toFloat()).coerceIn(0.08f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (bucket.durationMs > 0L) {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            )
                        },
                    ),
            )
        }
    }
}
