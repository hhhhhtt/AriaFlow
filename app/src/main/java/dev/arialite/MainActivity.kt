package dev.arialite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.*
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AriaApp() }
    }
}

fun bytes(value: Long): String {
    val v = value.coerceAtLeast(0).toDouble()
    return when {
        v >= 1073741824 -> String.format(Locale.US, "%.1f GB", v / 1073741824)
        v >= 1048576 -> String.format(Locale.US, "%.1f MB", v / 1048576)
        v >= 1024 -> String.format(Locale.US, "%.0f KB", v / 1024)
        else -> "${v.toLong()} B"
    }
}

@Composable
fun AriaApp(model: AppModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark =
        when (state.theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val activity = LocalActivity.current as? ComponentActivity
    SideEffect {
        val bars =
            if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
        activity?.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                model.refresh()
                delay(2000)
            }
        }
    }
    val palette = remember(dark) { FlowPalette(dark) }
    val scheme =
        remember(palette) {
            (if (dark) darkColorScheme() else lightColorScheme()).copy(
                primary = palette.accent,
                onPrimary = palette.onAction,
                background = palette.canvas,
                onBackground = palette.textPrimary,
                surface = palette.card,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.fieldFill,
                onSurfaceVariant = palette.textSecondary,
                secondaryContainer = palette.taskControlFill,
                onSecondaryContainer = palette.textPrimary,
            )
        }
    MaterialTheme(
        colorScheme = scheme,
        shapes =
            Shapes(
                medium = RoundedCornerShape(FlowMetric.noticeRadius),
                large = RoundedCornerShape(FlowMetric.navRadius),
            ),
    ) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var removing by remember { mutableStateOf<Download?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(model, context) {
            model.playbackErrors.collect { message ->
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                    .show()
            }
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let {
                if (it == "任务已更新") delay(FlowMetric.noticeMillis) else snack.showSnackbar(it)
                model.clearMessage()
            }
        }
        val haze = remember { HazeState() }
        CompositionLocalProvider(LocalFlow provides palette) {
            val pages = rememberSaveableStateHolder()
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(palette.canvas))
                Scaffold(
                    containerColor = palette.canvas,
                    contentColor = palette.textPrimary,
                    snackbarHost = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            AnimatedVisibility(
                                state.message == "任务已更新",
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Text(
                                    "任务已更新",
                                    Modifier.padding(bottom = FlowMetric.s12)
                                        .background(
                                            palette.noticeFill,
                                            RoundedCornerShape(FlowMetric.noticeRadius),
                                        )
                                        .padding(
                                            horizontal = FlowMetric.s16,
                                            vertical = FlowMetric.s8,
                                        )
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    color = palette.noticeText,
                                    style = FlowType.segment,
                                )
                            }
                            SnackbarHost(snack)
                        }
                    },
                    bottomBar = { FlowDock(tab) { tab = it } },
                ) { padding ->
                    Box(
                        Modifier.fillMaxSize()
                            .padding(bottom = padding.calculateBottomPadding())
                            .consumeWindowInsets(
                                PaddingValues(bottom = padding.calculateBottomPadding())
                            )
                            .clipToBounds()
                    ) {
                        AnimatedContent(
                            modifier =
                                Modifier.fillMaxSize().clipToBounds().background(palette.canvas),
                            targetState = tab,
                            transitionSpec = {
                                val direction = if (targetState > initialState) 1 else -1
                                (fadeIn(tween(230)) +
                                        slideInHorizontally(tween(270)) { direction * it / 14 })
                                    .togetherWith(
                                        fadeOut(tween(150)) +
                                            slideOutHorizontally(tween(210)) {
                                                -direction * it / 18
                                            }
                                    )
                            },
                            label = "页面切换",
                        ) { page ->
                            pages.SaveableStateProvider(page) {
                                when (page) {
                                    0 ->
                                        Downloads(
                                            haze,
                                            state,
                                            Modifier.padding(
                                                start =
                                                    padding.calculateStartPadding(
                                                        LocalLayoutDirection.current
                                                    ),
                                                end =
                                                    padding.calculateEndPadding(
                                                        LocalLayoutDirection.current
                                                    ),
                                            ),
                                            padding.calculateTopPadding(),
                                            { tab = 2 },
                                            model::toggle,
                                            { removing = it },
                                            model::pauseAll,
                                            model::resumeAll,
                                        )
                                    1 -> AddScreen(state, model, padding, { tab = 2 })
                                    else -> Settings(state, model, padding)
                                }
                            }
                        }
                    }
                }
            }
            removing?.let { task ->
                AlertDialog(
                    onDismissRequest = { removing = null },
                    title = { Text("移除任务？") },
                    text = { Text("${task.name}\n\n停止下载并移除记录。NAS 上已下载的文件会保留。") },
                    confirmButton = {
                        SecondaryButton(
                            onClick = {
                                model.remove(task)
                                removing = null
                            },
                            enabled = !state.busy,
                        ) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        SecondaryButton(onClick = { removing = null }) { Text("取消") }
                    },
                )
            }
        }
    }
}

