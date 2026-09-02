package com.amz.nftlistener.settings

import android.content.SharedPreferences

class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {
    override fun getString(key: String, default: String): String =
        prefs.getString(key, default) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
