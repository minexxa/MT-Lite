package com.mtlite.app.ui.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.mtlite.app.ui.theme.MTStyle

/** Black icon + black text, like the official MT Manager menus. */
@Composable
fun mtMenuColors(): MenuItemColors {
    val fg = if (MTStyle.isDark()) Color(0xFFEEEEEE) else Color(0xFF121212)
    val off = fg.copy(alpha = 0.38f)
    return MenuDefaults.itemColors(
        textColor = fg,
        leadingIconColor = fg,
        trailingIconColor = fg.copy(alpha = 0.6f),
        disabledTextColor = off,
        disabledLeadingIconColor = off,
        disabledTrailingIconColor = off
    )
}

@Composable
fun MTMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    DropdownMenuItem(
        text = { Text(text, fontSize = 16.sp, letterSpacing = 0.sp) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = trailing,
        onClick = onClick,
        enabled = enabled,
        colors = mtMenuColors()
    )
}
