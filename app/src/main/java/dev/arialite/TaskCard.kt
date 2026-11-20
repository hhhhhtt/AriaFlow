package dev.arialite

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import java.text.BreakIterator
import kotlin.math.roundToInt

@Composable
internal fun TaskCard(
    task: Download,
    server: String?,
    disabled: Boolean,
    pending: Boolean,
    toggle: () -> Unit,
    remove: () -> Unit,
) {
    val c = LocalFlow.current
    val m = FlowMetric
    val canToggle = task.status == "active" || task.status == "waiting" || task.status == "paused"
    val toggleLabel = if (task.status == "paused") "恢复" else "暂停"
    val width = with(LocalDensity.current) { (m.actionWidth * (if (canToggle) 2 else 1)).toPx() }
    var offset by remember(task.gid, width, disabled) { mutableFloatStateOf(0f) }
    var menu by remember(task.gid, disabled) { mutableStateOf(false) }
    val close = {
        offset = 0f
        menu = false
    }
    val onToggle = {
        close()
        toggle()
    }
    val onRemove = {
        close()
        remove()
    }
    val shape = RoundedCornerShape(m.cardRadius)
    Surface(shape = shape, color = c.card, shadowElevation = m.shadowY) {
        Box(Modifier.fillMaxWidth().clip(shape)) {
            if (offset < 0 && !disabled) {
                Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                    if (canToggle)
                        Box(
                            Modifier.width(m.actionWidth)
                                .fillMaxHeight()
                                .background(c.fileInstallerFill)
                                .clickable(onClick = onToggle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(toggleLabel, color = c.fileInstaller, style = FlowType.segment)
                        }
                    Box(
                        Modifier.width(m.actionWidth)
                            .fillMaxHeight()
                            .background(c.statusError)
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("移除", style = FlowType.segment, color = c.onRemove)
                    }
                }
            }
            Column(
                Modifier.absoluteOffset { IntOffset(offset.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(c.card)
                    .draggable(
                        rememberDraggableState { offset = (offset + it).coerceIn(-width, 0f) },
                        Orientation.Horizontal,
                        enabled = !disabled,
                        onDragStopped = { offset = if (offset < -width / 3) -width else 0f },
                    )
                    .pointerInput(disabled) {
                        if (!disabled)
                            detectTapGestures(
                                onLongPress = {
                                    offset = 0f
                                    menu = true
                                },
                                onTap = { offset = 0f },
                            )
                    }
                    .semantics {
                        if (!disabled)
                            onLongClick("任务操作") {
                                offset = 0f
                                menu = true
                                true
                            }
                    }
                    .padding(start = m.taskInset, end = m.taskInset, top = m.s6, bottom = m.s12)
            ) {
                // The 48dp hit row includes the six-dp gutters around the 36dp visual control.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = m.taskTouch),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(m.s8),
                ) {
                    Column(
                        Modifier.weight(1f).padding(vertical = m.s6),
                        verticalArrangement = Arrangement.spacedBy(m.s6),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(m.s8),
                        ) {
                            val (dot, label) =
                                when (task.status) {
                                    "active" -> c.statusActive to "活跃"
                                    "waiting" -> c.statusWaiting to "等待"
                                    "paused" -> c.statusWaiting to "已暂停"
                                    "complete" -> c.statusComplete to "完成"
                                    "error" -> c.statusError to "错误"
                                    else -> c.statusStopped to "停止"
                                }
                            Box(
                                Modifier.size(m.statusDot).background(dot, CircleShape).semantics {
                                    contentDescription = label
                                }
                            )
                            Filename(task.name)
                        }
                        Segments(task)
                    }
                    if (task.status == "active" || task.status == "paused") {
                        TaskControl(task.status == "paused", pending, !disabled, onToggle)
                    }
                }
                val total = totalLabel(task)
                val transferred = "${bytes(task.done)} / $total · ${(task.progress * 100).toInt()}%"
                val info =
                    when (task.status) {
                        "active" -> "$transferred · ↓ ${bytes(task.speed)}/s"
                        "paused" -> "已暂停 · $transferred"
                        "complete" -> "已完成 · $total"
                        "error" -> if (task.error.isBlank()) "下载失败" else "下载失败 · ${task.error}"
                        "waiting" -> "等待 · $total"
                        else -> "已停止 · $total"
                    }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildAnnotatedString {
                            if (server != null)
                                withStyle(
                                    FlowType.axis.copy(color = c.textSecondary).toSpanStyle()
                                ) {
                                    append("$server · aria2 · ")
                                }
                            append(info)
                        },
                        Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = FlowType.metadata,
                        color = if (task.status == "error") c.statusError else c.textSecondary,
                    )
                    if (task.fileCount > 1)
                        Text(
                            " · ${task.fileCount} 个文件",
                            style = FlowType.metadata,
                            color = c.textSecondary,
                            maxLines = 1,
                        )
                }
            }
            DropdownMenu(expanded = menu && !disabled, onDismissRequest = close) {
                if (canToggle) DropdownMenuItem(text = { Text(toggleLabel) }, onClick = onToggle)
                DropdownMenuItem(text = { Text("移除任务", color = c.statusError) }, onClick = onRemove)
            }
        }
    }
}

