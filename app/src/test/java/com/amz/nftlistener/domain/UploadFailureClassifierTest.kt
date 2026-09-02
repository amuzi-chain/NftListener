package com.amz.nftlistener.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class UploadFailureClassifierTest {
    @Test
    fun successOn2xx() {
        assertTrue(UploadFailureClassifier.fromHttpCode(200) is UploadOutcome.Success)
        assertTrue(UploadFailureClassifier.fromHttpCode(204) is UploadOutcome.Success)
    }

    @Test
    fun retryableOn5xx429AndTimeout() {
        assertTrue(UploadFailureClassifier.fromHttpCode(500) is UploadOutcome.Retryable)
        assertTrue(UploadFailureClassifier.fromHttpCode(429) is UploadOutcome.Retryable)
        assertTrue(UploadFailureClassifier.fromThrowable(SocketTimeoutException()) is UploadOutcome.Retryable)
        assertTrue(UploadFailureClassifier.fromThrowable(IOException("offline")) is UploadOutcome.Retryable)
    }

    @Test
    fun permanentOnOther4xx() {
        assertTrue(UploadFailureClassifier.fromHttpCode(400) is UploadOutcome.Permanent)
        assertTrue(UploadFailureClassifier.fromHttpCode(401) is UploadOutcome.Permanent)
        assertTrue(UploadFailureClassifier.fromHttpCode(404) is UploadOutcome.Permanent)
    }
}
