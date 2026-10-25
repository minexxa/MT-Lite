package com.mtlite.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.FileKind
import com.mtlite.app.data.PaneState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListPane(
    state: PaneState,
    isActive: Boolean,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeSelect: (FileItem) -> Unit = {},
    longPressItem: FileItem? = null,
    menuContent: @Composable (FileItem) -> Unit = {}
) {
    val visible = state.visibleItems
    val listState = rememberLazyListState()

    // Scroll a jumped-to search result into view.
    LaunchedEffect(state.highlightPath) {
        val idx = visible.indexOfFirst { it.path == state.highlightPath }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
    }

    Column(modifier = modifier.fillMaxHeight()) {
        // Active indicator
        if (isActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        state.searchQuery?.let { q ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "搜索: $q (${visible.size} 项)",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        state.filterQuery?.let { q ->
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "过滤: $q (${visible.size}/${state.items.size})",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.searchQuery != null) "无匹配结果"
                    else if (state.filterQuery != null) "无匹配文件"
                    else "空文件夹",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(visible, key = { it.path }) { item ->
                    FileListItem(
                        item = item,
                        isSelected = item.path in state.selectedItems,
                        isHighlighted = item.path == state.highlightPath,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onSwipeSelect = { onSwipeSelect(item) },
                        menu = if (item == longPressItem) {
                            { menuContent(item) }
                        } else null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    onSwipeSelect: () -> Unit = {},
    menu: (@Composable () -> Unit)? = null
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isHighlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    val thresholdPx = with(LocalDensity.current) { 88.dp.toPx() }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(bg)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .pointerInput(item.path) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = { total = 0f },
                        onDragCancel = { total = 0f },
                        onHorizontalDrag = { _, drag ->
                            total += drag
                            if (total > thresholdPx) {
                                onSwipeSelect()
                                total = 0f
                            }
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else iconFor(item),
                contentDescription = null,
                tint = tintFor(item),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.name != "..") {
                    Text(
                        text = buildString {
                            append(item.displayDate)
                            if (item.displaySize.isNotEmpty()) {
                                append("  ")
                                append(item.displaySize)
                            }
                            if (item.permission.isNotEmpty()) {
                                append("  ")
                                append(item.permission)
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        menu?.invoke()
    }
}

private fun iconFor(item: FileItem): ImageVector = when (item.fileKind) {
    FileKind.FOLDER -> Icons.Default.Folder
    FileKind.ARCHIVE -> Icons.Default.FolderZip
    FileKind.APK -> Icons.Default.InstallMobile
    FileKind.IMAGE -> Icons.Default.Image
    FileKind.VIDEO -> Icons.Default.VideoFile
    FileKind.AUDIO -> Icons.Default.AudioFile
    FileKind.TEXT -> Icons.Default.Article
    FileKind.CODE -> Icons.Default.Code
    FileKind.OTHER -> Icons.Default.InsertDriveFile
}

private fun tintFor(item: FileItem): Color = when (item.fileKind) {
    FileKind.FOLDER -> Color(0xFF42A5F5)
    FileKind.ARCHIVE -> Color(0xFFAB47BC)
    FileKind.APK -> Color(0xFF66BB6A)
    FileKind.IMAGE -> Color(0xFFEF5350)
    FileKind.VIDEO -> Color(0xFFEF5350)
    FileKind.AUDIO -> Color(0xFFFFCA28)
    FileKind.TEXT -> Color(0xFF78909C)
    FileKind.CODE -> Color(0xFF26C6DA)
    FileKind.OTHER -> Color(0xFF78909C)
}
