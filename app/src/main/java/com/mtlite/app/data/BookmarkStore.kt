package com.mtlite.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bookmark with optional group, modeled after MT管理器 bookmarks bar.
 * Persisted as JSON in the app files directory.
 */
data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val path: String,
    val group: String = ""
)

data class BookmarkGroup(
    val name: String,
    val expanded: Boolean = true
)

class BookmarkStore private constructor(context: Context) {

    private val file: File = File(context.applicationContext.filesDir, "bookmarks.json")

    @Volatile
    private var cache: List<Bookmark>? = null

    @Synchronized
    fun all(): List<Bookmark> {
        cache?.let { return it }
        val list = mutableListOf<Bookmark>()
        if (file.exists()) {
            runCatching {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Bookmark(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            name = o.optString("name"),
                            path = o.optString("path"),
                            group = o.optString("group")
                        )
                    )
                }
            }
        }
        if (list.isEmpty()) {
            // Seed with defaults on first run
            list.add(Bookmark(name = "内部存储", path = "/storage/emulated/0"))
            list.add(Bookmark(name = "下载", path = "/storage/emulated/0/Download"))
            list.add(Bookmark(name = "DCIM", path = "/storage/emulated/0/DCIM"))
            list.add(Bookmark(name = "根目录", path = "/"))
        }
        cache = list
        return list
    }

    @Synchronized
    fun add(name: String, path: String, group: String = ""): Bookmark {
        val b = Bookmark(name = name, path = path, group = group)
        save(all() + b)
        return b
    }

    @Synchronized
    fun remove(id: Long) {
        save(all().filter { it.id != id })
    }

    @Synchronized
    fun update(b: Bookmark) {
        save(all().map { if (it.id == b.id) b else it })
    }

    fun groups(): List<String> = all().map { it.group }.distinct()

    private fun save(list: List<Bookmark>) {
        cache = list
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("id", b.id)
                    put("name", b.name)
                    put("path", b.path)
                    put("group", b.group)
                }
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }

    companion object {
        @Volatile
        private var instance: BookmarkStore? = null

        fun get(context: Context): BookmarkStore =
            instance ?: synchronized(this) {
                instance ?: BookmarkStore(context).also { instance = it }
            }
    }
}
