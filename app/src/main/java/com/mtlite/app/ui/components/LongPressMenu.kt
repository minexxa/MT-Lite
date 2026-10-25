package com.mtlite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.FileKind

/**
 * MT-manager style long-press option popup (图五 single / 图一 batch).
 * Anchored to the pressed list item by the caller.
 *
 * [targets] is what the actions operate on: the pressed item alone, or the
 * whole selection when the pane is in multi-select mode. In a zip preview
 * the first entry becomes 解压到另一窗口 (replacing 复制).
 */
@Composable
fun LongPressMenu(
    expanded: Boolean,
    item: FileItem,
    targets: List<FileItem>,
    batchMode: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onProperties: () -> Unit,
    onShare: () -> Unit,
    onExtractTo: () -> Unit,
    onOpenWith: () -> Unit,
    onAddBookmark: () -> Unit,
    onSignatureInfo: () -> Unit,
    onExtractToOtherPane: () -> Unit,
    onBatchRename: () -> Unit,
    onChmod: () -> Unit,
    onHash: () -> Unit,
    onHex: () -> Unit,
    onAddToZip: () -> Unit,
    onTestZip: () -> Unit
) {
    val inZip = item.isZipEntry
    var toolsExpanded by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (batchMode) {
            Text(
                text = "带 ● 的菜单表示可以长按触发单窗口操作",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (inZip) {
            // ---- zip preview variant ----
            MenuRow(
                icon = Icons.Default.Unarchive,
                text = "解压到另一窗口",
                onClick = { onExtractToOtherPane(); onDismiss() }
            )
            MenuRow(
                icon = Icons.Default.OpenInNew,
                text = "打开方式...",
                onClick = { onOpenWith(); onDismiss() }
            )
            MenuRow(
                icon = Icons.Default.Info,
                text = "属性",
                onClick = { onProperties(); onDismiss() }
            )
            return@DropdownMenu
        }

        // 添加 / 移动 to the other pane (two columns, MT style)
        Row(Modifier.fillMaxWidth()) {
            DropdownMenuItem(
                text = { Text("<- 添加") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { onCopy(); onDismiss() },
                modifier = Modifier.weight(1f)
            )
            DropdownMenuItem(
                text = { Text("<- 移动") },
                leadingIcon = { Icon(Icons.Default.MoveDown, contentDescription = null) },
                onClick = { onMove(); onDismiss() },
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider()
        MenuRow(
            icon = Icons.Default.Delete,
            text = "删除",
            tint = MaterialTheme.colorScheme.error,
            onClick = { onDelete(); onDismiss() }
        )
        MenuRow(
            icon = Icons.Default.DriveFileRenameOutline,
            text = if (batchMode) "批量重命名" else "重命名",
            onClick = { onRename(); onDismiss() }
        )

        // ---- 工具 submenu ----
        val hasArchive = targets.any { it.fileKind == FileKind.ARCHIVE && !it.isZipEntry }
        val noZipEntries = targets.none { it.isZipEntry }
        val singleFile = !batchMode && !item.isDirectory
        DropdownMenuItem(
            text = { Text("工具") },
            leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) },
            trailingIcon = {
                Box {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    DropdownMenu(
                        expanded = toolsExpanded,
                        onDismissRequest = { toolsExpanded = false }
                    ) {
                        MenuRow(
                            icon = Icons.Default.DriveFileRenameOutline,
                            text = "批量重命名",
                            enabled = batchMode && noZipEntries,
                            onClick = { onBatchRename(); onDismiss() }
                        )
                        MenuRow(
                            icon = Icons.Default.Security,
                            text = "修改权限",
                            enabled = noZipEntries,
                            onClick = { onChmod(); onDismiss() }
                        )
                        MenuRow(
                            icon = Icons.Default.Fingerprint,
                            text = "计算哈希 (SHA-256)",
                            enabled = singleFile,
                            onClick = { onHash(); onDismiss() }
                        )
                        MenuRow(
                            icon = Icons.Default.Memory,
                            text = "Hex 查看",
                            enabled = singleFile,
                            onClick = { onHex(); onDismiss() }
                        )
                        MenuRow(
                            icon = Icons.Default.FolderZip,
                            text = "添加到压缩包",
                            enabled = hasArchive && targets.size > 1,
                            onClick = { onAddToZip(); onDismiss() }
                        )
                        MenuRow(
                            icon = Icons.Default.Unarchive,
                            text = "测试压缩包",
                            enabled = hasArchive,
                            onClick = { onTestZip(); onDismiss() }
                        )
                    }
                }
            },
            onClick = { toolsExpanded = true }
        )

        MenuRow(
            icon = Icons.Default.FolderZip,
            text = "压缩",
            enabled = noZipEntries,
            onClick = { onCompress(); onDismiss() }
        )
        MenuRow(
            icon = Icons.Default.Info,
            text = "属性",
            onClick = { onProperties(); onDismiss() }
        )
        MenuRow(
            icon = Icons.Default.Share,
            text = "分享",
            onClick = { onShare(); onDismiss() }
        )
        if (item.fileKind == FileKind.ARCHIVE && !batchMode) {
            MenuRow(
                icon = Icons.Default.Unarchive,
                text = "解压到...",
                onClick = { onExtractTo(); onDismiss() }
            )
        }
        if (item.fileKind == FileKind.APK && !batchMode) {
            // Read-only signature display — no verification, no modification.
            MenuRow(
                icon = Icons.Default.VerifiedUser,
                text = "签名信息",
                onClick = { onSignatureInfo(); onDismiss() }
            )
        }
        HorizontalDivider()
        MenuRow(
            icon = Icons.Default.OpenInNew,
            text = "打开方式...",
            enabled = !batchMode,
            onClick = { onOpenWith(); onDismiss() }
        )
        MenuRow(
            icon = Icons.Default.Bookmark,
            text = "添加书签",
            enabled = !batchMode,
            onClick = { onAddBookmark(); onDismiss() }
        )
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    enabled: Boolean = true
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick,
        enabled = enabled
    )
}

/**
 * "解压到..." destination picker: target directory + optional password
 * (pre-filled from the remembered password when available).
 */
@Composable
fun ExtractToDialog(
    archiveName: String,
    initialDest: String,
    needsPassword: Boolean,
    initialPassword: String = "",
    onDismiss: () -> Unit,
    onConfirm: (dest: String, password: String) -> Unit
) {
    var dest by remember { mutableStateOf(initialDest) }
    var password by remember { mutableStateOf(initialPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解压到") },
        text = {
            Column {
                Text(
                    text = archiveName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = dest,
                    onValueChange = { dest = it },
                    singleLine = true,
                    label = { Text("目标目录") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsPassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (initialPassword.isNotEmpty()) {
                        Text(
                            "已记住该压缩包密码，可直接解压。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dest.trim(), password) },
                enabled = dest.isNotBlank()
            ) { Text("解压") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
