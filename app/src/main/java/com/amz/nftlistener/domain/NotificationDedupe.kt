package com.amz.nftlistener.domain

class NotificationDedupe(private val maxEntries: Int = 500) {
    private val seenKeys = LinkedHashSet<String>()

    fun seen(key: String, postTime: Long): Boolean {
        val token = "$key|$postTime"
        if (seenKeys.contains(token)) return true
        seenKeys.add(token)
        while (seenKeys.size > maxEntries) {
            val oldest = seenKeys.iterator().next()
            seenKeys.remove(oldest)
        }
        return false
    }
}
