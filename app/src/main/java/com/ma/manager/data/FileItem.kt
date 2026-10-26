package com.ma.manager.data

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

    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    /** Coarse file kind used for icon selection and open-with logic. */
    val fileKind: FileKind
        get() = when {
            isDirectory -> FileKind.FOLDER
            extension == "apk" -> FileKind.APK
            extension in ZIP_EXTS -> FileKind.ARCHIVE
            extension in IMAGE_EXTS -> FileKind.IMAGE
            extension in VIDEO_EXTS -> FileKind.VIDEO
            extension in AUDIO_EXTS -> FileKind.AUDIO
            extension in TEXT_EXTS -> FileKind.TEXT
            extension in CODE_EXTS -> FileKind.CODE
            else -> FileKind.OTHER
        }

    companion object {
        val ZIP_EXTS = setOf(
            "zip", "apk", "jar", "aar", "xapk", "apks",
            "7z", "tar", "tar.gz", "gz", "bz2", "xz", "tgz", "tbz", "tbz2", "txz", "deb"
        )
        val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg", "ico", "heic", "heif")
        val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "ts", "m4v")
        val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr")
        val TEXT_EXTS = setOf(
            "txt", "md", "log", "ini", "conf", "properties", "gradle", "kts",
            "yaml", "yml", "toml", "csv", "tsv", "srt", "lrc"
        )
        val CODE_EXTS = setOf(
            "kt", "java", "py", "js", "ts", "tsx", "jsx", "c", "cpp", "h", "hpp",
            "cs", "go", "rs", "swift", "rb", "php", "lua", "sh", "bat", "ps1",
            "sql", "r", "dart", "html", "htm", "css", "scss", "xml", "json", "smali"
        )

        fun fromFile(file: File): FileItem {
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                isHidden = file.isHidden || file.name.startsWith("."),
                isSymlink = runCatching { file.canonicalPath != file.absolutePath }.getOrDefault(false)
            )
        }

        /** Official MA style storage text, e.g. "198.10G". */
        fun formatStorage(bytes: Long): String =
            String.format(Locale.US, "%.2fG", bytes / 1024.0 / 1024.0 / 1024.0)

        fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }

        /** Sort items per [config]; directories first when [foldersFirst]. */
        fun sortItems(
            items: List<FileItem>,
            sortBy: SortBy,
            ascending: Boolean,
            foldersFirst: Boolean
        ): List<FileItem> {
            val dirFactor = if (ascending) 1 else -1
            val cmp: Comparator<FileItem> = when (sortBy) {
                SortBy.NAME -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortBy.SIZE -> compareBy<FileItem> { it.size }
                SortBy.DATE -> compareBy<FileItem> { it.lastModified }
                SortBy.TYPE -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.extension }
                    .thenBy(String.CASE_INSENSITIVE_ORDER, FileItem::name)
            }
            return items.sortedWith(
                Comparator { a, b ->
                    if (foldersFirst && a.isDirectory != b.isDirectory) {
                        if (a.isDirectory) -dirFactor else dirFactor
                    } else {
                        if (ascending) cmp.compare(a, b) else cmp.compare(b, a)
                    }
                }
            )
        }
    }
}

enum class FileKind { FOLDER, ARCHIVE, APK, IMAGE, VIDEO, AUDIO, TEXT, CODE, OTHER }

enum class SortBy { NAME, SIZE, DATE, TYPE }

enum class PaneSide { LEFT, RIGHT }

data class PaneState(
    val currentPath: String = "/storage/emulated/0",
    val items: List<FileItem> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val history: List<String> = listOf("/storage/emulated/0"),
    val historyIndex: Int = 0,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    val showHidden: Boolean = true,
    /** Non-null when the pane shows search results. */
    val searchQuery: String? = null,
    /** Name filter applied on top of [items]; null = no filter. */
    val filterQuery: String? = null,
    /** Path to highlight (e.g. jumped-to search result). */
    val highlightPath: String? = null
) {
    val inSelectionMode: Boolean get() = selectedItems.isNotEmpty()

    /** Items after applying [filterQuery] (case-insensitive substring on name). */
    val visibleItems: List<FileItem>
        get() {
            val q = filterQuery?.trim()?.lowercase() ?: return items
            if (q.isEmpty()) return items
            return items.filter { it.name.lowercase().contains(q) }
        }
}

data class ClipboardData(
    val items: List<FileItem> = emptyList(),
    val isCut: Boolean = false, // true = move, false = copy
    val sourcePath: String = ""
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** How to resolve a name conflict during copy/move. */
enum class ConflictResolution { ASK, OVERWRITE, SKIP, KEEP_BOTH }

/** Progress state for long-running operations (copy/move/zip/...). */
data class OpProgress(
    val active: Boolean = false,
    val title: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val currentName: String = "",
    val finished: Boolean = false,
    val message: String? = null
) {
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
}
