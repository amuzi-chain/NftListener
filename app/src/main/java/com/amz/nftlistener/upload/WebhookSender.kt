package com.amz.nftlistener.upload

import com.amz.nftlistener.domain.UploadOutcome

interface WebhookSender {
    suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome
}
