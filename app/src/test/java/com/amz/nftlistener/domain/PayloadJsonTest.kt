package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadJsonTest {
    private val sample = NotificationFields(
        eventId = "evt-1",
        notificationKey = "key-1",
        postedAt = 1730000000000,
        packageName = "com.xxx.app",
        appLabel = "微信",
        title = "标题",
        text = "正文",
        subText = "",
        channelId = "msg",
        isOngoing = false,
    )

    @Test
    fun encodesAllKeysAndEmptyStrings() {
        val json = PayloadJson.encode(sample)
        assertTrue(json.contains("\"eventId\":\"evt-1\""))
        assertTrue(json.contains("\"postedAt\":1730000000000"))
        assertTrue(json.contains("\"packageName\":\"com.xxx.app\""))
        assertTrue(json.contains("\"appLabel\":\"微信\""))
        assertTrue(json.contains("\"title\":\"标题\""))
        assertTrue(json.contains("\"text\":\"正文\""))
        assertTrue(json.contains("\"subText\":\"\""))
        assertTrue(json.contains("\"channelId\":\"msg\""))
        assertTrue(json.contains("\"isOngoing\":false"))
        assertTrue(!json.contains("notificationKey"))
    }

    @Test
    fun escapesQuotesAndNewlines() {
        val json = PayloadJson.encode(
            sample.copy(title = "say \"hi\"", text = "line1\nline2"),
        )
        assertTrue(json.contains("\"title\":\"say \\\"hi\\\"\""))
        assertTrue(json.contains("\"text\":\"line1\\nline2\""))
    }

    @Test
    fun escapesRemainingControlCharacters() {
        val title = "a\u0000b\u0008c\u000cf"
        val json = PayloadJson.encode(sample.copy(title = title))
        assertTrue(json.contains("\"title\":\"a\\u0000b\\u0008c\\u000cf\""))

        val titleStart = json.indexOf("\"title\":\"") + "\"title\":\"".length
        val titleEnd = json.indexOf('"', titleStart)
        val titleValue = json.substring(titleStart, titleEnd)
        assertTrue(titleValue.contains("\\u0000"))
        assertTrue(titleValue.contains("\\u0008"))
        assertTrue(titleValue.contains("\\u000c"))
        assertTrue(!titleValue.contains('\u0000'))
        assertTrue(!titleValue.contains('\u0008'))
        assertTrue(!titleValue.contains('\u000c'))
    }

    @Test
    fun encodeIsStableObject() {
        val json = PayloadJson.encode(sample)
        assertEquals('{', json.first())
        assertEquals('}', json.last())
    }
}
