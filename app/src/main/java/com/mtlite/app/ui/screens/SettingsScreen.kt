package com.mtlite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.SettingsStore
import com.mtlite.app.data.SortBy
import com.mtlite.app.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore.get(context) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var showHidden by remember { mutableStateOf(settings.showHidden) }
    var foldersFirst by remember { mutableStateOf(settings.foldersFirst) }
    var useRecycleBin by remember { mutableStateOf(settings.useRecycleBin) }
    var confirmDelete by remember { mutableStateOf(settings.confirmDelete) }
    var textWrap by remember { mutableStateOf(settings.textWrap) }
    var defaultPath by remember { mutableStateOf(settings.defaultPath) }
    var sortLabel by remember {
        mutableStateOf(
            when (settings.sortBy) {
                SortBy.NAME -> "名称"
                SortBy.SIZE -> "大小"
                SortBy.DATE -> "修改时间"
                SortBy.TYPE -> "类型"
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection("外观") {
                Text(
                    "主题模式",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsStore.ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = {
                                themeMode = mode
                                settings.themeMode = mode
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        SettingsStore.ThemeMode.SYSTEM -> "跟随系统"
                                        SettingsStore.ThemeMode.LIGHT -> "浅色"
                                        SettingsStore.ThemeMode.DARK -> "深色"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
                SwitchRow(
                    title = "文件夹优先显示",
                    subtitle = "排序时文件夹始终排在文件前面",
                    checked = foldersFirst,
                    onChange = {
                        foldersFirst = it
                        settings.foldersFirst = it
                    }
                )
            }

            HorizontalDivider()

            SettingsSection("文件列表") {
                SwitchRow(
                    title = "显示隐藏文件",
                    subtitle = "显示以 . 开头的文件与文件夹",
                    checked = showHidden,
                    onChange = {
                        showHidden = it
                        settings.showHidden = it
                    }
                )
                ClickRow(
                    title = "默认排序",
                    subtitle = "$sortLabel · ${if (settings.sortAscending) "升序" else "降序"}",
                    onClick = {
                        val next = when (settings.sortBy) {
                            SortBy.NAME -> SortBy.SIZE
                            SortBy.SIZE -> SortBy.DATE
                            SortBy.DATE -> SortBy.TYPE
                            SortBy.TYPE -> SortBy.NAME
                        }
                        settings.sortBy = next
                        sortLabel = when (next) {
                            SortBy.NAME -> "名称"
                            SortBy.SIZE -> "大小"
                            SortBy.DATE -> "修改时间"
                            SortBy.TYPE -> "类型"
                        }
                    }
                )
                ClickRow(
                    title = "默认路径",
                    subtitle = defaultPath,
                    onClick = {
                        // cycle through common paths
                        val candidates = listOf(
                            "/storage/emulated/0",
                            "/storage/emulated/0/Download",
                            "/sdcard",
                            "/"
                        )
                        val idx = candidates.indexOf(defaultPath)
                        defaultPath = candidates[(idx + 1) % candidates.size]
                        settings.defaultPath = defaultPath
                    }
                )
            }

            HorizontalDivider()

            SettingsSection("删除") {
                SwitchRow(
                    title = "使用回收站",
                    subtitle = "删除时移动到 /storage/emulated/0/.MT_Lite_Trash",
                    checked = useRecycleBin,
                    onChange = {
                        useRecycleBin = it
                        settings.useRecycleBin = it
                    }
                )
                SwitchRow(
                    title = "删除前确认",
                    subtitle = "删除文件前弹出确认对话框",
                    checked = confirmDelete,
                    onChange = {
                        confirmDelete = it
                        settings.confirmDelete = it
                    }
                )
            }

            HorizontalDivider()

            SettingsSection("文本编辑器") {
                SwitchRow(
                    title = "自动换行",
                    subtitle = "长行自动换行显示",
                    checked = textWrap,
                    onChange = {
                        textWrap = it
                        settings.textWrap = it
                    }
                )
            }

            HorizontalDivider()

            SettingsSection("权限与状态") {
                InfoRow("Root 状态", if (FileHelper.isRootAvailable()) "已授权" else "未授权")
                InfoRow("Shell 状态", if (FileHelper.isShellAvailable()) "可用 (libsu)" else "不可用")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            title,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
