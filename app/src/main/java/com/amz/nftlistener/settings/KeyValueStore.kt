package com.amz.nftlistener.settings

interface KeyValueStore {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
}
