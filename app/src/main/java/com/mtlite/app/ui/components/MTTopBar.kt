package com.mtlite.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtlite.app.ui.theme.MTStyle
import com.mtlite.app.ui.theme.ShadowBelow

/**
 * Official MT Manager style toolbar: 56dp, #303030, 18sp path + 12sp info line,
 * with a soft elevation shadow below it.
 */
@Composable
fun MTTopBar(
    title: String,
    subtitle: String,
    onNavigationClick: () -> Unit,
    onTitleClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit
) {
    Box(modifier.fillMaxWidth()) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MTStyle.ToolbarBg)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(MTStyle.ToolbarHeight)
            ) {
                // navigation icon: 56dp wide, icon centred at x = 28dp
                IconButton(
                    onClick = onNavigationClick,
                    modifier = Modifier.size(56.dp).align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "菜单", tint = Color.White)
                }
                // title block, text origin at 60dp, baselines at 27.7dp / 44.7dp
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = 60.dp, end = 48.dp)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            onClick = onTitleClick
                        )
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.paddingFromBaseline(top = 27.7.dp)
                    )
                    Text(
                        text = subtitle,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.paddingFromBaseline(top = 44.7.dp)
                    )
                }
                // actions: last 48dp button flush with the right edge
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Row(
                        Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
        // shadow drawn below the bar, overlapping the content
        Box(Modifier.align(Alignment.BottomStart).offset(y = MTStyle.ShadowSize)) {
            ShadowBelow()
        }
    }
}
