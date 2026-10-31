package com.daytrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daytrace.data.PopupBgMode
import com.daytrace.data.PopupStyle
import com.daytrace.domain.PopupFormat
import com.daytrace.system.PopupBackground

/**
 * 到点弹窗的卡片本体。
 *
 * 真实弹窗（PopupReminderActivity）和设置页里的实时预览用的是同一个 composable，
 * 所以"预览看着怎样、弹出来就怎样"。
 */
@Composable
fun PopupCard(
    style: PopupStyle,
    appLabel: String,
    packageName: String,
    usedText: String,
    limitText: String?,
    progress: Float,
    snoozeLeft: Int,
    onExit: () -> Unit,
    onSnooze: () -> Unit,
    onSilence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, style.bgImagePath, style.bgMode) {
        value = if (style.bgMode == PopupBgMode.IMAGE) {
            PopupBackground.load(style.bgImagePath)
        } else {
            null
        }
    }

    val fg = Color(PopupFormat.effectiveTextColor(style))
    val fgSoft = fg.copy(alpha = 0.66f)
    val accent = Color(style.accentColor)
    val onAccent = Color(PopupFormat.autoTextColor(style.accentColor))
    val scale = style.textScale

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(style.bgColor),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(28.dp)),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = style.scrim)),
                )
            }

            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(packageName = packageName, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = fgSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = style.title.ifBlank { "今天的额度用完了" },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize * scale,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = fg,
                        )
                    }
                }

                val body = style.bodyText.ifBlank { PopupFormat.TOKEN_USED }
                    .let { template ->
                        PopupFormat.render(
                            template = template,
                            app = appLabel,
                            used = usedText,
                            limit = limitText.orEmpty(),
                            left = "",
                            opens = "",
                        )
                    }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = fgSoft,
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = usedText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = MaterialTheme.typography.displaySmall.fontSize * scale,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
                if (!limitText.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = limitText,
                        style = MaterialTheme.typography.labelMedium,
                        color = fgSoft,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fg.copy(alpha = 0.16f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = onAccent,
                    ),
                ) {
                    Text(style.exitLabel.ifBlank { "好，先退出" })
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, fg.copy(alpha = 0.32f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                    ) {
                        Text(style.snoozeLabel.ifBlank { "再给 5 分钟" })
                    }
                    TextButton(
                        onClick = onSilence,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = fgSoft),
                    ) {
                        Text(style.silenceLabel.ifBlank { "今天不再提醒" })
                    }
                }
                if (snoozeLeft > 0) {
                    Text(
                        text = "今天还能延后 $snoozeLeft 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = fgSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** 设置页预览用的占位数据 */
object PopupPreviewSample {
    const val LABEL = "抖音"
    const val PACKAGE = "com.ss.android.ugc.aweme"
    const val USED = "1 小时 05 分"
    const val LIMIT = "上限 1 小时"
    const val PROGRESS = 1.08f
    const val SNOOZE_LEFT = 2
}