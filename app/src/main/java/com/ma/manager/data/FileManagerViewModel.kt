package com.ma.manager.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ma.manager.util.ApkInfo
import com.ma.manager.util.ApkInfoUtil
import com.ma.manager.util.ArchiveFormat
import com.ma.manager.util.FileHelper
import com.ma.manager.util.TransferResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileManagerViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore.get(app)
    private val bookmarks = BookmarkStore.get(app)
    private val zipPasswords = ZipPasswordStore.get(app)

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
    var showFilterDialog by mutableStateOf(false)
    var showOpenWithDialog by mutableStateOf(false)

    // Long-press context menu (MA-manager style option popup)
    var longPressItem by mutableStateOf<FileItem?>(null)
        private set
    var longPressSide by mutableStateOf(PaneSide.LEFT)
        private set

    // "打开方式..." target
    var openWithItem by mutableStateOf<FileItem?>(null)
        private set

    // APK info dialog target (图四)
    var apkDialogItem by mutableStateOf<FileItem?>(null)
        private set
    var apkDialogInfo by mutableStateOf<ApkInfo?>(null)
        private set

    // Full-screen image viewer target
    var imageViewerPath by mutableStateOf<String?>(null)
        private set

    // Single-item compress target (long-press 压缩)
    private var zipSingleItems by mutableStateOf<List<FileItem>>(emptyList())

    /** Number of items queued for single-item compression. */
    fun zipSingleCount(): Int = zipSingleItems.size

    fun clearZipSingleItems() {
        zipSingleItems = emptyList()
    }

    // Items awaiting delete confirmation from the long-press menu
    private var pendingDeleteItems by mutableStateOf<List<FileItem>>(emptyList())

    // Multi-select summary properties
    var showMultiPropsDialog by mutableStateOf(false)
        private set
    var multiPropsItems by mutableStateOf<List<FileItem>>(emptyList())
        private set

    fun showMultiProperties(items: List<FileItem>) {
        multiPropsItems = items.filter { it.name != ".." }
        showMultiPropsDialog = multiPropsItems.isNotEmpty()
    }

    fun dismissMultiProperties() {
        showMultiPropsDialog = false
        multiPropsItems = emptyList()
    }

    /** Ask the user to confirm deleting an explicit item list (long-press 删除). */
    fun requestDelete(items: List<FileItem>) {
        pendingDeleteItems = items.filter { !it.isZipEntry && it.name != ".." }
        if (pendingDeleteItems.isEmpty()) {
            toastMessage = "无法删除"
            return
        }
        if (settings.confirmDelete) {
            showDeleteConfirm = true
        } else {
            deleteItems(activePane, pendingDeleteItems)
            pendingDeleteItems = emptyList()
        }
    }

    /** Items pending delete confirmation (empty when none). */
    fun pendingDeleteList(): List<FileItem> = pendingDeleteItems

    fun clearPendingDeleteItems() {
        pendingDeleteItems = emptyList()
    }

    /** Confirm deletion of the pending list, or of the current selection. */
    fun confirmDelete() {
        if (pendingDeleteItems.isNotEmpty()) {
            deleteItems(activePane, pendingDeleteItems)
            clearPendingDeleteItems()
        } else {
            deleteSelected(activePane)
        }
        showDeleteConfirm = false
    }

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
    var textEditorCharset by mutableStateOf<java.nio.charset.Charset>(java.nio.charset.StandardCharsets.UTF_8)
    var textEditorBytes by mutableStateOf<ByteArray?>(null)

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
            // Distinguish "empty" from "no permission" on system paths.
            val p = current.currentPath
            if (items.isEmpty() && !p.startsWith("zip://") && !FileHelper.isRootAvailable() &&
                (p == "/" ||
                    p == "/data" || p.startsWith("/data/") ||
                    p == "/system" || p.startsWith("/system/") ||
                    p == "/proc" || p == "/mnt" || p == "/root")
            ) {
                toastMessage = "无法访问 $p：需要 Root 权限（侧边栏 → Root 授权）"
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
                searchQuery = null,
                highlightPath = null
            )
        }
        refreshPane(side)
        persistPanePaths()
    }

    /** Jump to a search result's parent directory and highlight the file. */
    fun jumpToFile(side: PaneSide, item: FileItem) {
        val parent = File(item.path).parent ?: return
        navigateTo(side, parent)
        updatePane(side) { it.copy(highlightPath = item.path) }
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
        val cur = state.currentPath
        if (cur.startsWith("zip://")) {
            val parts = cur.removePrefix("zip://").split("!", limit = 2)
            val archive = parts[0]
            val internal = if (parts.size > 1) parts[1].trim('/') else ""
            if (internal.isEmpty()) {
                navigateTo(side, File(archive).parent ?: return)
            } else {
                val up = internal.substringBeforeLast('/', "")
                navigateTo(side, if (up.isEmpty()) "zip://$archive" else "zip://$archive!/$up")
            }
            return
        }
        val parent = File(cur).parent ?: return
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

    // ------------------------------------------- copy / move to other pane

    private fun otherPanePath(side: PaneSide): String =
        if (side == PaneSide.LEFT) rightPane.currentPath else leftPane.currentPath

    private fun transferToOtherPane(side: PaneSide, items: List<FileItem>, isCut: Boolean) {
        if (items.isEmpty()) return
        val dest = otherPanePath(side)
        viewModelScope.launch {
            val result = if (isCut) {
                FileHelper.moveFiles(items, dest, ::conflictResolver) { opProgress = it }
            } else {
                FileHelper.copyFiles(items, dest, ::conflictResolver) { opProgress = it }
            }
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = "${if (isCut) "移动" else "复制"}到 $dest: ${result.summary}"
            refreshPane(side)
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    /** 另一窗口是否处于压缩包预览（zip:// 虚拟路径）。 */
    fun isOtherPaneArchive(side: PaneSide): Boolean = otherPanePath(side).startsWith("zip://")

    /**
     * 长按「<- 添加」：另一窗口为普通目录 → 复制过去；
     * 另一窗口为压缩包预览 → 把文件/文件夹添加进该压缩包
     * （ZIP 类可直接追加；添加到预览中当前所在的子目录下）。
     */
    fun addOrCopyToOtherPane(side: PaneSide, items: List<FileItem>) {
        val dest = otherPanePath(side)
        if (!dest.startsWith("zip://")) {
            transferToOtherPane(side, items, isCut = false)
            return
        }
        val archivePath = dest.removePrefix("zip://").substringBefore("!")
        if (com.ma.manager.util.ArchiveHelper.detectType(archivePath) != com.ma.manager.util.ArchiveType.ZIP) {
            toastMessage = "该压缩包格式暂不支持添加（仅 ZIP 类可编辑）"
            return
        }
        val real = items.filter { !it.isZipEntry && it.name != ".." }
        if (real.isEmpty()) {
            toastMessage = "无可添加项"
            return
        }
        // zip 预览里当前所在的子目录（zip://archive!a/b → a/b）
        val internalDir = dest.substringAfter("!", "").removePrefix("/").trimEnd('/')
        viewModelScope.launch {
            opProgress = OpProgress(active = true, title = "添加", total = real.size)
            val result = FileHelper.addToZip(real, archivePath, internalDir.ifEmpty { null })
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) {
                "已添加 ${result.success} 项到 ${File(archivePath).name}" +
                    (if (internalDir.isNotEmpty()) "/$internalDir" else "")
            } else "添加失败"
            refreshPane(if (side == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    /** Long-press 复制: selected (or the pressed item) → other pane directory. */
    fun copyToOtherPane(side: PaneSide, items: List<FileItem>) {
        transferToOtherPane(side, items, isCut = false)
    }

    /** Long-press 移动: selected (or the pressed item) → other pane directory. */
    fun moveToOtherPane(side: PaneSide, items: List<FileItem>) {
        transferToOtherPane(side, items, isCut = true)
    }

    /** 交换窗口: swap both panes (path + history + selection). */
    fun swapPanes() {
        val l = leftPane
        val r = rightPane
        leftPane = r
        rightPane = l
        persistPanePaths()
        toastMessage = "已交换窗口"
    }

    /** 设为首页: remember current path as the startup path. */
    fun setAsHome() {
        val path = getActivePaneState().currentPath
        settings.defaultPath = path
        toastMessage = "已设为首页: $path"
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
        deleteItems(side, items)
    }

    /** Delete an explicit item list (used by the long-press menu). */
    fun deleteItems(side: PaneSide, items: List<FileItem>) {
        val real = items.filter { !it.isZipEntry && it.name != ".." }
        if (real.isEmpty()) {
            toastMessage = "无法删除"
            return
        }
        viewModelScope.launch {
            val useBin = settings.useRecycleBin
            var success = 0
            real.forEachIndexed { index, item ->
                opProgress = OpProgress(
                    active = true,
                    title = if (useBin) "移入回收站" else "删除",
                    current = index,
                    total = real.size,
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

    fun createArchiveFromSelection(side: PaneSide, name: String, password: String, format: ArchiveFormat) {
        val state = if (side == PaneSide.LEFT) leftPane else rightPane
        val items = if (state.selectedItems.isNotEmpty()) {
            state.items.filter { it.path in state.selectedItems && it.name != ".." }
        } else {
            zipSingleItems
        }
        val dest = File(
            state.currentPath,
            if (name.endsWith(format.extension)) name else name + format.extension
        ).absolutePath
        viewModelScope.launch {
            val result = FileHelper.createArchive(items, dest, format, password.ifBlank { null }) { opProgress = it }
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已创建 $dest" else "压缩失败"
            if (result.success > 0 && password.isNotBlank()) {
                zipPasswords.put(dest, password)
            }
            showZipCreateDialog = false
            zipSingleItems = emptyList()
            clearSelection(side)
            refreshPane(side)
        }
    }

    /** Long-press 压缩 on a single item. */
    fun compressItems(items: List<FileItem>) {
        zipSingleItems = items.filter { it.name != ".." && !it.isZipEntry }
        if (zipSingleItems.isEmpty()) {
            toastMessage = "无可压缩项"
            return
        }
        showZipCreateDialog = true
    }

    fun extractZipAt(side: PaneSide, zipPath: String, destDir: String, password: String) {
        viewModelScope.launch {
            val result = FileHelper.extractZip(zipPath, destDir, password.ifBlank { null }) { opProgress = it }
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已解压到 $destDir" else "解压失败"
            refreshPane(side)
        }
    }

    /**
     * Long-press inside a zip preview: extract the entry (or the whole folder
     * entry) into the *other* pane's directory.
     */
    fun extractEntryToOtherPane(entry: FileItem) {
        val dest = otherPanePath(longPressSide)
        viewModelScope.launch {
            opProgress = OpProgress(active = true, title = "解压", total = 1, currentName = entry.name)
            val ok = if (entry.isDirectory) {
                extractZipFolder(entry, dest)
            } else {
                FileHelper.extractZipEntry(entry, dest)
            }
            opProgress = OpProgress(finished = true, message = if (ok) "已解压到 $dest" else "解压失败")
            toastMessage = if (ok) "已解压 ${entry.name} 到 $dest" else "解压失败"
            refreshPane(longPressSide)
            refreshPane(if (longPressSide == PaneSide.LEFT) PaneSide.RIGHT else PaneSide.LEFT)
        }
    }

    private suspend fun extractZipFolder(entry: FileItem, destDir: String): Boolean {
        return FileHelper.extractArchiveFolder(entry, destDir)
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
            val bytes = FileHelper.readFileBytes(path)
            val cs = bytes?.let { FileHelper.detectCharset(it) } ?: java.nio.charset.StandardCharsets.UTF_8
            val content = bytes?.let { runCatching { String(it, cs) }.getOrNull() }
                ?: FileHelper.readTextFile(path) ?: ""
            textEditorBytes = bytes
            textEditorCharset = cs
            textEditorPath = path
            textEditorContent = content
            textEditorReadOnly = readOnly
            showTextEditor = true
        }
    }

    fun saveTextEditor(content: String, charset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8) {
        viewModelScope.launch {
            val ok = FileHelper.writeTextFile(textEditorPath, content, charset)
            toastMessage = if (ok) "保存成功" else "保存失败"
            showTextEditor = false
            refreshPane(activePane)
        }
    }

    /** Open a text entry inside a zip preview (read-only). */
    fun openZipEntryText(entry: FileItem) {
        viewModelScope.launch {
            val content = FileHelper.readZipEntryText(entry)
            if (content == null) {
                toastMessage = "无法读取条目(可能过大或非文本)"
                return@launch
            }
            textEditorPath = entry.path
            textEditorContent = content
            textEditorReadOnly = true
            showTextEditor = true
        }
    }

    // ---------------------------------------------------------------- filter

    fun setFilter(side: PaneSide, query: String?) {
        updatePane(side) { it.copy(filterQuery = query?.ifBlank { null }) }
    }

    fun clearFilter(side: PaneSide) {
        updatePane(side) { it.copy(filterQuery = null) }
    }

    // ------------------------------------------------------- long-press menu

    fun showLongPressMenu(side: PaneSide, item: FileItem) {
        switchActivePane(side)
        longPressSide = side
        longPressItem = item
    }

    fun dismissLongPressMenu() {
        longPressItem = null
    }

    // ------------------------------------------------------------- open-with

    fun showOpenWith(item: FileItem) {
        openWithItem = item
        showOpenWithDialog = true
    }

    fun dismissOpenWith() {
        showOpenWithDialog = false
        openWithItem = null
    }

    /** Route an "打开方式" choice. */
    fun handleOpenChoice(item: FileItem, choice: OpenChoice) {
        when (choice) {
            OpenChoice.TEXT_EDITOR -> {
                if (item.isZipEntry) openZipEntryText(item) else openTextEditor(item.path)
            }
            OpenChoice.IMAGE_VIEWER -> openImage(item)
            OpenChoice.MEDIA_PLAYER -> openMedia(activePane, item)
            OpenChoice.HEX_VIEWER -> openHex(item)
            OpenChoice.SYSTEM_APP -> openWithSystem(item)
            OpenChoice.INSTALL -> installApk(item)
            OpenChoice.SHARE -> shareItem(item)
        }
    }

    fun openWithSystem(item: FileItem) {
        val ctx = getApplication<android.app.Application>().applicationContext
        val authority = ctx.packageName + ".fileprovider"
        val target = if (item.isZipEntry) {
            null
        } else {
            item.path
        }
        if (target == null) {
            toastMessage = "压缩包内条目请先解压"
            return
        }
        val ok = FileHelper.openWithSystem(ctx, target, authority)
        if (!ok) toastMessage = "没有应用可以打开此文件"
    }

    fun installApk(item: FileItem) {
        if (item.isZipEntry) {
            toastMessage = "请先解压该 APK"
            return
        }
        val ctx = getApplication<android.app.Application>().applicationContext
        val authority = ctx.packageName + ".fileprovider"
        val ok = FileHelper.installApk(ctx, item.path, authority)
        if (!ok) toastMessage = "无法启动安装器"
    }

    fun shareItem(item: FileItem) {
        val ctx = getApplication<android.app.Application>().applicationContext
        val authority = ctx.packageName + ".fileprovider"
        if (item.isZipEntry) {
            toastMessage = "压缩包内条目请先解压"
            return
        }
        val ok = FileHelper.shareFile(ctx, item.path, authority)
        if (!ok) toastMessage = "分享失败"
    }

    /** Multi-select share via ACTION_SEND_MULTIPLE. */
    fun shareItems(items: List<FileItem>) {
        val ctx = getApplication<android.app.Application>().applicationContext
        val authority = ctx.packageName + ".fileprovider"
        val paths = items.filter { !it.isZipEntry && it.name != ".." }.map { it.path }
        if (paths.isEmpty()) {
            toastMessage = "无可分享文件"
            return
        }
        val ok = FileHelper.shareFiles(ctx, paths, authority)
        if (!ok) toastMessage = "分享失败"
    }

    // ------------------------------------------------------------- hash / props

    var hashResult by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun dismissHashResult() {
        hashResult = null
    }

    /** SHA-256 for a single file (long-press 工具). */
    fun hashItem(item: FileItem) {
        if (item.isDirectory || item.isZipEntry) {
            toastMessage = "只能计算文件的哈希"
            return
        }
        viewModelScope.launch {
            val digest = FileHelper.computeHash(item.path, "SHA-256")
            if (digest.isEmpty()) {
                toastMessage = "计算失败"
            } else {
                hashResult = item.name to digest
            }
        }
    }

    // ---------------------------------------------------------- zip tool ops

    /** 工具 → 添加到压缩包: first archive in [targets] is the destination (zip only). */
    fun addToZipTargets(side: PaneSide, targets: List<FileItem>) {
        val zip = targets.firstOrNull {
            it.fileKind == FileKind.ARCHIVE && !it.isZipEntry &&
                com.ma.manager.util.ArchiveHelper.detectType(it.path) == com.ma.manager.util.ArchiveType.ZIP
        }
        if (zip == null) {
            toastMessage = "请先选中一个压缩包作为目标"
            return
        }
        val toAdd = targets.filter { it.path != zip.path && !it.isZipEntry && it.name != ".." }
        if (toAdd.isEmpty()) {
            toastMessage = "没有要添加的文件"
            return
        }
        viewModelScope.launch {
            val result = FileHelper.addToZip(toAdd, zip.path)
            opProgress = OpProgress(finished = true, message = result.summary)
            toastMessage = if (result.success > 0) "已添加 $result.success 项到 ${zip.name}" else "添加失败"
            clearSelection(side)
            refreshPane(side)
        }
    }

    /** 工具 → 测试压缩包 (uses remembered password when input is blank). */
    fun testZipTarget(path: String) {
        viewModelScope.launch {
            toastMessage = FileHelper.testZip(path, null)
        }
    }

    /** Remembered password for an archive path (for dialog pre-fill). */
    fun rememberedZipPassword(path: String): String =
        zipPasswords.get(path) ?: ""

    /** Persist a zip password so future opens skip the prompt. */
    fun rememberZipPassword(path: String, password: String) {
        zipPasswords.put(path, password)
    }

    fun openHex(item: FileItem) {
        val target = if (item.isZipEntry) null else item.path
        if (target == null) {
            toastMessage = "压缩包内条目请先解压"
            return
        }
        hexViewerPath = target
    }

    /** Open an image in the full-screen viewer (extracts zip entries to cache). */
    fun openImage(item: FileItem) {
        if (!item.isZipEntry) {
            imageViewerPath = item.path
            return
        }
        viewModelScope.launch {
            val ctx = getApplication<android.app.Application>().applicationContext
            val cached = FileHelper.extractEntryToCache(item, File(ctx.cacheDir, "preview"))
            if (cached != null) {
                imageViewerPath = cached.absolutePath
            } else {
                toastMessage = "无法预览该图片"
            }
        }
    }

    fun dismissImageViewer() {
        imageViewerPath = null
    }

    // ------------------------------------------------------- built-in player

    /** Playlist shown by the built-in ExoPlayer; null = player closed. */
    var mediaPlaylist by mutableStateOf<List<String>?>(null)
        private set
    var mediaStartIndex by mutableStateOf(0)
        private set

    /**
     * Open a video / audio file in the built-in player. The playlist is made of
     * the files of the same kind in the pane's current listing.
     */
    fun openMedia(side: PaneSide, item: FileItem) {
        viewModelScope.launch {
            if (item.isZipEntry) {
                val ctx = getApplication<android.app.Application>().applicationContext
                val cached = FileHelper.extractEntryToCache(item, File(ctx.cacheDir, "preview"))
                if (cached == null) {
                    toastMessage = "无法播放该文件"
                } else {
                    mediaStartIndex = 0
                    mediaPlaylist = listOf(cached.absolutePath)
                }
                return@launch
            }
            if (!File(item.path).canRead()) {
                openWithSystem(item)
                return@launch
            }
            val pane = if (side == PaneSide.LEFT) leftPane else rightPane
            val kind = item.fileKind
            val sameKind = pane.visibleItems
                .filter { !it.isDirectory && !it.isZipEntry && it.fileKind == kind }
                .map { it.path }
            val idx = sameKind.indexOf(item.path)
            mediaStartIndex = idx.coerceAtLeast(0)
            mediaPlaylist = if (idx >= 0) sameKind else listOf(item.path)
        }
    }

    fun dismissMediaPlayer() {
        mediaPlaylist = null
    }

    var hexViewerPath by mutableStateOf<String?>(null)
        private set

    fun dismissHexViewer() {
        hexViewerPath = null
    }

    // ------------------------------------------------------------- apk info

    fun openApkDialog(item: FileItem) {
        apkDialogItem = item
        apkDialogInfo = null
        viewModelScope.launch {
            apkDialogInfo = ApkInfoUtil.parseApkFile(getApplication(), item.path)
        }
    }

    /** 在当前窗口以压缩包方式浏览 APK 内部文件。 */
    fun browseApkContents(item: FileItem) {
        dismissApkDialog()
        navigateTo(activePane, "zip://${item.path}")
    }

    fun dismissApkDialog() {
        apkDialogItem = null
        apkDialogInfo = null
    }

    // -------------------------------------------------------------- item click

    fun onItemClick(side: PaneSide, item: FileItem) {
        switchActivePane(side)
        val state = if (side == PaneSide.LEFT) leftPane else rightPane

        if (item.name == "..") {
            navigateTo(side, item.path)
            return
        }

        // Multi-select mode: tap toggles selection instead of opening.
        if (state.inSelectionMode) {
            toggleSelect(side, item.path)
            return
        }

        // Search results: files jump to their directory (highlighted),
        // folders are entered normally.
        if (state.searchQuery != null && !item.isDirectory && !item.isZipEntry) {
            jumpToFile(side, item)
            return
        }

        // Symlinks from a shell listing carry no trustworthy type (ls -p does
        // not classify link targets): resolve the target first so links to
        // directories can be entered and links to files dispatched normally.
        if (item.isSymlink && !item.isDirectory && !item.isZipEntry) {
            viewModelScope.launch {
                val resolved = resolveSymlinkTarget(item.path)
                if (resolved != null) {
                    onItemClick(side, item.copy(path = resolved.first, isDirectory = resolved.second, isSymlink = false))
                } else {
                    dispatchItemClick(side, item)
                }
            }
            return
        }

        dispatchItemClick(side, item)
    }

    private fun dispatchItemClick(side: PaneSide, item: FileItem) {
        when {
            item.isDirectory -> navigateTo(side, item.path)
            item.fileKind == FileKind.ARCHIVE && !item.isZipEntry -> {
                navigateTo(side, "zip://${item.path}")
            }
            item.fileKind == FileKind.IMAGE -> openImage(item)
            item.fileKind == FileKind.TEXT || item.fileKind == FileKind.CODE -> {
                if (item.isZipEntry) openZipEntryText(item) else openTextEditor(item.path)
            }
            item.fileKind == FileKind.VIDEO || item.fileKind == FileKind.AUDIO -> openMedia(side, item)
            item.fileKind == FileKind.APK && !item.isZipEntry -> openApkDialog(item)
            else -> showOpenWith(item)
        }
    }

    /**
     * Resolve a symlink via the shell. Returns the canonical target path and
     * whether it is a directory, or null when unresolvable (broken link).
     */
    private suspend fun resolveSymlinkTarget(path: String): Pair<String, Boolean>? =
        withContext(Dispatchers.IO) {
            try {
                val r = com.topjohnwu.superuser.Shell.cmd(
                    "p=\$(readlink -f \"$path\" 2>/dev/null); " +
                        "if [ -n \"\$p\" ]; then if [ -d \"\$p\" ]; then echo \"D \$p\"; else echo \"F \$p\"; fi; fi"
                ).exec()
                val out = r.out.firstOrNull()?.trim().orEmpty()
                if (out.length < 3 || out[1] != ' ') return@withContext null
                val target = out.substring(2).trim()
                if (target.isEmpty()) null else Pair(target, out[0] == 'D')
            } catch (_: Exception) {
                null
            }
        }

    fun onItemLongClick(side: PaneSide, item: FileItem) {
        switchActivePane(side)
        if (item.name == "..") return
        showLongPressMenu(side, item)
    }

    fun clearToast() {
        toastMessage = null
    }
}

/** Choices offered by the 打开方式 dialog. */
enum class OpenChoice { TEXT_EDITOR, IMAGE_VIEWER, MEDIA_PLAYER, HEX_VIEWER, SYSTEM_APP, INSTALL, SHARE }
