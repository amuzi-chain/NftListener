package com.amz.nftlistener.domain

object TextTruncator {
    const val MAX_CHARS = 4096

    fun truncate(value: String): String {
        return if (value.length <= MAX_CHARS) value else value.substring(0, MAX_CHARS)
    }
}
