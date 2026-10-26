package com.ma.manager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.data.FileItem
import com.ma.manager.data.FileKind
import com.ma.manager.data.OpenChoice

/**
 * "打开方式..." dialog (图三). Offered when a file has no default handler
 * (unknown extension) or when the user picks it from the long-press menu.
 */
@Composable
fun OpenWithDialog(
    item: FileItem,
    onDismiss: () -> Unit,
    onChoice: (OpenChoice) -> Unit
) {
    val options = buildList {
        add(OpenChoice.TEXT_EDITOR to "文本编辑器")
        add(OpenChoice.IMAGE_VIEWER to "图片查看")
        add(OpenChoice.MEDIA_PLAYER to "内置播放器")
        add(OpenChoice.HEX_VIEWER to "Hex 查看")
        add(OpenChoice.SYSTEM_APP to "系统应用打开")
        if (item.fileKind == FileKind.APK) add(OpenChoice.INSTALL to "安装")
        add(OpenChoice.SHARE to "分享")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打开方式") },
        text = {
            Column {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { (choice, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoice(choice) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = iconForChoice(choice),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(text = label, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun iconForChoice(choice: OpenChoice): ImageVector = when (choice) {
    OpenChoice.TEXT_EDITOR -> Icons.Default.Article
    OpenChoice.IMAGE_VIEWER -> Icons.Default.Image
    OpenChoice.MEDIA_PLAYER -> Icons.Default.PlayCircle
    OpenChoice.HEX_VIEWER -> Icons.Default.Memory
    OpenChoice.SYSTEM_APP -> Icons.Default.OpenInNew
    OpenChoice.INSTALL -> Icons.Default.InstallMobile
    OpenChoice.SHARE -> Icons.Default.Share
}
