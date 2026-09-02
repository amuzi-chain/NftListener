package com.amz.nftlistener.upload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amz.nftlistener.NftListenerApp
import com.amz.nftlistener.data.DrainResult

class WebhookUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NftListenerApp
        return when (app.repository.drainPending()) {
            DrainResult.HAS_RETRYABLE -> Result.retry()
            DrainResult.DONE -> Result.success()
        }
    }
}
