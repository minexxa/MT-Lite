package com.mtlite.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.util.FileHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onClose: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("MT-Lite Terminal\nRoot: ${FileHelper.isRootAvailable()}\n") }
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
                    placeholder = { Text("输入命令…") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
                IconButton(onClick = {
                    val cmd = input.trim()
                    if (cmd.isEmpty()) return@IconButton
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

    val filtered = remember(apps, filter) {
        if (filter.isBlank()) apps
        else apps.filter {
            it.label.contains(filter, true) || it.packageName.contains(filter, true)
        }
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
                                            ).let { !it.contains("error", true) }
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
                            },
                            modifier = Modifier.clickable { }
                        )
                        HorizontalDivider()
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书签") },
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
                Text("暂无书签\n在文件列表长按可添加（后续版本）", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(bookmarks) { (name, path) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(path) },
                        modifier = Modifier.clickable {
                            onOpen(path)
                            onClose()
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
