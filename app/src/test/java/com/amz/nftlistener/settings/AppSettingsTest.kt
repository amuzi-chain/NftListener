package com.amz.nftlistener.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun rejectsInvalidUrlAndKeepsUnconfigured() {
        val settings = AppSettings(InMemoryKeyValueStore())
        val error = settings.save("not-a-url", "token")
        assertEquals("Webhook URL 不合法", error)
        assertFalse(settings.isConfigured())
        assertEquals("", settings.webhookUrl())
        assertEquals("", settings.token())
    }

    @Test
    fun savesTrimmedUrlAndOptionalToken() {
        val settings = AppSettings(InMemoryKeyValueStore())
        assertNull(settings.save("  https://example.com/hook  ", "  abc  "))
        assertTrue(settings.isConfigured())
        assertEquals("https://example.com/hook", settings.webhookUrl())
        assertEquals("abc", settings.token())
    }

    @Test
    fun allowsEmptyToken() {
        val settings = AppSettings(InMemoryKeyValueStore())
        assertNull(settings.save("https://example.com/hook", "  "))
        assertEquals("", settings.token())
        assertTrue(settings.isConfigured())
    }
}
