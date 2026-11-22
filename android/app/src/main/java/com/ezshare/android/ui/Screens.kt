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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
            Toast.makeText(context, "请先选择共享目录", Toast.LENGTH_SHORT).show()
            treePicker.launch(null)
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
                ShareManager.openEntry(entry)?.let { openUrl(context, it) }
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
            title = { Text("加入 ${target.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("对方启用了访问码，请输入后继续。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it },
                        label = { Text("访问码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
            title = { Text("扫码访问") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val bitmap = remember(url) { qrBitmap(url) }
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.size(220.dp))
                    } else {
                        Text("暂无可用地址", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(url, style = MaterialTheme.typography.bodySmall)
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
        topBar = {
            TopAppBar(
                title = { Text("EZShare", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { ShareManager.discovery.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新发现")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { BrandHeader() }
            item {
                Text("局域网设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (state.peers.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Wifi,
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "或启动本机共享",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "选择一个共享目录，局域网内的设备就能发现并加入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStartLocal) { Text("启动本机服务") }
                            OutlinedButton(onClick = onPickTree) { Text("选择目录") }
                        }
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
            .height(158.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF2563EB))))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Wifi, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("局域网共享", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Column {
                Text("随时分享，轻松连接", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "发现同一网络内的共享，或直接创建自己的共享",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PeerCard(peer: Peer, onJoin: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.name.contains("mac", ignoreCase = true)) Icons.Filled.LaptopMac else Icons.Filled.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, fontWeight = FontWeight.SemiBold)
                Text(peer.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (peer.requiresCode) {
                    Text("需要访问码", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Button(onClick = onJoin) { Text("加入") }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            fontWeight = FontWeight.Bold
                        )
                        if (state.mode == SessionMode.LOCAL) {
                            Text("端口 ${state.port}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    if (state.mode == SessionMode.LOCAL) {
                        IconButton(onClick = onShowQr) { Icon(Icons.Filled.QrCode, contentDescription = "二维码") }
                        TextButton(onClick = onStopLocal) { Text("停止") }
                    } else {
                        TextButton(onClick = onLeaveRemote) { Text("断开") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    label = { Text("文件") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Article, contentDescription = null) },
                    label = { Text("文本") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text("设备") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> FilesScreen(state, onPickFiles, onPickFolder, onMkdir, onOpen, onDelete)
                1 -> TextsScreen(state, onSend, onCopy, onDeleteText)
                2 -> DevicesScreen(state, onJoin)
                3 -> SettingsScreen(state, onPickTree, onRegenerateCode, onUpdateSettings, onStopLocal, onLeaveRemote)
            }
            if (state.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun DevicesScreen(state: UiState, onJoin: (Peer) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("局域网设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ShareManager.discovery.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (state.peers.isEmpty()) {
            item { EmptyState(Icons.Filled.Wifi, "未发现其他设备", "下拉后可点击刷新按钮重新扫描") }
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
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("连接状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("模式：${if (state.mode == SessionMode.LOCAL) "本机服务" else "加入 ${state.peer?.name ?: "其他设备"}"}")
                    Text("共享名：${state.shareName}", style = MaterialTheme.typography.bodySmall)
                    if (state.mode == SessionMode.LOCAL) {
                        OutlinedButton(onClick = onStopLocal) { Text("停止本机服务") }
                    } else {
                        OutlinedButton(onClick = onLeaveRemote) { Text("断开连接") }
                    }
                }
            }
        }
        if (state.mode == SessionMode.LOCAL) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("本机服务设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedButton(onClick = onPickTree) { Text("更改共享目录") }
                        OutlinedTextField(
                            value = state.settings.port.toString(),
                            onValueChange = { value ->
                                onUpdateSettings { port = value.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080 }
                            },
                            label = { Text("端口") },
                            modifier = Modifier.fillMaxWidth()
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
                        OutlinedTextField(
                            value = state.settings.accessCode,
                            onValueChange = { value -> onUpdateSettings { accessCode = value } },
                            label = { Text("访问码（6 位字母数字）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = onRegenerateCode) { Text("随机生成访问码") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
