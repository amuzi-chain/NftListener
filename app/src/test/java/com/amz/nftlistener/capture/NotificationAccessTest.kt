package com.amz.nftlistener.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTest {
    @Test
    fun grantedWhenPackageListed() {
        assertTrue(
            NotificationAccess.isGranted(setOf("com.amz.nftlistener", "other"), "com.amz.nftlistener"),
        )
    }

    @Test
    fun deniedWhenMissing() {
        assertFalse(NotificationAccess.isGranted(setOf("other"), "com.amz.nftlistener"))
    }
}
