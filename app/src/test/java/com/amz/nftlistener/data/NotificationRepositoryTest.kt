package com.amz.nftlistener.data

import com.amz.nftlistener.domain.NotificationFields
import com.amz.nftlistener.domain.UploadOutcome
import com.amz.nftlistener.settings.AppSettings
import com.amz.nftlistener.settings.InMemoryKeyValueStore
import com.amz.nftlistener.upload.WebhookSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    private val fields = NotificationFields(
        eventId = "e1",
        notificationKey = "k1",
        postedAt = 11L,
        packageName = "com.foo",
        appLabel = "Foo",
        title = "Hi",
        text = "Body",
        subText = "",
        channelId = "c",
        isOngoing = false,
    )

    @Test
    fun unconfiguredWritesFailedLogWithoutQueue() = runTest {
        val fixture = Fixture(configured = false)
        fixture.repo.handleIncoming(fields)
        assertTrue(fixture.store.pending.isEmpty())
        assertEquals(LogStatus.FAILED.name, fixture.store.logs.single().status)
        assertEquals("未配置", fixture.store.logs.single().reason)
        assertEquals(0, fixture.sender.uploads)
        assertEquals(0, fixture.scheduler.times)
    }

    @Test
    fun onlineSuccessRemovesPending() = runTest {
        val fixture = Fixture(online = true, outcome = UploadOutcome.Success)
        fixture.repo.handleIncoming(fields)
        assertTrue(fixture.store.pending.isEmpty())
        assertEquals(LogStatus.SUCCESS.name, fixture.store.logs.single().status)
        assertEquals(1, fixture.sender.uploads)
        assertEquals(0, fixture.scheduler.times)
    }

    @Test
    fun offlineQueuesAndSchedules() = runTest {
        val fixture = Fixture(online = false)
        fixture.repo.handleIncoming(fields)
        assertEquals(1, fixture.store.pending.size)
        assertEquals(LogStatus.QUEUED.name, fixture.store.logs.single().status)
        assertEquals(0, fixture.sender.uploads)
        assertEquals(1, fixture.scheduler.times)
    }

    @Test
    fun retryableKeepsQueue() = runTest {
        val fixture = Fixture(online = true, outcome = UploadOutcome.Retryable("HTTP 500"))
        fixture.repo.handleIncoming(fields)
        assertEquals(1, fixture.store.pending.size)
        assertEquals(LogStatus.QUEUED.name, fixture.store.logs.single().status)
        assertEquals(1, fixture.scheduler.times)
    }

    @Test
    fun permanentFailureDropsQueue() = runTest {
        val fixture = Fixture(online = true, outcome = UploadOutcome.Permanent("HTTP 404"))
        fixture.repo.handleIncoming(fields)
        assertTrue(fixture.store.pending.isEmpty())
        assertEquals(LogStatus.FAILED.name, fixture.store.logs.single().status)
        assertEquals("HTTP 404", fixture.store.logs.single().reason)
    }

    @Test
    fun overflowDropsOldestPending() = runTest {
        val fixture = Fixture(online = false)
        repeat(1000) { index ->
            fixture.store.pending.add(
                PendingEventEntity(
                    eventId = "old-$index",
                    postedAt = index.toLong(),
                    packageName = "com.foo",
                    appLabel = "Foo",
                    title = "t",
                    payloadJson = "{}",
                    createdAt = index.toLong(),
                ),
            )
        }
        fixture.repo.handleIncoming(fields)
        assertEquals(1000, fixture.store.pending.size)
        assertTrue(fixture.store.pending.none { it.eventId == "old-0" })
        assertTrue(fixture.store.pending.any { it.eventId == "e1" })
        assertTrue(fixture.store.logs.any { it.reason == "队列溢出" })
    }

    @Test
    fun testEventUsesAppPackageAndTestTitle() = runTest {
        val fixture = Fixture(online = true, outcome = UploadOutcome.Success)
        fixture.repo.enqueueTestEvent(now = 99L)
        assertEquals("com.amz.nftlistener", fixture.sender.lastPackageName)
        assertTrue(fixture.sender.lastTitle.contains("test", ignoreCase = true))
    }

    @Test
    fun drainRetriesRemaining() = runTest {
        val fixture = Fixture(online = true, outcome = UploadOutcome.Retryable("HTTP 500"))
        fixture.store.pending.add(PendingEventEntity("e2", 1, "p", "A", "t", "{}", 1))
        val result = fixture.repo.drainPending()
        assertEquals(DrainResult.HAS_RETRYABLE, result)
        assertEquals(1, fixture.store.pending.size)
    }

    private class FakeSender(private val outcome: UploadOutcome) : WebhookSender {
        var uploads = 0
        var lastPackageName = ""
        var lastTitle = ""

        override suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome {
            uploads += 1
            if (jsonBody.contains("com.amz.nftlistener")) {
                lastPackageName = "com.amz.nftlistener"
            }
            if (jsonBody.contains("test", ignoreCase = true)) {
                lastTitle = "test"
            }
            return outcome
        }
    }

    private class CountingScheduler {
        var times = 0
        val impl = UploadScheduler { times += 1 }
    }

    private class Fixture(
        configured: Boolean = true,
        online: Boolean = true,
        outcome: UploadOutcome = UploadOutcome.Success,
    ) {
        val store = FakeEventStore()
        val settings = AppSettings(InMemoryKeyValueStore()).apply {
            if (configured) save("https://example.com/hook", "")
        }
        val sender = FakeSender(outcome)
        val scheduler = CountingScheduler()
        val repo = NotificationRepository(
            store = store,
            settings = settings,
            sender = sender,
            networkChecker = NetworkChecker { online },
            scheduler = scheduler.impl,
        )
    }
}
