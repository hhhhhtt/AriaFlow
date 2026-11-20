package dev.arialite

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.*

internal fun Modifier.flowGlass(haze: HazeState, c: FlowPalette) =
    hazeEffect(haze) {
        backgroundColor = c.canvas
        blurRadius = FlowMetric.topBlur
        tints = listOf(HazeTint(c.headerTint))
        fallbackTint = HazeTint(c.headerFallback)
    }

@Composable
fun FlowDock(tab: Int, select: (Int) -> Unit) {
    val tabs =
        listOf(
            "任务" to Icons.AutoMirrored.Outlined.FormatListBulleted,
            "添加" to Icons.Outlined.AddBox,
            "设置" to Icons.Outlined.Settings,
        )
    val c = LocalFlow.current
    val m = FlowMetric
    val shape = RoundedCornerShape(m.navRadius)
    BoxWithConstraints(
        Modifier.background(c.canvas)
            .navigationBarsPadding()
            .padding(start = m.page, end = m.page, bottom = m.navBottom)
            .fillMaxWidth()
            .height(m.navHeight)
            .shadow(
                m.s2,
                shape,
                clip = false,
                ambientColor = c.capsuleShadow,
                spotColor = c.capsuleShadow,
            )
            .clip(shape)
            .background(c.card)
            .border(m.headerDivider, c.navEdge, shape)
            .padding(m.s6)
    ) {
        val cell = maxWidth / tabs.size
        val offset by animateDpAsState(cell * tab, tween(m.navSlideMillis), label = "导航滑块")
        Box(
            Modifier.offset { IntOffset(offset.roundToPx(), 0) }
                .width(cell)
                .fillMaxHeight()
                .background(c.navSelectedFill, RoundedCornerShape(m.navSelectionRadius))
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            tabs.forEachIndexed { index, (name, icon) ->
                val selected = index == tab
                val ink = if (selected) c.accent else c.textSecondary
                Column(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .selectable(selected, role = Role.Tab, onClick = { select(index) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(m.s2, Alignment.CenterVertically),
                ) {
                    Icon(icon, null, Modifier.size(m.navIcon), tint = ink)
                    Text(name, style = FlowType.nav, color = ink)
                }
            }
        }
    }
}
