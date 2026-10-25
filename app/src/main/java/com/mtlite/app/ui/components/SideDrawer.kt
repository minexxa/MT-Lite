package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.Bookmark
import com.mtlite.app.ui.theme.MTStyle
import com.mtlite.app.util.FileHelper

enum class ToolAction {
    BOOKMARKS, PLUGINS, REMOTE, COLOR_PICKER, APK_EXTRACT,
    TEXT_EDITOR, TERMINAL, FTP, ACTIVITY_LOG, SETTINGS,
    HEX_VIEWER, IMAGE_VIEWER, TRASH, APK_INFO
}

/** Accent blue used by the official MT Manager (progress bars / percent). */
private val MTBlue = Color(0xFF42A5F5)

/**
 * Side drawer in the style of the official MT Manager:
 * dark #303030 header that extends under the status bar, collapsible
 * sections, 30dp dark circular icons and thin storage progress lines.
 *
 * The hosting [androidx.compose.material3.ModalDrawerSheet] should use
 * zero window insets, a rectangular shape and the MT list background.
 */
@Composable
fun SideDrawerContent(
    bookmarks: List<Bookmark>,
    onNavigate: (String) -> Unit,
    onTool: (ToolAction) -> Unit,
    onBookmarkRemove: (Long) -> Unit,
    onBookmarkAdd: () -> Unit,
    onClose: () -> Unit,
    onRequestRoot: () -> Unit = {}
) {
    var localOpen by remember { mutableStateOf(true) }
    var bookmarkOpen by remember { mutableStateOf(true) }
    var toolsOpen by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MTStyle.listBg())
            .verticalScroll(rememberScrollState())
    ) {
        // ---------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MTStyle.ToolbarBg)
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(MTStyle.ToolbarHeight)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Android,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("MT-Lite", color = Color.White, fontSize = 20.sp, maxLines = 1)
                Text(
                    if (FileHelper.isRootAvailable()) "Root 已授权" else "未 Root",
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            IconButton(onClick = {
                onTool(ToolAction.SETTINGS)
                onClose()
            }) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = Color.White)
            }
        }

        // ----------------------------------------------------------- local
        SectionTitle("本地", expanded = localOpen, onToggle = { localOpen = !localOpen })
        if (localOpen) {
            FileHelper.getStorageEntries().forEach { (title, path, subtitle) ->
                val showUsage = path == "/" || path == "/storage/emulated/0"
                if (showUsage) {
                    val (used, total) = FileHelper.getStorageInfo(path)
                    StorageUsageItem(
                        icon = if (path == "/") Icons.Default.PhoneAndroid else Icons.Default.SdCard,
                        title = title,
                        used = used,
                        total = total,
                        onClick = {
                            onNavigate(path)
                            onClose()
                        }
                    )
                } else {
                    DrawerItem(
                        icon = when {
                            path.contains("Trash") -> Icons.Default.DeleteSweep
                            path.contains("emulated") -> Icons.Default.SdCard
                            else -> Icons.Default.Storage
                        },
                        title = title,
                        subtitle = subtitle,
                        onClick = {
                            onNavigate(path)
                            onClose()
                        }
                    )
                }
            }
            DrawerItem(
                icon = Icons.Default.Security,
                title = "Root 授权",
                subtitle = if (FileHelper.isRootAvailable()) "已授权 · 可访问所有目录" else "点击授予 · 否则仅能部分访问",
                onClick = {
                    onRequestRoot()
                    onClose()
                }
            )
        }

        // -------------------------------------------------------- bookmarks
        SectionTitle(
            "书签",
            expanded = bookmarkOpen,
            onToggle = { bookmarkOpen = !bookmarkOpen },
            extra = {
                Text(
                    "${bookmarks.size} 个",
                    fontSize = 12.sp,
                    color = MTStyle.dateColor(),
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(
                    onClick = {
                        onBookmarkAdd()
                        onClose()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.BookmarkAdd,
                        contentDescription = "添加书签",
                        tint = MTStyle.barIcon(),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
        if (bookmarkOpen) {
            if (bookmarks.isEmpty()) {
                Text(
                    "暂无书签，点击右上角图标添加当前路径",
                    fontSize = 12.sp,
                    color = MTStyle.dateColor(),
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
        }

        // ------------------------------------------------------------ tools
        SectionTitle("工具", expanded = toolsOpen, onToggle = { toolsOpen = !toolsOpen })
        if (toolsOpen) {
            ToolItem(Icons.Default.Terminal, "终端模拟器") { onTool(ToolAction.TERMINAL); onClose() }
            ToolItem(Icons.Default.Edit, "文本编辑器") { onTool(ToolAction.TEXT_EDITOR); onClose() }
            ToolItem(Icons.Default.Image, "图片查看器") { onTool(ToolAction.IMAGE_VIEWER); onClose() }
            ToolItem(Icons.Default.Info, "安装包提取") { onTool(ToolAction.APK_EXTRACT); onClose() }
            ToolItem(Icons.Default.Bookmark, "书签管理") { onTool(ToolAction.BOOKMARKS); onClose() }
            ToolItem(Icons.Default.Cloud, "FTP客户端") { onTool(ToolAction.FTP); onClose() }
            ToolItem(Icons.Default.Cloud, "远程管理") { onTool(ToolAction.REMOTE); onClose() }
            ToolItem(Icons.Default.ColorLens, "屏幕取色") { onTool(ToolAction.COLOR_PICKER); onClose() }
            ToolItem(Icons.Default.Extension, "插件管理") { onTool(ToolAction.PLUGINS); onClose() }
            ToolItem(Icons.Default.History, "Activity记录") { onTool(ToolAction.ACTIVITY_LOG); onClose() }
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun SectionTitle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    extra: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onToggle)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = MTStyle.dateColor(),
            modifier = Modifier.weight(1f)
        )
        extra()
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MTStyle.nameColor(),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Dark circular icon tile (30dp) used by every drawer row. */
@Composable
private fun CircleIcon(icon: ImageVector, size: Dp = 30.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MTStyle.tile()),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Row with title + optional grey subtitle. */
@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MTStyle.nameColor(), maxLines = 1)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MTStyle.dateColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Storage row: title, blue usage line with percent, "xx已用 , xx可用". */
@Composable
private fun StorageUsageItem(
    icon: ImageVector,
    title: String,
    used: Long,
    total: Long,
    onClick: () -> Unit
) {
    val ratio = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (ratio * 100f).toInt()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 9.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MTStyle.nameColor(), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ratio > 0f) {
                    Box(
                        Modifier
                            .weight(ratio)
                            .height(2.dp)
                            .background(MTBlue)
                    )
                }
                Text(
                    "$percent%",
                    fontSize = 13.sp,
                    color = MTBlue,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                if (ratio < 1f) {
                    Box(
                        Modifier
                            .weight(1f - ratio)
                            .height(2.dp)
                            .background(MTStyle.dateColor().copy(alpha = 0.3f))
                    )
                }
            }
            Text(
                if (total > 0) "${fmtSize(used)}已用 , ${fmtSize((total - used).coerceAtLeast(0L))}可用" else "不可用",
                fontSize = 12.sp,
                color = MTStyle.dateColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            .padding(start = 9.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIcon(Icons.Default.Bookmark)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.name,
                fontSize = 16.sp,
                color = MTStyle.nameColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                bookmark.path,
                fontSize = 12.sp,
                color = MTStyle.dateColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.DeleteSweep,
                contentDescription = "删除书签",
                modifier = Modifier.size(18.dp),
                tint = MTStyle.dateColor()
            )
        }
    }
}

@Composable
private fun ToolItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 16.sp, color = MTStyle.nameColor(), maxLines = 1)
    }
}

/** Official style size text: 1003.77M / 197.41G / 0B. */
private fun fmtSize(bytes: Long): String {
    val k = 1024.0
    val b = bytes.toDouble()
    return when {
        b >= k * k * k * k -> String.format("%.2fT", b / (k * k * k * k))
        b >= k * k * k -> String.format("%.2fG", b / (k * k * k))
        b >= k * k -> String.format("%.2fM", b / (k * k))
        b >= k -> String.format("%.2fK", b / k)
        else -> "${bytes}B"
    }
}
