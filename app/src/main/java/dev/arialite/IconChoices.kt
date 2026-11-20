package dev.arialite

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role

private enum class Launcher(val title: String, val image: Int) {
    Ocean("海洋蓝", R.drawable.art_ocean),
    Mint("薄荷", R.drawable.art_mint),
    Forest("森林", R.drawable.art_forest),
    Sand("沙色", R.drawable.art_sand),
    Coral("珊瑚", R.drawable.art_coral),
    Graphite("石墨", R.drawable.art_graphite);

    fun component(pkg: String) = ComponentName(pkg, "$pkg.Launcher$name")
}

@Composable
internal fun IconChoices() {
    val context = LocalContext.current
    val manager = context.packageManager
    val pkg = context.packageName
    var selected by remember {
        mutableStateOf(
            Launcher.entries.firstOrNull {
                manager.getComponentEnabledSetting(it.component(pkg)) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Launcher.Ocean
        )
    }
    var status by remember { mutableStateOf("") }
    fun choose(icon: Launcher) {
        if (selected == icon) return
        val flags = PackageManager.DONT_KILL_APP
        val enable = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disable = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            val ordered = listOf(icon) + Launcher.entries.filter { it != icon }
            if (Build.VERSION.SDK_INT >= 33) {
                manager.setComponentEnabledSettings(
                    ordered.map {
                        PackageManager.ComponentEnabledSetting(
                            it.component(pkg),
                            if (it == icon) enable else disable,
                            flags,
                        )
                    }
                )
            } else {
                ordered.forEach {
                    manager.setComponentEnabledSetting(
                        it.component(pkg),
                        if (it == icon) enable else disable,
                        flags,
                    )
                }
            }
            selected = icon
            status = "已选择" + icon.title + "，桌面可能需要片刻刷新。"
        } catch (e: SecurityException) {
            status = "系统未能切换图标，请重新选择。"
        }
    }
    Column(Modifier.selectableGroup()) {
        Launcher.entries.chunked(3).forEach { icons ->
            Row(Modifier.fillMaxWidth()) {
                icons.forEach { icon ->
                    Column(
                        Modifier.weight(1f)
                            .selectable(selected == icon, role = Role.RadioButton) { choose(icon) }
                            .padding(vertical = FlowMetric.launcherItemPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painterResource(icon.image),
                            null,
                            Modifier.size(FlowMetric.launcherPreview)
                                .clip(RoundedCornerShape(FlowMetric.launcherRadius)),
                        )
                        Text(
                            (if (selected == icon) "✓ " else "") + icon.title,
                            style = FlowType.section,
                            color = LocalFlow.current.textPrimary,
                        )
                    }
                }
            }
        }
        if (status.isNotEmpty())
            Text(status, style = FlowType.metadata, color = LocalFlow.current.textSecondary)
    }
}
