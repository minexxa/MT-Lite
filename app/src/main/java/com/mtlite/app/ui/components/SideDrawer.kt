package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.util.FileHelper

enum class ToolAction {
    BOOKMARKS, PLUGINS, REMOTE, COLOR_PICKER, APK_EXTRACT,
    TEXT_EDITOR, TERMINAL, FTP, ACTIVITY_LOG, SETTINGS
}

@Composable
fun SideDrawerContent(
    onNavigate: (String) -> Unit,
    onTool: (ToolAction) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF212121))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MT-Lite", color = Color.White, fontSize = 18.sp)
                Text(
                    if (FileHelper.isRootAvailable()) "Root 已授权" else "未 Root",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                onTool(ToolAction.SETTINGS)
                onClose()
            }) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
            }
        }

        // Storage section
        SectionTitle("存储")
        FileHelper.getStorageEntries().forEach { (title, path, subtitle) ->
            StorageItem(
                icon = when {
                    path == "/" -> Icons.Default.Storage
                    path.contains("emulated") -> Icons.Default.SdCard
                    else -> Icons.Default.Storage
                },
                title = title,
                subtitle = subtitle,
                progress = if (path.contains("emulated")) {
                    val (u, t) = FileHelper.getStorageInfo(path)
                    if (t > 0) u.toFloat() / t else 0f
                } else 0f,
                onClick = {
                    onNavigate(path)
                    onClose()
                }
            )
        }

        // Tools
        SectionTitle("工具")
        ToolItem(Icons.Default.Bookmark, "书签") {
            onTool(ToolAction.BOOKMARKS); onClose()
        }
        ToolItem(Icons.Default.Extension, "插件管理") {
            onTool(ToolAction.PLUGINS); onClose()
        }
        ToolItem(Icons.Default.Cloud, "远程管理") {
            onTool(ToolAction.REMOTE); onClose()
        }
        ToolItem(Icons.Default.ColorLens, "屏幕取色") {
            onTool(ToolAction.COLOR_PICKER); onClose()
        }
        ToolItem(Icons.Default.Info, "安装包提取") {
            onTool(ToolAction.APK_EXTRACT); onClose()
        }
        ToolItem(Icons.Default.Edit, "文本编辑器") {
            onTool(ToolAction.TEXT_EDITOR); onClose()
        }
        ToolItem(Icons.Default.Terminal, "终端模拟器") {
            onTool(ToolAction.TERMINAL); onClose()
        }
        ToolItem(Icons.Default.Cloud, "FTP客户端") {
            onTool(ToolAction.FTP); onClose()
        }
        ToolItem(Icons.Default.History, "Activity记录") {
            onTool(ToolAction.ACTIVITY_LOG); onClose()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun StorageItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        if (progress > 0f) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp)
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 15.sp)
    }
}
