package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Publish
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.FileKind
import com.mtlite.app.ui.theme.MTStyle

private class MenuCell(
    val icon: ImageVector,
    val text: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Official MT style long-press popup: centred two-column grid on a flat
 * #FAFAFA panel. [targets] is what the actions operate on (the pressed item,
 * or the whole selection in batch mode).
 */
@Composable
fun LongPressMenu(
    expanded: Boolean,
    item: FileItem,
    targets: List<FileItem>,
    batchMode: Boolean,
    otherPaneIsZip: Boolean = false,
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
    if (!expanded) return
    val inZip = item.isZipEntry
    var toolsExpanded by remember { mutableStateOf(false) }

    val hasArchive = targets.any { it.fileKind == FileKind.ARCHIVE && !it.isZipEntry }
    val noZipEntries = targets.none { it.isZipEntry }
    val singleFile = !batchMode && !item.isDirectory

    fun act(block: () -> Unit): () -> Unit = { block(); onDismiss() }

    val cells = mutableListOf<MenuCell>()
    if (inZip) {
        cells += MenuCell(Icons.Default.Unarchive, "解压到另一窗口") { act(onExtractToOtherPane)() }
        cells += MenuCell(Icons.Default.Check, "打开方式...") { act(onOpenWith)() }
        cells += MenuCell(Icons.Default.Error, "属性") { act(onProperties)() }
    } else {
        cells += MenuCell(
            Icons.Default.Add,
            if (otherPaneIsZip) "<- 添加到压缩包" else "<- 添加"
        ) { act(onCopy)() }
        cells += MenuCell(Icons.Default.ContentCut, "<- 移动", !otherPaneIsZip) { act(onMove)() }
        cells += MenuCell(Icons.Default.Delete, "删除") { act(onDelete)() }
        cells += MenuCell(Icons.Default.Edit, if (batchMode) "批量重命名" else "重命名") { act(onRename)() }
        cells += MenuCell(Icons.Default.Build, "工具") { toolsExpanded = true }
        cells += MenuCell(Icons.Default.Download, "压缩", noZipEntries) { act(onCompress)() }
        cells += MenuCell(Icons.Default.Error, "属性") { act(onProperties)() }
        cells += MenuCell(Icons.Default.Share, "分享") { act(onShare)() }
        cells += MenuCell(Icons.Default.Check, "打开方式...", !batchMode) { act(onOpenWith)() }
        cells += MenuCell(Icons.Default.CollectionsBookmark, "添加书签", !batchMode) { act(onAddBookmark)() }
        if (item.fileKind == FileKind.ARCHIVE && !batchMode) {
            cells += MenuCell(Icons.Default.Unarchive, "解压到...") { act(onExtractTo)() }
        }
        if (item.fileKind == FileKind.APK && !batchMode) {
            // Read-only signature display — no verification, no modification.
            cells += MenuCell(Icons.Default.VerifiedUser, "签名信息") { act(onSignatureInfo)() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            shape = RoundedCornerShape(2.dp),
            color = MTStyle.barBg(),
            shadowElevation = 12.dp
        ) {
            Column {
                if (batchMode) {
                    Text(
                        text = "带 ● 的菜单表示可以长按触发单窗口操作",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                cells.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { cell ->
                            Box(Modifier.weight(1f)) {
                                MenuGridCell(cell)
                                if (cell.text == "工具" && !inZip) {
                                    DropdownMenu(
                                        expanded = toolsExpanded,
                                        onDismissRequest = { toolsExpanded = false },
                                        shadowElevation = 8.dp
                                    ) {
                                        MTMenuItem("批量重命名", Icons.Default.Edit, { onBatchRename(); onDismiss() }, batchMode && noZipEntries)
                                        MTMenuItem("修改权限", Icons.Default.Security, { onChmod(); onDismiss() }, noZipEntries)
                                        MTMenuItem("计算哈希 (SHA-256)", Icons.Default.Fingerprint, { onHash(); onDismiss() }, singleFile)
                                        MTMenuItem("Hex 查看", Icons.Default.Memory, { onHex(); onDismiss() }, singleFile)
                                        MTMenuItem("添加到压缩包", Icons.Default.FolderZip, { onAddToZip(); onDismiss() }, hasArchive && targets.size > 1)
                                        MTMenuItem("测试压缩包", Icons.Default.Unarchive, { onTestZip(); onDismiss() }, hasArchive)
                                    }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuGridCell(cell: MenuCell) {
    val fg = if (MTStyle.isDark()) Color(0xFFEEEEEE) else Color(0xFF121212)
    val c = if (cell.enabled) fg else fg.copy(alpha = 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = cell.enabled, onClick = cell.onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(cell.icon, contentDescription = null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            cell.text,
            color = c,
            fontSize = 16.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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
