package com.amz.nftlistener.domain

sealed class UploadOutcome {
    data object Success : UploadOutcome()
    data class Retryable(val reason: String) : UploadOutcome()
    data class Permanent(val reason: String) : UploadOutcome()
}
