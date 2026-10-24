package com.mtlite.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: String,
    initialContent: String,
    readOnly: Boolean = false,
    wrap: Boolean = true,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    var content by remember { mutableStateOf(initialContent) }
    val fileName = File(path).name
    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val charCount = content.length

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, maxLines = 1)
                        Text(
                            "行 $lineCount · 字符 $charCount" +
                                    (if (readOnly) " · 只读" else "") +
                                    (if (!wrap) " · 不换行" else ""),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = { showFind = !showFind }) {
                            Icon(Icons.Default.FindReplace, contentDescription = "查找替换")
                        }
                        IconButton(onClick = { onSave(content) }) {
                            Icon(Icons.Default.Save, contentDescription = "保存")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Text(
                    text = statusMsg ?: path,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    maxLines = 1
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            if (showFind && !readOnly) {
                // Find / replace bar
                Column {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("查找内容") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (findText.isNotEmpty()) {
                                IconButton(onClick = {
                                    val idx = content.indexOf(findText)
                                    if (idx >= 0) {
                                        statusMsg = "第 ${content.substring(0, idx).count { it == '\n' } + 1} 行找到匹配"
                                    } else {
                                        statusMsg = "未找到匹配"
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "查找下一个")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("替换为") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (findText.isEmpty()) return@FilledTonalButton
                                val count = countOccurrences(content, findText)
                                content = content.replace(findText, replaceText)
                                statusMsg = "已替换 $count 处"
                            }
                        ) { Text("替换全部") }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { showFind = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭查找")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            SelectionContainer {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    readOnly = readOnly,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    placeholder = { Text(if (readOnly) "只读文件" else "空文件") }
                )
            }
        }
    }

    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            kotlinx.coroutines.delay(3000)
            statusMsg = null
        }
    }
}

private fun countOccurrences(text: String, sub: String): Int {
    if (sub.isEmpty()) return 0
    var count = 0
    var idx = text.indexOf(sub)
    while (idx >= 0) {
        count++
        idx = text.indexOf(sub, idx + sub.length)
    }
    return count
}
