package com.mtlite.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MT-manager style 5-key bottom bar: 后退 / 前进 / 新建 / 同步 / 上级.
 * File operations live in the long-press menu and the overflow menu.
 */
@Composable
fun BottomActionBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomKey(Icons.AutoMirrored.Filled.ArrowBack, "后退", enabled = canGoBack, onClick = onBack)
            BottomKey(Icons.AutoMirrored.Filled.ArrowForward, "前进", enabled = canGoForward, onClick = onForward)
            BottomKey(Icons.Default.ArrowUpward, "上级", onClick = onUp)
            BottomKey(Icons.Default.SyncAlt, "同步", onClick = onSync)
            BottomKey(Icons.Default.Add, "新建", onClick = onNew)
        }
    }
}

@Composable
private fun BottomKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
        )
    }
}
