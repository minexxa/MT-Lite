package com.mtlite.app.util

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.mtlite.app.data.FileItem
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileHelper {

    fun isRootAvailable(): Boolean {
        return Shell.isAppGrantedRoot() == true || Shell.getShell().isRoot
    }

    suspend fun listFiles(path: String, showHidden: Boolean = true): List<FileItem> =
        withContext(Dispatchers.IO) {
            try {
                if (path.startsWith("zip://")) {
                    return@withContext listZipEntries(path)
                }

                val dir = File(path)
                if (!dir.exists() || !dir.isDirectory) {
                    return@withContext emptyList()
                }

                val files = dir.listFiles() ?: emptyArray()
                val items = files
                    .filter { showHidden || !it.name.startsWith(".") }
                    .map { FileItem.fromFile(it) }
                    .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

                // Add parent ".."
                val parent = dir.parentFile
                val result = mutableListOf<FileItem>()
                if (parent != null && path != "/") {
                    result.add(
                        FileItem(
                            name = "..",
                            path = parent.absolutePath,
                            isDirectory = true,
                            lastModified = parent.lastModified()
                        )
                    )
                }
                result.addAll(items)
                result
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun listZipEntries(zipPath: String): List<FileItem> {
        // Format: zip:///path/to/file.zip!/internal/path
        val parts = zipPath.removePrefix("zip://").split("!", limit = 2)
        val realZip = parts[0]
        val internal = if (parts.size > 1) parts[1].trimStart('/') else ""

        return try {
            val zip = ZipFile(realZip)
            val entries = zip.fileHeaders
            val result = mutableListOf<FileItem>()

            // Parent
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
                    // Directory
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
                    // File
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
            if (src.isZipEntry) return@withContext false // TODO: extract from zip
            val source = File(src.path)
            val target = File(destDir, src.name)
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveFile(src: FileItem, destDir: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (src.isZipEntry) return@withContext false
            val source = File(src.path)
            val target = File(destDir, src.name)
            source.renameTo(target) || (copyFile(src, destDir) && deleteFile(src))
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFile(item: FileItem): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.isZipEntry) return@withContext false
            val file = File(item.path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createFile(parent: String, name: String, isDir: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val file = File(parent, name)
                if (isDir) file.mkdirs() else file.createNewFile()
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

    suspend fun renameFile(item: FileItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            val target = File(file.parent, newName)
            file.renameTo(target)
        } catch (e: Exception) {
            e.printStackTrace()
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

    fun getDefaultPaths(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        list.add("内部存储" to Environment.getExternalStorageDirectory().absolutePath)
        list.add("根目录" to "/")
        // Common paths
        val emulated = "/storage/emulated/0"
        if (File(emulated).exists()) list.add("内部存储" to emulated)
        return list.distinctBy { it.second }
    }

    suspend fun readTextFile(path: String, maxSize: Long = 2 * 1024 * 1024): String? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists() || file.length() > maxSize) return@withContext null
                file.readText()
            } catch (e: Exception) {
                null
            }
        }

    suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}
