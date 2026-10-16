package com.mtlite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf<ToolAction?>(null) }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    val left = viewModel.leftPane
    val right = viewModel.rightPane
    val active = viewModel.activePane

    viewModel.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            snackMsg = msg
            viewModel.clearToast()
        }
    }

    // Full-screen tool pages
    when (currentTool) {
        ToolAction.TERMINAL -> {
            TerminalScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.APK_EXTRACT -> {
            ApkExtractorScreen(onClose = { currentTool = null })
            return
        }
        ToolAction.BOOKMARKS -> {
            BookmarksScreen(
                bookmarks = listOf(
                    "内部存储" to "/storage/emulated/0",
                    "下载" to "/storage/emulated/0/Download",
                    "根目录" to "/"
                ),
                onOpen = { viewModel.navigateTo(active, it) },
                onClose = { currentTool = null }
            )
            return
        }
        ToolAction.TEXT_EDITOR -> {
            TextEditorScreen(
                path = "/storage/emulated/0/MT-Lite-note.txt",
                initialContent = "",
                onSave = {
                    viewModel.saveTextEditor(it)
                    currentTool = null
                },
                onClose = { currentTool = null }
            )
            // Ensure path is set for save
            LaunchedEffect(Unit) {
                viewModel.textEditorPath = "/storage/emulated/0/MT-Lite-note.txt"
            }
            return
        }
        ToolAction.PLUGINS,
        ToolAction.REMOTE,
        ToolAction.COLOR_PICKER,
        ToolAction.FTP,
        ToolAction.ACTIVITY_LOG,
        ToolAction.SETTINGS -> {
            // Placeholder dialog-style page
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
                            ToolAction.SETTINGS -> "设置：主题、显示隐藏文件、默认路径等。"
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
                    onNavigate = { path ->
                        viewModel.navigateTo(active, path)
                    },
                    onTool = { action ->
                        currentTool = action
                    },
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
                                text = if (active == PaneSide.LEFT) left.currentPath else right.currentPath,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    viewModel.showJumpDialog = true
                                }
                            )
                            val state = if (active == PaneSide.LEFT) left else right
                            val folders = state.items.count { it.isDirectory && it.name != ".." }
                            val files = state.items.count { !it.isDirectory }
                            val (used, total) = FileHelper.getStorageInfo("/storage/emulated/0")
                            Text(
                                text = "文件夹: $folders  文件: $files  储存: ${com.mtlite.app.data.FileItem.formatSize(used)}/${com.mtlite.app.data.FileItem.formatSize(total)}" +
                                        if (FileHelper.isRootAvailable()) "  [Root]" else "",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制") },
                                onClick = {
                                    viewModel.copySelected(active)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("剪切") },
                                onClick = {
                                    viewModel.cutSelected(active)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("粘贴") },
                                onClick = {
                                    viewModel.paste(active)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    showDeleteConfirm = true
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("全选") },
                                onClick = {
                                    viewModel.selectAll(active)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    viewModel.refreshPane(active)
                                    menuExpanded = false
                                }
                            )
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
                val state = if (active == PaneSide.LEFT) left else right
                BottomActionBar(
                    canGoBack = state.historyIndex > 0,
                    canGoForward = state.historyIndex < state.history.lastIndex,
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
                FileListPane(
                    state = left,
                    isActive = active == PaneSide.LEFT,
                    onItemClick = {
                        viewModel.switchActivePane(PaneSide.LEFT)
                        viewModel.onItemClick(PaneSide.LEFT, it)
                    },
                    onItemLongClick = {
                        viewModel.switchActivePane(PaneSide.LEFT)
                        viewModel.toggleSelect(PaneSide.LEFT, it.path)
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
                        viewModel.toggleSelect(PaneSide.RIGHT, it.path)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.switchActivePane(PaneSide.RIGHT) }
                )
            }
        }
    }

    if (viewModel.showJumpDialog) {
        JumpPathDialog(
            currentPath = if (active == PaneSide.LEFT) left.currentPath else right.currentPath,
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

    if (showDeleteConfirm) {
        val count = (if (active == PaneSide.LEFT) left else right).selectedItems.size
        ConfirmDeleteDialog(
            count = count,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteSelected(active)
                showDeleteConfirm = false
            }
        )
    }

    if (viewModel.showTextEditor && currentTool == null) {
        TextEditorScreen(
            path = viewModel.textEditorPath,
            initialContent = viewModel.textEditorContent,
            onSave = { viewModel.saveTextEditor(it) },
            onClose = { viewModel.showTextEditor = false }
        )
    }
}
