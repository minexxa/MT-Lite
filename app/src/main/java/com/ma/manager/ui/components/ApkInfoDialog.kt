package com.ma.manager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.FileItem
import com.ma.manager.util.ApkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * APK information dialog (图四). Read-only metadata from PackageManager —
 * no dex/resource parsing or modification.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkInfoDialog(
    item: FileItem,
    info: ApkInfo?,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onFullInfo: () -> Unit,
    onBrowse: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("APK 信息") },
        text = {
            if (info == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("正在解析...", fontSize = 12.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    InfoRow("名称", info.label)
                    InfoRow("包名", info.packageName)
                    InfoRow("版本", info.versionDisplay)
                    if (info.isInstalled) {
                        InfoRow(
                            "已安装版本",
                            (info.installedVersionName ?: "?") +
                                (info.installedVersionCode?.let { " ($it)" } ?: "") +
                                if (info.sameAsInstalled) " · 与文件一致" else " · 与文件不同"
                        )
                    } else {
                        InfoRow("安装状态", "未安装")
                    }
                    InfoRow("最低 SDK", info.sdkName(info.minSdk))
                    InfoRow("目标 SDK", info.sdkName(info.targetSdk))
                    InfoRow("大小", FileItem.formatSize(info.sizeBytes))
                    InfoRow("签名方案", info.signatureScheme.ifEmpty { "未知" })
                    if (info.signatureSha256.isNotEmpty()) {
                        InfoRow(
                            "签名 SHA-256",
                            info.signatureSha256.chunked(32).joinToString("\n"),
                            mono = true
                        )
                    }
                    InfoRow("界面组件", "活动 ${info.activityCount} · 服务 ${info.serviceCount} · 广播 ${info.receiverCount}")
                    InfoRow("权限", "${info.permissions.size} 项")
                    if (info.isDebuggable) InfoRow("可调试", "是")
                    if (info.isInstalled) {
                        if (info.uid >= 0) InfoRow("UID", info.uid.toString())
                        if (info.firstInstallTime > 0) {
                            InfoRow("首次安装", formatTime(info.firstInstallTime))
                        }
                        if (info.lastUpdateTime > 0) {
                            InfoRow("更新时间", formatTime(info.lastUpdateTime))
                        }
                        info.dataDir?.let { InfoRow("数据目录", it, mono = true) }
                        info.installer?.let { InfoRow("安装来源", it) }
                    }
                    if (info.permissions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "权限列表",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        info.permissions.take(20).forEach { p ->
                            Text(
                                text = "· ${p.removePrefix("android.permission.")}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        if (info.permissions.size > 20) {
                            Text(
                                text = "... 共 ${info.permissions.size} 项",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // FlowRow: five buttons do not fit on one line on narrow screens.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onBrowse) { Text("查看") }
                if (info != null && !info.isInstalled) {
                    TextButton(onClick = onInstall) { Text("安装") }
                }
                TextButton(onClick = onShare) { Text("分享") }
                TextButton(onClick = onFullInfo) { Text("详细信息") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = if (mono) FontFamily.Monospace else null,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
