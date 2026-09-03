package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTruncatorTest {
    @Test
    fun leavesShortTextUnchanged() {
        assertEquals("hello", TextTruncator.truncate("hello"))
    }

    @Test
    fun truncatesTo4096Chars() {
        val input = "a".repeat(5000)
        val out = TextTruncator.truncate(input)
        assertEquals(4096, out.length)
        assertTrue(out.all { it == 'a' })
    }
}
