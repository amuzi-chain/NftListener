package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationParserTest {
    @Test
    fun mapsExtrasAndFallsBack() {
        val fields = NotificationParser.parse(
            eventId = "e1",
            notificationKey = "k1",
            postedAt = 10L,
            packageName = "com.foo",
            appLabel = "",
            extrasTitle = "Hello",
            extrasText = null,
            extrasSubText = null,
            extrasBigText = "Big body",
            channelId = "ch",
            isOngoing = true,
        )
        assertEquals("e1", fields.eventId)
        assertEquals("k1", fields.notificationKey)
        assertEquals(10L, fields.postedAt)
        assertEquals("com.foo", fields.packageName)
        assertEquals("com.foo", fields.appLabel)
        assertEquals("Hello", fields.title)
        assertEquals("Big body", fields.text)
        assertEquals("", fields.subText)
        assertEquals("ch", fields.channelId)
        assertEquals(true, fields.isOngoing)
    }

    @Test
    fun truncatesLongTitle() {
        val fields = NotificationParser.parse(
            eventId = "e1",
            notificationKey = "k1",
            postedAt = 10L,
            packageName = "com.foo",
            appLabel = "Foo",
            extrasTitle = "x".repeat(5000),
            extrasText = "y",
            extrasSubText = "",
            extrasBigText = null,
            channelId = null,
            isOngoing = false,
        )
        assertEquals(4096, fields.title.length)
        assertEquals("", fields.channelId)
    }
}
