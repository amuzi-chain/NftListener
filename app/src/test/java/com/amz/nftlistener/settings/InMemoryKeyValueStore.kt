package com.amz.nftlistener.settings

class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, default: String): String = values[key] ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
