package com.amz.nftlistener.capture

object NotificationAccess {
    fun isGranted(enabledPackages: Set<String>, packageName: String): Boolean {
        return enabledPackages.contains(packageName)
    }
}
