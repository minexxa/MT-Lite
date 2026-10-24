package com.mtlite.app.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.Bookmark
import com.mtlite.app.util.FileHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onClose: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("MT-Lite Terminal\nRoot: ${FileHelper.isRootAvailable()}\n") }
    val history = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端模拟器") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { output = "" }) {
                        Icon(Icons.Default.Refresh, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(
                text = output,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入命令…（↑↓ 浏览历史）") },
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val cmd = input.trim()
                            if (cmd.isEmpty()) return@KeyboardActions
                            history.add(cmd)
                            scope.launch {
                                output += "\n$ $cmd\n"
                                val result = FileHelper.runShell(cmd)
                                output += result + "\n"
                                input = ""
                            }
                        }
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Go
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                IconButton(onClick = {
                    val cmd = input.trim()
                    if (cmd.isEmpty()) return@IconButton
                    history.add(cmd)
                    scope.launch {
                        output += "\n$ $cmd\n"
                        val result = FileHelper.runShell(cmd)
                        output += result + "\n"
                        input = ""
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "执行")
                }
            }
            // History quick bar
            if (history.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    history.takeLast(10).reversed().forEach { cmd ->
                        AssistChip(
                            onClick = { input = cmd },
                            label = { Text(cmd, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        }
    }
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val sourceDir: String,
    val isSystem: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkExtractorScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val pm = context.packageManager
        val list = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map {
                AppEntry(
                    label = pm.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    sourceDir = it.sourceDir ?: "",
                    isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
        apps = list
        loading = false
    }

    val filtered = remember(apps, filter, showSystem) {
        apps.filter { (showSystem || !it.isSystem) }
            .filter { filter.isBlank() || it.label.contains(filter, true) || it.packageName.contains(filter, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安装包提取") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                singleLine = true,
                placeholder = { Text("搜索应用名 / 包名") }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                Text("显示系统应用", fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} 个应用", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.packageName }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                Text(
                                    "${app.packageName}\n${app.sourceDir}",
                                    fontSize = 11.sp
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        // APK info (read-only)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.fromFile(File(app.sourceDir)), "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(intent) }
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "信息")
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            val destDir = File(
                                                "/storage/emulated/0/Download",
                                                "MT-Lite-APK"
                                            )
                                            destDir.mkdirs()
                                            val dest = File(destDir, "${app.packageName}.apk")
                                            val ok = try {
                                                File(app.sourceDir).copyTo(dest, overwrite = true)
                                                true
                                            } catch (_: Exception) {
                                                FileHelper.runShell(
                                                    "cp \"${app.sourceDir}\" \"${dest.absolutePath}\""
                                                )
                                                dest.exists()
                                            }
                                            message = if (ok) {
                                                "已提取到 ${dest.absolutePath}"
                                            } else {
                                                "提取失败（可能需要 Root）"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "提取")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        message?.let { msg ->
            AlertDialog(
                onDismissRequest = { message = null },
                title = { Text("提示") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { message = null }) { Text("确定") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Bookmark>,
    onOpen: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书签管理") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无书签\n在侧边栏点击 + 可添加当前路径", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(bookmarks, key = { it.id }) { bm ->
                    ListItem(
                        headlineContent = { Text(bm.name) },
                        supportingContent = { Text(bm.path, fontSize = 12.sp) },
                        leadingContent = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemove(bm.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            onOpen(bm.path)
                            onClose()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onClose: () -> Unit,
    onEmpty: () -> Unit
) {
    val trashPath = "/storage/emulated/0/.MT_Lite_Trash"
    var items by remember { mutableStateOf<List<com.mtlite.app.data.FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        items = FileHelper.listFiles(trashPath, showHidden = true)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { confirmEmpty = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (items.none { it.name != ".." }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("回收站为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(items.filter { it.name != ".." }, key = { it.path }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Text(
                                if (item.isDirectory) "文件夹" else com.mtlite.app.data.FileItem.formatSize(item.size),
                                fontSize = 12.sp
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("清空回收站") },
            text = { Text("确定要永久删除回收站中的所有文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onEmpty()
                    confirmEmpty = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("取消") }
            }
        )
    }
}
