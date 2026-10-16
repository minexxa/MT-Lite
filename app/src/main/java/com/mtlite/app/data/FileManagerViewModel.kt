package com.mtlite.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtlite.app.util.FileHelper
import kotlinx.coroutines.launch

class FileManagerViewModel : ViewModel() {

    var leftPane by mutableStateOf(PaneState())
        private set
    var rightPane by mutableStateOf(PaneState())
        private set

    var activePane by mutableStateOf(PaneSide.LEFT)
        private set

    var clipboard by mutableStateOf(ClipboardData())
        private set

    var showJumpDialog by mutableStateOf(false)
    var showNewDialog by mutableStateOf(false)
    var showDrawer by mutableStateOf(false)
    var showTextEditor by mutableStateOf(false)
    var textEditorPath by mutableStateOf("")
    var textEditorContent by mutableStateOf("")

    var toastMessage by mutableStateOf<String?>(null)

    init {
        refreshPane(PaneSide.LEFT)
        refreshPane(PaneSide.RIGHT)
    }

    fun switchActivePane(side: PaneSide) {
        activePane = side
    }

    fun getActivePaneState(): PaneState =
        if (activePane == PaneSide.LEFT) leftPane else rightPane

    private fun updatePane(side: PaneSide, update: (PaneState) -> PaneState) {
        if (side == PaneSide.LEFT) leftPane = update(leftPane)
        else rightPane = update(rightPane)
    }

    fun refreshPane(side: PaneSide) {
        val current = if (side == PaneSide.LEFT) leftPane else rightPane
        updatePane(side) { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = FileHelper.listFiles(current.currentPath, current.showHidden)
            updatePane(side) {
                it.copy(items = items, isLoading = false, selectedItems = emptySet())
            }
        }
    }

    fun navigateTo(side: PaneSide, path: String) {
        updatePane(side) { state ->
            val newHistory = state.history.take(state.historyIndex + 1) + path
            state.copy(
                currentPath = path,
                history = newHistory,
                historyIndex = newHistory.lastIndex,
                selectedItems = emptySet()
            )
        }
        refreshPane(side)
    }

    fun goBack(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        if (state.historyIndex > 0) {
            val newIndex = state.historyIndex - 1
            val path = state.history[newIndex]
            updatePane(side) {
                it.copy(currentPath = path, historyIndex = newIndex, selectedItems = emptySet())
            }
            refreshPane(side)
        }
    }

    fun goForward(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        if (state.historyIndex < state.history.lastIndex) {
            val newIndex = state.historyIndex + 1
            val path = state.history[newIndex]
            updatePane(side) {
                it.copy(currentPath = path, historyIndex = newIndex, selectedItems = emptySet())
            }
            refreshPane(side)
        }
    }

    fun goUp(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val parent = java.io.File(state.currentPath).parent ?: return
        navigateTo(side, parent)
    }

    fun syncPanes(from: PaneSide) {
        val source = if (from == PaneSide.LEFT) leftPane else rightPane
        val target = if (from == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT
        navigateTo(target, source.currentPath)
    }

    fun toggleSelect(side: PaneSide, path: String) {
        updatePane(side) { state ->
            val newSelected = if (path in state.selectedItems) {
                state.selectedItems - path
            } else {
                state.selectedItems + path
            }
            state.copy(selectedItems = newSelected)
        }
    }

    fun selectAll(side: PaneSide) {
        updatePane(side) { state ->
            val all = state.items.filter { it.name != ".." }.map { it.path }.toSet()
            state.copy(selectedItems = all)
        }
    }

    fun clearSelection(side: PaneSide) {
        updatePane(side) { it.copy(selectedItems = emptySet()) }
    }

    fun copySelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems }
        clipboard = ClipboardData(items = items, isCut = false, sourcePath = state.currentPath)
        toastMessage = "已复制 ${items.size} 项"
        clearSelection(side)
    }

    fun cutSelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems }
        clipboard = ClipboardData(items = items, isCut = true, sourcePath = state.currentPath)
        toastMessage = "已剪切 ${items.size} 项"
        clearSelection(side)
    }

    fun paste(side: PaneSide) {
        if (clipboard.items.isEmpty()) return
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val dest = state.currentPath
        viewModelScope.launch {
            var success = 0
            for (item in clipboard.items) {
                val ok = if (clipboard.isCut) {
                    FileHelper.moveFile(item, dest)
                } else {
                    FileHelper.copyFile(item, dest)
                }
                if (ok) success++
            }
            if (clipboard.isCut) clipboard = ClipboardData()
            toastMessage = "完成 $success/${clipboard.items.size}"
            refreshPane(side)
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    fun deleteSelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems }
        viewModelScope.launch {
            var success = 0
            for (item in items) {
                if (FileHelper.deleteFile(item)) success++
            }
            toastMessage = "已删除 $success 项"
            clearSelection(side)
            refreshPane(side)
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    fun createNew(name: String, isDir: Boolean) {
        val state = getActivePaneState()
        viewModelScope.launch {
            val ok = FileHelper.createFile(state.currentPath, name, isDir)
            toastMessage = if (ok) "创建成功" else "创建失败"
            refreshPane(activePane)
        }
        showNewDialog = false
    }

    fun openTextEditor(path: String) {
        viewModelScope.launch {
            val content = FileHelper.readTextFile(path) ?: ""
            textEditorPath = path
            textEditorContent = content
            showTextEditor = true
        }
    }

    fun saveTextEditor(content: String) {
        viewModelScope.launch {
            val ok = FileHelper.writeTextFile(textEditorPath, content)
            toastMessage = if (ok) "保存成功" else "保存失败"
            showTextEditor = false
        }
    }

    fun onItemClick(side: PaneSide, item: FileItem) {
        if (item.name == "..") {
            navigateTo(side, item.path)
            return
        }
        if (item.isDirectory || (item.isZipEntry && item.isDirectory)) {
            navigateTo(side, item.path)
        } else if (item.path.endsWith(".zip", true) || item.path.endsWith(".apk", true)) {
            navigateTo(side, "zip://${item.path}")
        } else if (isTextFile(item.name)) {
            openTextEditor(item.path)
        }
    }

    private fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf(
            "txt", "md", "json", "xml", "html", "htm", "css", "js", "ts",
            "kt", "java", "py", "sh", "bat", "properties", "gradle", "kts",
            "c", "cpp", "h", "hpp", "log", "ini", "conf", "yaml", "yml"
        )
    }

    fun clearToast() {
        toastMessage = null
    }
}
