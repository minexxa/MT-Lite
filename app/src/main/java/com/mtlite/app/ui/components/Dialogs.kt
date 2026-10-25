package com.mtlite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.ConflictResolution
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.SortBy
import com.mtlite.app.util.ApkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JumpPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转路径") },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("路径") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path.trim()) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    clipboard.getText()?.text?.let { path = it }
                }) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("粘贴")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isDir: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") }
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), false) },
                    enabled = name.isNotBlank()
                ) { Text("文件") }
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), true) },
                    enabled = name.isNotBlank()
                ) { Text("文件夹") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    useRecycleBin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = {
            Text(
                if (useRecycleBin) "确定要将选中的 $count 个项目移入回收站吗？"
                else "确定要永久删除选中的 $count 个项目吗？此操作不可恢复。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (useRecycleBin) "移入回收站" else "删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun SortDialog(
    currentSort: SortBy,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SortBy, Boolean) -> Unit
) {
    var sort by remember { mutableStateOf(currentSort) }
    var asc by remember { mutableStateOf(ascending) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序方式") },
        text = {
            Column {
                listOf(
                    SortBy.NAME to "名称",
                    SortBy.SIZE to "大小",
                    SortBy.DATE to "修改时间",
                    SortBy.TYPE to "类型"
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(selected = sort == value, onClick = { sort = value })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = asc, onClick = { asc = true }, label = { Text("升序") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !asc, onClick = { asc = false }, label = { Text("降序") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sort, asc) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun RenameDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新名称") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != item.name
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun SearchDialog(
    initialQuery: String,
    initialContent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var content by remember { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索文件") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("关键字") }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = content, onCheckedChange = { content = it })
                    Text("同时搜索文件内容（文本文件）")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(query.trim(), content) },
                enabled = query.isNotBlank()
            ) { Text("搜索") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ConflictDialog(
    name: String,
    onResolve: (ConflictResolution) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onResolve(ConflictResolution.SKIP) },
        title = { Text("目标已存在") },
        text = { Text("“$name” 已存在，如何处理？") },
        confirmButton = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onResolve(ConflictResolution.OVERWRITE) }) { Text("覆盖") }
                    TextButton(onClick = { onResolve(ConflictResolution.SKIP) }) { Text("跳过") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onResolve(ConflictResolution.KEEP_BOTH) }) { Text("保留两者") }
                }
            }
        }
    )
}

@Composable
fun ZipCreateDialog(
    currentPath: String,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("archive.zip") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建压缩包 ($count 项)") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("文件名") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码（可选）") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), password) },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun BatchRenameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量重命名") },
        text = {
            Column {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("查找") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = replace,
                    onValueChange = { replace = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("替换为") }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "将选中文件名的“查找”内容替换为“替换为”，保留扩展名。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(find, replace) },
                enabled = find.isNotBlank()
            ) { Text("执行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ChmodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var octal by remember { mutableStateOf("755") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改权限 (需 Root)") },
        text = {
            Column {
                OutlinedTextField(
                    value = octal,
                    onValueChange = { octal = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("八进制权限，如 755 / 644 / 777") }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("644", "755", "777").forEach { preset ->
                        FilterChip(
                            selected = octal == preset,
                            onClick = { octal = preset },
                            label = { Text(preset) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(octal) }) { Text("执行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun BookmarkAddDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(File(currentPath).name.ifBlank { currentPath }) }
    var path by remember { mutableStateOf(currentPath) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加书签") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("路径") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), path.trim()) },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun File(path: String): java.io.File = java.io.File(path)

@Composable
fun PropertiesDialog(
    item: FileItem,
    apkInfo: ApkInfo?,
    onDismiss: () -> Unit,
    onOpenApkInfo: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PropertyRow("名称", item.name)
                PropertyRow("路径", item.path)
                PropertyRow("类型", if (item.isDirectory) "文件夹" else "文件 (${item.extension.uppercase()})")
                if (!item.isDirectory) PropertyRow("大小", "${FileItem.formatSize(item.size)} (${item.size} B)")
                if (item.lastModified > 0) PropertyRow("修改时间", dateFormat.format(Date(item.lastModified)))
                if (item.permission.isNotEmpty()) PropertyRow("权限", item.permission)
                if (item.isSymlink) PropertyRow("链接", "是")
                if (item.isZipEntry) PropertyRow("所在压缩包", item.zipParentPath ?: "-")
                if (item.fileKind == com.mtlite.app.data.FileKind.APK && !item.isZipEntry) {
                    Spacer(Modifier.height(8.dp))
                    if (apkInfo != null) {
                        PropertyRow("包名", apkInfo.packageName)
                        PropertyRow("版本", "${apkInfo.versionName} (${apkInfo.versionCode})")
                        PropertyRow("最低 SDK", "${apkInfo.minSdk} · 目标 SDK ${apkInfo.targetSdk}")
                    }
                    TextButton(onClick = onOpenApkInfo) { Text("查看完整 APK 信息") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Default,
            textAlign = TextAlign.Start
        )
    }
}

/** Summary properties for a multi-selection (批量属性). */
@Composable
fun MultiPropertiesDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit
) {
    val dirs = items.count { it.isDirectory }
    val files = items.size - dirs
    val totalSize = items.filter { !it.isDirectory }.sumOf { it.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("属性 (${items.size} 项)") },
        text = {
            Column {
                PropertyRow("项目总数", "${items.size}")
                PropertyRow("文件夹", "$dirs 个")
                PropertyRow("文件", "$files 个")
                PropertyRow("总大小", "${FileItem.formatSize(totalSize)} ($totalSize B)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** SHA-256 result for a single file (工具 → 计算哈希). */
@Composable
fun HashResultDialog(
    name: String,
    digest: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SHA-256") },
        text = {
            Column {
                Text(
                    text = name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = digest,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun OpProgressDialog(progress: com.mtlite.app.data.OpProgress, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (progress.finished) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(progress.title.ifBlank { "操作" }) },
            text = { Text(progress.message ?: "完成") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("确定") }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(progress.title) },
            text = {
                Column {
                    Text(
                        "${progress.current}/${progress.total}  ${progress.currentName}",
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onCancel) { Text("取消", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}
