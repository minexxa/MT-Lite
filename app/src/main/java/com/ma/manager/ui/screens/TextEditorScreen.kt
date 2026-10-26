package com.ma.manager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ma.manager.util.CodeFolding
import com.ma.manager.util.CodeLanguage
import com.ma.manager.util.EditorThemes
import com.ma.manager.util.FoldRange
import com.ma.manager.util.SyntaxHighlighter
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

/**
 * MA 管理器风格的代码/文本编辑器：
 *  - 顶部工具栏（菜单 / 跳转行 / 撤销 / 重做 / 保存 / 编辑模式 / 更多）
 *  - 信息行：文件名 · 行:列 · 字符编码（可点击切换）
 *  - 行号槽 + 代码折叠标记（点击折叠/展开，点击行号选中整行）
 *  - 多语言语法高亮（YAML/JSON/XML/Kotlin/Java/Python/Go/C/...）
 *  - 底部符号栏：→ / + - * = <
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    path: String,
    initialContent: String,
    readOnly: Boolean = false,
    wrap: Boolean = false,
    charset: Charset = StandardCharsets.UTF_8,
    fileBytes: ByteArray? = null,
    onSave: (String, Charset) -> Unit,
    onClose: () -> Unit
) {
    val fileName = File(path).name
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val palette = if (isDark) EditorThemes.Dark else EditorThemes.Light

    var textValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialContent,
                selection = if (readOnly) TextRangeZero else TextRange(initialContent.length)
            )
        )
    }
    var dirty by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(!readOnly) }
    var currentCharset by remember { mutableStateOf(charset) }
    var lang by remember { mutableStateOf(CodeLanguage.detect(fileName)) }
    var langAuto by remember { mutableStateOf(true) }
    var wrapEnabled by remember { mutableStateOf(wrap) }
    var fontSize by remember { mutableStateOf(13f) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }

    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    var showGoto by remember { mutableStateOf(false) }
    var gotoText by remember { mutableStateOf("") }
    var showCharset by remember { mutableStateOf(false) }
    var showLang by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    var suppressPush by remember { mutableStateOf(false) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var editorHeightPx by remember { mutableStateOf(0) }
    val gutterMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()

    val lineHeightSp = fontSize * 1.4f
    val lineHeightPx = with(density) { lineHeightSp.sp.toPx() }
    val monoStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = lineHeightSp.sp,
        color = palette.plain
    )

    val text = textValue.text
    val lines = remember(text) { text.split('\n') }
    val highlighted = remember(text, lang, palette) { SyntaxHighlighter.highlightLines(text, lang, palette) }
    val foldRanges = remember(text, lang) { CodeFolding.compute(text, lang) }
    val foldByStart = remember(foldRanges) { foldRanges.associateBy { it.startLine } }
    val lineStarts = remember(lines) {
        val arr = IntArray(lines.size + 1)
        var acc = 0
        for (i in lines.indices) { arr[i] = acc; acc += lines[i].length + 1 }
        arr[lines.size] = acc
        arr
    }
    val visibleLines = remember(lines, foldRanges, collapsed) {
        val hidden = mutableSetOf<Int>()
        for (f in foldRanges) {
            if (f.startLine in collapsed) for (l in f.startLine + 1..f.endLine) if (l < lines.size) hidden.add(l)
        }
        lines.indices.filter { it !in hidden }
    }
    val padTopPx = with(density) { 4.dp.toPx() }

    // Exact top offset (content px) of the vi-th visible line, taken from the real
    // text layout so gutter numbers never drift from their lines; uniform-grid
    // fallback before the first layout pass.
    fun gutterLineTop(vi: Int): Float {
        val l = layoutResult
        return if (l != null && vi in 0 until l.lineCount) padTopPx + l.getLineTop(vi)
        else padTopPx + vi * lineHeightPx
    }

    // Inverse of gutterLineTop: content y -> original line index.
    fun gutterLineAt(y: Float): Int {
        val l = layoutResult
        val vi = if (l != null && l.lineCount > 0) {
            var lo = 0
            var hi = l.lineCount - 1
            var ans = 0
            val target = y - padTopPx
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (l.getLineTop(mid) <= target) { ans = mid; lo = mid + 1 } else hi = mid - 1
            }
            ans
        } else ((y - padTopPx) / lineHeightPx).toInt().coerceAtLeast(0)
        return visibleLines.getOrElse(vi) { lines.size - 1 }
    }

    val transformation = remember(highlighted, foldRanges, collapsed, lines) {
        EditorTransformation(lines, highlighted, foldRanges, collapsed, lineStarts)
    }

    // Cursor position (line:col) for the info row.
    val cursorOffset = textValue.selection.start.coerceIn(0, text.length)
    val cursorLine = remember(text, cursorOffset) { text.substring(0, cursorOffset).count { it == '\n' } + 1 }
    val cursorCol = remember(text, cursorOffset) {
        cursorOffset - text.lastIndexOf('\n', (cursorOffset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 } + 1
    }

    val matches = remember(findText, text) {
        if (findText.isEmpty()) emptyList()
        else {
            val list = mutableListOf<Int>()
            var idx = text.indexOf(findText)
            while (idx >= 0) { list.add(idx); idx = text.indexOf(findText, idx + findText.length) }
            list
        }
    }

    fun pushHistory(v: TextFieldValue) {
        if (suppressPush) return
        undoStack.add(v)
        if (undoStack.size > 100) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun onEdit(nv: TextFieldValue) {
        val old = textValue
        var result = nv
        // Auto-indent: typing a newline carries over the current line's indentation.
        if (nv.text.length == old.text.length + 1) {
            val p = commonPrefixLength(old.text, nv.text)
            if (p < nv.text.length && nv.text[p] == '\n') {
                val lineStart = old.text.lastIndexOf('\n', p - 1) + 1
                val indent = old.text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
                if (indent.isNotEmpty()) {
                    val newText = StringBuilder(nv.text).insert(p + 1, indent).toString()
                    result = TextFieldValue(newText, TextRange(p + 1 + indent.length))
                }
            }
        }
        pushHistory(old)
        textValue = result
        dirty = true
    }

    fun insertSymbol(sym: String) {
        val sel = textValue.selection
        val newText = text.replaceRange(sel.start, sel.end, sym)
        pushHistory(textValue)
        textValue = TextFieldValue(newText, TextRange(sel.start + sym.length))
        dirty = true
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        suppressPush = true
        redoStack.add(textValue)
        textValue = prev
        suppressPush = false
        dirty = true
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        suppressPush = true
        undoStack.add(textValue)
        textValue = next
        suppressPush = false
        dirty = true
    }

    fun jumpToLine(line: Int) {
        val idx = (line - 1).coerceIn(0, lines.size - 1)
        val offset = lineStarts[idx]
        suppressPush = true
        textValue = textValue.copy(selection = TextRange(offset, offset))
        suppressPush = false
        scope.launch {
            layoutResult?.let { layout ->
                val rect = layout.getCursorRect(offset)
                vScroll.scrollTo(rect.top.toInt())
                if (!wrapEnabled) hScroll.scrollTo(rect.left.toInt())
            }
        }
    }

    // Keep the cursor visible while typing / navigating.
    LaunchedEffect(textValue.selection, text, viewport) {
        val layout = layoutResult ?: return@LaunchedEffect
        if (viewport.height == 0) return@LaunchedEffect
        val rect = layout.getCursorRect(cursorOffset)
        val top = vScroll.value.toFloat()
        val bottom = top + viewport.height
        if (rect.top < top) vScroll.scrollTo(rect.top.toInt())
        else if (rect.bottom > bottom) vScroll.scrollTo((rect.bottom - viewport.height).toInt().coerceAtLeast(0))
        if (!wrapEnabled && viewport.width > 0) {
            val left = hScroll.value.toFloat()
            val right = left + viewport.width
            if (rect.left < left) hScroll.scrollTo(rect.left.toInt())
            else if (rect.right > right) hScroll.scrollTo((rect.right - viewport.width).toInt().coerceAtLeast(0))
        }
    }

    fun requestClose() {
        if (dirty) showCloseConfirm = true else onClose()
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            Surface(
                color = com.ma.manager.ui.theme.MAStyle.ToolbarBg,
                contentColor = androidx.compose.ui.graphics.Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    // Row 1: MA-style icon toolbar
                    Row(
                        Modifier.fillMaxWidth().height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            EditorBarIcon(Icons.Default.Menu, "菜单") { showMenu = true }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (dirty) "保存 *" else "保存") },
                                    onClick = { showMenu = false; onSave(text, currentCharset); dirty = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("查找替换") },
                                    onClick = { showMenu = false; showFind = !showFind }
                                )
                                DropdownMenuItem(
                                    text = { Text("跳转到行") },
                                    onClick = { showMenu = false; showGoto = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("字符编码") },
                                    onClick = { showMenu = false; showCharset = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("语法语言") },
                                    onClick = { showMenu = false; showLang = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("退出") },
                                    onClick = { showMenu = false; requestClose() }
                                )
                            }
                        }
                        EditorBarIcon(Icons.Default.PushPin, "跳转到行") { showGoto = true }
                        EditorBarIcon(Icons.AutoMirrored.Filled.Undo, "撤销") { undo() }
                        EditorBarIcon(Icons.AutoMirrored.Filled.Redo, "重做") { redo() }
                        EditorBarIcon(Icons.Default.Save, "保存") {
                            onSave(text, currentCharset)
                            dirty = false
                        }
                        EditorBarIcon(
                            Icons.Default.Edit,
                            if (editMode) "切换只读预览" else "切换编辑模式"
                        ) { editMode = !editMode }
                        Spacer(Modifier.weight(1f))
                        Box {
                            EditorBarIcon(Icons.Default.MoreVert, "更多") { showMore = true }
                            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text(if (wrapEnabled) "✓ 自动换行" else "自动换行") },
                                leadingIcon = { Icon(Icons.Default.WrapText, null) },
                                onClick = { showMore = false; wrapEnabled = !wrapEnabled }
                            )
                            DropdownMenuItem(
                                text = { Text("增大字号") },
                                leadingIcon = { Icon(Icons.Default.FormatSize, null) },
                                onClick = {
                                    showMore = false
                                    fontSize = (fontSize + 1f).coerceIn(9f, 24f)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("减小字号") },
                                leadingIcon = { Icon(Icons.Default.FormatSize, null) },
                                onClick = {
                                    showMore = false
                                    fontSize = (fontSize - 1f).coerceIn(9f, 24f)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (editMode) "切换只读预览" else "切换编辑模式") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showMore = false; editMode = !editMode }
                            )
                            DropdownMenuItem(
                                text = { Text("全选") },
                                onClick = {
                                    showMore = false
                                    suppressPush = true
                                    textValue = textValue.copy(selection = TextRange(0, text.length))
                                    suppressPush = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭") },
                                onClick = { showMenu = false; showMore = false; requestClose() }
                            )
                            }
                        }
                    }
                    // Row 2: file name · line:col · charset
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fileName + if (dirty) " •" else "",
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$cursorLine:$cursorCol",
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFCCCCCC),
                            modifier = Modifier.clickable { showGoto = true }.padding(horizontal = 6.dp)
                        )
                        Text(
                            charsetName(currentCharset),
                            fontSize = 11.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF64B5F6),
                            modifier = Modifier.clickable { showCharset = true }.padding(horizontal = 4.dp)
                        )
                    }
                    if (showFind) {
                        FindReplaceBar(
                            findText = findText,
                            onFindChange = { findText = it; matchIndex = 0 },
                            replaceText = replaceText,
                            onReplaceChange = { replaceText = it },
                            matchCount = matches.size,
                            matchIndex = matchIndex,
                            onPrev = {
                                if (matches.isNotEmpty()) {
                                    matchIndex = (matchIndex - 1 + matches.size) % matches.size
                                    textValue = selectMatch(text, matches[matchIndex], findText.length)
                                }
                            },
                            onNext = {
                                if (matches.isNotEmpty()) {
                                    matchIndex = (matchIndex + 1) % matches.size
                                    textValue = selectMatch(text, matches[matchIndex], findText.length)
                                }
                            },
                            onReplaceOne = {
                                val m = matches.getOrNull(matchIndex)
                                if (m != null) {
                                    val newText = text.replaceRange(m, m + findText.length, replaceText)
                                    pushHistory(textValue)
                                    textValue = TextFieldValue(newText, TextRange(m + replaceText.length))
                                    dirty = true
                                }
                            },
                            onReplaceAll = {
                                if (findText.isNotEmpty()) {
                                    val count = matches.size
                                    val newText = text.replace(findText, replaceText)
                                    pushHistory(textValue)
                                    textValue = TextFieldValue(newText, TextRange(0))
                                    dirty = true
                                    statusMsg = "已替换 $count 处"
                                }
                            },
                            onClose = { showFind = false }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("→", "/", "+", "-", "*", "=", "<").forEach { sym ->
                        Text(
                            sym,
                            style = monoStyle.copy(fontSize = 17.sp),
                            modifier = Modifier
                                .clickable { insertSymbol(sym) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(palette.background)
                .onGloballyPositioned { viewport = it.size }
        ) {
            // One vertical scroll container shared by the gutter and the text field,
            // so line numbers stay locked to their lines at every scroll offset.
            Column(Modifier.fillMaxWidth().verticalScroll(vScroll)) {
            Row(Modifier.fillMaxWidth()) {
                // Line-number + fold gutter (only meaningful without soft wrap)
                if (!wrapEnabled) {
                    val gutterWidth = 64.dp
                    val contentHeight = with(density) {
                        if (editorHeightPx > 0) editorHeightPx.toDp()
                        else ((lines.size + 1) * lineHeightPx).toDp()
                    }
                    val gutterStyle = monoStyle.copy(
                        fontSize = (fontSize * 0.82f).sp,
                        lineHeight = lineHeightSp.sp
                    )
                    Canvas(
                        Modifier
                            .width(gutterWidth)
                            .height(contentHeight)
                            .pointerInput(foldRanges, collapsed, lines.size, lineHeightPx) {
                                detectTapGestures { pos ->
                                    val line = gutterLineAt(pos.y)
                                    val fold = foldByStart[line]
                                    val foldZonePx = with(density) { 30.dp.toPx() }
                                    if (fold != null && pos.x < foldZonePx) {
                                        collapsed = if (line in collapsed) collapsed - line else collapsed + line
                                    } else {
                                        val start = lineStarts[line]
                                        val end = (start + lines[line].length).coerceAtMost(text.length)
                                        suppressPush = true
                                        textValue = textValue.copy(selection = TextRange(start, end))
                                        suppressPush = false
                                    }
                                }
                            }
                    ) {
                        val first = ((vScroll.value - padTopPx) / lineHeightPx).toInt().coerceAtLeast(0)
                        val count = (viewport.height / lineHeightPx).toInt() + 3
                        val numberX = size.width - with(density) { 34.dp.toPx() }
                        val markerX = with(density) { 4.dp.toPx() }
                        val previewX = with(density) { 16.dp.toPx() }
                        for (vi in first until minOf(visibleLines.size, first + count)) {
                            val i = visibleLines[vi]
                            val y = gutterLineTop(vi)
                            val fold = foldByStart[i]
                            if (fold != null) {
                                val marker = if (i in collapsed) "▸" else "▾"
                                drawText(
                                    gutterMeasurer, marker,
                                    topLeft = Offset(markerX, y),
                                    style = gutterStyle.copy(color = palette.gutterActive)
                                )
                                val preview = lines[i].trim().take(5)
                                if (preview.isNotEmpty()) {
                                    drawText(
                                        gutterMeasurer, preview,
                                        topLeft = Offset(previewX, y),
                                        style = gutterStyle.copy(
                                            color = palette.gutter,
                                            fontSize = (fontSize * 0.7f).sp
                                        )
                                    )
                                }
                            } else {
                                drawText(
                                    gutterMeasurer, "${i + 1}",
                                    topLeft = Offset(numberX, y),
                                    style = gutterStyle.copy(
                                        color = if (i == cursorLine - 1) palette.gutterActive else palette.gutter
                                    )
                                )
                            }
                        }
                    }
                }

                // Editor surface
                Box(
                    Modifier
                        .weight(1f)
                        .then(if (wrapEnabled) Modifier.fillMaxWidth() else Modifier.horizontalScroll(hScroll))
                        .onGloballyPositioned { editorHeightPx = it.size.height }
                ) {
                    if (text.isEmpty()) {
                        Text(
                            if (readOnly) "只读文件" else "空文件 — 开始输入",
                            style = monoStyle.copy(color = palette.gutter),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    BasicTextField(
                        value = textValue,
                        onValueChange = { nv ->
                            if (nv.text != textValue.text) onEdit(nv) else textValue = nv
                        },
                        readOnly = !editMode,
                        textStyle = monoStyle,
                        cursorBrush = SolidColor(palette.cursor),
                        visualTransformation = transformation,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier
                            .then(
                                if (wrapEnabled) Modifier.fillMaxWidth()
                                else Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            }

            statusMsg?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                ) {
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- dialogs

    if (showGoto) {
        AlertDialog(
            onDismissRequest = { showGoto = false },
            title = { Text("跳转到行") },
            text = {
                OutlinedTextField(
                    value = gotoText,
                    onValueChange = { gotoText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    label = { Text("行号 (1 - ${lines.size})") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val line = gotoText.toIntOrNull()
                    if (line != null) jumpToLine(line)
                    showGoto = false
                }) { Text("跳转") }
            },
            dismissButton = { TextButton(onClick = { showGoto = false }) { Text("取消") } }
        )
    }

    if (showCharset) {
        val options = listOf(
            "UTF-8" to StandardCharsets.UTF_8,
            "UTF-16 LE" to Charset.forName("UTF-16LE"),
            "UTF-16 BE" to Charset.forName("UTF-16BE"),
            "GBK" to Charset.forName("GBK"),
            "GB18030" to Charset.forName("GB18030"),
            "Big5" to Charset.forName("Big5"),
            "ISO-8859-1" to StandardCharsets.ISO_8859_1,
            "Windows-1252" to Charset.forName("windows-1252"),
            "US-ASCII" to StandardCharsets.US_ASCII
        )
        AlertDialog(
            onDismissRequest = { showCharset = false },
            title = { Text("字符编码") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { (name, cs) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCharset = false
                                    if (cs == currentCharset) return@clickable
                                    currentCharset = cs
                                    val bytes = fileBytes
                                    if (bytes != null) {
                                        val decoded = runCatching { String(bytes, cs) }.getOrNull()
                                        if (decoded != null) {
                                            undoStack.clear(); redoStack.clear()
                                            textValue = TextFieldValue(decoded, TextRange(0))
                                            dirty = true
                                            statusMsg = "已按 $name 重新解码"
                                        } else {
                                            statusMsg = "无法按 $name 解码，仅更改保存编码"
                                        }
                                    } else {
                                        statusMsg = "保存编码已设为 $name"
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cs == currentCharset, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCharset = false }) { Text("关闭") } }
        )
    }

    if (showLang) {
        AlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text("语法高亮语言") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            showLang = false
                            langAuto = true
                            lang = CodeLanguage.detect(fileName)
                        }.padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = langAuto, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("自动检测（${CodeLanguage.detect(fileName).displayName}）")
                    }
                    CodeLanguage.entries.forEach { cl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                showLang = false
                                langAuto = false
                                lang = cl
                            }.padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !langAuto && lang == cl, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(cl.displayName, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text("关闭") } }
        )
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("文件已修改") },
            text = { Text("是否保存对 $fileName 的更改？") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirm = false
                    onSave(text, currentCharset)
                    dirty = false
                    onClose()
                }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false; onClose() }) { Text("放弃更改") }
            }
        )
    }

    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            kotlinx.coroutines.delay(2500)
            statusMsg = null
        }
    }
}

private val TextRangeZero = androidx.compose.ui.text.TextRange(0)

@Composable
private fun EditorBarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FindReplaceBar(
    findText: String,
    onFindChange: (String) -> Unit,
    replaceText: String,
    onReplaceChange: (String) -> Unit,
    matchCount: Int,
    matchIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
    Column(Modifier.padding(horizontal = 8.dp).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = findText,
                onValueChange = onFindChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("查找内容", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon = {
                    Text(
                        if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
                        fontSize = 11.sp
                    )
                }
            )
            IconButton(onClick = onPrev) { Text("↑", fontSize = 14.sp) }
            IconButton(onClick = onNext) { Text("↓", fontSize = 14.sp) }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "关闭查找", Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replaceText,
                onValueChange = onReplaceChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("替换为", fontSize = 12.sp) }
            )
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = onReplaceOne, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("替换", fontSize = 12.sp)
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick = onReplaceAll, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("全部", fontSize = 12.sp)
            }
        }
    }
    }
}

private fun charsetName(cs: Charset): String = when (cs.name()) {
    "UTF-8" -> "UTF-8"
    "UTF-16LE" -> "UTF-16 LE"
    "UTF-16BE" -> "UTF-16 BE"
    else -> cs.name().uppercase()
}

private fun commonPrefixLength(a: String, b: String): Int {
    val n = minOf(a.length, b.length)
    var i = 0
    while (i < n && a[i] == b[i]) i++
    return i
}

private fun selectMatch(text: String, start: Int, length: Int): TextFieldValue =
    TextFieldValue(text, TextRange(start, start + length))

/** Offset mapping that hides folded lines from the transformed text. */
private class FoldOffsetMapping(
    private val lines: List<String>,
    private val lineStarts: IntArray,
    private val visible: List<Int>,
    private val transStarts: IntArray,
    private val ownerOf: IntArray,
    private val transformedLength: Int
) : OffsetMapping {

    private fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = lines.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (lineStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    override fun originalToTransformed(offset: Int): Int {
        val line = lineOf(offset.coerceIn(0, maxOf(0, lineStarts[lines.size] - 1)))
        val vi = visible.binarySearch(line)
        if (vi >= 0) return transStarts[vi] + (offset - lineStarts[line])
        val owner = ownerOf.getOrElse(line) { -1 }
        if (owner >= 0) {
            val ovi = visible.binarySearch(owner)
            if (ovi >= 0) return transStarts[ovi] + lines[owner].length
        }
        return transformedLength
    }

    override fun transformedToOriginal(offset: Int): Int {
        if (visible.isEmpty()) return 0
        var lo = 0
        var hi = visible.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (transStarts[mid] <= offset) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        val line = visible[ans]
        val within = (offset - transStarts[ans]).coerceIn(0, lines[line].length)
        return lineStarts[line] + within
    }
}

/**
 * Visual transformation combining syntax highlighting with fold collapsing.
 */
private class EditorTransformation(
    private val lines: List<String>,
    private val highlighted: List<AnnotatedString>?,
    private val foldRanges: List<FoldRange>,
    private val collapsed: Set<Int>,
    private val lineStarts: IntArray
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        if (collapsed.isEmpty() || foldRanges.isEmpty()) {
            val builder = AnnotatedString.Builder()
            if (highlighted != null) {
                for (i in highlighted.indices) {
                    if (i > 0) builder.append('\n')
                    builder.append(highlighted[i])
                }
            } else {
                builder.append(src)
            }
            return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        }

        val hidden = BooleanArray(lines.size)
        val ownerOf = IntArray(lines.size) { -1 }
        for (f in foldRanges) {
            if (f.startLine !in collapsed) continue
            for (l in f.startLine + 1..f.endLine) {
                if (l >= lines.size) continue
                hidden[l] = true
                if (ownerOf[l] < 0) ownerOf[l] = f.startLine
            }
        }
        val visible = lines.indices.filter { !hidden[it] }
        val transStarts = IntArray(visible.size)
        var acc = 0
        visible.forEachIndexed { vi, li ->
            transStarts[vi] = acc
            acc += lines[li].length + 1
        }

        val builder = AnnotatedString.Builder()
        visible.forEachIndexed { vi, li ->
            if (vi > 0) builder.append('\n')
            if (highlighted != null) builder.append(highlighted[li]) else builder.append(lines[li])
        }
        val transformed = builder.toAnnotatedString()
        return TransformedText(
            transformed,
            FoldOffsetMapping(lines, lineStarts, visible, transStarts, ownerOf, transformed.length)
        )
    }
}
