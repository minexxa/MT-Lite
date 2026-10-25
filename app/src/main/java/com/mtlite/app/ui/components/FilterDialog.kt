package com.mtlite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Name filter for the current pane listing (菜单 → 过滤).
 * Matches file names containing the query (case-insensitive).
 */
@Composable
fun FilterDialog(
    initialQuery: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit
) {
    var query by remember { mutableStateOf(initialQuery ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("过滤") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("文件名包含") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!initialQuery.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { onApply(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("清除过滤") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(query.trim().ifBlank { null }) },
                enabled = true
            ) { Text("应用") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
