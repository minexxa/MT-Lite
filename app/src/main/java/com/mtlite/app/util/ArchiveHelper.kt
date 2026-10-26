package com.mtlite.app.util

import com.mtlite.app.data.FileItem
import com.mtlite.app.data.OpProgress
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream

/** Supported archive container types (browsing + extraction). */
enum class ArchiveType {
    ZIP, SEVENZ, TAR, TGZ, TBZ2, TXZ, GZ, BZ2, XZ, DEB, UNSUPPORTED;

    val isTarFamily: Boolean get() = this == TAR || this == TGZ || this == TBZ2 || this == TXZ
    val isSingleFileCompressed: Boolean get() = this == GZ || this == BZ2 || this == XZ
    val isSupported: Boolean get() = this != UNSUPPORTED
}

/** Create formats offered by the compress dialog. */
enum class ArchiveFormat(val extension: String, val label: String) {
    ZIP(".zip", "ZIP"),
    SEVENZ(".7z", "7z"),
    TAR(".tar", "TAR"),
    TGZ(".tar.gz", "TAR.GZ"),
    TBZ2(".tar.bz2", "TAR.BZ2"),
    TXZ(".tar.xz", "TAR.XZ");

    /** Only zip supports passwords (7z write encryption is not supported by commons-compress). */
    val supportsPassword: Boolean get() = this == ZIP
}

/** Flat archive member descriptor used to build the browse listing. */
data class ArchiveEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long = 0L
)

/**
 * Browsing / extraction for 7z, tar, tar.gz, tar.bz2, tar.xz, gz, bz2, xz and
 * deb (ar) archives via commons-compress. Zip archives stay on zip4j inside
 * [FileHelper]. Entry paths use the same `zip://archive!internal` scheme, so
 * the existing UI works unchanged.
 */
object ArchiveHelper {

