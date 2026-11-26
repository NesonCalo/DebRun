package com.nesoncalo.debrun.core

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object Prefs {
    private const val NAME = "debrun"

    private fun sp(): SharedPreferences = Env.app.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var distroId: String
        get() = sp().getString("distro", RootfsManager.DISTRO_UBUNTU) ?: RootfsManager.DISTRO_UBUNTU
        set(value) = sp().edit().putString("distro", value).apply()

    var dns: String
        get() = sp().getString("dns", "223.5.5.5") ?: "223.5.5.5"
        set(value) = sp().edit().putString("dns", value.trim()).apply()

    var useChinaMirror: Boolean
        get() = sp().getBoolean("cn_mirror", true)
        set(value) = sp().edit().putBoolean("cn_mirror", value).apply()

    var autoFixDeps: Boolean
        get() = sp().getBoolean("auto_fix_deps", true)
        set(value) = sp().edit().putBoolean("auto_fix_deps", value).apply()

    var fontSize: Int
        get() = sp().getInt("font_size", 13)
        set(value) = sp().edit().putInt("font_size", value).apply()

    /** null keeps the system language. */
    var language: String?
        get() = sp().getString("language", null)
        set(value) = sp().edit().putString("language", value).apply()

    fun effectiveTag(): String = language ?: Locale.getDefault().toString()
}