private fun totalLabel(task: Download) = if (task.total > 0) bytes(task.total) else "未知大小"

@Composable
private fun Filename(name: String) {
    val measure = rememberTextMeasurer()
    val style = LocalTextStyle.current.merge(FlowType.file)
    BoxWithConstraints {
        val width = constraints.maxWidth
        val shown =
            remember(name, width, style, measure) {
                fun fits(value: String) =
                    !measure
                        .measure(
                            value,
                            style,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = width),
                        )
                        .hasVisualOverflow
                if (fits(name)) name
                else {
                    val iter = BreakIterator.getCharacterInstance().apply { setText(name) }
                    val edges =
                        generateSequence(iter.first()) {
                                iter.next().takeIf { it != BreakIterator.DONE }
                            }
                            .toList()
                    val dotIndex = name.lastIndexOf('.')
                    val extensionStart = edges.binarySearch(dotIndex)
                    val minSuffix = if (extensionStart > 0 && fits("…" + name.substring(dotIndex))) {
                        edges.lastIndex - extensionStart
                    } else 0
                    fun shorten(count: Int): String {
                        val suffix = maxOf(minSuffix, count / 2)
                        return name.take(edges[count - suffix]) + "…" + name.substring(edges[edges.lastIndex - suffix])
                    }
                    var low = minSuffix
                    var high = edges.lastIndex
                    while (low < high) {
                        val mid = (low + high + 1) / 2
                        if (fits(shorten(mid))) low = mid else high = mid - 1
                    }
                    shorten(low)
                }
            }
        Text(
            shown,
            Modifier.clearAndSetSemantics { contentDescription = name },
            style = style,
            maxLines = 1,
            color = LocalFlow.current.textPrimary,
        )
    }
}

@Composable
private fun TaskControl(paused: Boolean, pending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val m = FlowMetric
    val c = LocalFlow.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(if (pressed) m.pressScale else 1f, tween(m.pressMillis), label = "按压")
    val fill by
        animateColorAsState(
            if (pressed) c.taskControlPressedFill else c.taskControlFill,
            tween(m.pressMillis),
            label = "按钮底色",
        )
    val label = if (pending) "请求中" else if (paused) "继续下载" else "暂停下载"
    Box(
        Modifier.size(m.taskTouch)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !pending,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(m.taskControl)
                .scale(scale)
                .background(fill, RoundedCornerShape(m.taskControlRadius)),
            contentAlignment = Alignment.Center,
        ) {
            val face =
                if (pending) ControlFace.Loading
                else if (paused) ControlFace.Play else ControlFace.Pause
            Crossfade(face, animationSpec = tween(m.iconFadeMillis), label = "任务按钮") { current ->
                Box(Modifier.size(m.taskControlIcon), contentAlignment = Alignment.Center) {
                    when (current) {
                        ControlFace.Loading -> PlaybackSpinner()
                        ControlFace.Play,
                        ControlFace.Pause -> {
                            val play = current == ControlFace.Play
                            Icon(
                                painterResource(
                                    if (play) R.drawable.symbol_play_arrow
                                    else R.drawable.symbol_pause
                                ),
                                null,
                                Modifier.size(m.taskControlIcon)
                                    .then(
                                        if (play) Modifier.absoluteOffset(x = m.playOffset)
                                        else Modifier
                                    ),
                                tint = c.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ControlFace {
    Play,
    Pause,
    Loading,
}

@Composable
internal fun PlaybackSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(FlowMetric.spinner).semantics { contentDescription = "请求中" },
        color = LocalFlow.current.accent,
        strokeWidth = FlowMetric.spinnerStroke,
    )
}

@Composable
private fun Segments(task: Download) {
    val c = LocalFlow.current
    val m = FlowMetric
    val complete = task.status == "complete"
    val progress = if (complete) 1f else task.progress
    val ink = if (complete) c.statusComplete else c.downloadColor
    Canvas(Modifier.fillMaxWidth().height(m.progressHeight).progressSemantics(progress)) {
        val count = m.progressSegments
        val gap = minOf(m.progressGap.toPx(), size.width / (count * 2))
        val width = (size.width - gap * (count - 1)) / count
        val radius = CornerRadius(minOf(width, size.height) / 2)
        repeat(count) { index ->
            val x = index * (width + gap)
            val origin = Offset(x, 0f)
            val block = Size(width, size.height)
            drawRoundRect(c.progressTrack, origin, block, radius)
            val fraction = (progress * count - index).coerceIn(0f, 1f)
            if (fraction > 0)
                clipRect(left = x, right = x + width * fraction) {
                    drawRoundRect(ink, origin, block, radius)
                }
        }
    }
}
