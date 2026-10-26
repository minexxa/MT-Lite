package com.ma.manager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.*
import com.ma.manager.ui.components.MAAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.FileItem
import com.ma.manager.util.ApkInfo
import com.ma.manager.util.ApkInfoUtil
import com.ma.manager.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only APK information viewer. Shows package metadata, permissions and
 * signature digests. No dex/resource parsing or modification — info display only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInfoScreen(
    apkPath: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var info by remember { mutableStateOf<ApkInfo?>(null) }
    var icon by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(apkPath) {
        loading = true
        info = withContext(Dispatchers.IO) { ApkInfoUtil.parseApkFile(context, apkPath) }
        icon = withContext(Dispatchers.IO) {
            val d = ApkInfoUtil.loadIcon(context, apkPath)
            d?.let { drawable ->
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            MAAppBar(
                title = { Text("APK 信息") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.fromFile(File(apkPath)), "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { message = "无法启动系统安装器" }
                    }) {
                        Icon(Icons.Default.InstallMobile, contentDescription = "安装")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val apk = info
        if (apk == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("无法解析该 APK\n可能文件已损坏或不是有效的 APK", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(
                        bitmap = icon!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        Icons.Default.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(apk.label, fontSize = 17.sp)
                    Text(apk.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            InfoSection("基本信息") {
                InfoRow("文件路径", apk.path)
                InfoRow("文件大小", "${FileItem.formatSize(apk.sizeBytes)} (${apk.sizeBytes} B)")
                InfoRow("版本名称", apk.versionName)
                InfoRow("版本号", apk.versionCode.toString())
                InfoRow("最低 SDK", "${apk.minSdk} · ${apk.sdkName(apk.minSdk)}")
                InfoRow("目标 SDK", "${apk.targetSdk} · ${apk.sdkName(apk.targetSdk)}")
            }

            Spacer(Modifier.height(12.dp))
            InfoSection("组件") {
                InfoRow("Activity 数量", apk.activityCount.toString())
                InfoRow("Service 数量", apk.serviceCount.toString())
                InfoRow("Receiver 数量", apk.receiverCount.toString())
            }

            Spacer(Modifier.height(12.dp))
            InfoSection("签名") {
                InfoRow("SHA-256", apk.signatureSha256.ifBlank { "-" })
                Spacer(Modifier.height(4.dp))
                InfoRow("SHA-1", apk.signatureSha1.ifBlank { "-" })
                Spacer(Modifier.height(4.dp))
                InfoRow("MD5", apk.signatureMd5.ifBlank { "-" })
            }

            if (apk.permissions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                InfoSection("权限 (${apk.permissions.size})") {
                    apk.permissions.forEach { perm ->
                        Text(
                            perm.removePrefix("android.permission."),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
            InfoSection("时间") {
                if (apk.firstInstallTime > 0) InfoRow("首次安装", sdf.format(Date(apk.firstInstallTime)))
                if (apk.lastUpdateTime > 0) InfoRow("最近更新", sdf.format(Date(apk.lastUpdateTime)))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("确定") } }
        )
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

/** Basic read-only image viewer using BitmapFactory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    initialPath: String? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var path by remember { mutableStateOf(initialPath ?: "") }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        if (path.isBlank()) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            try {
                android.graphics.BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                error = e.message
                null
            }
        }
        if (bitmap == null) error = "无法解码图片"
    }

    Scaffold(
        topBar = {
            MAAppBar(
                title = { Text(if (path.isBlank()) "图片查看器" else File(path).name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                path.isBlank() -> Text("从文件列表选择图片文件打开", color = MaterialTheme.colorScheme.onSurfaceVariant)
                bitmap != null -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = path,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                else -> Text(error ?: "无法加载图片", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Read-only hex viewer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(
    initialPath: String? = null,
    onClose: () -> Unit
) {
    var path by remember { mutableStateOf(initialPath ?: "") }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var offset by remember { mutableStateOf(0L) }
    val pageSize = 2048
    var loading by remember { mutableStateOf(false) }
    var fileSize by remember { mutableStateOf(0L) }

    LaunchedEffect(path, offset) {
        if (path.isBlank()) return@LaunchedEffect
        loading = true
        fileSize = runCatching { File(path).length() }.getOrDefault(0L)
        lines = FileHelper.readHexLines(path, offset, pageSize)
        loading = false
    }

    Scaffold(
        topBar = {
            MAAppBar(
                title = {
                    Column {
                        Text(if (path.isBlank()) "十六进制查看器" else File(path).name, maxLines = 1)
                        if (fileSize > 0) {
                            Text(
                                "大小 ${FileItem.formatSize(fileSize)} · 偏移 %08X".format(offset),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { offset = (offset - pageSize).coerceAtLeast(0L) }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Text("上一页", fontSize = 12.sp)
                    }
                    TextButton(onClick = { offset += pageSize }, colors = ButtonDefaults.textButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                        Text("下一页", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                path.isBlank() -> Text(
                    "在文件列表中长按选择文件后，通过更多菜单打开",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                lines.isEmpty() -> Text(
                    "无法读取文件内容",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
