package com.amz.nftlistener.upload

import com.amz.nftlistener.domain.UploadOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookUploaderTest {
    @Test
    fun postsJsonAndBearerToken() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val uploader = WebhookUploader()
            val outcome = uploader.upload(
                url = server.url("/hook").toString(),
                token = "secret",
                jsonBody = "{\"eventId\":\"e1\"}",
            )
            assertTrue(outcome is UploadOutcome.Success)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            assertEquals("{\"eventId\":\"e1\"}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun omitsAuthorizationWhenTokenBlank() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            WebhookUploader().upload(server.url("/hook").toString(), "", "{}")
            val request = server.takeRequest()
            assertEquals(null, request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun classifies404AsPermanent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        try {
            val outcome = WebhookUploader().upload(server.url("/hook").toString(), "", "{}")
            assertTrue(outcome is UploadOutcome.Permanent)
        } finally {
            server.shutdown()
        }
    }
}