@Composable
private fun Downloads(
    haze: HazeState,
    state: AppState,
    modifier: Modifier,
    safeTop: androidx.compose.ui.unit.Dp,
    settings: () -> Unit,
    toggle: (Download) -> Unit,
    remove: (Download) -> Unit,
    pauseAll: () -> Unit,
    resumeAll: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(TaskGroup.ACTIVE) }
    var sort by rememberSaveable { mutableStateOf(TaskSort.CREATED) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var sorting by remember { mutableStateOf(false) }
    var bulkMenu by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val filtered =
        sortedTasks(
            state.tasks.filter { group.includes(it) && it.name.contains(search, true) },
            sort,
            descending,
        )
    val c = LocalFlow.current
    val m = FlowMetric
    val pill = androidx.compose.foundation.shape.CircleShape
    val scroll = rememberLazyListState()
    val scrolled by remember { derivedStateOf { scroll.canScrollBackward } }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze).background(c.canvas),
            state = scroll,
            contentPadding =
                PaddingValues(
                    start = m.page,
                    end = m.page,
                    top = safeTop + m.s8 + m.topHeight + m.s8,
                    bottom = m.s12,
                ),
            verticalArrangement = Arrangement.spacedBy(m.cardGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(m.s8)) {
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = FlowType.search.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = m.searchHeight)
                                .clip(pill)
                                .background(c.searchFill)
                                .semantics { contentDescription = "搜索任务" },
                        decorationBox = { inner ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = m.searchInset),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    null,
                                    Modifier.size(m.searchIcon),
                                    tint = c.textPrimary,
                                )
                                Spacer(Modifier.width(m.s8))
                                Box {
                                    if (search.isEmpty())
                                        Text("搜索", style = FlowType.search, color = c.placeholder)
                                    inner()
                                }
                            }
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(pill)
                            .background(c.segmentTrack)
                            .padding(m.s2)
                            .selectableGroup()
                    ) {
                        TaskGroup.entries.forEach { entry ->
                            val active = entry == group
                            val label = if (entry == TaskGroup.ACTIVE) "活跃" else entry.label
                            Box(
                                Modifier.weight(1f)
                                    .heightIn(min = m.segmentHeight - m.s2 * 2)
                                    .then(
                                        if (active)
                                            Modifier.segmentShade(
                                                    c.segmentShadow,
                                                    c.segmentSelected,
                                                )
                                                .background(c.segmentSelected, pill)
                                        else Modifier
                                    )
                                    .clip(pill)
                                    .selectable(active, role = Role.Tab) { group = entry },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$label (${state.tasks.count { entry.includes(it) }})",
                                    color = c.textPrimary,
                                    style =
                                        if (active) FlowType.segmentSelected else FlowType.segment,
                                )
                            }
                        }
                    }
                }
            }
            item { SpeedChart(state) }
            state.error?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    SecondaryButton(onClick = settings) { Text("检查连接设置") }
                }
            }
            if (search.isNotBlank() && filtered.isEmpty()) item { Text("没有匹配的任务") }
            items(filtered, key = { it.gid }) { task ->
                Box(Modifier.animateItem()) {
                    TaskCard(
                        task,
                        if (state.urls.size > 1)
                            state.connection.name.ifBlank {
                                state.connection.url.toUri().host ?: "NAS"
                            }
                        else null,
                        state.busy || !state.connected,
                        state.pendingPlayback == PlaybackTarget.Task(task.gid),
                        { toggle(task) },
                        { remove(task) },
                    )
                }
            }
            if (group == TaskGroup.STOPPED && filtered.isEmpty() && search.isBlank())
                item {
                    Text(
                        "已停止包含完成、失败和移除的任务",
                        modifier = Modifier.fillMaxWidth().padding(vertical = m.s24),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = FlowType.metadata,
                        color = LocalFlow.current.textSecondary,
                    )
                }
        }
        Column(
            Modifier.fillMaxWidth().then(if (scrolled) Modifier.flowGlass(haze, c) else Modifier)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = m.page, end = m.page, top = safeTop + m.s8)
                    .height(m.topHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "全部任务",
                    Modifier.padding(horizontal = m.s8),
                    style = FlowType.title,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.align(Alignment.CenterEnd)
                        .size(m.capsuleWidth, m.capsuleHeight)
                        .shadow(
                            m.s2,
                            pill,
                            clip = false,
                            ambientColor = c.capsuleShadow,
                            spotColor = c.capsuleShadow,
                        )
                        .clip(pill)
                        .background(c.glassTint)
                        .border(m.highlight, c.glassEdge, pill)
                ) {
                    Box {
                        IconButton(
                            modifier = Modifier.size(m.capsuleWidth / 2, m.capsuleHeight),
                            onClick = { bulkMenu = true },
                            enabled = state.connected && !state.busy,
                        ) {
                            if (state.pendingPlayback == PlaybackTarget.All) PlaybackSpinner()
                            else
                                HeaderMark(
                                    false,
                                    if (state.connected && !state.busy) c.iconInk
                                    else c.unavailableIcon,
                                )
                        }
                        DropdownMenu(expanded = bulkMenu, onDismissRequest = { bulkMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("全部暂停") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.PauseCircleOutline,
                                        null,
                                        tint =
                                            if (
                                                state.tasks.any {
                                                    it.status == "active" || it.status == "waiting"
                                                }
                                            )
                                                c.iconInk
                                            else c.unavailableIcon,
                                    )
                                },
                                onClick = {
                                    bulkMenu = false
                                    pauseAll()
                                },
                                enabled =
                                    state.tasks.any {
                                        it.status == "active" || it.status == "waiting"
                                    },
                            )
                            DropdownMenuItem(
                                text = { Text("全部开始") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.PlayCircleOutline,
                                        null,
                                        tint =
                                            if (state.tasks.any { it.status == "paused" }) c.iconInk
                                            else c.unavailableIcon,
                                    )
                                },
                                onClick = {
                                    bulkMenu = false
                                    resumeAll()
                                },
                                enabled = state.tasks.any { it.status == "paused" },
                            )
                        }
                    }
                    Box {
                        IconButton(
                            modifier = Modifier.size(m.capsuleWidth / 2, m.capsuleHeight),
                            onClick = { sorting = true },
                        ) {
                            HeaderMark(true, c.iconInk)
                        }
                        DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                            TaskSort.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Text((if (sort == entry) "✓  " else "") + entry.label)
                                    },
                                    onClick = {
                                        sort = entry
                                        sorting = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (descending) "↓ 降序 · 点击切换升序" else "↑ 升序 · 点击切换降序")
                                },
                                onClick = {
                                    descending = !descending
                                    sorting = false
                                },
                            )
                            Text(
                                "已有任务按首次发现时间替代创建时间",
                                Modifier.width(m.menuWidth).padding(m.s12),
                                style = FlowType.metadata,
                                color = LocalFlow.current.textSecondary,
                            )
                        }
                    }
                }
            }
            if (scrolled) HorizontalDivider(thickness = m.headerDivider, color = c.grid)
        }
    }
}

