package com.amz.nftlistener.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.amz.nftlistener.data.NetworkChecker

class ConnectivityNetworkChecker(context: Context) : NetworkChecker {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
