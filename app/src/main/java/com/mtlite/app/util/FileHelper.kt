package com.mtlite.app.util

import android.os.StatFs
import com.mtlite.app.data.FileItem
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

object FileHelper {

    fun isRootAvailable(): Boolean {
        return try {
            Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listFiles(path: String, showHidden: Boolean = true): List<FileItem> =
        withContext(Dispatchers.IO) {
            try {
                if (path.startsWith("zip://")) {
                    return@withContext listZipEntries(path)
                }

                val dir = File(path)
                val normalList = try {
                    if (dir.exists() && dir.isDirectory && dir.canRead()) {
                        dir.listFiles()
                    } else null
                } catch (_: Exception) {
                    null
                }

                if (normalList != null) {
                    return@withContext buildResult(path, normalList.map { FileItem.fromFile(it) }, showHidden)
                }

                // Shell fallback (root or restricted dirs)
                val shellItems = listViaShell(path)
                buildResult(path, shellItems, showHidden)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun buildResult(
        path: String,
        items: List<FileItem>,
        showHidden: Boolean
    ): List<FileItem> {
        val filtered = items
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

        val result = mutableListOf<FileItem>()
        if (path != "/" && path.isNotEmpty()) {
            val parent = File(path).parent ?: "/"
            result.add(FileItem(name = "..", path = parent, isDirectory = true))
        }
        result.addAll(filtered)
        return result
    }

    /**
     * List via shell. Uses `ls -1pA` so directories end with `/`.
     */
    private fun listViaShell(path: String): List<FileItem> {
        return try {
            val safe = path.replace("\"", "\\\"")
            val result = Shell.cmd("ls -1pA \"$safe\" 2>/dev/null").exec()
            if (!result.isSuccess && result.out.isEmpty()) {
                return emptyList()
            }
            val base = if (path.endsWith("/")) path else "$path/"
            result.out
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "./" && it != "../" && it != "." && it != ".." }
                .map { raw ->
                    val isDir = raw.endsWith("/")
                    val name = raw.trimEnd('/')
                    val full = base + name
                    val f = File(full)
                    FileItem(
                        name = name,
                        path = full,
                        isDirectory = isDir || (try { f.isDirectory } catch (_: Exception) { false }),
                        size = if (!isDir) try { f.length() } catch (_: Exception) { 0L } else 0L,
                        lastModified = try { f.lastModified() } catch (_: Exception) { 0L },
                        isHidden = name.startsWith(".")
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
            result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun copyFile(src: FileItem, destDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (src.isZipEntry) return@withContext false
            val source = File(src.path)
            val target = File(destDir, src.name)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            true
        } catch (e: Exception) {
            try {
                Shell.cmd("cp -a \"${src.path}\" \"$destDir/\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun moveFile(src: FileItem, destDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (src.isZipEntry) return@withContext false
            val source = File(src.path)
            val target = File(destDir, src.name)
            if (source.renameTo(target)) return@withContext true
            copyFile(src, destDir) && deleteFile(src)
        } catch (e: Exception) {
            try {
                Shell.cmd("mv \"${src.path}\" \"$destDir/\"").exec().isSuccess
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteFile(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.isZipEntry) return@withContext false
            val file = File(item.path)
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
            if (file.renameTo(target)) return@withContext true
            Shell.cmd("mv \"${item.path}\" \"${file.parent}/$newName\"").exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }

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
        return list
    }

    suspend fun readTextFile(path: String, maxSize: Long = 2 * 1024 * 1024): String? =
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
}