@Composable
private fun FormPage(
    title: String,
    insets: PaddingValues,
    action: @Composable () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val m = FlowMetric
    val c = LocalFlow.current
    val direction = LocalLayoutDirection.current
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .padding(
                start = insets.calculateStartPadding(direction),
                end = insets.calculateEndPadding(direction),
                top = insets.calculateTopPadding(),
            )
    ) {
        Box(
            Modifier.fillMaxWidth()
                .padding(start = m.page, end = m.page, top = m.s8)
                .height(m.topHeight)
        ) {
            Text(
                title,
                Modifier.align(Alignment.Center),
                style = FlowType.title,
                color = c.textPrimary,
            )
            Box(Modifier.align(Alignment.CenterEnd)) { action() }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            contentPadding =
                PaddingValues(start = m.page, end = m.page, top = m.s12, bottom = m.s12),
            verticalArrangement = Arrangement.spacedBy(m.s16),
            content = content,
        )
    }
}

@Composable
private fun formFieldColors(): TextFieldColors {
    val c = LocalFlow.current
    return TextFieldDefaults.colors(
        focusedContainerColor = c.fieldFill,
        unfocusedContainerColor = c.fieldFill,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSecondary,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
}

@Composable
private fun AddScreen(
    state: AppState,
    model: AppModel,
    insets: PaddingValues,
    settings: () -> Unit,
) {
    var link by rememberSaveable { mutableStateOf("") }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::torrent)
        }
    val c = LocalFlow.current
    FormPage(
        "添加任务",
        insets,
        action = {
            val enabled = state.connected && !state.busy
            IconButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = enabled) {
                Icon(
                    Icons.Outlined.CreateNewFolder,
                    "导入 BT 种子",
                    tint = if (enabled) c.iconInk else c.unavailableIcon,
                )
            }
        },
    ) {
        item {
            SettingsCard("下载链接") {
                FieldWithHint("一次添加一个链接，或通过右上角导入 .torrent 文件。") {
                    TextField(
                        link,
                        { link = it },
                        Modifier.fillMaxWidth().heightIn(min = FlowMetric.linkInputMin),
                        shape = RoundedCornerShape(FlowMetric.fileRadius),
                        colors = formFieldColors(),
                        placeholder = { Text("粘贴 HTTP、HTTPS 或 magnet 链接") },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }
        }
        item {
            SettingsCard("目标服务器") {
                Text(
                    state.connection.name.ifBlank { state.connection.url.ifBlank { "尚未连接 NAS" } },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SecondaryButton(onClick = settings) { Text("连接设置") }
            }
        }
        item {
            SettingsCard("操作") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FlowMetric.s12),
                ) {
                    SecondaryButton(
                        onClick = { link = "" },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("清空输入")
                    }
                    PrimaryButton(
                        onClick = { model.addLink(link) },
                        enabled = state.connected && !state.busy && link.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.busy) "正在添加…" else "添加下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun Settings(state: AppState, model: AppModel, insets: PaddingValues) {
    var name by
        rememberSaveable(state.connection.url, state.connection.name) {
            mutableStateOf(state.connection.name)
        }
    var url by rememberSaveable(state.connection.url) { mutableStateOf(state.connection.url) }
    var secret by
        remember(state.connection.url, state.connection.secret) {
            mutableStateOf(state.connection.secret)
        }
    var speed by rememberSaveable(state.limit) { mutableStateOf(state.limit) }
    var count by rememberSaveable(state.concurrent) { mutableStateOf(state.concurrent) }
    FormPage("设置", insets) {
        item {
            SettingsCard("下载器") {
                state.urls.forEach { endpoint ->
                    SecondaryButton(
                        onClick = { model.select(endpoint) },
                        enabled =
                            !state.busy && (endpoint != state.connection.url || !state.connected),
                    ) {
                        Text(
                            (if (endpoint == state.connection.url) "✓  " else "") +
                                state.names[endpoint].orEmpty().ifBlank { endpoint }
                        )
                    }
                }
                if (state.urls.isNotEmpty()) {
                    SecondaryButton(
                        onClick = {
                            name = ""
                            url = ""
                            secret = ""
                        },
                        enabled = !state.busy,
                    ) {
                        Text("＋ 添加下载器")
                    }
                    HorizontalDivider()
                }
                TextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FlowMetric.fileRadius),
                    colors = formFieldColors(),
                    label = { Text("服务器名称") },
                    placeholder = { Text("例如：家用 NAS") },
                    singleLine = true,
                )
                TextField(
                    url,
                    { url = it },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FlowMetric.fileRadius),
                    colors = formFieldColors(),
                    label = { Text("JSON-RPC 地址") },
                    placeholder = { Text("http://NAS-IP:6800/jsonrpc") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                SecondaryButton(onClick = { url = "http://192.168.1.100:6800/jsonrpc" }) {
                    Text("填入示例")
                }
                FieldWithHint(
                    "Secret 使用 Android Keystore 加密保存在本机。HTTP 适用于可信局域网；远程连接建议使用 HTTPS 或 VPN。"
                ) {
                    TextField(
                        secret,
                        { secret = it },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FlowMetric.fileRadius),
                        colors = formFieldColors(),
                        label = { Text("RPC secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(FlowMetric.s12)) {
                    SecondaryButton(
                        onClick = { model.testConnection(url, secret) },
                        enabled = !state.busy && url.isNotBlank(),
                    ) {
                        Text("测试连接")
                    }
                    PrimaryButton(
                        onClick = { model.saveConnection(name, url, secret) },
                        enabled = !state.busy && url.isNotBlank(),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
        item {
            SettingsCard("下载偏好") {
                FieldWithHint("0 为不限速；500K / 10M，单位为字节每秒") {
                    TextField(
                        speed,
                        { speed = it },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(FlowMetric.fileRadius),
                        colors = formFieldColors(),
                        label = { Text("全局下载限速") },
                        singleLine = true,
                    )
                }
                TextField(
                    count,
                    { count = it },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FlowMetric.fileRadius),
                    colors = formFieldColors(),
                    label = { Text("最大同时下载任务数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(FlowMetric.s12)) {
                    SecondaryButton(
                        onClick = model::loadOptions,
                        enabled = !state.busy && state.connected,
                    ) {
                        Text("读取当前值")
                    }
                    PrimaryButton(
                        onClick = { model.limits(speed, count) },
                        enabled = !state.busy && state.connected,
                    ) {
                        Text("应用到 NAS")
                    }
                }
                Text(
                    "修改立即生效。aria2 重启后是否保留，取决于 NAS 的配置。",
                    style = FlowType.metadata,
                    color = LocalFlow.current.textSecondary,
                )
            }
        }
        item {
            SettingsCard("外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(FlowMetric.s8)) {
                    listOf("system" to "自动", "light" to "浅色", "dark" to "深色").forEach {
                        (value, label) ->
                        FilterChip(
                            selected = state.theme == value,
                            onClick = { model.theme(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        item { SettingsCard("App 图标") { IconChoices() } }
    }
}

@Composable
private fun FieldWithHint(hint: String, field: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowMetric.s6)) {
        field()
        Text(hint, style = FlowType.metadata, color = LocalFlow.current.textSecondary)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalFlow.current
    val m = FlowMetric
    Column(verticalArrangement = Arrangement.spacedBy(m.s6)) {
        Text(
            title,
            Modifier.padding(start = m.s4),
            style = FlowType.section,
            color = c.textSecondary,
        )
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(m.cardRadius),
            color = c.card,
            contentColor = c.textPrimary,
        ) {
            Column(
                Modifier.padding(m.s16),
                verticalArrangement = Arrangement.spacedBy(m.s12),
                content = content,
            )
        }
    }
}

@Composable
private fun HeaderMark(sort: Boolean, ink: Color) {
    val m = FlowMetric
    val label = if (sort) "任务排序" else "全部暂停或开始"
    Canvas(Modifier.size(m.icon).semantics { contentDescription = label }) {
        val unit = size.width / 24f
        val stroke = m.iconStroke.toPx()
        fun line(x: Int, y: Int, xx: Int, yy: Int) =
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(x * unit, y * unit),
                androidx.compose.ui.geometry.Offset(xx * unit, yy * unit),
                stroke,
                androidx.compose.ui.graphics.StrokeCap.Round,
            )
        if (sort) {
            drawRoundRect(
                ink,
                topLeft = androidx.compose.ui.geometry.Offset(unit, unit),
                size = androidx.compose.ui.geometry.Size(22 * unit, 22 * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3 * unit),
                style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
            )
            line(8, 17, 8, 7)
            line(5, 10, 8, 7)
            line(8, 7, 11, 10)
            line(16, 7, 16, 17)
            line(13, 14, 16, 17)
            line(16, 17, 19, 14)
        } else {
            line(1, 4, 1, 20)
            line(1, 4, 13, 12)
            line(13, 12, 1, 20)
            line(17, 4, 17, 20)
            line(23, 4, 23, 20)
        }
    }
}
