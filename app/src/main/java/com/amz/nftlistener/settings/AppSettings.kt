package com.amz.nftlistener.settings

import com.amz.nftlistener.domain.WebhookUrlValidator

class AppSettings(private val store: KeyValueStore) {
    fun webhookUrl(): String = store.getString(KEY_URL)

    fun token(): String = store.getString(KEY_TOKEN)

    fun isConfigured(): Boolean = webhookUrl().isNotEmpty()

    fun save(rawUrl: String, rawToken: String): String? {
        val url = WebhookUrlValidator.normalize(rawUrl) ?: return "Webhook URL 不合法"
        store.putString(KEY_URL, url)
        store.putString(KEY_TOKEN, rawToken.trim())
        return null
    }

    private companion object {
        const val KEY_URL = "webhook_url"
        const val KEY_TOKEN = "webhook_token"
    }
}
