package com.ma.manager.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers archive passwords per zip path so re-opening an encrypted
 * archive (preview / extract / test) does not ask again.
 */
class ZipPasswordStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(path: String): String? =
        prefs.getString(path, null)?.takeIf { it.isNotEmpty() }

    fun put(path: String, password: String) {
        if (password.isEmpty()) return
        prefs.edit().putString(path, password).apply()
    }

    fun remove(path: String) {
        prefs.edit().remove(path).apply()
    }

    companion object {
        private const val PREFS_NAME = "ma_zip_passwords"

        @Volatile
        private var instance: ZipPasswordStore? = null

        fun get(context: Context): ZipPasswordStore =
            instance ?: synchronized(this) {
                instance ?: ZipPasswordStore(context).also { instance = it }
            }
    }
}
