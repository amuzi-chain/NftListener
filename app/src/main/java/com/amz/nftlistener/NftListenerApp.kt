package com.amz.nftlistener

import android.app.Application
import com.amz.nftlistener.data.AppDatabase
import com.amz.nftlistener.data.NotificationRepository
import com.amz.nftlistener.data.RoomEventStore
import com.amz.nftlistener.settings.AppSettings
import com.amz.nftlistener.settings.SharedPreferencesKeyValueStore
import com.amz.nftlistener.upload.ConnectivityNetworkChecker
import com.amz.nftlistener.upload.WebhookUploader
import com.amz.nftlistener.upload.WorkManagerUploadScheduler

class NftListenerApp : Application() {
    lateinit var settings: AppSettings
        private set
    lateinit var repository: NotificationRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(
            SharedPreferencesKeyValueStore(getSharedPreferences("nft_listener", MODE_PRIVATE)),
        )
        repository = NotificationRepository(
            store = RoomEventStore(AppDatabase.create(this)),
            settings = settings,
            sender = WebhookUploader(),
            networkChecker = ConnectivityNetworkChecker(this),
            scheduler = WorkManagerUploadScheduler(this),
        )
    }
}
