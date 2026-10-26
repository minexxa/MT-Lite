package com.ma.manager.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App settings persisted with SharedPreferences.
 * Mirrors MA管理器 preference options relevant to a lite file manager.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()

    var defaultPath: String
        get() = prefs.getString(KEY_DEFAULT_PATH, "/storage/emulated/0") ?: "/storage/emulated/0"
        set(value) = prefs.edit().putString(KEY_DEFAULT_PATH, value).apply()

    var sortBy: SortBy
        get() = runCatching { SortBy.valueOf(prefs.getString(KEY_SORT_BY, SortBy.NAME.name)!!) }
            .getOrDefault(SortBy.NAME)
        set(value) = prefs.edit().putString(KEY_SORT_BY, value.name).apply()

    var sortAscending: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASC, true)
        set(value) = prefs.edit().putBoolean(KEY_SORT_ASC, value).apply()

    /** Move deleted files into a local recycle bin instead of permanent delete. */
    var useRecycleBin: Boolean
        get() = prefs.getBoolean(KEY_RECYCLE_BIN, true)
        set(value) = prefs.edit().putBoolean(KEY_RECYCLE_BIN, value).apply()

    var confirmDelete: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_DELETE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_DELETE, value).apply()

    var foldersFirst: Boolean
        get() = prefs.getBoolean(KEY_FOLDERS_FIRST, true)
        set(value) = prefs.edit().putBoolean(KEY_FOLDERS_FIRST, value).apply()

    var textWrap: Boolean
        get() = prefs.getBoolean(KEY_TEXT_WRAP, true)
        set(value) = prefs.edit().putBoolean(KEY_TEXT_WRAP, value).apply()

    /** Remember last path per pane so sessions can be restored. */
    fun savePanePaths(left: String, right: String) {
        prefs.edit().putString(KEY_LEFT_PATH, left).putString(KEY_RIGHT_PATH, right).apply()
    }

    fun loadLeftPath(): String = prefs.getString(KEY_LEFT_PATH, null) ?: defaultPath

    fun loadRightPath(): String = prefs.getString(KEY_RIGHT_PATH, null) ?: defaultPath

    companion object {
        const val PREFS_NAME = "ma_settings"
        const val RECYCLE_BIN_DIR = ".MA_Trash"

        private const val KEY_THEME = "theme_mode"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_DEFAULT_PATH = "default_path"
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SORT_ASC = "sort_asc"
        private const val KEY_RECYCLE_BIN = "recycle_bin"
        private const val KEY_CONFIRM_DELETE = "confirm_delete"
        private const val KEY_FOLDERS_FIRST = "folders_first"
        private const val KEY_TEXT_WRAP = "text_wrap"
        private const val KEY_LEFT_PATH = "left_path"
        private const val KEY_RIGHT_PATH = "right_path"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
