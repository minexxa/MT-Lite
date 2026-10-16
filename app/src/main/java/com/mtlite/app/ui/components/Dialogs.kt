package com.mtlite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun JumpPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转") },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("路径") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path.trim()) }) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    // paste from clipboard would go here
                }) {
                    Text("粘贴")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, isDir: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") }
            )
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), false) },
                    enabled = name.isNotBlank()
                ) {
                    Text("文件")
                }
                TextButton(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), true) },
                    enabled = name.isNotBlank()
                ) {
                    Text("文件夹")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除") },
        text = { Text("确定要删除选中的 $count 个项目吗？此操作不可恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
