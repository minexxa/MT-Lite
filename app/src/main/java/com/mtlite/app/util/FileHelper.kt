package com.mtlite.app.util

import android.os.StatFs
import com.mtlite.app.data.ConflictResolution
import com.mtlite.app.data.FileItem
import com.mtlite.app.data.OpProgress
import com.mtlite.app.data.SortBy
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

data class TransferResult(
    val success: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0
) {
    val summary: String
        get() = buildString {
            append("完成 $success")
            if (failed > 0) append(" · 失败 $failed")
            if (skipped > 0) append(" · 跳过 $skipped")
        }
}

/** Resolver invoked when a destination name already exists; returns the chosen action. */
typealias ConflictResolver = suspend (String) -> ConflictResolution

object FileHelper {

    @Volatile
    var cancelRequested: Boolean = false

    fun requestCancel() {
        cancelRequested = true
    }

    fun resetCancel() {
        cancelRequested = false
    }

    fun isRootAvailable(): Boolean {
        return try {
            Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    fun isShellAvailable(): Boolean {
        return try {
            Shell.getShell().isAlive
        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- listing

    suspend fun listFiles(
        path: String,
        showHidden: Boolean = true,
        sortBy: SortBy = SortBy.NAME,
        ascending: Boolean = true,
        foldersFirst: Boolean = true
    ): List<FileItem> = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("zip://")) {
                return@withContext FileItem.sortItems(
                    listZipEntries(path), sortBy, ascending, foldersFirst
                )
            }

            val dir = File(path)
            val normalList = try {
                if (dir.exists() && dir.isDirectory && dir.canRead()) dir.listFiles() else null
            } catch (_: Exception) {
                null
            }

            val raw = if (normalList != null) {
                normalList.map { FileItem.fromFile(it) }
            } else {
                listViaShell(path)
            }
            FileItem.sortItems(
                raw.filter { showHidden || !it.isHidden }.filter { it.name != "" },
                sortBy, ascending, foldersFirst
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * List via shell. Uses `ls -lap` so directories end with `/` and the
     * permission string is available.
     */
    private fun listViaShell(path: String): List<FileItem> {
        return try {
            val safe = path.replace("\"", "\\\"")
            val result = Shell.cmd("ls -lap \"$safe\" 2>/dev/null").exec()
            if (!result.isSuccess && result.out.isEmpty()) return emptyList()
            val base = if (path.endsWith("/")) path else "$path/"
            result.out
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.startsWith("total ") }
                .mapNotNull { raw ->
                    // Format: perms links owner group size date time name
                    val parts = raw.split(Regex("\\s+"), limit = 9)
                    if (parts.size < 9) return@mapNotNull null
                    val perms = parts[0]
                    val isDir = perms.startsWith("d") || perms.startsWith("l") && parts[8].endsWith("/")
                    val name = parts[8].trimEnd('/')
                    if (name == "." || name == "..") return@mapNotNull null
                    val full = base + name
                    FileItem(
                        name = name,
                        path = full,
                        isDirectory = isDir,
                        size = parts[4].toLongOrNull() ?: 0L,
                        isHidden = name.startsWith("."),
                        permission = perms,
                        isSymlink = perms.startsWith("l")
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun listZipEntries(zipPath: String): List<FileItem> {
        val parts = zipPath.removePrefix("zip://").split("!", limit = 2)
        val realZip = parts[0]
        val internal = if (parts.size > 1) parts[1].trimStart('/') else ""

        return try {
            val zip = ZipFile(realZip)
            val entries = zip.fileHeaders
            val result = mutableListOf<FileItem>()

            if (internal.isNotEmpty()) {
                val parentInternal = File(internal).parent ?: ""
                result.add(
                    FileItem(
                        name = "..",
                        path = if (parentInternal.isEmpty()) "zip://$realZip" else "zip://$realZip!/$parentInternal",
                        isDirectory = true,
                        isZipEntry = true,
                        zipParentPath = realZip
                    )
                )
            }

            val prefix = if (internal.isEmpty()) "" else "$internal/"
            val seenDirs = mutableSetOf<String>()

            for (header in entries) {
                val name = header.fileName
                if (!name.startsWith(prefix) || name == prefix) continue

                val relative = name.removePrefix(prefix)
                val firstSeg = relative.substringBefore("/")
                if (firstSeg.isEmpty()) continue

                if (relative.contains("/")) {
                    if (seenDirs.add(firstSeg)) {
                        result.add(
                            FileItem(
                                name = firstSeg,
                                path = "zip://$realZip!/$prefix$firstSeg",
                                isDirectory = true,
                                isZipEntry = true,
                                zipParentPath = realZip,
                                lastModified = header.lastModifiedTimeEpoch
                            )
                        )
                    }
                } else {
                    result.add(
                        FileItem(
                            name = firstSeg,
                            path = "zip://$realZip!/$name",
                            isDirectory = false,
                            size = header.uncompressedSize,
                            isZipEntry = true,
                            zipParentPath = realZip,
                            lastModified = header.lastModifiedTimeEpoch
                        )
                    )
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ------------------------------------------------------- copy / move / delete

    /**
     * Copy items into [destDir]. Conflicts resolved via [resolver]; progress
     * reported through [onProgress]. Set [FileHelper.cancelRequested] to abort.
     */
    suspend fun copyFiles(
        items: List<FileItem>,
        destDir: String,
        resolver: ConflictResolver,
        onProgress: (OpProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        resetCancel()
        var success = 0
        var failed = 0
        var skipped = 0
        items.forEachIndexed { index, item ->
            if (cancelRequested) return@withContext TransferResult(success, failed, skipped + (items.size - index))
            onProgress(
                OpProgress(
                    active = true,
                    title = "复制",
                    current = index,
                    total = items.size,
                    currentName = item.name
                )
            )
            val dest = File(destDir, item.name)
            when (resolveConflict(item, dest, resolver)) {
                ConflictResolution.SKIP -> skipped++
                ConflictResolution.OVERWRITE -> {
                    if (copyOne(item, destDir, overwrite = true)) success++ else failed++
                }
                ConflictResolution.KEEP_BOTH -> {
                    if (copyOne(item, destDir, overwrite = false, keepBoth = true)) success++ else failed++
                }
                ConflictResolution.ASK -> skipped++
            }
        }
        TransferResult(success, failed, skipped)
    }

    suspend fun moveFiles(
        items: List<FileItem>,
        destDir: String,
        resolver: ConflictResolver,
        onProgress: (OpProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        resetCancel()
        var success = 0
        var failed = 0
        var skipped = 0
        items.forEachIndexed { index, item ->
            if (cancelRequested) return@withContext TransferResult(success, failed, skipped + (items.size - index))
            onProgress(
                OpProgress(
                    active = true,
                    title = "移动",
                    current = index,
                    total = items.size,
                    currentName = item.name
                )
            )
            val dest = File(destDir, item.name)
            when (resolveConflict(item, dest, resolver)) {
                ConflictResolution.SKIP -> skipped++
                ConflictResolution.OVERWRITE -> {
                    if (moveOne(item, destDir, overwrite = true)) success++ else failed++
                }
                ConflictResolution.KEEP_BOTH -> {
                    if (moveOne(item, destDir, overwrite = false, keepBoth = true)) success++ else failed++
                }
                ConflictResolution.ASK -> skipped++
            }
        }
        TransferResult(success, failed, skipped)
    }

    private suspend fun resolveConflict(
        item: FileItem,
        dest: File,
        resolver: ConflictResolver
    ): ConflictResolution {
        if (!dest.exists()) return ConflictResolution.OVERWRITE
        // Refuse to copy a directory into itself
        if (item.isDirectory && dest.absolutePath.startsWith(item.path + "/")) {
            return ConflictResolution.SKIP
        }
        return resolver(item.name)
    }

    private fun copyOne(item: FileItem, destDir: String, overwrite: Boolean, keepBoth: Boolean = false): Boolean {
        return try {
            val source = File(item.path)
            var target = File(destDir, item.name)
            if (target.exists() && keepBoth) target = uniqueName(target)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = overwrite)
            } else {
                source.copyTo(target, overwrite = overwrite)
            }
            true
        } catch (e: Exception) {
            try {
                val targetName = if (keepBoth) uniqueName(File(destDir, item.name)).name else item.name
                Shell.cmd("cp -a \"${item.path}\" \"$destDir/$targetName\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun moveOne(item: FileItem, destDir: String, overwrite: Boolean, keepBoth: Boolean = false): Boolean {
        return try {
            val source = File(item.path)
            var target = File(destDir, item.name)
            if (target.exists() && keepBoth) target = uniqueName(target)
            if (source.renameTo(target)) return true
            if (copyOne(item, destDir, overwrite)) deleteOne(File(item.path)) else false
        } catch (e: Exception) {
            try {
                val targetName = if (keepBoth) uniqueName(File(destDir, item.name)).name else item.name
                Shell.cmd("mv \"${item.path}\" \"$destDir/$targetName\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Non-suspend delete used internally from non-suspend helpers. */
    private fun deleteOne(file: File): Boolean {
        return try {
            val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (ok) return true
            Shell.cmd("rm -rf \"${file.absolutePath}\"").exec().isSuccess
        } catch (_: Exception) {
            try {
                Shell.cmd("rm -rf \"${file.absolutePath}\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    /** "file.txt" -> "file (1).txt", "file (2).txt", ... */
    fun uniqueName(file: File): File {
        if (!file.exists()) return file
        val base = file.nameWithoutExtension
        val ext = file.extension
        var i = 1
        while (true) {
            val candidate = File(file.parentFile, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    suspend fun deleteFile(item: FileItem, useRecycleBin: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (item.isZipEntry) return@withContext false
                val file = File(item.path)
                if (useRecycleBin) return@withContext moveToRecycleBin(item)
                val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (ok) return@withContext true
                Shell.cmd("rm -rf \"${item.path}\"").exec().isSuccess
            } catch (e: Exception) {
                try {
                    Shell.cmd("rm -rf \"${item.path}\"").exec().isSuccess
                } catch (_: Exception) {
                    false
                }
            }
        }

    /** Move item into /storage/emulated/0/.MT_Lite_Trash (created lazily). */
    private fun moveToRecycleBin(item: FileItem): Boolean {
        return try {
            val source = File(item.path)
            val trashRoot = File("/storage/emulated/0", ".MT_Lite_Trash")
            if (!trashRoot.exists()) trashRoot.mkdirs()
            val dest = uniqueName(File(trashRoot, item.name))
            if (source.renameTo(dest)) return true
            if (source.isDirectory) source.copyRecursively(dest, overwrite = true) else source.copyTo(dest, overwrite = true)
            if (source.isDirectory) source.deleteRecursively() else source.delete()
        } catch (e: Exception) {
            try {
                Shell.cmd("mv \"${item.path}\" \"/storage/emulated/0/.MT_Lite_Trash/${item.name}\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Empty the recycle bin. Returns true when the directory is gone/empty. */
    suspend fun emptyRecycleBin(): Boolean = withContext(Dispatchers.IO) {
        try {
            val trash = File("/storage/emulated/0", ".MT_Lite_Trash")
            if (!trash.exists()) return@withContext true
            val ok = trash.deleteRecursively()
            if (ok) true else Shell.cmd("rm -rf \"${trash.absolutePath}\"").exec().isSuccess
        } catch (_: Exception) {
            false
        }
    }

    fun recycleBinExists(): Boolean = File("/storage/emulated/0", ".MT_Lite_Trash").exists()

    // ------------------------------------------------------------ create / rename

    suspend fun createFile(parent: String, name: String, isDir: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(parent, name)
                val ok = if (isDir) file.mkdirs() else file.createNewFile()
                if (ok) return@withContext true
                if (isDir) {
                    Shell.cmd("mkdir -p \"$parent/$name\"").exec().isSuccess
                } else {
                    Shell.cmd("touch \"$parent/$name\"").exec().isSuccess
                }
            } catch (e: Exception) {
                false
            }
        }

    suspend fun renameFile(item: FileItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            val target = File(file.parent, newName)
            if (target.exists()) return@withContext false
            if (file.renameTo(target)) return@withContext true
            Shell.cmd("mv \"${item.path}\" \"${file.parent}/$newName\"").exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /** Batch rename with simple find/replace on the base name. */
    suspend fun batchRename(
        items: List<FileItem>,
        find: String,
        replace: String,
        useRegex: Boolean = false
    ): TransferResult = withContext(Dispatchers.IO) {
        var success = 0
        var failed = 0
        for (item in items) {
            if (cancelRequested) break
            val base = item.name
            val ext = File(base).extension
            val stem = if (ext.isEmpty()) base else base.removeSuffix(".$ext")
            val newStem = if (useRegex) {
                runCatching { Regex(find).replace(stem, replace) }.getOrDefault(stem)
            } else {
                stem.replace(find, replace)
            }
            if (newStem.isBlank() || newStem == stem) continue
            val newName = if (ext.isEmpty()) newStem else "$newStem.$ext"
            if (renameFile(item, newName)) success++ else failed++
        }
        TransferResult(success, failed)
    }

    // ------------------------------------------------------------------- zip ops

    suspend fun createZip(
        items: List<FileItem>,
        destZip: String,
        password: String? = null,
        onProgress: (OpProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        resetCancel()
        try {
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.ZIP_STANDARD
                }
            }
            val files = items.map { File(it.path) }.filter { it.exists() }
            if (files.isEmpty()) return@withContext TransferResult()
            val zipFile = ZipFile(destZip)
            if (!password.isNullOrEmpty()) zipFile.setPassword(password.toCharArray())
            files.forEachIndexed { index, f ->
                if (cancelRequested) return@withContext TransferResult(success = index)
                onProgress(
                    OpProgress(
                        active = true,
                        title = "压缩",
                        current = index,
                        total = files.size,
                        currentName = f.name
                    )
                )
                runCatching {
                    if (f.isDirectory) zipFile.addFolder(f, params) else zipFile.addFile(f, params)
                }
            }
            TransferResult(success = files.size)
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = items.size)
        }
    }

    suspend fun addToZip(
        items: List<FileItem>,
        zipPath: String
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
            val zipFile = ZipFile(zipPath)
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
            }
            var ok = 0
            for (item in items) {
                val f = File(item.path)
                if (!f.exists()) continue
                runCatching {
                    if (f.isDirectory) zipFile.addFolder(f, params) else zipFile.addFile(f, params)
                    ok++
                }
            }
            TransferResult(success = ok)
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = items.size)
        }
    }

    suspend fun extractZip(
        zipPath: String,
        destDir: String,
        password: String? = null,
        onProgress: (OpProgress) -> Unit
    ): TransferResult = withContext(Dispatchers.IO) {
        resetCancel()
        try {
            val zipFile = if (password.isNullOrEmpty()) ZipFile(zipPath)
            else ZipFile(zipPath, password.toCharArray())
            if (zipFile.isEncrypted && password.isNullOrEmpty()) {
                return@withContext TransferResult(failed = 1)
            }
            val headers = zipFile.fileHeaders
            val total = headers.size
            var done = 0
            File(destDir).mkdirs()
            for (header in headers) {
                if (cancelRequested) break
                onProgress(
                    OpProgress(
                        active = true,
                        title = "解压",
                        current = done,
                        total = total,
                        currentName = header.fileName
                    )
                )
                runCatching { zipFile.extractFile(header, destDir) }
                done++
            }
            TransferResult(success = done, failed = total - done)
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = 1)
        }
    }

    /** Extract a single entry from a zip (zip:// paths supported). */
    suspend fun extractZipEntry(entry: FileItem, destDir: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val zipPath = entry.zipParentPath ?: return@withContext false
                val zipFile = ZipFile(zipPath)
                val internal = entry.path.substringAfter("!", "").removePrefix("/")
                zipFile.extractFile(internal, destDir)
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Read every entry and verify it can be decompressed. zip4j 2.11 has no
     * built-in test API, so entries are streamed and discarded.
     */
    suspend fun testZip(zipPath: String, password: String? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val zipFile = if (password.isNullOrEmpty()) ZipFile(zipPath)
                else ZipFile(zipPath, password.toCharArray())
                if (!zipFile.isValidZipFile) return@withContext "文件损坏或不是有效的压缩包"
                if (zipFile.isEncrypted && password.isNullOrEmpty()) {
                    return@withContext "压缩包已加密，需要密码"
                }
                val buffer = ByteArray(8192)
                val headers = zipFile.fileHeaders
                if (headers.isEmpty()) return@withContext "压缩包为空"
                for (header in headers) {
                    if (cancelRequested) return@withContext "测试已取消"
                    zipFile.getInputStream(header).use { input ->
                        while (input.read(buffer) > 0) {
                            // drain
                        }
                    }
                }
                "测试通过，${headers.size} 个条目未发现错误"
            } catch (e: Exception) {
                "测试失败: ${e.message}"
            }
        }

    fun zipNeedsPassword(zipPath: String): Boolean = try {
        ZipFile(zipPath).isEncrypted
    } catch (_: Exception) {
        false
    }

    fun zipEntryCount(zipPath: String): Int = try {
        ZipFile(zipPath).fileHeaders.size
    } catch (_: Exception) {
        0
    }

    // --------------------------------------------------------------------- search

    /**
     * Recursive name search under [rootPath]; when [content] is true also
     * greps text files smaller than 1 MB.
     */
    suspend fun searchFiles(
        rootPath: String,
        query: String,
        content: Boolean = false,
        onProgress: (OpProgress) -> Unit = {}
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        if (query.isBlank()) return@withContext results
        val q = query.lowercase()
        fun walk(dir: File, depth: Int) {
            if (cancelRequested || depth > 12) return
            val children = try { dir.listFiles() } catch (_: Exception) { null } ?: return
            for (f in children) {
                if (cancelRequested) return
                if (f.name.lowercase().contains(q)) {
                    results.add(FileItem.fromFile(f))
                }
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else if (content && f.length() in 1..(1024 * 1024)) {
                    runCatching {
                        val text = f.readText()
                        if (text.lowercase().contains(q)) results.add(FileItem.fromFile(f))
                    }
                }
            }
            onProgress(OpProgress(active = true, title = "搜索", currentName = dir.path))
        }
        walk(File(rootPath), 0)
        results.distinctBy { it.path }
    }

    // ----------------------------------------------------------------- text io

    suspend fun readTextFile(path: String, maxSize: Long = 4 * 1024 * 1024): String? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead() && file.length() <= maxSize) {
                    return@withContext file.readText()
                }
                val r = Shell.cmd("head -c $maxSize \"$path\" 2>/dev/null").exec()
                if (r.isSuccess) r.out.joinToString("\n") else null
            } catch (e: Exception) {
                null
            }
        }

    suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).writeText(content)
            true
        } catch (e: Exception) {
            try {
                val b64 = android.util.Base64.encodeToString(
                    content.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                )
                Shell.cmd("echo '$b64' | base64 -d > \"$path\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Read raw bytes for the hex viewer. Returns at most [length] bytes. */
    suspend fun readHexLines(path: String, offset: Long, length: Int, bytesPerLine: Int = 16): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val lines = mutableListOf<String>()
                RandomAccessFile(File(path), "r").use { raf ->
                    if (offset >= raf.length()) return@withContext emptyList()
                    raf.seek(offset)
                    val buf = ByteArray(length)
                    val read = raf.read(buf)
                    var i = 0
                    while (i < read) {
                        val end = minOf(i + bytesPerLine, read)
                        val chunk = buf.copyOfRange(i, end)
                        val hex = chunk.joinToString(" ") { "%02X".format(it) }
                        val ascii = chunk.map { b ->
                            val c = b.toInt() and 0xFF
                            if (c in 32..126) c.toChar() else '.'
                        }.joinToString("")
                        lines.add("%08X  %-47s  %s".format(offset + i, hex, ascii))
                        i = end
                    }
                }
                lines
            } catch (e: Exception) {
                emptyList()
            }
        }

    // ------------------------------------------------------------------- hashes

    suspend fun computeHash(path: String, algorithm: String = "SHA-256"): String =
        withContext(Dispatchers.IO) {
            try {
                val md = MessageDigest.getInstance(algorithm)
                File(path).inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } > 0) {
                        md.update(buffer, 0, read)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                ""
            }
        }

    // ------------------------------------------------------------------ storage

    fun getStorageInfo(path: String): Pair<Long, Long> {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            Pair(total - free, total)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    fun getStorageEntries(): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        list.add(Triple("根目录", "/", if (isRootAvailable()) "Root 可用" else "需要 Root"))
        val emulated = "/storage/emulated/0"
        val (used, total) = getStorageInfo(emulated)
        list.add(
            Triple(
                "内部存储",
                emulated,
                "${FileItem.formatSize(used)} / ${FileItem.formatSize(total)}"
            )
        )
        list.add(Triple("系统", "/system", "/system"))
        list.add(Triple("数据", "/data", "/data"))
        list.add(Triple("SD卡/OTG", "/storage", "/storage"))
        list.add(Triple("下载", "$emulated/Download", "Download"))
        list.add(Triple("DCIM", "$emulated/DCIM", "DCIM"))
        list.add(Triple("回收站", "$emulated/.MT_Lite_Trash", if (recycleBinExists()) "有文件" else "空"))
        return list
    }

    // ------------------------------------------------------------------- shell

    suspend fun runShell(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd(cmd).exec()
            buildString {
                append(r.out.joinToString("\n"))
                if (r.err.isNotEmpty()) {
                    if (isNotEmpty()) append("\n")
                    append(r.err.joinToString("\n"))
                }
                if (isEmpty()) append("(exit ${r.code})")
            }
        } catch (e: Exception) {
            e.message ?: "error"
        }
    }

    /** chmod via shell (root). Returns new permission string or null on failure. */
    suspend fun chmod(item: FileItem, octal: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("chmod $octal \"${item.path}\" && ls -ld \"${item.path}\" | awk '{print $1}'").exec()
            if (r.isSuccess && r.out.isNotEmpty()) r.out[0] else null
        } catch (_: Exception) {
            null
        }
    }
}
