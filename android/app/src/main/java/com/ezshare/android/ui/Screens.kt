package com.ezshare.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LaptopMac
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezshare.android.server.Peer
import com.ezshare.android.server.SessionMode
import com.ezshare.android.server.ShareManager
import com.ezshare.android.server.ShareService
import com.ezshare.android.server.ShareSettings
import com.ezshare.android.server.UiState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val state by ShareManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var joinTarget by remember { mutableStateOf<Peer?>(null) }
    var codeInput by remember { mutableStateOf("") }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var pendingFolder by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) ShareManager.setTreeUri(uri)
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = ShareManager.addFiles(uris)
                Toast.makeText(context, "已添加 $count 个文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && pendingFolder) {
            val count = ShareManager.addFolder(uri)
            Toast.makeText(context, "已添加 $count 个文件", Toast.LENGTH_SHORT).show()
        }
        pendingFolder = false
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            )
        }
        ShareManager.discovery.refresh()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            ShareManager.consumeMessage()
        }
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            )
        }
    }

    fun startLocal() {
        requestPermissions()
        if (!ShareManager.root.isValid) {
            Toast.makeText(context, "共享目录不可用，请在设置中重新选择", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, ShareService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "启动失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    if (state.mode == SessionMode.IDLE) {
        ConnectScreen(
            state = state,
            onJoin = { peer ->
                if (peer.requiresCode) {
                    joinTarget = peer
                    codeInput = ""
                } else {
                    scope.launch { ShareManager.joinRemote(peer, "") }
                }
            },
            onStartLocal = { startLocal() },
            onPickTree = { treePicker.launch(null) }
        )
    } else {
        MainShell(
            state = state,
            scope = scope,
            onStopLocal = {
                context.stopService(Intent(context, ShareService::class.java))
                ShareManager.stopLocal()
            },
            onLeaveRemote = { ShareManager.leaveRemote() },
            onShowQr = {
                qrUrl = state.addresses.firstOrNull()?.let { "http://$it:${state.port}" }
            },
            onPickFiles = { filesPicker.launch("*/*") },
            onPickFolder = {
                pendingFolder = true
                folderPicker.launch(null)
            },
            onMkdir = { name -> scope.launch { ShareManager.mkdir(name) } },
            onOpen = { entry ->
                if (entry.isDirectory) {
                    ShareManager.browse(entry.relativePath)
                } else {
                    ShareManager.openEntry(entry)?.let { openUrl(context, it) }
                }
            },
            onDelete = { entry -> scope.launch { ShareManager.deleteEntry(entry) } },
            onSend = { text ->
                scope.launch {
                    if (!ShareManager.addText(text)) Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show()
                }
            },
            onCopy = { text ->
                copy(context, text)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            },
            onDeleteText = { id -> scope.launch { ShareManager.deleteText(id) } },
            onJoin = { peer ->
                if (peer.requiresCode) {
                    joinTarget = peer
                    codeInput = ""
                } else {
                    scope.launch { ShareManager.joinRemote(peer, "") }
                }
            },
            onPickTree = { treePicker.launch(null) },
            onRegenerateCode = {
                ShareManager.regenerateCode()
                Toast.makeText(context, "已生成新访问码", Toast.LENGTH_SHORT).show()
            },
            onUpdateSettings = { block -> ShareManager.updateSettings(block) }
        )
    }

    val target = joinTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { joinTarget = null },
            title = { Text("加入 ${target.name}", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("对方启用了访问码，请输入后继续。", style = MaterialTheme.typography.bodySmall)
                    EzTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = "访问码"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = ShareManager.joinRemote(target, codeInput)
                            if (ok) joinTarget = null
                        }
                    },
                    enabled = !state.busy
                ) { Text(if (state.busy) "连接中…" else "加入") }
            },
            dismissButton = { TextButton(onClick = { joinTarget = null }) { Text("取消") } }
        )
    }

    qrUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { qrUrl = null },
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("扫码访问", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val bitmap = remember(url) { qrBitmap(url) }
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.size(190.dp))
                    } else {
                        Text("暂无可用地址", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(url, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { TextButton(onClick = { qrUrl = null }) { Text("关闭") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectScreen(
    state: UiState,
    onJoin: (Peer) -> Unit,
    onStartLocal: () -> Unit,
    onPickTree: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(size = 22)
                        Spacer(Modifier.width(8.dp))
                        Text("EZShare", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    RefreshIconButton(onClick = { ShareManager.discovery.refresh() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { BrandHeader() }
            item { SectionTitle(text = "局域网设备") }
            if (state.peers.isEmpty()) {
                item {
                    EzEmptyState(
                        icon = Icons.Outlined.Wifi,
                        title = "未发现其他设备",
                        subtitle = "确保对方已启动共享，且与本机连接同一 Wi-Fi"
                    )
                }
            } else {
                items(state.peers, key = { it.url }) { peer ->
                    PeerCard(peer = peer, onJoin = { onJoin(peer) })
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "或启动本机共享",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "选择一个共享目录，局域网内的设备就能发现并加入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EzButton(text = "启动本机服务", onClick = onStartLocal, compact = true)
                        EzButton(text = "选择目录", onClick = onPickTree, filled = false, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 24)
                Spacer(Modifier.width(8.dp))
                Text("局域网共享", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
            Column {
                Text("随时分享，轻松连接", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "发现同一网络内的共享，或直接创建自己的共享",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PeerCard(peer: Peer, onJoin: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (peer.name.contains("mac", ignoreCase = true)) Icons.Outlined.LaptopMac else Icons.Outlined.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(peer.url, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (peer.requiresCode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("需要访问码", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        EzButton(text = "加入", onClick = onJoin, compact = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: UiState,
    scope: kotlinx.coroutines.CoroutineScope,
    onStopLocal: () -> Unit,
    onLeaveRemote: () -> Unit,
    onShowQr: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onMkdir: (String) -> Unit,
    onOpen: (com.ezshare.android.server.ShareEntry) -> Unit,
    onDelete: (com.ezshare.android.server.ShareEntry) -> Unit,
    onSend: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDeleteText: (String) -> Unit,
    onJoin: (Peer) -> Unit,
    onPickTree: () -> Unit,
    onRegenerateCode: () -> Unit,
    onUpdateSettings: (ShareSettings.() -> Unit) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (state.mode) {
                                SessionMode.LOCAL -> "本机共享"
                                SessionMode.REMOTE -> "已连接 ${state.peer?.name ?: "设备"}"
                                else -> "EZShare"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.mode == SessionMode.LOCAL) {
                            Text("端口 ${state.port}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (state.mode == SessionMode.LOCAL) {
                        IconButton(onClick = onShowQr) { Icon(Icons.Outlined.QrCode, contentDescription = "二维码") }
                        StatusButton(
                            label = "共享中",
                            hint = "点击停止",
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            dot = MaterialTheme.colorScheme.primary,
                            onClick = onStopLocal
                        )
                    } else {
                        StatusButton(
                            label = "已连接",
                            hint = "点击断开",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            dot = MaterialTheme.colorScheme.secondary,
                            onClick = onLeaveRemote
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("文件", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.Article, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("文本", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("设备", style = MaterialTheme.typography.labelSmall) }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("设置", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = tab, animationSpec = tween(180), label = "tab") { target ->
                when (target) {
                    0 -> FilesScreen(state, onPickFiles, onPickFolder, onMkdir, onOpen, onDelete)
                    1 -> TextsScreen(state, onSend, onCopy, onDeleteText)
                    2 -> DevicesScreen(state, onJoin)
                    3 -> SettingsScreen(state, onPickTree, onRegenerateCode, onUpdateSettings, onStopLocal, onLeaveRemote)
                }
            }
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun StatusButton(
    label: String,
    hint: String,
    container: Color,
    content: Color,
    dot: Color,
    onClick: () -> Unit
) {
    val animContainer by animateColorAsState(container, tween(220), label = "statusContainer")
    val animContent by animateColorAsState(content, tween(220), label = "statusContent")
    val animDot by animateColorAsState(dot, tween(220), label = "statusDot")
    Button(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = animContainer, contentColor = animContent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp, vertical = 0.dp)
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(animDot))
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = animContent.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun DevicesScreen(state: UiState, onJoin: (Peer) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(text = "局域网设备", modifier = Modifier.weight(1f))
                RefreshIconButton(onClick = { ShareManager.discovery.refresh() }, modifier = Modifier.size(38.dp))
            }
        }
        if (state.peers.isEmpty()) {
            item { EzEmptyState(Icons.Outlined.Wifi, "未发现其他设备", "点击刷新按钮重新扫描") }
        } else {
            items(state.peers, key = { it.url }) { peer ->
                PeerCard(peer = peer, onJoin = { onJoin(peer) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: UiState,
    onPickTree: () -> Unit,
    onRegenerateCode: () -> Unit,
    onUpdateSettings: (ShareSettings.() -> Unit) -> Unit,
    onStopLocal: () -> Unit,
    onLeaveRemote: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SettingsCard(title = "连接状态") {
                Text("模式：${if (state.mode == SessionMode.LOCAL) "本机服务" else "加入 ${state.peer?.name ?: "其他设备"}"}",
                    style = MaterialTheme.typography.bodySmall)
                Text("共享名：${state.shareName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                EzButton(
                    text = if (state.mode == SessionMode.LOCAL) "停止本机服务" else "断开连接",
                    onClick = if (state.mode == SessionMode.LOCAL) onStopLocal else onLeaveRemote,
                    filled = false, compact = true
                )
            }
        }
        if (state.mode == SessionMode.LOCAL) {
            item {
                SettingsCard(title = "本机服务设置") {
                    Text("共享目录", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ShareManager.root.displayPath, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    EzButton(text = "更改共享目录", onClick = onPickTree, filled = false, compact = true)
                    EzTextField(
                        value = state.settings.port.toString(),
                        onValueChange = { value ->
                            onUpdateSettings { port = value.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080 }
                        },
                        label = "端口"
                    )
                    SwitchRow("允许上传 / 写入", state.settings.allowUpload) {
                        onUpdateSettings { allowUpload = it }
                    }
                    SwitchRow("允许删除", state.settings.allowDelete) {
                        onUpdateSettings { allowDelete = it }
                    }
                    SwitchRow("需要访问码", state.settings.requireCode) {
                        onUpdateSettings { requireCode = it }
                    }
                    EzTextField(
                        value = state.settings.accessCode,
                        onValueChange = { value -> onUpdateSettings { accessCode = value } },
                        label = "访问码（6 位字母数字）"
                    )
                    EzButton(text = "随机生成访问码", onClick = onRegenerateCode, filled = false, compact = true)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun copy(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("EZShare", text))
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

private fun qrBitmap(text: String): Bitmap? {
    if (text.isEmpty()) return null
    return try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565)
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
