package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.Bookmark
import com.mtlite.app.util.FileHelper

enum class ToolAction {
    BOOKMARKS, PLUGINS, REMOTE, COLOR_PICKER, APK_EXTRACT,
    TEXT_EDITOR, TERMINAL, FTP, ACTIVITY_LOG, SETTINGS,
    HEX_VIEWER, IMAGE_VIEWER, TRASH, APK_INFO
}

@Composable
fun SideDrawerContent(
    bookmarks: List<Bookmark>,
    onNavigate: (String) -> Unit,
    onTool: (ToolAction) -> Unit,
    onBookmarkRemove: (Long) -> Unit,
    onBookmarkAdd: () -> Unit,
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
                    path.contains("Trash") -> Icons.Default.DeleteSweep
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

        HorizontalDivider()

        // Bookmarks section
        SectionTitle("书签")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${bookmarks.size} 个",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                onBookmarkAdd()
                onClose()
            }) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "添加书签", modifier = Modifier.size(18.dp))
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "暂无书签，点击 + 添加当前路径",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        bookmarks.forEach { bm ->
            BookmarkItem(
                bookmark = bm,
                onClick = {
                    onNavigate(bm.path)
                    onClose()
                },
                onRemove = { onBookmarkRemove(bm.id) }
            )
        }

        HorizontalDivider()

        // Tools
        SectionTitle("工具")
        ToolItem(Icons.Default.Terminal, "终端模拟器") {
            onTool(ToolAction.TERMINAL); onClose()
        }
        ToolItem(Icons.Default.Edit, "文本编辑器") {
            onTool(ToolAction.TEXT_EDITOR); onClose()
        }
        ToolItem(Icons.Default.Image, "图片查看器") {
            onTool(ToolAction.IMAGE_VIEWER); onClose()
        }
        ToolItem(Icons.Default.Info, "安装包提取") {
            onTool(ToolAction.APK_EXTRACT); onClose()
        }
        ToolItem(Icons.Default.Bookmark, "书签管理") {
            onTool(ToolAction.BOOKMARKS); onClose()
        }
        ToolItem(Icons.Default.Cloud, "FTP客户端") {
            onTool(ToolAction.FTP); onClose()
        }
        ToolItem(Icons.Default.Cloud, "远程管理") {
            onTool(ToolAction.REMOTE); onClose()
        }
        ToolItem(Icons.Default.ColorLens, "屏幕取色") {
            onTool(ToolAction.COLOR_PICKER); onClose()
        }
        ToolItem(Icons.Default.Extension, "插件管理") {
            onTool(ToolAction.PLUGINS); onClose()
        }
        ToolItem(Icons.Default.History, "Activity记录") {
            onTool(ToolAction.ACTIVITY_LOG); onClose()
        }
        Spacer(Modifier.height(24.dp))
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
private fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Bookmark,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(bookmark.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                bookmark.path,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "删除书签",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
