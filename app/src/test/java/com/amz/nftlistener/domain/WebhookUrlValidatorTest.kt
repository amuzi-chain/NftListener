package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookUrlValidatorTest {
    @Test
    fun acceptsHttpAndHttps() {
        assertEquals(
            "https://webhook.site/abc",
            WebhookUrlValidator.normalize("https://webhook.site/abc"),
        )
        assertEquals(
            "http://10.0.0.2:8080/hook",
            WebhookUrlValidator.normalize("http://10.0.0.2:8080/hook"),
        )
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(
            "https://example.com/hook",
            WebhookUrlValidator.normalize("  https://example.com/hook  "),
        )
    }

    @Test
    fun rejectsBlankJavascriptAndMissingScheme() {
        assertNull(WebhookUrlValidator.normalize(""))
        assertNull(WebhookUrlValidator.normalize("   "))
        assertNull(WebhookUrlValidator.normalize("webhook.site/abc"))
        assertNull(WebhookUrlValidator.normalize("javascript:alert(1)"))
        assertNull(WebhookUrlValidator.normalize("ftp://example.com/hook"))
    }
}
