package com.mtlite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.FileManagerViewModel
import com.mtlite.app.data.PaneSide
import com.mtlite.app.ui.components.*
import com.mtlite.app.util.FileHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FileManagerViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf<ToolAction?>(null) }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    val left = viewModel.leftPane
    val right = viewModel.rightPane
    val active = viewModel.activePane
    val activeState = if (active == PaneSide.LEFT) left else right
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { com.mtlite.app.data.SettingsStore.get(context) }

    viewModel.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackMsg = msg
            viewModel.clearToast()
        }
    }

    // ---- full-screen tool pages ----
    when (currentTool) {
        ToolAction.TERMINAL -> {
            TerminalScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.IMAGE_VIEWER -> {
            ImageViewerScreen(
                initialPath = if (activeState.items.size == 1) activeState.items[0].path else null,
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.HEX_VIEWER -> {
            HexViewerScreen(
                initialPath = activeState.items.firstOrNull { it.path in activeState.selectedItems && !it.isDirectory }?.path,
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.APK_EXTRACT -> {
            ApkExtractorScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.APK_INFO -> {
            ApkInfoScreen(
                apkPath = activeState.items.firstOrNull { it.path in activeState.selectedItems && !it.isDirectory }?.path
                    ?: "/storage/emulated/0",
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.BOOKMARKS -> {
            BookmarksScreen(
                bookmarks = viewModel.bookmarkList,
                onOpen = { viewModel.navigateTo(active, it) },
                onRemove = { viewModel.removeBookmark(it) },
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.SETTINGS -> {
            SettingsScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.TEXT_EDITOR -> {
            TextEditorScreen(
                path = "/storage/emulated/0/MT-Lite-note.txt",
                initialContent = "",
                onSave = {
                    viewModel.textEditorPath = "/storage/emulated/0/MT-Lite-note.txt"
                    viewModel.saveTextEditor(it)
                    currentTool = null
                },
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.TRASH -> {
            TrashScreen(
                onClose = { currentTool = null },
                onEmpty = { viewModel.emptyTrash() }
            )
            return
        }
        ToolAction.PLUGINS,
        ToolAction.REMOTE,
        ToolAction.COLOR_PICKER,
        ToolAction.FTP,
        ToolAction.ACTIVITY_LOG -> {
            AlertDialog(
                onDismissRequest = { currentTool = null },
                title = { Text("功能开发中") },
                text = {
                    Text(
                        when (currentTool) {
                            ToolAction.PLUGINS -> "插件管理：后续支持自定义脚本与翻译引擎。"
                            ToolAction.REMOTE -> "远程管理：HTTP/FTP 服务即将加入。"
                            ToolAction.COLOR_PICKER -> "屏幕取色：需要悬浮窗权限，后续实现。"
                            ToolAction.FTP -> "FTP 客户端：连接远程服务器浏览文件。"
                            ToolAction.ACTIVITY_LOG -> "Activity 记录：查看最近打开的路径与操作。"
                            else -> "敬请期待"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { currentTool = null }) { Text("确定") }
                }
            )
        }
        null -> {}
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SideDrawerContent(
                    bookmarks = viewModel.bookmarkList,
                    onNavigate = { viewModel.navigateTo(active, it) },
                    onTool = { currentTool = it },
                    onBookmarkRemove = { viewModel.removeBookmark(it) },
                    onBookmarkAdd = { viewModel.showBookmarkAddDialog = true },
                    onClose = { scope.launch { drawerState.close() } }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeState.currentPath,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    viewModel.showJumpDialog = true
                                }
                            )
                            val folders = activeState.items.count { it.isDirectory && it.name != ".." }
                            val files = activeState.items.count { !it.isDirectory }
                            val (used, total) = FileHelper.getStorageInfo("/storage/emulated/0")
                            Text(
                                text = "文件夹: $folders  文件: $files  储存: ${FileItem.formatSize(used)}/${FileItem.formatSize(total)}" +
                                        if (FileHelper.isRootAvailable()) "  [Root]" else "",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        if (activeState.inSelectionMode) {
                            Text(
                                "已选 ${activeState.selectedItems.size}",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(onClick = { viewModel.clearSelection(active) }) {
                                Icon(Icons.Default.Close, contentDescription = "取消选择")
                            }
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            val hasSelection = activeState.inSelectionMode
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                enabled = activeState.selectedItems.size == 1,
                                onClick = {
                                    activeState.items.firstOrNull { it.path in activeState.selectedItems }
                                        ?.let { viewModel.showRename(it) }
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("属性") },
                                enabled = activeState.selectedItems.size == 1,
                                onClick = {
                                    activeState.items.firstOrNull { it.path in activeState.selectedItems }
                                        ?.let { viewModel.showProperties(it) }
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("批量重命名") },
                                enabled = hasSelection,
                                onClick = {
                                    viewModel.showBatchRenameDialog = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("修改权限 (chmod)") },
                                enabled = hasSelection,
                                onClick = {
                                    viewModel.showChmodDialog = true
                                    menuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("添加到压缩包") },
                                enabled = hasSelection,
                                onClick = {
                                    activeState.items.firstOrNull { it.fileKind == com.mtlite.app.data.FileKind.ARCHIVE && !it.isZipEntry }
                                        ?.let { viewModel.addToZip(active, it.path) }
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("测试压缩包") },
                                onClick = {
                                    activeState.items.firstOrNull { it.fileKind == com.mtlite.app.data.FileKind.ARCHIVE && !it.isZipEntry }
                                        ?.let { viewModel.testZipAt(it.path, "") }
                                    menuExpanded = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (activeState.showHidden) "隐藏隐藏文件" else "显示隐藏文件") },
                                onClick = {
                                    viewModel.toggleShowHidden()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("反选") },
                                onClick = {
                                    viewModel.invertSelection(active)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("添加书签") },
                                onClick = {
                                    viewModel.showBookmarkAddDialog = true
                                    menuExpanded = false
                                }
                            )
                            if (activeState.searchQuery != null) {
                                DropdownMenuItem(
                                    text = { Text("清除搜索结果") },
                                    onClick = {
                                        viewModel.clearSearch(active)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF212121),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                BottomActionBar(
                    canGoBack = activeState.historyIndex > 0,
                    canGoForward = activeState.historyIndex < activeState.history.lastIndex,
                    hasClipboard = !viewModel.clipboard.isEmpty,
                    onBack = { viewModel.goBack(active) },
                    onForward = { viewModel.goForward(active) },
                    onNew = { viewModel.showNewDialog = true },
                    onSync = { viewModel.syncPanes(active) },
                    onUp = { viewModel.goUp(active) },
                    onSort = { viewModel.showSortDialog = true },
                    onSearch = { viewModel.showSearchDialog = true },
                    onCopy = { viewModel.copySelected(active) },
                    onCut = { viewModel.cutSelected(active) },
                    onPaste = { viewModel.paste(active) },
                    onDelete = {
                        if (activeState.inSelectionMode) {
                            if (settings.confirmDelete) viewModel.showDeleteConfirm = true
                            else viewModel.deleteSelected(active)
                        }
                    },
                    onSelectAll = { viewModel.selectAll(active) },
                    onZip = { viewModel.showZipCreateDialog = true }
                )
            },
            snackbarHost = {
                snackMsg?.let { msg ->
                    Snackbar(
                        action = {
                            TextButton(onClick = { snackMsg = null }) { Text("关闭") }
                        }
                    ) { Text(msg) }
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2500)
                        snackMsg = null
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                FileListPane(
                    state = left,
                    isActive = active == PaneSide.LEFT,
                    onItemClick = {
                        viewModel.switchActivePane(PaneSide.LEFT)
                        viewModel.onItemClick(PaneSide.LEFT, it)
                    },
                    onItemLongClick = {
                        viewModel.switchActivePane(PaneSide.LEFT)
                        viewModel.onItemLongClick(PaneSide.LEFT, it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.switchActivePane(PaneSide.LEFT) }
                )

                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )

                FileListPane(
                    state = right,
                    isActive = active == PaneSide.RIGHT,
                    onItemClick = {
                        viewModel.switchActivePane(PaneSide.RIGHT)
                        viewModel.onItemClick(PaneSide.RIGHT, it)
                    },
                    onItemLongClick = {
                        viewModel.switchActivePane(PaneSide.RIGHT)
                        viewModel.onItemLongClick(PaneSide.RIGHT, it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.switchActivePane(PaneSide.RIGHT) }
                )
            }
        }
    }

    // ------------------------------------------------------------- dialogs
    if (viewModel.showJumpDialog) {
        JumpPathDialog(
            currentPath = activeState.currentPath,
            onDismiss = { viewModel.showJumpDialog = false },
            onConfirm = {
                viewModel.navigateTo(active, it)
                viewModel.showJumpDialog = false
            }
        )
    }

    if (viewModel.showNewDialog) {
        NewFileDialog(
            onDismiss = { viewModel.showNewDialog = false },
            onCreate = { name, isDir -> viewModel.createNew(name, isDir) }
        )
    }

    if (viewModel.showSortDialog) {
        SortDialog(
            currentSort = activeState.sortBy,
            ascending = activeState.sortAscending,
            onDismiss = { viewModel.showSortDialog = false },
            onConfirm = { sort, asc -> viewModel.setSort(sort, asc) }
        )
    }

    if (viewModel.showSearchDialog) {
        SearchDialog(
            initialQuery = viewModel.searchQuery,
            initialContent = viewModel.searchContent,
            onDismiss = { viewModel.showSearchDialog = false },
            onConfirm = { query, content ->
                viewModel.searchQuery = query
                viewModel.searchContent = content
                viewModel.runSearch(active)
            }
        )
    }

    viewModel.renameTarget?.let { target ->
        if (viewModel.showRenameDialog) {
            RenameDialog(
                item = target,
                onDismiss = { viewModel.showRenameDialog = false },
                onConfirm = { viewModel.confirmRename(it) }
            )
        }
    }

    if (viewModel.showPropertiesDialog) {
        viewModel.propertiesItem?.let { item ->
            PropertiesDialog(
                item = item,
                apkInfo = viewModel.apkInfo,
                onDismiss = {
                    viewModel.showPropertiesDialog = false
                    viewModel.apkInfo = null
                },
                onOpenApkInfo = {
                    viewModel.showPropertiesDialog = false
                    currentTool = ToolAction.APK_INFO
                }
            )
        }
    }

    if (viewModel.showConflictDialog) {
        ConflictDialog(
            name = viewModel.conflictName,
            onResolve = { viewModel.resolveConflict(it) }
        )
    }

    if (viewModel.showZipCreateDialog) {
        ZipCreateDialog(
            currentPath = activeState.currentPath,
            count = activeState.selectedItems.size,
            onDismiss = { viewModel.showZipCreateDialog = false },
            onConfirm = { name, pwd -> viewModel.createZipFromSelection(active, name, pwd) }
        )
    }

    if (viewModel.showBatchRenameDialog) {
        BatchRenameDialog(
            onDismiss = { viewModel.showBatchRenameDialog = false },
            onConfirm = { find, replace ->
                viewModel.batchFind = find
                viewModel.batchReplace = replace
                viewModel.batchRenameSelected(active)
            }
        )
    }

    if (viewModel.showChmodDialog) {
        ChmodDialog(
            onDismiss = { viewModel.showChmodDialog = false },
            onConfirm = { viewModel.chmodSelected(it) }
        )
    }

    if (viewModel.showBookmarkAddDialog) {
        BookmarkAddDialog(
            currentPath = activeState.currentPath,
            onDismiss = { viewModel.showBookmarkAddDialog = false },
            onConfirm = { name, path -> viewModel.addBookmark(name, path) }
        )
    }

    if (viewModel.showDeleteConfirm) {
        ConfirmDeleteDialog(
            count = activeState.selectedItems.size,
            useRecycleBin = settings.useRecycleBin,
            onDismiss = { viewModel.showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteSelected(active)
                viewModel.showDeleteConfirm = false
            }
        )
    }

    if (viewModel.opProgress.active || viewModel.opProgress.finished) {
        OpProgressDialog(
            progress = viewModel.opProgress,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.dismissProgress() }
        )
    }

    if (viewModel.showTextEditor && currentTool == null) {
        TextEditorScreen(
            path = viewModel.textEditorPath,
            initialContent = viewModel.textEditorContent,
            readOnly = viewModel.textEditorReadOnly,
            wrap = settings.textWrap,
            onSave = { viewModel.saveTextEditor(it) },
            onClose = { viewModel.showTextEditor = false }
        )
    }
}
