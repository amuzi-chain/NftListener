package com.amz.nftlistener.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupeTest {
    @Test
    fun firstSightingIsNotDuplicate() {
        val dedupe = NotificationDedupe()
        assertFalse(dedupe.seen("key-a", 100L))
    }

    @Test
    fun sameKeyAndPostTimeIsDuplicate() {
        val dedupe = NotificationDedupe()
        dedupe.seen("key-a", 100L)
        assertTrue(dedupe.seen("key-a", 100L))
    }

    @Test
    fun sameKeyDifferentPostTimeIsNew() {
        val dedupe = NotificationDedupe()
        dedupe.seen("key-a", 100L)
        assertFalse(dedupe.seen("key-a", 200L))
    }

    @Test
    fun evictsOldestWhenOverMaxEntries() {
        val dedupe = NotificationDedupe(maxEntries = 2)
        dedupe.seen("a", 1L)
        dedupe.seen("b", 1L)
        dedupe.seen("c", 1L)
        assertFalse(dedupe.seen("a", 1L))
        assertTrue(dedupe.seen("c", 1L))
    }
}
