package com.amz.nftlistener.upload

import com.amz.nftlistener.capture.CaptureLog
import com.amz.nftlistener.domain.UploadFailureClassifier
import com.amz.nftlistener.domain.UploadOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WebhookUploader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
) : WebhookSender {
    override suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome {
        return withContext(Dispatchers.IO) {
            val body = jsonBody.toRequestBody(JSON)
            val builder = Request.Builder().url(url).post(body)
            if (token.isNotEmpty()) {
                builder.header("Authorization", "Bearer $token")
            }
            try {
                client.newCall(builder.build()).execute().use { response ->
                    CaptureLog.i("http ${response.code} url=$url bytes=${jsonBody.length} hasToken=${token.isNotEmpty()}")
                    UploadFailureClassifier.fromHttpCode(response.code)
                }
            } catch (error: Throwable) {
                CaptureLog.e("http failed url=$url", error)
                UploadFailureClassifier.fromThrowable(error)
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
