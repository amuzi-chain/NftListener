package com.amz.nftlistener.capture

import android.util.Log
import com.amz.nftlistener.domain.NotificationFields
import com.amz.nftlistener.domain.UploadOutcome

object CaptureLog {
    const val TAG = "NftListener"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, error)
        }
    }

    fun fields(fields: NotificationFields) {
        i(
            "parsed eventId=${fields.eventId} key=${fields.notificationKey} " +
                "postedAt=${fields.postedAt} pkg=${fields.packageName} app=${fields.appLabel} " +
                "channel=${fields.channelId} ongoing=${fields.isOngoing} " +
                "title=${fields.title} text=${fields.text} subText=${fields.subText}",
        )
    }

    fun outcome(eventId: String, outcome: UploadOutcome) {
        when (outcome) {
            UploadOutcome.Success -> i("upload success eventId=$eventId")
            is UploadOutcome.Retryable -> w("upload retryable eventId=$eventId reason=${outcome.reason}")
            is UploadOutcome.Permanent -> w("upload permanent eventId=$eventId reason=${outcome.reason}")
        }
    }
}
