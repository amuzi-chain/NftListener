package com.amz.nftlistener.data

fun interface NetworkChecker {
    fun isOnline(): Boolean
}
