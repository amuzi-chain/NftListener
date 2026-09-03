package com.amz.nftlistener.domain

import java.io.IOException

object UploadFailureClassifier {
    fun fromHttpCode(code: Int): UploadOutcome {
        return when (code) {
            in 200..299 -> UploadOutcome.Success
            429 -> UploadOutcome.Retryable("HTTP 429")
            in 500..599 -> UploadOutcome.Retryable("HTTP $code")
            in 400..499 -> UploadOutcome.Permanent("HTTP $code")
            else -> UploadOutcome.Retryable("HTTP $code")
        }
    }

    fun fromThrowable(error: Throwable): UploadOutcome {
        val reason = error.message?.ifBlank { error.javaClass.simpleName } ?: error.javaClass.simpleName
        return if (error is IOException) {
            UploadOutcome.Retryable(reason)
        } else {
            UploadOutcome.Retryable(reason)
        }
    }
}
