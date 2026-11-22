package com.ezshare.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezshare.android.server.Peer
import com.ezshare.android.server.ShareManager
import com.ezshare.android.server.ShareService
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShareManager.init(applicationContext)
        setContent {
            MaterialTheme {
                EZShareScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EZShareScreen() {
    val context = LocalContext.current
    val state by ShareManager.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) ShareManager.setTreeUri(uri)
    }
    val filesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val n = ShareManager.addFiles(uris)
            toast(context, "已添加 $n 个文件")
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val n = ShareManager.addFolder(uri)
            toast(context, "已添加 $n 个文件")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    LaunchedEffect(Unit) {
        ShareManager.discovery.refresh()
        while (true) {
            delay(2000)
            ShareManager.refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EZShare", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = {
                        if (state.running) {
                            context.stopService(Intent(context, ShareService::class.java))
                            ShareManager.stop()
                        } else {
                            if (Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                        Manifest.permission.NEARBY_WIFI_DEVICES
                                    )
                                )
                            }
                            if (!ShareManager.root.isValid) {
                                toast(context, "请先选择共享目录")
                            } else {
                                val intent = Intent(context, ShareService::class.java)
                                try {
                                    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                                    else context.startService(intent)
                                } catch (e: Exception) {
                                    toast(context, "启动失败：${e.message}")
                                }
                            }
                        }
                    }) {
                        Text(if (state.running) "停止共享" else "启动共享")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (state.running) "正在共享" else "共享已停止", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("共享目录：${state.shareName}", style = MaterialTheme.typography.bodySmall)
                        Text("端口：${state.port}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { treePicker.launch(null) }, Modifier.padding(top = 8.dp)) {
                            Text("更改共享目录")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("访问地址", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showQr = true }) { Text("二维码") }
                        }
                        if (state.addresses.isEmpty()) {
                            Text("未检测到局域网地址", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.addresses.forEach { ip ->
                                val url = "http://$ip:${state.port}"
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(url, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = {
                                        copy(context, url)
                                        toast(context, "地址已复制")
                                    }) { Text("复制") }
                                    TextButton(onClick = { openUrl(context, url) }) { Text("打开") }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("文件") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("文本") })
                }
            }

            if (tab == 0) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("共享文件", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { folderPicker.launch(null) }) { Text("添加文件夹") }
                                TextButton(onClick = { filesPicker.launch("*/*") }) { Text("添加文件") }
                            }
                            if (ShareManager.currentPath.isNotEmpty()) {
                                TextButton(onClick = { ShareManager.browse("..") }) { Text("← 返回上一级") }
                            }
                            if (state.fileEntries.isEmpty()) {
                                Text("目录为空", style = MaterialTheme.typography.bodySmall)
                            } else {
                                state.fileEntries.forEach { entry ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable {
                                                if (entry.isDirectory) ShareManager.browse(entry.relativePath)
                                                else ShareManager.openSharedFile(entry)
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(if (entry.isDirectory) "▸" else "·")
                                        Spacer(Modifier.size(8.dp))
                                        Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if (entry.isDirectory) "" else formatSize(entry.size),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("发送文本", fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                                placeholder = { Text("粘贴或输入要共享的文本…") }
                            )
                            Row {
                                Spacer(Modifier.weight(1f))
                                Button(onClick = {
                                    if (ShareManager.addText(draft)) {
                                        draft = ""
                                        toast(context, "已发送")
                                    } else {
                                        toast(context, "内容不能为空")
                                    }
                                }) { Text("发送到共享") }
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("共享文本（${state.snippets.size}）", fontWeight = FontWeight.Bold)
                            if (state.snippets.isEmpty()) {
                                Text("还没有文本", style = MaterialTheme.typography.bodySmall)
                            } else {
                                state.snippets.forEach { item ->
                                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                        Text(
                                            item.content.replace('\n', ' ').take(160),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Row {
                                            Text(
                                                "${item.content.length} 字符",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = {
                                                copy(context, item.content)
                                                toast(context, "文本已复制")
                                            }) { Text("复制") }
                                            if (state.settings.allowDelete) {
                                                TextButton(onClick = { ShareManager.deleteText(item.id) }) { Text("删除") }
                                            }
                                        }
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("局域网设备", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { ShareManager.discovery.refresh() }) { Text("刷新") }
                        }
                        if (state.peers.isEmpty()) {
                            Text("未发现其他设备", style = MaterialTheme.typography.bodySmall)
                        } else {
                            state.peers.forEach { peer -> PeerRow(peer, context) }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("设置", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.settings.port.toString(),
                            onValueChange = { value ->
                                ShareManager.updateSettings {
                                    port = value.toIntOrNull()?.coerceIn(1024, 65535) ?: 8080
                                }
                            },
                            label = { Text("端口") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        SwitchRow("允许上传 / 写入", state.settings.allowUpload) {
                            ShareManager.updateSettings { allowUpload = it }
                        }
                        SwitchRow("允许删除", state.settings.allowDelete) {
                            ShareManager.updateSettings { allowDelete = it }
                        }
                        SwitchRow("需要访问码", state.settings.requireCode) {
                            ShareManager.updateSettings { requireCode = it }
                        }
                        OutlinedTextField(
                            value = state.settings.accessCode,
                            onValueChange = { value -> ShareManager.updateSettings { accessCode = value } },
                            label = { Text("访问码（6 位字母数字）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = {
                            ShareManager.regenerateCode()
                            toast(context, "已生成新访问码")
                        }) { Text("随机生成访问码") }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showQr) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("扫码访问") },
            text = {
                val url = state.addresses.firstOrNull()?.let { "http://$it:${state.port}" } ?: ""
                val bitmap = remember(url) { qrBitmap(url) }
                if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), contentDescription = "二维码", modifier = Modifier.size(220.dp))
                } else {
                    Text("暂无可用地址")
                }
                Text(url, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PeerRow(peer: Peer, context: Context) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(peer.name, style = MaterialTheme.typography.bodyMedium)
            Text(peer.url, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { openUrl(context, peer.url) }) { Text("打开") }
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

private fun qrBitmap(text: String): Bitmap? {
    if (text.isEmpty()) return null
    return try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565)
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
