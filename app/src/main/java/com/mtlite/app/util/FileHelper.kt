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

    private lateinit var appContext: android.content.Context

    /** Call once from Application.onCreate so zip helpers can reach Context. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    /**
     * Open a zip, preferring [password] and falling back to the remembered
     * password for this path.
     */
    private fun openZip(path: String, password: String? = null): ZipFile {
        val pw = password ?: runCatching {
            if (::appContext.isInitialized) {
                com.mtlite.app.data.ZipPasswordStore.get(appContext).get(path)
            } else null
        }.getOrNull()
        return if (pw.isNullOrEmpty()) ZipFile(path) else ZipFile(path, pw.toCharArray())
    }

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
                    listArchiveEntries(path), sortBy, ascending, foldersFirst
                )
            }

            val dir = File(path)
            val normalList = try {
                if (dir.exists() && dir.isDirectory) dir.listFiles() else null
            } catch (_: Exception) {
                null
            }

            // File API can return null OR an empty array when SELinux blocks
            // the listing (e.g. /data without root). Fall back to the shell
            // in both cases — with root granted, libsu runs as root and can
            // list every directory.
            val raw = if (!normalList.isNullOrEmpty()) {
                normalList.map { FileItem.fromFile(it) }
            } else {
                listViaShell(path)
            }
            val sorted = FileItem.sortItems(
                raw.filter { showHidden || !it.isHidden }.filter { it.name != "" },
                sortBy, ascending, foldersFirst
            )
            // Prepend the ".." entry so the list header can go up one level.
            val parent = dir.parent
            if (parent != null && parent != path) {
                listOf(FileItem(name = "..", path = parent, isDirectory = true)) + sorted
            } else {
                sorted
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * List via shell. Uses `ls -lap` so directories end with `/` and the
     * permission string is available. Falls back to `ls -la` and finally
     * `ls -1` (names only) for restricted directories. With root granted,
     * libsu executes as root and can list every directory.
     *
     * The parser is format-agnostic: it never assumes how many columns the
     * date occupies (toybox uses "2026-10-23 10:02", GNU/busybox use
     * "Oct 25 10:25"), because root shells frequently ship a different ls
     * than the platform toybox one.
     */
    private fun listViaShell(path: String): List<FileItem> {
        return try {
            val safe = path.replace("\"", "\\\"")
            val base = if (path.endsWith("/")) path else "$path/"

            var lines = runShellOut("ls -lap \"$safe\" 2>/dev/null")
            if (lines.isEmpty()) lines = runShellOut("ls -la \"$safe\" 2>/dev/null")
            if (lines.isEmpty()) lines = runShellOut("ls -1 \"$safe\" 2>/dev/null")
            if (lines.isEmpty()) return emptyList()

            val parsed = lines.mapNotNull { parseLsLine(it, base) }
            if (parsed.isNotEmpty()) return parsed

            // Long-format parsing produced nothing (unknown ls dialect):
            // degrade to a names-only listing.
            runShellOut("ls -1A \"$safe\" 2>/dev/null")
                .mapNotNull { raw ->
                    val name = raw.trim().substringBefore(" -> ").trim().trimEnd('/')
                    if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                    val f = File(base + name)
                    FileItem(
                        name = name,
                        path = f.absolutePath,
                        isDirectory = f.isDirectory,
                        size = if (f.isFile) f.length() else 0L,
                        isHidden = name.startsWith(".")
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parse one `ls -la` line into a FileItem. Layout:
     * `perms links owner group size <date...> name [-> target]`.
     *
     * The name is located by skipping date-like columns (ISO date, HH:MM,
     * month abbreviation, bare day number) after the size column — this is
     * independent of how many date columns the local ls printed. Lines whose
     * name column is missing (some root ls builds drop symlink names) are
     * skipped, matching MT管理器 which shows only real entries.
     */
    private fun parseLsLine(line0: String, base: String): FileItem? {
        val line = line0.trimEnd()
        if (line.isEmpty() || line.startsWith("total ")) return null
        if (line.length < 11) return null
        val perms = line.substring(0, 10)
        if (perms[0] !in "dlbcps-") return null
        val sep = line[10]
        // ' ' normally; '+' when GNU ls appends an ACL marker.
        if (sep != ' ' && sep != '+') return null

        val fields = line.substring(11).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // links owner group size + at least one date column + name
        if (fields.size < 6) return null

        val size = fields[3].toLongOrNull() ?: 0L

        // Walk past the date columns; the first non-date token starts the name.
        var nameIdx = -1
        for (i in 4 until fields.size) {
            if (fields[i] == "->") break
            if (!isDateToken(fields[i])) {
                nameIdx = i
                break
            }
        }

        val arrowIdx = fields.indexOfFirst { it == "->" }
        val nameEnd = if (arrowIdx > nameIdx) arrowIdx else fields.size

        val name = if (nameIdx >= 0) {
            // Names may contain spaces: join everything up to the arrow (or EOL).
            // `ls -p` appends '/' to directories — strip it from the name only.
            fields.subList(nameIdx, nameEnd).joinToString(" ").trimEnd('/')
        } else {
            // Malformed row: some ls builds print symlinks as "-> target" with
            // the name column missing. Derive the name from the link target so
            // entries like /sdcard -> /storage/emulated/0 stay reachable.
            if (arrowIdx in 0 until fields.size - 1) {
                fields[arrowIdx + 1].trimEnd('/').substringAfterLast('/')
            } else ""
        }
        if (name.isEmpty() || name == "." || name == "..") return null

        val target = if (arrowIdx in 0 until fields.size - 1) {
            fields.subList(arrowIdx + 1, fields.size).joinToString(" ")
        } else null
        val isDir = perms.startsWith("d") || (target != null && target.endsWith("/"))

        return FileItem(
            name = name,
            path = base + name,
            isDirectory = isDir,
            size = size,
            lastModified = parseLsDate(fields),
            isHidden = name.startsWith("."),
            permission = perms,
            isSymlink = perms.startsWith("l")
        )
    }

    /**
     * Best-effort mtime from the date columns of an `ls -la` line. Handles
     * toybox ISO ("2026-10-23 10:02") and GNU/busybox month-name formats
     * ("Oct 25 10:25", "Oct 25  2024"). Returns 0 when unparseable.
     */
    private fun parseLsDate(fields: List<String>): Long {
        val iso = fields.firstOrNull { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
        if (iso != null) {
            val time = fields.firstOrNull { Regex("""\d{1,2}:\d{2}(:\d{2})?""").matches(it) } ?: "00:00"
            return runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    .parse("$iso $time")?.time
            }.getOrNull() ?: 0L
        }
        val mon = fields.firstOrNull { Regex("""[A-Z][a-z]{2}""").matches(it) } ?: return 0L
        val i = fields.indexOf(mon)
        if (i + 2 >= fields.size) return 0L
        val day = fields[i + 1]
        if (!Regex("""\d{1,2}""").matches(day)) return 0L
        val third = fields[i + 2]
        if (Regex("""\d{4}""").matches(third)) {
            // "Oct 25 2024" — year column instead of time
            return runCatching {
                java.text.SimpleDateFormat("MMM dd yyyy", java.util.Locale.US)
                    .parse("$mon $day $third")?.time
            }.getOrNull() ?: 0L
        }
        // "Oct 25 10:25" — no year column; assume the current year
        return runCatching {
            val d = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.US)
                .parse("$mon $day $third") ?: return@runCatching null
            val cal = java.util.Calendar.getInstance().apply { time = d }
            cal.set(java.util.Calendar.YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
            cal.timeInMillis
        }.getOrNull() ?: 0L
    }

    /** True for tokens that belong to the date part of an `ls -la` line. */
    private fun isDateToken(f: String): Boolean =
        Regex("""\d{4}-\d{2}-\d{2}""").matches(f) ||          // 2026-10-23
            Regex("""\d{1,2}:\d{2}(:\d{2})?""").matches(f) ||  // 10:02 / 10:02:33
            Regex("""[A-Z][a-z]{2}""").matches(f) ||           // Oct
            Regex("""\d{1,2}""").matches(f) ||                 // day-of-month
            Regex("""\d{4}""").matches(f)                      // year (busybox old dates)

    /** Run a shell command and return its stdout lines (empty on failure). */
    private fun runShellOut(cmd: String): List<String> {
        return try {
            val result = Shell.cmd(cmd).exec()
            if (result.out.isEmpty()) emptyList() else result.out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * List the contents of a virtual `zip://archive!internal` path. Real zip
     * archives use zip4j; 7z / tar family / gz / bz2 / xz / deb go through
     * [ArchiveHelper], producing the same FileItem shape.
     */
    private fun listArchiveEntries(virtualPath: String): List<FileItem> {
        val parts = virtualPath.removePrefix("zip://").split("!", limit = 2)
        val realArchive = parts[0]
        val internal = if (parts.size > 1) parts[1].trimStart('/') else ""

        return if (ArchiveHelper.detectType(realArchive) == ArchiveType.ZIP) {
            listZipEntries(realArchive, internal)
        } else {
            ArchiveHelper.buildItems(
                realArchive, internal, ArchiveHelper.listEntriesCached(realArchive)
            )
        }
    }

    private fun listZipEntries(realZip: String, internal: String): List<FileItem> {
        return try {
            val zip = openZip(realZip)
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

    /**
     * Create an archive in [format]: zip (zip4j, password supported),
     * 7z (LZMA2) and tar / tar.gz / tar.bz2 / tar.xz via [ArchiveHelper].
     */
    suspend fun createArchive(
        items: List<FileItem>,
        dest: String,
        format: ArchiveFormat,
        password: String? = null,
        onProgress: (OpProgress) -> Unit
    ): TransferResult = when (format) {
        ArchiveFormat.ZIP -> createZip(items, dest, password, onProgress)
        ArchiveFormat.SEVENZ -> ArchiveHelper.create7z(items, dest, onProgress)
        ArchiveFormat.TAR -> ArchiveHelper.createTar(items, dest, ArchiveType.TAR, onProgress)
        ArchiveFormat.TGZ -> ArchiveHelper.createTar(items, dest, ArchiveType.TGZ, onProgress)
        ArchiveFormat.TBZ2 -> ArchiveHelper.createTar(items, dest, ArchiveType.TBZ2, onProgress)
        ArchiveFormat.TXZ -> ArchiveHelper.createTar(items, dest, ArchiveType.TXZ, onProgress)
    }

    /**
     * Extract a folder entry (zip:// path) into [destDir]. For real zips the
     * matching headers are extracted by prefix; other archive types let
     * [ArchiveHelper.extractSingle] handle the prefix.
     */
    suspend fun extractArchiveFolder(entry: FileItem, destDir: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val archivePath = entry.zipParentPath ?: return@withContext false
                val internal = entry.path.substringAfter("!", "").removePrefix("/").trimEnd('/')
                if (ArchiveHelper.detectType(archivePath) != ArchiveType.ZIP) {
                    return@withContext ArchiveHelper.extractSingle(archivePath, internal, destDir)
                }
                val zipFile = openZip(archivePath)
                val prefix = "$internal/"
                var ok = true
                for (header in zipFile.fileHeaders) {
                    if (!header.fileName.startsWith(prefix)) continue
                    runCatching { zipFile.extractFile(header, destDir) }.onFailure { ok = false }
                }
                ok
            } catch (e: Exception) {
                false
            }
        }

    suspend fun addToZip(
        items: List<FileItem>,
        zipPath: String,
        destDirInZip: String? = null
    ): TransferResult = withContext(Dispatchers.IO) {
        try {
            val zipFile = openZip(zipPath)
            // destDirInZip: zip 预览内当前所在子目录，作为添加的根前缀
            val root = destDirInZip?.trim('/')?.takeIf { it.isNotEmpty() }?.let { "$it/" }
            val params = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                if (root != null) rootFolderNameInZip = root
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
        if (ArchiveHelper.detectType(zipPath) != ArchiveType.ZIP) {
            return@withContext ArchiveHelper.extractAll(zipPath, destDir, onProgress)
        }
        try {
            val zipFile = openZip(zipPath, password)
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

    /** Extract a single entry from any supported archive (zip:// paths). */
    suspend fun extractZipEntry(entry: FileItem, destDir: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val archivePath = entry.zipParentPath ?: return@withContext false
                val internal = entry.path.substringAfter("!", "").removePrefix("/")
                if (ArchiveHelper.detectType(archivePath) != ArchiveType.ZIP) {
                    return@withContext ArchiveHelper.extractSingle(archivePath, internal, destDir)
                }
                val zipFile = openZip(archivePath)
                zipFile.extractFile(internal, destDir)
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Extract one entry into [cacheDir] and return the temp file, so viewers
     * (image / text) can open archive contents without touching the archive.
     */
    suspend fun extractEntryToCache(entry: FileItem, cacheDir: File): File? =
        withContext(Dispatchers.IO) {
            try {
                val archivePath = entry.zipParentPath ?: return@withContext null
                val internal = entry.path.substringAfter("!", "").removePrefix("/")
                cacheDir.mkdirs()
                if (ArchiveHelper.detectType(archivePath) != ArchiveType.ZIP) {
                    val ok = ArchiveHelper.extractSingle(archivePath, internal, cacheDir.absolutePath)
                    if (!ok) return@withContext null
                    // Prefer the exact member path; fall back to the decompressed
                    // single file name for .gz/.bz2/.xz archives.
                    File(cacheDir, internal).takeIf { it.exists() }
                        ?: File(cacheDir, ArchiveHelper.innerFileName(File(archivePath).name))
                            .takeIf { it.exists() }
                        ?: return@withContext null
                } else {
                    val zipFile = openZip(archivePath)
                    val out = File(cacheDir, internal.substringAfterLast('/'))
                    zipFile.extractFile(internal, cacheDir.absolutePath)
                    if (out.exists()) out else null
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Read an archive entry as text (zip:// item supported). Returns null on failure. */
    suspend fun readZipEntryText(entry: FileItem, maxSize: Long = 4 * 1024 * 1024): String? =
        withContext(Dispatchers.IO) {
            try {
                val archivePath = entry.zipParentPath ?: return@withContext null
                val internal = entry.path.substringAfter("!", "").removePrefix("/")
                if (ArchiveHelper.detectType(archivePath) != ArchiveType.ZIP) {
                    return@withContext ArchiveHelper.readEntryBytes(archivePath, internal, maxSize)
                        ?.toString(Charsets.UTF_8)
                }
                val zipFile = openZip(archivePath)
                val header = zipFile.fileHeaders.firstOrNull { it.fileName == internal }
                    ?: return@withContext null
                if (header.uncompressedSize > maxSize) return@withContext null
                zipFile.getInputStream(header).use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Read every entry and verify it can be decompressed. zip4j 2.11 has no
     * built-in test API, so entries are streamed and discarded.
     */
    suspend fun testZip(zipPath: String, password: String? = null): String =
        withContext(Dispatchers.IO) {
            if (ArchiveHelper.detectType(zipPath) != ArchiveType.ZIP) {
                return@withContext try {
                    val entries = ArchiveHelper.listEntriesCached(zipPath)
                    if (entries.isEmpty()) "文件损坏或不是有效的压缩包"
                    else "测试通过，${entries.size} 个条目未发现错误"
                } catch (e: Exception) {
                    "测试失败: ${e.message}"
                }
            }
            try {
                val zipFile = openZip(zipPath, password)
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

    /** Only real zip archives support passwords (7z write encryption unavailable). */
    fun zipNeedsPassword(zipPath: String): Boolean = try {
        ArchiveHelper.detectType(zipPath) == ArchiveType.ZIP && openZip(zipPath).isEncrypted
    } catch (_: Exception) {
        false
    }

    fun zipEntryCount(zipPath: String): Int = try {
        openZip(zipPath).fileHeaders.size
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

    suspend fun writeTextFile(path: String, content: String, charset: java.nio.charset.Charset = java.nio.charset.StandardCharsets.UTF_8): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).writeBytes(content.toByteArray(charset))
            true
        } catch (e: Exception) {
            try {
                val b64 = android.util.Base64.encodeToString(
                    content.toByteArray(charset),
                    android.util.Base64.NO_WRAP
                )
                Shell.cmd("echo '$b64' | base64 -d > \"$path\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Read raw bytes of a file. Falls back to a base64 round-trip through the
     * shell for root-only paths the app itself cannot open.
     */
    suspend fun readFileBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val f = File(path)
            if (f.exists() && f.canRead()) return@withContext f.readBytes()
            val r = Shell.cmd("base64 \"$path\" 2>/dev/null").exec()
            if (r.isSuccess && r.out.isNotEmpty()) {
                android.util.Base64.decode(r.out.joinToString(""), android.util.Base64.DEFAULT)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect a file's charset: BOM first, then a strict UTF-8 probe, then
     * GB18030 (covers GBK), finally ISO-8859-1 as a lossless last resort.
     */
    fun detectCharset(bytes: ByteArray): java.nio.charset.Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return java.nio.charset.StandardCharsets.UTF_8
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return java.nio.charset.Charset.forName("UTF-16LE")
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return java.nio.charset.Charset.forName("UTF-16BE")
        }
        val decoded = runCatching { String(bytes, java.nio.charset.StandardCharsets.UTF_8) }.getOrNull()
        if (decoded != null && !decoded.contains('\uFFFD')) {
            return java.nio.charset.StandardCharsets.UTF_8
        }
        return runCatching {
            java.nio.charset.Charset.forName("GB18030")
        }.getOrDefault(java.nio.charset.StandardCharsets.ISO_8859_1)
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

    // ------------------------------------------------------------------- share

    /**
     * Share a file via FileProvider + system chooser. [authority] is the
     * applicationId + ".fileprovider" declared in the manifest.
     */
    fun shareFile(context: android.content.Context, path: String, authority: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "分享 ${file.name}")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Share several files at once via ACTION_SEND_MULTIPLE. */
    fun shareFiles(
        context: android.content.Context,
        paths: List<String>,
        authority: String
    ): Boolean {
        if (paths.isEmpty()) return false
        return try {
            val uris = ArrayList<android.net.Uri>()
            var mime = "*/*"
            for (p in paths) {
                val file = File(p)
                if (!file.exists()) continue
                uris.add(
                    androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                )
                val m = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                if (m != null && mime == "*/*") mime = m
            }
            if (uris.isEmpty()) return false
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "分享 ${uris.size} 个文件")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Open [path] with a system app via ACTION_VIEW (video/audio/unknown). */    fun openWithSystem(context: android.content.Context, path: String, authority: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Hand an APK to the system package installer. */
    fun installApk(context: android.content.Context, path: String, authority: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
