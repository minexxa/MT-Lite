package com.mtlite.app.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mtlite.app.util.ApkInfo
import com.mtlite.app.util.ApkInfoUtil
import com.mtlite.app.util.FileHelper
import com.mtlite.app.util.TransferResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore.get(app)
    private val bookmarks = BookmarkStore.get(app)

    var leftPane by mutableStateOf(
        PaneState(
            currentPath = settings.loadLeftPath(),
            history = listOf(settings.loadLeftPath()),
            sortBy = settings.sortBy,
            sortAscending = settings.sortAscending,
            showHidden = settings.showHidden
        )
    )
        private set

    var rightPane by mutableStateOf(
        PaneState(
            currentPath = settings.loadRightPath(),
            history = listOf(settings.loadRightPath()),
            sortBy = settings.sortBy,
            sortAscending = settings.sortAscending,
            showHidden = settings.showHidden
        )
    )
        private set

    var activePane by mutableStateOf(PaneSide.LEFT)
        private set

    var clipboard by mutableStateOf(ClipboardData())
        private set

    // Dialog visibility
    var showJumpDialog by mutableStateOf(false)
    var showNewDialog by mutableStateOf(false)
    var showDrawer by mutableStateOf(false)
    var showTextEditor by mutableStateOf(false)
    var showSortDialog by mutableStateOf(false)
    var showSearchDialog by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var showPropertiesDialog by mutableStateOf(false)
    var showConflictDialog by mutableStateOf(false)
    var showZipCreateDialog by mutableStateOf(false)
    var showBatchRenameDialog by mutableStateOf(false)
    var showChmodDialog by mutableStateOf(false)
    var showBookmarkAddDialog by mutableStateOf(false)
    var showDeleteConfirm by mutableStateOf(false)

    // Operation progress
    var opProgress by mutableStateOf(OpProgress())
        private set

    var toastMessage by mutableStateOf<String?>(null)

    // Search state
    var searchQuery by mutableStateOf("")
    var searchContent by mutableStateOf(false)

    // Batch rename state
    var batchFind by mutableStateOf("")
    var batchReplace by mutableStateOf("")

    // Conflict resolution
    private var conflictDeferred: CompletableDeferred<ConflictResolution>? = null
    var conflictName by mutableStateOf("")
        private set

    // Properties / apk info
    var propertiesItem by mutableStateOf<FileItem?>(null)
    var apkInfo by mutableStateOf<ApkInfo?>(null)

    // Bookmarks snapshot for UI
    var bookmarkList by mutableStateOf(bookmarks.all())
        private set

    var textEditorPath by mutableStateOf("")
    var textEditorContent by mutableStateOf("")
    var textEditorReadOnly by mutableStateOf(false)

    init {
        refreshPane(PaneSide.LEFT)
        refreshPane(PaneSide.RIGHT)
    }

    // ------------------------------------------------------------- pane basics

    fun switchActivePane(side: PaneSide) {
        activePane = side
    }

    fun getActivePaneState(): PaneState =
        if (activePane == PaneSide.LEFT) leftPane else rightPane

    private fun updatePane(side: PaneSide, update: (PaneState) -> PaneState) {
        if (side == PaneSide.LEFT) leftPane = update(leftPane) else rightPane = update(rightPane)
    }

    fun refreshPane(side: PaneSide) {
        val current = if (side == PaneSide.LEFT) leftPane else rightPane
        updatePane(side) { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = FileHelper.listFiles(
                current.currentPath,
                current.showHidden,
                current.sortBy,
                current.sortAscending,
                settings.foldersFirst
            )
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
                selectedItems = emptySet(),
                searchQuery = null
            )
        }
        refreshPane(side)
        persistPanePaths()
    }

    fun goBack(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        if (state.historyIndex > 0) {
            val newIndex = state.historyIndex - 1
            updatePane(side) {
                it.copy(currentPath = state.history[newIndex], historyIndex = newIndex, selectedItems = emptySet())
            }
            refreshPane(side)
            persistPanePaths()
        }
    }

    fun goForward(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        if (state.historyIndex < state.history.lastIndex) {
            val newIndex = state.historyIndex + 1
            updatePane(side) {
                it.copy(currentPath = state.history[newIndex], historyIndex = newIndex, selectedItems = emptySet())
            }
            refreshPane(side)
            persistPanePaths()
        }
    }

    fun goUp(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val parent = File(state.currentPath).parent ?: return
        navigateTo(side, parent)
    }

    fun syncPanes(from: PaneSide) {
        val source = if (from == PaneSide.LEFT) leftPane else rightPane
        val target = if (from == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT
        navigateTo(target, source.currentPath)
    }

    private fun persistPanePaths() {
        settings.savePanePaths(leftPane.currentPath, rightPane.currentPath)
    }

    // ---------------------------------------------------------------- sorting

    fun setSort(sortBy: SortBy, ascending: Boolean) {
        settings.sortBy = sortBy
        settings.sortAscending = ascending
        updatePane(PaneSide.LEFT) { it.copy(sortBy = sortBy, sortAscending = ascending) }
        updatePane(PaneSide.RIGHT) { it.copy(sortBy = sortBy, sortAscending = ascending) }
        refreshPane(PaneSide.LEFT)
        refreshPane(PaneSide.RIGHT)
        showSortDialog = false
    }

    fun toggleShowHidden() {
        val newValue = !settings.showHidden
        settings.showHidden = newValue
        updatePane(PaneSide.LEFT) { it.copy(showHidden = newValue) }
        updatePane(PaneSide.RIGHT) { it.copy(showHidden = newValue) }
        refreshPane(PaneSide.LEFT)
        refreshPane(PaneSide.RIGHT)
    }

    // --------------------------------------------------------------- selection

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

    fun invertSelection(side: PaneSide) {
        updatePane(side) { state ->
            val all = state.items.filter { it.name != ".." }.map { it.path }.toSet()
            state.copy(selectedItems = all - state.selectedItems)
        }
    }

    // --------------------------------------------------------------- clipboard

    fun copySelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        clipboard = ClipboardData(items = items, isCut = false, sourcePath = state.currentPath)
        toastMessage = "已复制 ${items.size} 项"
        clearSelection(side)
    }

    fun cutSelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        clipboard = ClipboardData(items = items, isCut = true, sourcePath = state.currentPath)
        toastMessage = "已剪切 ${items.size} 项"
        clearSelection(side)
    }

    fun paste(side: PaneSide) {
        if (clipboard.isEmpty) return
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val dest = state.currentPath
        val isCut = clipboard.isCut
        val items = clipboard.items
        viewModelScope.launch {
            val result = if (isCut) {
                FileHelper.moveFiles(items, dest, ::conflictResolver) { opProgress = it }
            } else {
                FileHelper.copyFiles(items, dest, ::conflictResolver) { opProgress = it }
            }
            if (isCut) clipboard = ClipboardData()
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = result.summary
            refreshPane(side)
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    private suspend fun conflictResolver(name: String): ConflictResolution {
        conflictName = name
        showConflictDialog = true
        val deferred = CompletableDeferred<ConflictResolution>()
        conflictDeferred = deferred
        val choice = deferred.await()
        showConflictDialog = false
        conflictDeferred = null
        return choice
    }

    fun resolveConflict(resolution: ConflictResolution) {
        conflictDeferred?.complete(resolution)
    }

    fun dismissProgress() {
        opProgress = OpProgress()
    }

    fun cancelOperation() {
        FileHelper.requestCancel()
        opProgress = opProgress.copy(finished = true, message = "已取消")
    }

    // ----------------------------------------------------------------- delete

    fun deleteSelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        viewModelScope.launch {
            val useBin = settings.useRecycleBin
            var success = 0
            items.forEachIndexed { index, item ->
                opProgress = OpProgress(
                    active = true,
                    title = if (useBin) "移入回收站" else "删除",
                    current = index,
                    total = items.size,
                    currentName = item.name
                )
                if (FileHelper.deleteFile(item, useBin)) success++
            }
            opProgress = OpProgress(finished = true, message = "已删除 $success 项")
            toastMessage = if (useBin) "已移入回收站 $success 项" else "已删除 $success 项"
            clearSelection(side)
            refreshPane(side)
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val ok = FileHelper.emptyRecycleBin()
            toastMessage = if (ok) "回收站已清空" else "清空失败"
            refreshPane(activePane)
        }
    }

    // ------------------------------------------------------- create / rename

    fun createNew(name: String, isDir: Boolean) {
        val state = getActivePaneState()
        viewModelScope.launch {
            val ok = FileHelper.createFile(state.currentPath, name, isDir)
            toastMessage = if (ok) "创建成功" else "创建失败"
            refreshPane(activePane)
        }
        showNewDialog = false
    }

    var renameTarget by mutableStateOf<FileItem?>(null)
        private set

    fun showRename(item: FileItem) {
        renameTarget = item
        showRenameDialog = true
    }

    fun confirmRename(newName: String) {
        val item = renameTarget ?: return
        viewModelScope.launch {
            val ok = FileHelper.renameFile(item, newName)
            toastMessage = if (ok) "已重命名" else "重命名失败"
            showRenameDialog = false
            renameTarget = null
            refreshPane(activePane)
            refreshPane(if (activePane == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    fun batchRenameSelected(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        viewModelScope.launch {
            val result = FileHelper.batchRename(items, batchFind, batchReplace)
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = "批量重命名: ${result.summary}"
            showBatchRenameDialog = false
            clearSelection(side)
            refreshPane(side)
        }
    }

    fun chmodSelected(octal: String) {
        val state = getActivePaneState()
        val items = state.items.filter { it.path in state.selectedItems }
        viewModelScope.launch {
            var ok = 0
            items.forEach { if (FileHelper.chmod(it, octal) != null) ok++ }
            toastMessage = "已修改 $ok/${items.size} 项权限"
            showChmodDialog = false
            refreshPane(activePane)
        }
    }

    // ---------------------------------------------------------------- properties

    fun showProperties(item: FileItem) {
        propertiesItem = item
        showPropertiesDialog = true
        if (item.fileKind == FileKind.APK && !item.isZipEntry) {
            viewModelScope.launch {
                apkInfo = ApkInfoUtil.parseApkFile(getApplication(), item.path)
            }
        }
    }

    // -------------------------------------------------------------------- search

    fun runSearch(side: PaneSide) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        updatePane(side) { it.copy(isLoading = true) }
        viewModelScope.launch {
            val results = FileHelper.searchFiles(state.currentPath, query, searchContent)
            FileHelper.resetCancel()
            updatePane(side) {
                it.copy(
                    items = FileItem.sortItems(results, it.sortBy, it.sortAscending, settings.foldersFirst),
                    isLoading = false,
                    selectedItems = emptySet(),
                    searchQuery = query
                )
            }
            showSearchDialog = false
            toastMessage = "找到 ${results.size} 个结果"
        }
    }

    fun clearSearch(side: PaneSide) {
        updatePane(side) { it.copy(searchQuery = null) }
        refreshPane(side)
    }

    // ------------------------------------------------------------------ zip ops

    fun createZipFromSelection(side: PaneSide, zipName: String, password: String) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        val dest = File(state.currentPath, if (zipName.endsWith(".zip")) zipName else "$zipName.zip").absolutePath
        viewModelScope.launch {
            val result = FileHelper.createZip(items, dest, password.ifBlank { null }) { opProgress = it }
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已创建 $dest" else "压缩失败"
            showZipCreateDialog = false
            clearSelection(side)
            refreshPane(side)
        }
    }

    fun extractZipAt(side: PaneSide, zipPath: String, destDir: String, password: String) {
        viewModelScope.launch {
            val result = FileHelper.extractZip(zipPath, destDir, password.ifBlank { null }) { opProgress = it }
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已解压到 $destDir" else "解压失败"
            refreshPane(side)
        }
    }

    fun testZipAt(zipPath: String, password: String) {
        viewModelScope.launch {
            toastMessage = FileHelper.testZip(zipPath, password.ifBlank { null })
        }
    }

    fun addToZip(side: PaneSide, zipPath: String) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = state.items.filter { it.path in state.selectedItems && it.name != ".." }
        viewModelScope.launch {
            val result = FileHelper.addToZip(items, zipPath)
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已添加 $result.success 项" else "添加失败"
            clearSelection(side)
            refreshPane(side)
        }
    }

    // ---------------------------------------------------------------- bookmarks

    fun refreshBookmarks() {
        bookmarkList = bookmarks.all()
    }

    fun addBookmark(name: String, path: String, group: String = "") {
        bookmarks.add(name, path, group)
        refreshBookmarks()
        toastMessage = "已添加书签"
        showBookmarkAddDialog = false
    }

    fun removeBookmark(id: Long) {
        bookmarks.remove(id)
        refreshBookmarks()
    }

    // ------------------------------------------------------------- text editor

    fun openTextEditor(path: String, readOnly: Boolean = false) {
        viewModelScope.launch {
            val content = FileHelper.readTextFile(path) ?: ""
            textEditorPath = path
            textEditorContent = content
            textEditorReadOnly = readOnly
            showTextEditor = true
        }
    }

    fun saveTextEditor(content: String) {
        viewModelScope.launch {
            val ok = FileHelper.writeTextFile(textEditorPath, content)
            toastMessage = if (ok) "保存成功" else "保存失败"
            showTextEditor = false
            refreshPane(activePane)
        }
    }

    // -------------------------------------------------------------- item click

    fun onItemClick(side: PaneSide, item: FileItem) {
        switchActivePane(side)
        if (item.name == "..") {
            navigateTo(side, item.path)
            return
        }
        when {
            item.isDirectory -> navigateTo(side, item.path)
            item.fileKind == FileKind.ARCHIVE && !item.isZipEntry -> {
                navigateTo(side, "zip://${item.path}")
            }
            item.fileKind == FileKind.TEXT || item.fileKind == FileKind.CODE -> {
                openTextEditor(item.path)
            }
            item.fileKind == FileKind.APK && !item.isZipEntry -> {
                showProperties(item)
            }
        }
    }

    fun onItemLongClick(side: PaneSide, item: FileItem) {
        switchActivePane(side)
        if (item.name != "..") toggleSelect(side, item.path)
    }

    fun clearToast() {
        toastMessage = null
    }
}
