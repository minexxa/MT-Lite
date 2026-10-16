package com.mtlite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    val left = viewModel.leftPane
    val right = viewModel.rightPane
    val active = viewModel.activePane

    // Toast
    viewModel.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            // Simple handling - in real app use Snackbar
            viewModel.clearToast()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SideDrawerContent(
                    onNavigate = { path ->
                        viewModel.navigateTo(active, path)
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
                                text = "文件夹: $folders  文件: $files  储存: ${com.mtlite.app.data.FileItem.formatSize(used)}/${com.mtlite.app.data.FileItem.formatSize(total)}",
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
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Left pane
                FileListPane(
                    state = left,
                    isActive = active == PaneSide.LEFT,
                    onItemClick = {
                        viewModel.setActivePane(PaneSide.LEFT)
                        viewModel.onItemClick(PaneSide.LEFT, it)
                    },
                    onItemLongClick = {
                        viewModel.setActivePane(PaneSide.LEFT)
                        viewModel.toggleSelect(PaneSide.LEFT, it.path)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.setActivePane(PaneSide.LEFT) }
                )

                // Divider
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )

                // Right pane
                FileListPane(
                    state = right,
                    isActive = active == PaneSide.RIGHT,
                    onItemClick = {
                        viewModel.setActivePane(PaneSide.RIGHT)
                        viewModel.onItemClick(PaneSide.RIGHT, it)
                    },
                    onItemLongClick = {
                        viewModel.setActivePane(PaneSide.RIGHT)
                        viewModel.toggleSelect(PaneSide.RIGHT, it.path)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.setActivePane(PaneSide.RIGHT) }
                )
            }
        }
    }

    // Dialogs
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

    // Simple Text Editor
    if (viewModel.showTextEditor) {
        TextEditorScreen(
            path = viewModel.textEditorPath,
            initialContent = viewModel.textEditorContent,
            onSave = { viewModel.saveTextEditor(it) },
            onClose = { viewModel.showTextEditor = false }
        )
    }
}
