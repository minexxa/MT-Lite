package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.util.FileHelper

@Composable
fun SideDrawerContent(
    onNavigate: (String) -> Unit,
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
            Icon(Icons.Default.Android, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MT-Lite", color = Color.White, fontSize = 18.sp)
                Text("未登录", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
            }
        }

        // Storage section
        SectionTitle("存储")
        val (used, total) = FileHelper.getStorageInfo("/storage/emulated/0")
        StorageItem(
            icon = Icons.Default.PhoneAndroid,
            title = "根目录",
            subtitle = "系统根",
            progress = 1f,
            onClick = { onNavigate("/"); onClose() }
        )
        StorageItem(
            icon = Icons.Default.SdCard,
            title = "内部存储",
            subtitle = "${FileItem.formatSize(used)} / ${FileItem.formatSize(total)}",
            progress = if (total > 0) used.toFloat() / total else 0f,
            onClick = { onNavigate("/storage/emulated/0"); onClose() }
        )

        // Tools
        SectionTitle("工具")
        ToolItem(Icons.Default.Star, "书签", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Extension, "插件管理", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Cloud, "远程管理", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Colorize, "屏幕取色", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Inventory, "安装包提取", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Edit, "文本编辑器", onClick = { /* TODO */ })
        ToolItem(Icons.Default.Terminal, "终端模拟器", onClick = { /* TODO */ })
        ToolItem(Icons.Default.CloudQueue, "FTP客户端", onClick = { /* TODO */ })
        ToolItem(Icons.Default.History, "Activity记录", onClick = { /* TODO */ })
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
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
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

// Helper to avoid import conflict
private object FileItem {
    fun formatSize(bytes: Long): String = com.mtlite.app.data.FileItem.formatSize(bytes)
}
