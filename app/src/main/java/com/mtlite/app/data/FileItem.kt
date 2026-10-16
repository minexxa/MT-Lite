package com.mtlite.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isHidden: Boolean = false,
    val isSymlink: Boolean = false,
    val permission: String = "",
    // For ZIP entries
    val isZipEntry: Boolean = false,
    val zipParentPath: String? = null
) {
    val displaySize: String
        get() = if (isDirectory) "" else formatSize(size)

    val displayDate: String
        get() {
            if (lastModified <= 0) return ""
            val sdf = SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    companion object {
        fun fromFile(file: File): FileItem {
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                isHidden = file.isHidden || file.name.startsWith("."),
                isSymlink = file.canonicalPath != file.absolutePath
            )
        }

        fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}

enum class PaneSide { LEFT, RIGHT }

data class PaneState(
    val currentPath: String = "/storage/emulated/0",
    val items: List<FileItem> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val history: List<String> = listOf("/storage/emulated/0"),
    val historyIndex: Int = 0,
    val sortBy: SortBy = SortBy.NAME,
    val showHidden: Boolean = true
)

enum class SortBy { NAME, SIZE, DATE, TYPE }

data class ClipboardData(
    val items: List<FileItem> = emptyList(),
    val isCut: Boolean = false, // true = move, false = copy
    val sourcePath: String = ""
)
