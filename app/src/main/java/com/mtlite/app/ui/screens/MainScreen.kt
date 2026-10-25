package com.mtlite.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mtlite.app.ui.theme.ShadowToLeft
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.FileManagerViewModel
import com.mtlite.app.data.FileKind
import com.mtlite.app.data.OpenChoice
import com.mtlite.app.data.PaneSide
import com.mtlite.app.ui.components.*
import com.mtlite.app.util.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FileManagerViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var hiddenMenuExpanded by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf<ToolAction?>(null) }
    var snackMsg by remember { mutableStateOf<String?>(null) }
    var showExtractTo by remember { mutableStateOf<FileItem?>(null) }
    var fullApkPath by remember { mutableStateOf<String?>(null) }

    val left = viewModel.leftPane
    val right = viewModel.rightPane
    val active = viewModel.activePane
    val activeState = if (active == PaneSide.LEFT) left else right
    val context = LocalContext.current
    val settings = remember { com.mtlite.app.data.SettingsStore.get(context) }

    viewModel.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackMsg = msg
            viewModel.clearToast()
        }
    }

    // ---- full-screen pages ----
    when (currentTool) {
        ToolAction.TERMINAL -> {
            TerminalScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.SETTINGS -> {
            SettingsScreen(onClose = { currentTool = null })
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
        ToolAction.TRASH -> {
            TrashScreen(
                onClose = { currentTool = null },
                onEmpty = { viewModel.emptyTrash() }
            )
            return
        }
        ToolAction.TEXT_EDITOR -> {
            TextEditorScreen(
                path = "/storage/emulated/0/MT-Lite-note.txt",
                initialContent = "",
                onSave = { content, _ ->
                    viewModel.textEditorPath = "/storage/emulated/0/MT-Lite-note.txt"
                    viewModel.saveTextEditor(content)
                    currentTool = null
                },
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.APK_EXTRACT -> {
            ApkExtractorScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.APK_INFO -> {
            val p = fullApkPath
                ?: viewModel.propertiesItem?.path
                ?: activeState.items.firstOrNull { it.path in activeState.selectedItems && !it.isDirectory }?.path
                ?: activeState.currentPath
            ApkInfoScreen(apkPath = p, onClose = { currentTool = null })
            return
        }
        ToolAction.IMAGE_VIEWER -> {
            ImageViewerScreen(
                initialPath = activeState.items.firstOrNull { it.path in activeState.selectedItems && !it.isDirectory }?.path,
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
        else -> {}
    }

    // ---- full-screen viewers (click dispatch targets) ----
    viewModel.imageViewerPath?.let { path ->
        ImageViewerScreen(initialPath = path, onClose = { viewModel.dismissImageViewer() })
        return
    }
    viewModel.hexViewerPath?.let { path ->
        HexViewerScreen(initialPath = path, onClose = { viewModel.dismissHexViewer() })
        return
    }

    // Long-press menu content shared by both panes (single vs batch).
    val paneMenu: @Composable (PaneSide, FileItem) -> Unit = { side, item ->
        val paneState = if (side == PaneSide.LEFT) left else right
        val batch = item.path in paneState.selectedItems && paneState.selectedItems.size > 1
        val targets = if (batch) {
            paneState.items.filter { it.path in paneState.selectedItems && it.name != ".." }
        } else {
            listOf(item)
        }
        LongPressMenu(
            expanded = true,
            item = item,
            targets = targets,
            batchMode = batch,
            onDismiss = { viewModel.dismissLongPressMenu() },
            onCopy = { viewModel.copyToOtherPane(side, targets) },
            onMove = { viewModel.moveToOtherPane(side, targets) },
            onDelete = { viewModel.requestDelete(targets) },
            onRename = {
                if (batch) {
                    viewModel.batchFind = ""
                    viewModel.batchReplace = ""
                    viewModel.showBatchRenameDialog = true
                } else {
                    viewModel.showRename(item)
                }
            },
            onCompress = { viewModel.compressItems(targets) },
            onProperties = {
                if (batch) viewModel.showMultiProperties(targets)
                else viewModel.showProperties(item)
            },
            onShare = {
                if (batch) viewModel.shareItems(targets) else viewModel.shareItem(item)
            },
            onExtractTo = { showExtractTo = item },
            onOpenWith = { viewModel.showOpenWith(item) },
            onAddBookmark = { viewModel.showBookmarkAddDialog = true },
            onSignatureInfo = { viewModel.openApkDialog(item) },
            onExtractToOtherPane = { viewModel.extractEntryToOtherPane(item) },
            onBatchRename = {
                viewModel.batchFind = ""
                viewModel.batchReplace = ""
                viewModel.showBatchRenameDialog = true
            },
            onChmod = { viewModel.showChmodDialog = true },
            onHash = { viewModel.hashItem(item) },
            onHex = { viewModel.openHex(item) },
            onAddToZip = { viewModel.addToZipTargets(side, targets) },
            onTestZip = {
                targets.firstOrNull { it.fileKind == FileKind.ARCHIVE && !it.isZipEntry }
                    ?.let { viewModel.testZipTarget(it.path) }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = androidx.compose.ui.graphics.RectangleShape,
                drawerContainerColor = com.mtlite.app.ui.theme.MTStyle.listBg(),
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                SideDrawerContent(
                    bookmarks = viewModel.bookmarkList,
                    onNavigate = { viewModel.navigateTo(active, it) },
                    onTool = { currentTool = it },
                    onBookmarkRemove = { viewModel.removeBookmark(it) },
                    onBookmarkAdd = { viewModel.showBookmarkAddDialog = true },
                    onClose = { scope.launch { drawerState.close() } },
                    onRequestRoot = {
                        scope.launch {
                            val isRoot = withContext(Dispatchers.IO) {
                                com.topjohnwu.superuser.Shell.getShell().isRoot
                            }
                            viewModel.toastMessage =
                                if (isRoot) "Root 已授权，可访问所有目录" else "未获得 Root 权限，仅能部分访问"
                            viewModel.refreshPane(active)
                            viewModel.refreshPane(
                                if (active == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT
                            )
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val visibleNow = activeState.visibleItems
                val folderCount = visibleNow.count { it.isDirectory && it.name != ".." }
                val fileCount = visibleNow.count { !it.isDirectory }
                val (usedB, totalB) = FileHelper.getStorageInfo("/storage/emulated/0")
                MTTopBar(
                    title = activeState.currentPath.let { if (it.endsWith("/")) it else "$it/" },
                    subtitle = "文件夹: $folderCount  文件: $fileCount  储存: " +
                            "${FileItem.formatStorage(usedB)}/${FileItem.formatStorage(totalB)}",
                    onNavigationClick = { scope.launch { drawerState.open() } },
                    onTitleClick = { viewModel.showJumpDialog = true },
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
                        Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.width(196.dp),
                            offset = androidx.compose.ui.unit.DpOffset(0.dp, (-48).dp),
                            shadowElevation = 12.dp
                        ) {
                            MTMenuItem("刷新", Icons.Default.Refresh, {
                                viewModel.refreshPane(active); menuExpanded = false
                            })
                            MTMenuItem("搜索", Icons.Default.Search, {
                                viewModel.showSearchDialog = true; menuExpanded = false
                            })
                            if (activeState.searchQuery != null) {
                                MTMenuItem("搜索结果", Icons.Default.ListAlt, {
                                    viewModel.toastMessage = "当前显示搜索 \"${activeState.searchQuery}\" 的结果"
                                    menuExpanded = false
                                })
                            }
                            MTMenuItem("全选", Icons.Default.SelectAll, {
                                viewModel.selectAll(active); menuExpanded = false
                            })
                            MTMenuItem("过滤", Icons.Outlined.FilterAlt, {
                                viewModel.showFilterDialog = true; menuExpanded = false
                            })
                            MTMenuItem("排序方式", Icons.AutoMirrored.Filled.Sort, {
                                viewModel.showSortDialog = true; menuExpanded = false
                            })
                            MTMenuItem("打开终端", Icons.Outlined.Terminal, {
                                currentTool = ToolAction.TERMINAL; menuExpanded = false
                            })
                            // 隐藏文件▶ with nested submenu
                            Box {
                                MTMenuItem(
                                    "隐藏文件", Icons.Default.Visibility,
                                    { hiddenMenuExpanded = true },
                                    trailing = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = hiddenMenuExpanded,
                                    onDismissRequest = { hiddenMenuExpanded = false },
                                    shadowElevation = 12.dp
                                ) {
                                    MTMenuItem(
                                        if (activeState.showHidden) "不显示隐藏文件" else "显示隐藏文件",
                                        Icons.Default.VisibilityOff,
                                        {
                                            viewModel.toggleShowHidden()
                                            hiddenMenuExpanded = false
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                            MTMenuItem("添加书签", Icons.Default.CollectionsBookmark, {
                                viewModel.showBookmarkAddDialog = true; menuExpanded = false
                            })
                            MTMenuItem("设为首页", Icons.Default.Home, {
                                viewModel.setAsHome(); menuExpanded = false
                            })
                            MTMenuItem("交换窗口", Icons.AutoMirrored.Filled.Login, {
                                viewModel.swapPanes(); menuExpanded = false
                            })
                            MTMenuItem("设置", Icons.Default.Settings, {
                                currentTool = ToolAction.SETTINGS; menuExpanded = false
                            })
                            MTMenuItem("退出", Icons.AutoMirrored.Filled.ExitToApp, {
                                menuExpanded = false
                                (context as? Activity)?.finish()
                            })
                        }
                        }
                    }
                )
            },
            bottomBar = {
                BottomActionBar(
                    canGoBack = activeState.historyIndex > 0,
                    canGoForward = activeState.historyIndex < activeState.history.lastIndex,
                    onBack = { viewModel.goBack(active) },
                    onForward = { viewModel.goForward(active) },
                    onNew = { viewModel.showNewDialog = true },
                    onSync = { viewModel.syncPanes(active) },
                    onUp = { viewModel.goUp(active) }
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
                Box(Modifier.weight(1f).fillMaxHeight()) {
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
                    onSwipeSelect = { viewModel.toggleSelect(PaneSide.LEFT, it.path) },
                    longPressItem = if (viewModel.longPressSide == PaneSide.LEFT) viewModel.longPressItem else null,
                    menuContent = { item -> paneMenu(PaneSide.LEFT, item) },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewModel.switchActivePane(PaneSide.LEFT) }
                )
                ShadowToLeft(Modifier.align(Alignment.CenterEnd))
                }

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
                    onSwipeSelect = { viewModel.toggleSelect(PaneSide.RIGHT, it.path) },
                    longPressItem = if (viewModel.longPressSide == PaneSide.RIGHT) viewModel.longPressItem else null,
                    menuContent = { item -> paneMenu(PaneSide.RIGHT, item) },
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

    if (viewModel.showFilterDialog) {
        FilterDialog(
            initialQuery = activeState.filterQuery,
            onDismiss = { viewModel.showFilterDialog = false },
            onApply = { q ->
                viewModel.setFilter(active, q)
                viewModel.showFilterDialog = false
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
                    fullApkPath = item.path
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
            count = activeState.selectedItems.size.takeIf { it > 0 } ?: viewModel.zipSingleCount(),
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
        val pending = viewModel.pendingDeleteList().ifEmpty {
            activeState.items.filter { it.path in activeState.selectedItems }
        }
        ConfirmDeleteDialog(
            count = pending.size,
            useRecycleBin = settings.useRecycleBin,
            onDismiss = {
                viewModel.showDeleteConfirm = false
                viewModel.clearPendingDeleteItems()
            },
            onConfirm = {
                viewModel.confirmDelete()
            }
        )
    }

    // ---- 打开方式 (图三) ----
    viewModel.openWithItem?.let { item ->
        if (viewModel.showOpenWithDialog) {
            OpenWithDialog(
                item = item,
                onDismiss = { viewModel.dismissOpenWith() },
                onChoice = { choice ->
                    viewModel.handleOpenChoice(item, choice)
                    viewModel.dismissOpenWith()
                }
            )
        }
    }

    // ---- APK 信息 (图四) ----
    viewModel.apkDialogItem?.let { item ->
        ApkInfoDialog(
            item = item,
            info = viewModel.apkDialogInfo,
            onDismiss = { viewModel.dismissApkDialog() },
            onInstall = { viewModel.installApk(item) },
            onShare = { viewModel.shareItem(item) },
            onFullInfo = {
                viewModel.dismissApkDialog()
                viewModel.showPropertiesDialog = false
                fullApkPath = item.path
                currentTool = ToolAction.APK_INFO
            }
        )
    }

    // ---- 解压到... ----
    showExtractTo?.let { item ->
        ExtractToDialog(
            archiveName = item.name,
            initialDest = viewModel.rightPane.currentPath,
            needsPassword = FileHelper.zipNeedsPassword(item.path),
            initialPassword = viewModel.rememberedZipPassword(item.path),
            onDismiss = { showExtractTo = null },
            onConfirm = { dest, pwd ->
                if (pwd.isNotBlank()) viewModel.rememberZipPassword(item.path, pwd)
                viewModel.extractZipAt(active, item.path, dest, pwd)
                showExtractTo = null
            }
        )
    }

    // ---- 多选属性汇总 ----
    if (viewModel.showMultiPropsDialog) {
        MultiPropertiesDialog(
            items = viewModel.multiPropsItems,
            onDismiss = { viewModel.dismissMultiProperties() }
        )
    }

    // ---- 哈希结果 ----
    viewModel.hashResult?.let { (name, digest) ->
        HashResultDialog(
            name = name,
            digest = digest,
            onDismiss = { viewModel.dismissHashResult() }
        )
    }

    if (viewModel.opProgress.active || viewModel.opProgress.finished) {
        OpProgressDialog(
            progress = viewModel.opProgress,
            onCancel = { viewModel.cancelOperation() },
            onDismiss = { viewModel.dismissProgress() }
        )
    }

        if (viewModel.showTextEditor && viewModel.imageViewerPath == null && viewModel.hexViewerPath == null) {
            TextEditorScreen(
                path = viewModel.textEditorPath,
                initialContent = viewModel.textEditorContent,
                readOnly = viewModel.textEditorReadOnly,
                wrap = false,
                charset = viewModel.textEditorCharset,
                fileBytes = viewModel.textEditorBytes,
                onSave = { content, cs -> viewModel.saveTextEditor(content, cs) },
                onClose = { viewModel.showTextEditor = false }
            )
        }
}
