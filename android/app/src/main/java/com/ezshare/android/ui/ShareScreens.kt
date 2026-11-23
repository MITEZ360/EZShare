package com.ezshare.android.ui

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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezshare.android.server.ShareEntry
import com.ezshare.android.server.UiState

@Composable
fun FilesScreen(
    state: UiState,
    onPickFiles: () -> Unit,
    onPickFolder: () -> Unit,
    onMkdir: (String) -> Unit,
    onOpen: (ShareEntry) -> Unit,
    onDelete: (ShareEntry) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showMkdir by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { menuExpanded = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("添加文件") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { menuExpanded = false; onPickFiles() }
                    )
                    DropdownMenuItem(
                        text = { Text("导入文件夹") },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = { menuExpanded = false; onPickFolder() }
                    )
                    DropdownMenuItem(
                        text = { Text("新建文件夹") },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        onClick = { menuExpanded = false; folderName = ""; showMkdir = true }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.currentPath.isNotEmpty()) {
                        IconButton(onClick = { com.ezshare.android.server.ShareManager.browse("..") }) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回上一级",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.currentPath.isEmpty()) "共享目录" else state.currentPath,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${state.fileEntries.size} 个项目",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            if (state.fileEntries.isEmpty()) {
                item { EzEmptyState(Icons.Filled.Folder, "这里还是空的", "点击右下角添加文件") }
            } else {
                items(state.fileEntries, key = { it.relativePath }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpen(entry) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (entry.isDirectory) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Article,
                                contentDescription = null,
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            val subtitle = when {
                                entry.isDirectory -> "文件夹"
                                entry.modifiedLabel.isNotEmpty() -> formatSize(entry.size) + " · " + entry.modifiedLabel.take(16)
                                else -> formatSize(entry.size)
                            }
                            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.allowDelete) {
                            IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMkdir) {
        AlertDialog(
            onDismissRequest = { showMkdir = false },
            title = { Text("新建文件夹", style = MaterialTheme.typography.titleMedium) },
            text = {
                EzTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = "文件夹名称"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) onMkdir(folderName.trim())
                    showMkdir = false
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showMkdir = false }) { Text("取消") } }
        )
    }
}

@Composable
fun TextsScreen(
    state: UiState,
    onSend: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDeleteText: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 22)
                    Spacer(Modifier.width(8.dp))
                    Text("发送文本", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                EzTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "粘贴或输入要共享的文字…",
                    modifier = Modifier.height(92.dp),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EzButton(text = "发送到共享", onClick = {
                        onSend(draft)
                        draft = ""
                    }, compact = true)
                }
            }
        }
        item {
            SectionTitle(text = "共享文本（${state.snippets.size}）")
        }
        if (state.snippets.isEmpty()) {
            item { EzEmptyState(Icons.Filled.Article, "还没有共享文本", "在上方输入文字并发送") }
        } else {
            items(state.snippets, key = { it.id }) { snippet ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        snippet.content.replace('\n', ' ').take(200),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${snippet.content.length} 字符",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onCopy(snippet.content) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制", style = MaterialTheme.typography.labelMedium)
                        }
                        if (state.allowDelete) {
                            TextButton(onClick = { onDeleteText(snippet.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("删除", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
