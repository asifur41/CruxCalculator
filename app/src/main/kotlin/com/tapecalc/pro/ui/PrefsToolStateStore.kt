package com.tapecalc.pro.ui

import android.content.Context
import android.content.SharedPreferences
import com.tapecalc.core.ui.ToolStateStore

/**
 * Keeps "where the person was" (open tool, typed boxes, graph view, converter choices, selected tab) in the app's
 * private storage, so it comes back after leaving the app and after closing it. Nothing leaves the phone.
 *
 * SharedPreferences keeps everything in memory after the first read and writes to disk in the background, so reading and
 * writing here is cheap enough to do on every key press.
 */
class PrefsToolStateStore(context: Context) : ToolStateStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private companion object {
        const val FILE = "crux_where_you_were"
    }
}