    /** Detect type by file name. Compound extensions are checked first. */
    fun detectType(path: String): ArchiveType {
        val n = path.substringAfterLast('/').lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveType.TGZ
            n.endsWith(".tar.bz2") || n.endsWith(".tbz") || n.endsWith(".tbz2") -> ArchiveType.TBZ2
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveType.TXZ
            n.endsWith(".tar") -> ArchiveType.TAR
            n.endsWith(".7z") -> ArchiveType.SEVENZ
            n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".apk") ||
                n.endsWith(".aar") || n.endsWith(".xapk") || n.endsWith(".apks") -> ArchiveType.ZIP
            n.endsWith(".gz") -> ArchiveType.GZ
            n.endsWith(".bz2") -> ArchiveType.BZ2
            n.endsWith(".xz") -> ArchiveType.XZ
            n.endsWith(".deb") -> ArchiveType.DEB
            else -> ArchiveType.UNSUPPORTED
        }
    }

    // ------------------------------------------------------------- entry cache

    /**
     * Small LRU of entry listings keyed by archive path. Tar-family listings
     * require a full decompress scan, so repeated navigation inside one
     * archive reuses the cached result (invalidated by size + mtime).
     */
    private val listingCache = object : LinkedHashMap<String, Pair<Long, List<ArchiveEntryInfo>>>(
        4, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<ArchiveEntryInfo>>>): Boolean =
            size > 4
    }

    /** Full entry list of [archivePath], cached. Empty when unreadable. */
    fun listEntriesCached(archivePath: String): List<ArchiveEntryInfo> {
        val f = File(archivePath)
        if (!f.exists()) return emptyList()
        val stamp = f.lastModified() xor (f.length() shl 20)
        synchronized(listingCache) {
            listingCache[archivePath]?.let { (s, list) -> if (s == stamp) return list }
        }
        val list = listEntries(archivePath)
        if (list.isNotEmpty()) {
            synchronized(listingCache) { listingCache[archivePath] = stamp to list }
        }
        return list
    }

    fun listEntries(archivePath: String): List<ArchiveEntryInfo> = try {
        when (detectType(archivePath)) {
            ArchiveType.SEVENZ -> list7z(archivePath)
            ArchiveType.TAR, ArchiveType.TGZ, ArchiveType.TBZ2, ArchiveType.TXZ -> listTar(archivePath)
            ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> listOf(singleEntry(archivePath))
            ArchiveType.DEB -> listDeb(archivePath)
            else -> emptyList()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    /**
     * Synthesize the pane listing for `zip://archive!internal` from a flat
     * entry list — same shape as the zip4j listing in [FileHelper].
     */
    fun buildItems(
        archivePath: String,
        internalPath: String,
        entries: List<ArchiveEntryInfo>
    ): List<FileItem> {
        val result = mutableListOf<FileItem>()
        if (internalPath.isNotEmpty()) {
            val parent = File(internalPath).parent ?: ""
            result.add(
                FileItem(
                    name = "..",
                    path = if (parent.isEmpty()) "zip://$archivePath" else "zip://$archivePath!/$parent",
                    isDirectory = true,
                    isZipEntry = true,
                    zipParentPath = archivePath
                )
            )
        }
        val prefix = if (internalPath.isEmpty()) "" else "$internalPath/"
        val seenDirs = mutableSetOf<String>()
        for (e in entries) {
            val name = e.name
            if (!name.startsWith(prefix) || name == prefix) continue
            val rel = name.removePrefix(prefix)
            val firstSeg = rel.substringBefore('/')
            if (firstSeg.isEmpty()) continue
            if (rel.contains('/')) {
                if (seenDirs.add(firstSeg)) {
                    result.add(
                        FileItem(
                            name = firstSeg,
                            path = "zip://$archivePath!/$prefix$firstSeg",
                            isDirectory = true,
                            isZipEntry = true,
                            zipParentPath = archivePath,
                            lastModified = e.lastModified
                        )
                    )
                }
            } else {
                result.add(
                    FileItem(
                        name = firstSeg,
                        path = "zip://$archivePath!/$name",
                        isDirectory = e.isDirectory,
                        size = if (e.isDirectory) 0L else e.size,
                        isZipEntry = true,
                        zipParentPath = archivePath,
                        lastModified = e.lastModified
                    )
                )
            }
        }
        return result
    }

    // ----------------------------------------------------------------- listing

    private fun list7z(path: String): List<ArchiveEntryInfo> =
        SevenZFile.Builder().setFile(File(path)).get().use { z ->
            z.entries.asSequence().filterIsInstance<SevenZArchiveEntry>().toList().map {
                // Entries may lack any timestamp — getLastModifiedDate throws then.
                val mtime = try { it.lastModifiedDate?.time ?: 0L } catch (_: UnsupportedOperationException) { 0L }
                ArchiveEntryInfo(
                    name = normalize(it.name),
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 0L else it.size,
                    lastModified = mtime
                )
            }
        }

    private fun listTar(path: String): List<ArchiveEntryInfo> {
        val input = openDecompressed(path) ?: return emptyList()
        return input.use { stream ->
            TarArchiveInputStream(stream).use { t ->
                val list = mutableListOf<ArchiveEntryInfo>()
                var e = t.nextEntry
                while (e != null) {
                    if (e.name.isNotBlank()) {
                        list.add(
                            ArchiveEntryInfo(
                                name = normalize(e.name),
                                isDirectory = e.isDirectory,
                                size = if (e.isDirectory) 0L else e.size,
                                lastModified = e.modTime.time
                            )
                        )
                    }
                    e = t.nextEntry
                }
                list
            }
        }
    }

    private fun listDeb(path: String): List<ArchiveEntryInfo> {
        ArArchiveInputStream(BufferedInputStream(FileInputStream(path), 65536)).use { a ->
            val list = mutableListOf<ArchiveEntryInfo>()
            var e: ArArchiveEntry? = a.nextEntry
            while (e != null) {
                list.add(
                    ArchiveEntryInfo(
                        name = normalize(e.name),
                        isDirectory = false,
                        size = e.length,
                        lastModified = e.lastModified * 1000L
                    )
                )
                e = a.nextEntry
            }
            return list
        }
    }

    /** The single pseudo entry of a .gz / .bz2 / .xz file. */
    private fun singleEntry(path: String): ArchiveEntryInfo {
        val f = File(path)
        val inner = innerFileName(f.name)
        // Counting requires a full decompress; only bother for modest files.
        val size = if (f.length() <= 64L * 1024 * 1024) decompressedSize(path) else 0L
        return ArchiveEntryInfo(inner, false, size, f.lastModified())
    }

    /** "data.gz" -> "data"; "archive.xz" -> "archive". */
    fun innerFileName(archiveName: String): String {
        val base = archiveName.substringBeforeLast('.')
        return if (base.isBlank()) "${archiveName}_out" else base
    }

    private fun decompressedSize(path: String, cap: Long = 512L * 1024 * 1024): Long =
        try {
            var n = 0L
            openDecompressed(path)?.use { input ->
                val buf = ByteArray(65536)
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    n += r
                    if (n > cap) return@use
                }
            }
            n
        } catch (_: Exception) {
            0L
        }

    // ---------------------------------------------------------------- streams

    /** Open the (de)compressed input stream for an archive path. */
    private fun openDecompressed(path: String): InputStream? = try {
        val raw: InputStream = BufferedInputStream(FileInputStream(path), 65536)
        when (detectType(path)) {
            ArchiveType.TGZ -> GZIPInputStream(raw, 8192)
            ArchiveType.TBZ2 -> BZip2CompressorInputStream(raw)
            ArchiveType.TXZ -> XZCompressorInputStream(raw)
            ArchiveType.GZ -> GZIPInputStream(raw, 8192)
            ArchiveType.BZ2 -> BZip2CompressorInputStream(raw)
            ArchiveType.XZ -> XZCompressorInputStream(raw)
            else -> raw
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /** Strip leading "/" and "./"; unify separators; drop trailing "/". */
    private fun normalize(name: String): String {
        var n = name.replace('\\', '/').trimStart('/')
        while (n.startsWith("./")) n = n.substring(2)
        return n.trimEnd('/')
    }

    /** Guard against path traversal ("zip slip") when extracting. */
    private fun safeTarget(destDir: File, entryName: String): File? = try {
        val f = File(destDir, normalize(entryName))
        if (f.canonicalPath.startsWith(destDir.canonicalPath)) f else null
    } catch (_: Exception) {
        null
    }

    private fun readFully(input: InputStream, size: Long): ByteArray {
        val buf = ByteArray(size.toInt())
        var off = 0
        while (off < buf.size) {
            val r = input.read(buf, off, buf.size - off)
            if (r <= 0) break
            off += r
        }
        return buf
    }

    // -------------------------------------------------------------- extraction

    suspend fun extractAll(
        archivePath: String,
        destDir: String,
        onProgress: (OpProgress) -> Unit
    ): TransferResult {
        FileHelper.resetCancel()
        return try {
            when (detectType(archivePath)) {
                ArchiveType.SEVENZ -> extract7zAll(archivePath, destDir, onProgress)
                ArchiveType.TAR, ArchiveType.TGZ, ArchiveType.TBZ2, ArchiveType.TXZ ->
                    extractTarAll(archivePath, destDir, onProgress)
                ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> {
                    File(destDir).mkdirs()
                    val out = File(destDir, innerFileName(File(archivePath).name))
                    val ok = copyDecompressed(archivePath, out)
                    TransferResult(success = if (ok) 1 else 0, failed = if (ok) 0 else 1)
                }
                ArchiveType.DEB -> extractDebAll(archivePath, destDir, onProgress)
                else -> TransferResult(failed = 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = 1)
        }
    }

    private fun extract7zAll(
        path: String,
        destDir: String,
        onProgress: (OpProgress) -> Unit
    ): TransferResult =
        SevenZFile.Builder().setFile(File(path)).get().use { z ->
            val entries = z.entries.asSequence().filterIsInstance<SevenZArchiveEntry>().toList()
            val total = entries.size
            var done = 0
            File(destDir).mkdirs()
            for (e in entries) {
                if (FileHelper.cancelRequested) break
                onProgress(
                    OpProgress(
                        active = true, title = "解压",
                        current = done, total = total, currentName = e.name
                    )
                )
                val out = safeTarget(File(destDir), e.name)
                if (out != null) {
                    runCatching {
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            z.getInputStream(e).use { input ->
                                out.outputStream().use { input.copyTo(it, 65536) }
                            }
                        }
                        done++
                    }
                }
            }
            TransferResult(success = done, failed = total - done)
        }

    private fun extractTarAll(
        path: String,
        destDir: String,
        onProgress: (OpProgress) -> Unit
    ): TransferResult {
        val input = openDecompressed(path) ?: return TransferResult(failed = 1)
        var done = 0
        var failed = 0
        File(destDir).mkdirs()
        input.use { stream ->
            TarArchiveInputStream(stream).use { t ->
                var e = t.nextEntry
                while (e != null) {
                    if (FileHelper.cancelRequested) break
                    onProgress(
                        OpProgress(
                            active = true, title = "解压",
                            current = done + failed, total = done + failed + 1,
                            currentName = e.name
                        )
                    )
                    if (extractTarEntry(t, e, File(destDir))) done++ else failed++
                    e = t.nextEntry
                }
            }
        }
        return TransferResult(success = done, failed = failed)
    }

    /** Copy the current tar entry to disk; caller advances to the next entry. */
    private fun extractTarEntry(t: TarArchiveInputStream, e: TarArchiveEntry, destDir: File): Boolean {
        return try {
            val out = safeTarget(destDir, e.name) ?: return false
            if (e.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    var remaining = e.size
                    while (remaining > 0) {
                        val r = t.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (r <= 0) break
                        output.write(buf, 0, r)
                        remaining -= r
                    }
                }
            }
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }

    private fun extractDebAll(
        path: String,
        destDir: String,
        onProgress: (OpProgress) -> Unit
    ): TransferResult {
        var done = 0
        var failed = 0
        File(destDir).mkdirs()
        ArArchiveInputStream(BufferedInputStream(FileInputStream(path), 65536)).use { a ->
            var e: ArArchiveEntry? = a.nextEntry
            while (e != null) {
                if (FileHelper.cancelRequested) break
                onProgress(
                    OpProgress(
                        active = true, title = "解压",
                        current = done + failed, total = done + failed + 1, currentName = e.name
                    )
                )
                val out = safeTarget(File(destDir), e.name)
                val ok = if (out != null) {
                    runCatching {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { a.copyTo(it, 65536) }
                    }.isSuccess
                } else false
                if (ok) done++ else failed++
                e = a.nextEntry
            }
        }
        return TransferResult(success = done, failed = failed)
    }

    /** Decompress a single-file .gz/.bz2/.xz into [out]. */
    fun copyDecompressed(archivePath: String, out: File): Boolean {
        return try {
            val input = openDecompressed(archivePath) ?: return false
            input.use {
                out.parentFile?.mkdirs()
                out.outputStream().use { output -> input.copyTo(output, 65536) }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Extract one member (or a whole folder when [entryName] is a directory
     * prefix) into [destDir]. Returns true on success.
     */
    fun extractSingle(archivePath: String, entryName: String, destDir: String): Boolean {
        return try {
        val target = normalize(entryName)
        when (detectType(archivePath)) {
            ArchiveType.SEVENZ -> {
                var ok = false
                SevenZFile.Builder().setFile(File(archivePath)).get().use { z ->
                    for (e in z.entries.asSequence().filterIsInstance<SevenZArchiveEntry>().toList()) {
                        val name = normalize(e.name)
                        if (name == target || name.startsWith("$target/")) {
                            val out = safeTarget(File(destDir), name) ?: continue
                            if (e.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                z.getInputStream(e).use { input ->
                                    out.outputStream().use { input.copyTo(it, 65536) }
                                }
                            }
                            ok = true
                        }
                    }
                }
                ok
            }
            ArchiveType.TAR, ArchiveType.TGZ, ArchiveType.TBZ2, ArchiveType.TXZ -> {
                val input = openDecompressed(archivePath) ?: return false
                var ok = false
                input.use { stream ->
                    TarArchiveInputStream(stream).use { t ->
                        var e = t.nextEntry
                        while (e != null) {
                            val name = normalize(e.name)
                            if (name == target || name.startsWith("$target/")) {
                                if (extractTarEntry(t, e, File(destDir))) ok = true
                            }
                            e = t.nextEntry
                        }
                    }
                }
                ok
            }
            ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> {
                val out = safeTarget(File(destDir), innerFileName(File(archivePath).name))
                out != null && copyDecompressed(archivePath, out)
            }
            ArchiveType.DEB -> {
                var ok = false
                ArArchiveInputStream(BufferedInputStream(FileInputStream(archivePath), 65536)).use { a ->
                    var e: ArArchiveEntry? = a.nextEntry
                    while (e != null) {
                        val entry: ArArchiveEntry = e ?: break
                        if (normalize(entry.name) == target) {
                            val out = safeTarget(File(destDir), target)
                            if (out != null) {
                                out.parentFile?.mkdirs()
                                out.outputStream().use { a.copyTo(it, 65536) }
                                ok = true
                            }
                            break
                        }
                        e = a.nextEntry
                    }
                }
                ok
            }
            else -> false
        }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Read one member fully; null when missing or larger than [maxBytes]. */
    fun readEntryBytes(archivePath: String, entryName: String, maxBytes: Long): ByteArray? {
        return try {
        val target = normalize(entryName)
        when (detectType(archivePath)) {
            ArchiveType.SEVENZ -> {
                var bytes: ByteArray? = null
                SevenZFile.Builder().setFile(File(archivePath)).get().use { z ->
                    val e = z.entries.asSequence().filterIsInstance<SevenZArchiveEntry>().toList()
                        .firstOrNull { normalize(it.name) == target } ?: return@use
                    if (!e.isDirectory && e.size <= maxBytes) {
                        z.getInputStream(e).use { bytes = readFully(it, e.size) }
                    }
                }
                bytes
            }
            ArchiveType.TAR, ArchiveType.TGZ, ArchiveType.TBZ2, ArchiveType.TXZ -> {
                val input = openDecompressed(archivePath) ?: return null
                var bytes: ByteArray? = null
                input.use { stream ->
                    TarArchiveInputStream(stream).use { t ->
                        var e = t.nextEntry
                        while (e != null) {
                            if (normalize(e.name) == target) {
                                if (!e.isDirectory && e.size in 1..maxBytes) {
                                    bytes = readFully(t, e.size)
                                }
                                break
                            }
                            e = t.nextEntry
                        }
                    }
                }
                bytes
            }
            ArchiveType.GZ, ArchiveType.BZ2, ArchiveType.XZ -> {
                val input = openDecompressed(archivePath) ?: return null
                input.use { s ->
                    val buf = ByteArray((maxBytes + 1).toInt().coerceAtLeast(1))
                    var off = 0
                    while (off < buf.size) {
                        val r = s.read(buf, off, buf.size - off)
                        if (r <= 0) break
                        off += r
                    }
                    if (off <= maxBytes) buf.copyOf(off) else null
                }
            }
            ArchiveType.DEB -> {
                var bytes: ByteArray? = null
                ArArchiveInputStream(BufferedInputStream(FileInputStream(archivePath), 65536)).use { a ->
                    var e: ArArchiveEntry? = a.nextEntry
                    while (e != null) {
                        val entry: ArArchiveEntry = e ?: break
                        if (normalize(entry.name) == target) {
                            if (entry.length in 1..maxBytes) bytes = readFully(a, entry.length)
                            break
                        }
                        e = a.nextEntry
                    }
                }
                bytes
            }
            else -> null
        }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ---------------------------------------------------------------- creation

    suspend fun create7z(
        items: List<FileItem>,
        dest: String,
        onProgress: (OpProgress) -> Unit
    ): TransferResult {
        FileHelper.resetCancel()
        return try {
            val files = items.map { File(it.path) }.filter { it.exists() }
            if (files.isEmpty()) return TransferResult()
            SevenZOutputFile(File(dest)).use { out ->
                out.setContentCompression(SevenZMethod.LZMA2)
                var index = 0
                for (f in files) {
                    if (FileHelper.cancelRequested) return TransferResult(success = index)
                    onProgress(
                        OpProgress(
                            active = true, title = "压缩",
                            current = index, total = files.size, currentName = f.name
                        )
                    )
                    if (f.isDirectory) add7zDir(out, f, f.name) else add7zFile(out, f, f.name)
                    index++
                }
                TransferResult(success = files.size)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = items.size)
        }
    }

    private fun add7zDir(out: SevenZOutputFile, dir: File, arcName: String) {
        val e = SevenZArchiveEntry()
        e.name = "$arcName/"
        e.isDirectory = true
        out.putArchiveEntry(e)
        out.closeArchiveEntry()
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (child.isDirectory) add7zDir(out, child, "$arcName/${child.name}")
            else add7zFile(out, child, "$arcName/${child.name}")
        }
    }

    private fun add7zFile(out: SevenZOutputFile, f: File, arcName: String) {
        val e = SevenZArchiveEntry()
        e.name = arcName
        e.setSize(f.length())
        out.putArchiveEntry(e)
        val buf = ByteArray(65536)
        f.inputStream().use { input ->
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                out.write(buf, 0, r)
            }
        }
        out.closeArchiveEntry()
    }

    /** Create a tar (optionally gz/bz2/xz compressed) at [dest]. */
    suspend fun createTar(
        items: List<FileItem>,
        dest: String,
        type: ArchiveType,
        onProgress: (OpProgress) -> Unit
    ): TransferResult {
        FileHelper.resetCancel()
        return try {
            val files = items.map { File(it.path) }.filter { it.exists() }
            if (files.isEmpty()) return TransferResult()
            val raw: OutputStream = BufferedOutputStream(FileOutputStream(dest), 65536)
            val compressed: OutputStream = when (type) {
                ArchiveType.TGZ -> GzipCompressorOutputStream(raw)
                ArchiveType.TBZ2 -> BZip2CompressorOutputStream(raw)
                ArchiveType.TXZ -> XZCompressorOutputStream(raw)
                else -> raw
            }
            TarArchiveOutputStream(BufferedOutputStream(compressed, 65536)).use { t ->
                t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                var index = 0
                for (f in files) {
                    if (FileHelper.cancelRequested) break
                    onProgress(
                        OpProgress(
                            active = true, title = "压缩",
                            current = index, total = files.size, currentName = f.name
                        )
                    )
                    if (f.isDirectory) addTarDir(t, f, f.name) else addTarFile(t, f, f.name)
                    index++
                }
                TransferResult(success = index)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(failed = items.size)
        }
    }

    private fun addTarDir(t: TarArchiveOutputStream, dir: File, arcName: String) {
        val e = TarArchiveEntry("$arcName/")
        e.setModTime(dir.lastModified())
        t.putArchiveEntry(e)
        t.closeArchiveEntry()
        dir.listFiles()?.sortedBy { it.name }?.forEach { child ->
            if (child.isDirectory) addTarDir(t, child, "$arcName/${child.name}")
            else addTarFile(t, child, "$arcName/${child.name}")
        }
    }

    private fun addTarFile(t: TarArchiveOutputStream, f: File, arcName: String) {
        val e = TarArchiveEntry(arcName)
        e.size = f.length()
        e.setModTime(f.lastModified())
        e.mode = if (f.canExecute()) 0b111101101 else 0b110100100
        t.putArchiveEntry(e)
        f.inputStream().use { it.copyTo(t, 65536) }
        t.closeArchiveEntry()
    }
}
