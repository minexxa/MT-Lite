package com.ma.manager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.SettingsStore
import com.ma.manager.data.SortBy
import com.ma.manager.ui.components.MASubTopBar
import com.ma.manager.ui.theme.MAStyle
import com.ma.manager.util.FileHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
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
        containerColor = MAStyle.listBg(),
        topBar = { MASubTopBar(title = "设置", onBack = onClose) }
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
                    fontSize = 16.sp,
                    color = MAStyle.nameColor(),
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
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MABlue.copy(alpha = 0.22f),
                                selectedLabelColor = MAStyle.nameColor()
                            ),
                            label = {
                                Text(
                                    when (mode) {
                                        SettingsStore.ThemeMode.SYSTEM -> "跟随系统"
                                        SettingsStore.ThemeMode.LIGHT -> "浅色"
                                        SettingsStore.ThemeMode.DARK -> "深色"
                                    },
                                    fontSize = 13.sp
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

            SettingsDivider()

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

            SettingsDivider()

            SettingsSection("删除") {
                SwitchRow(
                    title = "使用回收站",
                    subtitle = "删除时移动到 /storage/emulated/0/.MA_Trash",
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

            SettingsDivider()

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

            SettingsDivider()

            SettingsSection("权限与状态") {
                InfoRow("Root 状态", if (FileHelper.isRootAvailable()) "已授权" else "未授权")
                InfoRow("Shell 状态", if (FileHelper.isShellAvailable()) "可用 (libsu)" else "不可用")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private val MABlue = Color(0xFF42A5F5)

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MAStyle.dateColor().copy(alpha = 0.25f),
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            title,
            fontSize = 15.sp,
            color = MABlue,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        content()
    }
}

/** Old-school MA switch: thin light track, bigger coloured thumb with shadow. */
@Composable
private fun MASwitch(checked: Boolean) {
    val dark = MAStyle.isDark()
    val offset by animateDpAsState(if (checked) 14.dp else 0.dp, label = "thumb")
    val track = if (checked) Color(0xFF90CAF9) else if (dark) Color(0xFF5A5A5A) else Color(0xFFBDBDBD)
    val thumb = if (checked) MABlue else if (dark) Color(0xFFB0B0B0) else Color(0xFFF5F5F5)
    Box(Modifier.width(34.dp).height(24.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .background(track)
        )
        Box(
            Modifier
                .offset(x = offset)
                .size(20.dp)
                .shadow(3.dp, CircleShape)
                .background(thumb, CircleShape)
        )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, fontSize = 16.sp, color = MAStyle.nameColor())
            Text(subtitle, fontSize = 13.sp, color = MAStyle.dateColor())
        }
        MASwitch(checked)
    }
}

@Composable
private fun ClickRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 16.sp, color = MAStyle.nameColor())
        Text(subtitle, fontSize = 13.sp, color = MAStyle.dateColor())
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 16.sp, color = MAStyle.nameColor(), modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, color = MAStyle.dateColor())
    }
}
