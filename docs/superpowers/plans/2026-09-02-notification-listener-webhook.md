# Notification Listener Webhook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在用户授予通知使用权后，捕获系统上所有 App 的新弹出通知，HTTP POST JSON 到可配置 Webhook，断网入队、联网补发，并在单页里配置 URL / 查看最近上报日志。

**Architecture:** `NotificationCaptureService`（`NotificationListenerService`）解析并去重后交给 `NotificationRepository`。Repository 把事件写入 Room 队列与日志，在线立刻用 OkHttp POST，失败则由 WorkManager 在有网时指数退避重试。配置存在 `AppSettings`；主界面只负责权限引导、保存 URL/Token、测试上报和展示日志。

**Tech Stack:** Kotlin、AppCompat + Material + View Binding、Room、WorkManager、OkHttp、JUnit4 本地单元测试。不引入 Compose、Hilt。

## Global Constraints

- 包名 / applicationId：`com.amz.nftlistener`；minSdk 24；targetSdk 37；compileSdk 37。
- 语言 Kotlin；UI 用 AppCompat + Material + View Binding，不引入 Compose。
- 捕获 API 只能是 `NotificationListenerService`，禁止无障碍服务，禁止前台常驻服务。
- 第一版全量上报，不按包名/关键词过滤；不上报图标/大图等二进制；不处理撤掉/点击。
- JSON 键固定为：`eventId`、`postedAt`、`packageName`、`appLabel`、`title`、`text`、`subText`、`channelId`、`isOngoing`；空字符串保留键；`title`/`text`/`subText` 各最多 4096 个字符。
- Token 可空；非空时请求头为 `Authorization: Bearer {token}`。
- 队列最多 1000 条待发，超出丢最旧并记「队列溢出」；日志最多 200 条。
- 4xx（除 429）永久失败并停重试；断网 / 超时 / 5xx / 429 重试。
- 项目当前可能还不是 git 仓库：第一次 commit 前若没有 `.git`，先 `git init`。不要改 git config。

## File map

新建（按职责，相关文件放一起）：

- `app/src/main/java/com/amz/nftlistener/domain/WebhookUrlValidator.kt` — URL 校验
- `app/src/main/java/com/amz/nftlistener/domain/TextTruncator.kt` — 文本截断
- `app/src/main/java/com/amz/nftlistener/domain/NotificationFields.kt` — 领域模型
- `app/src/main/java/com/amz/nftlistener/domain/PayloadJson.kt` — JSON 组装
- `app/src/main/java/com/amz/nftlistener/domain/NotificationDedupe.kt` — key+postTime 去重
- `app/src/main/java/com/amz/nftlistener/domain/UploadOutcome.kt` — 上报结果
- `app/src/main/java/com/amz/nftlistener/domain/UploadFailureClassifier.kt` — HTTP/异常分类
- `app/src/main/java/com/amz/nftlistener/domain/NotificationParser.kt` — extras → fields
- `app/src/main/java/com/amz/nftlistener/settings/KeyValueStore.kt` — 配置存储接口
- `app/src/main/java/com/amz/nftlistener/settings/AppSettings.kt` — URL/Token
- `app/src/main/java/com/amz/nftlistener/settings/SharedPreferencesKeyValueStore.kt`
- `app/src/main/java/com/amz/nftlistener/upload/WebhookUploader.kt` — OkHttp POST
- `app/src/main/java/com/amz/nftlistener/data/PendingEventEntity.kt`
- `app/src/main/java/com/amz/nftlistener/data/UploadLogEntity.kt`
- `app/src/main/java/com/amz/nftlistener/data/PendingEventDao.kt`
- `app/src/main/java/com/amz/nftlistener/data/UploadLogDao.kt`
- `app/src/main/java/com/amz/nftlistener/data/AppDatabase.kt`
- `app/src/main/java/com/amz/nftlistener/data/EventStore.kt`
- `app/src/main/java/com/amz/nftlistener/data/RoomEventStore.kt`
- `app/src/main/java/com/amz/nftlistener/data/UploadScheduler.kt`
- `app/src/main/java/com/amz/nftlistener/data/NetworkChecker.kt`
- `app/src/main/java/com/amz/nftlistener/data/NotificationRepository.kt`
- `app/src/main/java/com/amz/nftlistener/upload/WorkManagerUploadScheduler.kt`
- `app/src/main/java/com/amz/nftlistener/upload/WebhookUploadWorker.kt`
- `app/src/main/java/com/amz/nftlistener/upload/ConnectivityNetworkChecker.kt`
- `app/src/main/java/com/amz/nftlistener/capture/NotificationAccess.kt`
- `app/src/main/java/com/amz/nftlistener/capture/NotificationCaptureService.kt`
- `app/src/main/java/com/amz/nftlistener/NftListenerApp.kt`
- `app/src/main/java/com/amz/nftlistener/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/item_upload_log.xml`
- `app/src/main/res/xml/notification_listener_service.xml`
- `app/src/test/java/com/amz/nftlistener/...` — 与上述 domain/data/upload 对应的测试
- `app/src/test/java/com/amz/nftlistener/data/FakeEventStore.kt`

修改：

- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/java/com/amz/nftlistener/upload/WebhookSender.kt`

删除：`app/src/test/java/com/amz/nftlistener/ExampleUnitTest.kt`（被真实测试替换）。

---

### Task 1: Kotlin 构建与 WebhookUrlValidator

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/amz/nftlistener/domain/WebhookUrlValidator.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/WebhookUrlValidatorTest.kt`
- Delete: `app/src/test/java/com/amz/nftlistener/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: 无
- Produces: `object WebhookUrlValidator { fun normalize(raw: String): String? }` — 合法 http/https URL 返回 trim 后的字符串，否则 `null`

- [ ] **Step 1: 打开 Kotlin / ViewBinding / 测试依赖**

把 `gradle/libs.versions.toml` 写成：

```toml
[versions]
agp = "9.3.2"
kotlin = "2.1.20"
ksp = "2.1.20-1.0.31"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.8.0"
material = "1.14.0"
room = "2.7.2"
work = "2.10.3"
okhttp = "4.12.0"
lifecycle = "2.9.3"
activity = "1.10.1"
recyclerview = "1.4.0"
constraintlayout = "2.2.1"
coroutines = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activity" }
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version.ref = "recyclerview" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

`app/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.amz.nftlistener"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.amz.nftlistener"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

- [ ] **Step 2: 写失败的 URL 校验测试**

`app/src/test/java/com/amz/nftlistener/domain/WebhookUrlValidatorTest.kt`：

```kotlin
package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebhookUrlValidatorTest {
    @Test
    fun acceptsHttpAndHttps() {
        assertEquals(
            "https://webhook.site/abc",
            WebhookUrlValidator.normalize("https://webhook.site/abc"),
        )
        assertEquals(
            "http://10.0.0.2:8080/hook",
            WebhookUrlValidator.normalize("http://10.0.0.2:8080/hook"),
        )
    }

    @Test
    fun trimsWhitespace() {
        assertEquals(
            "https://example.com/hook",
            WebhookUrlValidator.normalize("  https://example.com/hook  "),
        )
    }

    @Test
    fun rejectsBlankJavascriptAndMissingScheme() {
        assertNull(WebhookUrlValidator.normalize(""))
        assertNull(WebhookUrlValidator.normalize("   "))
        assertNull(WebhookUrlValidator.normalize("webhook.site/abc"))
        assertNull(WebhookUrlValidator.normalize("javascript:alert(1)"))
        assertNull(WebhookUrlValidator.normalize("ftp://example.com/hook"))
    }
}
```

删除 `ExampleUnitTest.kt`。

- [ ] **Step 3: 运行测试，确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.WebhookUrlValidatorTest`

Expected: 编译失败或测试失败，提示找不到 `WebhookUrlValidator`。

- [ ] **Step 4: 最小实现**

`app/src/main/java/com/amz/nftlistener/domain/WebhookUrlValidator.kt`：

```kotlin
package com.amz.nftlistener.domain

import java.net.URI

object WebhookUrlValidator {
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return trimmed
    }
}
```

- [ ] **Step 5: 再跑测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.WebhookUrlValidatorTest`

Expected: `BUILD SUCCESSFUL`，测试通过。

- [ ] **Step 6: Commit**

```bash
test -d .git || git init
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts \
  app/src/main/java/com/amz/nftlistener/domain/WebhookUrlValidator.kt \
  app/src/test/java/com/amz/nftlistener/domain/WebhookUrlValidatorTest.kt
git add -u app/src/test/java/com/amz/nftlistener/ExampleUnitTest.kt
git commit -m "$(cat <<'EOF'
feat: enable Kotlin and validate webhook URLs

EOF
)"
```

---

### Task 2: 文本截断与 JSON 组装

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/domain/TextTruncator.kt`
- Create: `app/src/main/java/com/amz/nftlistener/domain/NotificationFields.kt`
- Create: `app/src/main/java/com/amz/nftlistener/domain/PayloadJson.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/TextTruncatorTest.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/PayloadJsonTest.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `object TextTruncator { const val MAX_CHARS = 4096; fun truncate(value: String): String }`
  - `data class NotificationFields(eventId: String, notificationKey: String, postedAt: Long, packageName: String, appLabel: String, title: String, text: String, subText: String, channelId: String, isOngoing: Boolean)`
  - `object PayloadJson { fun encode(fields: NotificationFields): String }`

- [ ] **Step 1: 写失败测试**

`TextTruncatorTest.kt`：

```kotlin
package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTruncatorTest {
    @Test
    fun leavesShortTextUnchanged() {
        assertEquals("hello", TextTruncator.truncate("hello"))
    }

    @Test
    fun truncatesTo4096Chars() {
        val input = "a".repeat(5000)
        val out = TextTruncator.truncate(input)
        assertEquals(4096, out.length)
        assertTrue(out.all { it == 'a' })
    }
}
```

`PayloadJsonTest.kt`：

```kotlin
package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadJsonTest {
    private val sample = NotificationFields(
        eventId = "evt-1",
        notificationKey = "key-1",
        postedAt = 1730000000000,
        packageName = "com.xxx.app",
        appLabel = "微信",
        title = "标题",
        text = "正文",
        subText = "",
        channelId = "msg",
        isOngoing = false,
    )

    @Test
    fun encodesAllKeysAndEmptyStrings() {
        val json = PayloadJson.encode(sample)
        assertTrue(json.contains("\"eventId\":\"evt-1\""))
        assertTrue(json.contains("\"postedAt\":1730000000000"))
        assertTrue(json.contains("\"packageName\":\"com.xxx.app\""))
        assertTrue(json.contains("\"appLabel\":\"微信\""))
        assertTrue(json.contains("\"title\":\"标题\""))
        assertTrue(json.contains("\"text\":\"正文\""))
        assertTrue(json.contains("\"subText\":\"\""))
        assertTrue(json.contains("\"channelId\":\"msg\""))
        assertTrue(json.contains("\"isOngoing\":false"))
        assertTrue(!json.contains("notificationKey"))
    }

    @Test
    fun escapesQuotesAndNewlines() {
        val json = PayloadJson.encode(
            sample.copy(title = "say \"hi\"", text = "line1\nline2"),
        )
        assertTrue(json.contains("\"title\":\"say \\\"hi\\\"\""))
        assertTrue(json.contains("\"text\":\"line1\\nline2\""))
    }

    @Test
    fun encodeIsStableObject() {
        val json = PayloadJson.encode(sample)
        assertEquals('{', json.first())
        assertEquals('}', json.last())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.TextTruncatorTest --tests com.amz.nftlistener.domain.PayloadJsonTest`

Expected: 找不到 `TextTruncator` / `PayloadJson`。

- [ ] **Step 3: 实现**

`TextTruncator.kt`：

```kotlin
package com.amz.nftlistener.domain

object TextTruncator {
    const val MAX_CHARS = 4096

    fun truncate(value: String): String {
        return if (value.length <= MAX_CHARS) value else value.substring(0, MAX_CHARS)
    }
}
```

`NotificationFields.kt`：

```kotlin
package com.amz.nftlistener.domain

data class NotificationFields(
    val eventId: String,
    val notificationKey: String,
    val postedAt: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String,
    val channelId: String,
    val isOngoing: Boolean,
)
```

`PayloadJson.kt`：

```kotlin
package com.amz.nftlistener.domain

object PayloadJson {
    fun encode(fields: NotificationFields): String {
        return buildString {
            append('{')
            appendString("eventId", fields.eventId)
            append(',')
            appendLong("postedAt", fields.postedAt)
            append(',')
            appendString("packageName", fields.packageName)
            append(',')
            appendString("appLabel", fields.appLabel)
            append(',')
            appendString("title", fields.title)
            append(',')
            appendString("text", fields.text)
            append(',')
            appendString("subText", fields.subText)
            append(',')
            appendString("channelId", fields.channelId)
            append(',')
            appendBoolean("isOngoing", fields.isOngoing)
            append('}')
        }
    }

    private fun StringBuilder.appendString(key: String, value: String) {
        append('"').append(key).append('"').append(':')
        append('"').append(escape(value)).append('"')
    }

    private fun StringBuilder.appendLong(key: String, value: Long) {
        append('"').append(key).append('"').append(':').append(value)
    }

    private fun StringBuilder.appendBoolean(key: String, value: Boolean) {
        append('"').append(key).append('"').append(':').append(value)
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }
}
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.TextTruncatorTest --tests com.amz.nftlistener.domain.PayloadJsonTest`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/domain/TextTruncator.kt \
  app/src/main/java/com/amz/nftlistener/domain/NotificationFields.kt \
  app/src/main/java/com/amz/nftlistener/domain/PayloadJson.kt \
  app/src/test/java/com/amz/nftlistener/domain/TextTruncatorTest.kt \
  app/src/test/java/com/amz/nftlistener/domain/PayloadJsonTest.kt
git commit -m "$(cat <<'EOF'
feat: encode notification webhook JSON with truncation

EOF
)"
```

---

### Task 3: 去重与失败分类

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/domain/NotificationDedupe.kt`
- Create: `app/src/main/java/com/amz/nftlistener/domain/UploadOutcome.kt`
- Create: `app/src/main/java/com/amz/nftlistener/domain/UploadFailureClassifier.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/NotificationDedupeTest.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/UploadFailureClassifierTest.kt`

**Interfaces:**
- Consumes: 无
- Produces:
  - `class NotificationDedupe(maxEntries: Int = 500) { fun seen(key: String, postTime: Long): Boolean }` — 已见过返回 `true`（应丢弃）；首次返回 `false` 并记住
  - `sealed class UploadOutcome { data object Success; data class Retryable(val reason: String); data class Permanent(val reason: String) }`
  - `object UploadFailureClassifier { fun fromHttpCode(code: Int): UploadOutcome; fun fromThrowable(error: Throwable): UploadOutcome }`

- [ ] **Step 1: 写失败测试**

`NotificationDedupeTest.kt`：

```kotlin
package com.amz.nftlistener.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDedupeTest {
    @Test
    fun firstSightingIsNotDuplicate() {
        val dedupe = NotificationDedupe()
        assertFalse(dedupe.seen("key-a", 100L))
    }

    @Test
    fun sameKeyAndPostTimeIsDuplicate() {
        val dedupe = NotificationDedupe()
        dedupe.seen("key-a", 100L)
        assertTrue(dedupe.seen("key-a", 100L))
    }

    @Test
    fun sameKeyDifferentPostTimeIsNew() {
        val dedupe = NotificationDedupe()
        dedupe.seen("key-a", 100L)
        assertFalse(dedupe.seen("key-a", 200L))
    }

    @Test
    fun evictsOldestWhenOverMaxEntries() {
        val dedupe = NotificationDedupe(maxEntries = 2)
        dedupe.seen("a", 1L)
        dedupe.seen("b", 1L)
        dedupe.seen("c", 1L)
        assertFalse(dedupe.seen("a", 1L))
        assertTrue(dedupe.seen("c", 1L))
    }
}
```

`UploadFailureClassifierTest.kt`：

```kotlin
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.NotificationDedupeTest --tests com.amz.nftlistener.domain.UploadFailureClassifierTest`

Expected: 找不到类。

- [ ] **Step 3: 实现**

`NotificationDedupe.kt`：

```kotlin
package com.amz.nftlistener.domain

class NotificationDedupe(private val maxEntries: Int = 500) {
    private val seenKeys = LinkedHashSet<String>()

    fun seen(key: String, postTime: Long): Boolean {
        val token = "$key|$postTime"
        if (seenKeys.contains(token)) return true
        seenKeys.add(token)
        while (seenKeys.size > maxEntries) {
            val oldest = seenKeys.iterator().next()
            seenKeys.remove(oldest)
        }
        return false
    }
}
```

`UploadOutcome.kt`：

```kotlin
package com.amz.nftlistener.domain

sealed class UploadOutcome {
    data object Success : UploadOutcome()
    data class Retryable(val reason: String) : UploadOutcome()
    data class Permanent(val reason: String) : UploadOutcome()
}
```

`UploadFailureClassifier.kt`：

```kotlin
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
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.NotificationDedupeTest --tests com.amz.nftlistener.domain.UploadFailureClassifierTest`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/domain/NotificationDedupe.kt \
  app/src/main/java/com/amz/nftlistener/domain/UploadOutcome.kt \
  app/src/main/java/com/amz/nftlistener/domain/UploadFailureClassifier.kt \
  app/src/test/java/com/amz/nftlistener/domain/NotificationDedupeTest.kt \
  app/src/test/java/com/amz/nftlistener/domain/UploadFailureClassifierTest.kt
git commit -m "$(cat <<'EOF'
feat: dedupe notifications and classify upload failures

EOF
)"
```

---

### Task 4: NotificationParser

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/domain/NotificationParser.kt`
- Create: `app/src/test/java/com/amz/nftlistener/domain/NotificationParserTest.kt`

**Interfaces:**
- Consumes: `NotificationFields`、`TextTruncator.truncate`
- Produces: `object NotificationParser { fun parse(eventId: String, notificationKey: String, postedAt: Long, packageName: String, appLabel: String, extrasTitle: CharSequence?, extrasText: CharSequence?, extrasSubText: CharSequence?, extrasBigText: CharSequence?, channelId: String?, isOngoing: Boolean): NotificationFields }`
  - `title`/`text`/`subText`/`channelId`/`appLabel` 为 null 时变 `""`（`appLabel` 空则回退 `packageName`）
  - `text` 为空时用 `extrasBigText`
  - 三个文本字段经过 `TextTruncator.truncate`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.amz.nftlistener.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationParserTest {
    @Test
    fun mapsExtrasAndFallsBack() {
        val fields = NotificationParser.parse(
            eventId = "e1",
            notificationKey = "k1",
            postedAt = 10L,
            packageName = "com.foo",
            appLabel = "",
            extrasTitle = "Hello",
            extrasText = null,
            extrasSubText = null,
            extrasBigText = "Big body",
            channelId = "ch",
            isOngoing = true,
        )
        assertEquals("e1", fields.eventId)
        assertEquals("k1", fields.notificationKey)
        assertEquals(10L, fields.postedAt)
        assertEquals("com.foo", fields.packageName)
        assertEquals("com.foo", fields.appLabel)
        assertEquals("Hello", fields.title)
        assertEquals("Big body", fields.text)
        assertEquals("", fields.subText)
        assertEquals("ch", fields.channelId)
        assertEquals(true, fields.isOngoing)
    }

    @Test
    fun truncatesLongTitle() {
        val fields = NotificationParser.parse(
            eventId = "e1",
            notificationKey = "k1",
            postedAt = 10L,
            packageName = "com.foo",
            appLabel = "Foo",
            extrasTitle = "x".repeat(5000),
            extrasText = "y",
            extrasSubText = "",
            extrasBigText = null,
            channelId = null,
            isOngoing = false,
        )
        assertEquals(4096, fields.title.length)
        assertEquals("", fields.channelId)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.NotificationParserTest`

Expected: 找不到 `NotificationParser`。

- [ ] **Step 3: 实现**

```kotlin
package com.amz.nftlistener.domain

object NotificationParser {
    fun parse(
        eventId: String,
        notificationKey: String,
        postedAt: Long,
        packageName: String,
        appLabel: String,
        extrasTitle: CharSequence?,
        extrasText: CharSequence?,
        extrasSubText: CharSequence?,
        extrasBigText: CharSequence?,
        channelId: String?,
        isOngoing: Boolean,
    ): NotificationFields {
        val rawText = extrasText?.toString().orEmpty().ifEmpty {
            extrasBigText?.toString().orEmpty()
        }
        return NotificationFields(
            eventId = eventId,
            notificationKey = notificationKey,
            postedAt = postedAt,
            packageName = packageName,
            appLabel = appLabel.ifBlank { packageName },
            title = TextTruncator.truncate(extrasTitle?.toString().orEmpty()),
            text = TextTruncator.truncate(rawText),
            subText = TextTruncator.truncate(extrasSubText?.toString().orEmpty()),
            channelId = channelId.orEmpty(),
            isOngoing = isOngoing,
        )
    }
}
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.domain.NotificationParserTest`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/domain/NotificationParser.kt \
  app/src/test/java/com/amz/nftlistener/domain/NotificationParserTest.kt
git commit -m "$(cat <<'EOF'
feat: parse notification extras into webhook fields

EOF
)"
```

---

### Task 5: AppSettings

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/settings/KeyValueStore.kt`
- Create: `app/src/main/java/com/amz/nftlistener/settings/AppSettings.kt`
- Create: `app/src/test/java/com/amz/nftlistener/settings/InMemoryKeyValueStore.kt`
- Create: `app/src/test/java/com/amz/nftlistener/settings/AppSettingsTest.kt`

**Interfaces:**
- Consumes: `WebhookUrlValidator.normalize`
- Produces:
  - `interface KeyValueStore { fun getString(key: String, default: String = ""): String; fun putString(key: String, value: String) }`
  - `class AppSettings(store: KeyValueStore) { fun webhookUrl(): String; fun token(): String; fun isConfigured(): Boolean; fun save(rawUrl: String, rawToken: String): String? }`
  - `save`：URL 非法返回错误文案且不写入；合法则写入 trim 后的 URL 与 trim 后的 token，返回 `null`
  - `isConfigured()`：`webhookUrl()` 非空

- [ ] **Step 1: 写失败测试**

`InMemoryKeyValueStore.kt`：

```kotlin
package com.amz.nftlistener.settings

class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, default: String): String = values[key] ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}
```

`AppSettingsTest.kt`：

```kotlin
package com.amz.nftlistener.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun rejectsInvalidUrlAndKeepsUnconfigured() {
        val settings = AppSettings(InMemoryKeyValueStore())
        val error = settings.save("not-a-url", "token")
        assertEquals("Webhook URL 不合法", error)
        assertFalse(settings.isConfigured())
        assertEquals("", settings.webhookUrl())
        assertEquals("", settings.token())
    }

    @Test
    fun savesTrimmedUrlAndOptionalToken() {
        val settings = AppSettings(InMemoryKeyValueStore())
        assertNull(settings.save("  https://example.com/hook  ", "  abc  "))
        assertTrue(settings.isConfigured())
        assertEquals("https://example.com/hook", settings.webhookUrl())
        assertEquals("abc", settings.token())
    }

    @Test
    fun allowsEmptyToken() {
        val settings = AppSettings(InMemoryKeyValueStore())
        assertNull(settings.save("https://example.com/hook", "  "))
        assertEquals("", settings.token())
        assertTrue(settings.isConfigured())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.settings.AppSettingsTest`

Expected: 找不到 `AppSettings` / `KeyValueStore`。

- [ ] **Step 3: 实现**

`KeyValueStore.kt`：

```kotlin
package com.amz.nftlistener.settings

interface KeyValueStore {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
}
```

`AppSettings.kt`：

```kotlin
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
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.settings.AppSettingsTest`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/settings \
  app/src/test/java/com/amz/nftlistener/settings
git commit -m "$(cat <<'EOF'
feat: persist webhook URL and optional token

EOF
)"
```

---

### Task 6: OkHttp WebhookUploader

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/upload/WebhookUploader.kt`
- Create: `app/src/test/java/com/amz/nftlistener/upload/WebhookUploaderTest.kt`

**Interfaces:**
- Consumes: `UploadOutcome`、`UploadFailureClassifier`
- Produces: `class WebhookUploader(client: OkHttpClient = OkHttpClient()) { suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome }`
  - POST；`Content-Type: application/json; charset=utf-8`
  - token 非空时加 `Authorization: Bearer {token}`
  - 用 `fromHttpCode` / `fromThrowable` 分类

- [ ] **Step 1: 写失败测试**

```kotlin
package com.amz.nftlistener.upload

import com.amz.nftlistener.domain.UploadOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookUploaderTest {
    @Test
    fun postsJsonAndBearerToken() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val uploader = WebhookUploader()
            val outcome = uploader.upload(
                url = server.url("/hook").toString(),
                token = "secret",
                jsonBody = "{\"eventId\":\"e1\"}",
            )
            assertTrue(outcome is UploadOutcome.Success)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
            assertEquals("Bearer secret", request.getHeader("Authorization"))
            assertEquals("{\"eventId\":\"e1\"}", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun omitsAuthorizationWhenTokenBlank() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            WebhookUploader().upload(server.url("/hook").toString(), "", "{}")
            val request = server.takeRequest()
            assertEquals(null, request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun classifies404AsPermanent() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        try {
            val outcome = WebhookUploader().upload(server.url("/hook").toString(), "", "{}")
            assertTrue(outcome is UploadOutcome.Permanent)
        } finally {
            server.shutdown()
        }
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.upload.WebhookUploaderTest`

Expected: 找不到 `WebhookUploader`。

- [ ] **Step 3: 实现**

```kotlin
package com.amz.nftlistener.upload

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
) {
    suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome {
        return withContext(Dispatchers.IO) {
            val body = jsonBody.toRequestBody(JSON)
            val builder = Request.Builder().url(url).post(body)
            if (token.isNotEmpty()) {
                builder.header("Authorization", "Bearer $token")
            }
            try {
                client.newCall(builder.build()).execute().use { response ->
                    UploadFailureClassifier.fromHttpCode(response.code)
                }
            } catch (error: Throwable) {
                UploadFailureClassifier.fromThrowable(error)
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.upload.WebhookUploaderTest`

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/upload/WebhookUploader.kt \
  app/src/test/java/com/amz/nftlistener/upload/WebhookUploaderTest.kt
git commit -m "$(cat <<'EOF'
feat: POST notification JSON to webhook URL

EOF
)"
```

---

### Task 7: NotificationRepository（FakeEventStore）

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/data/PendingEventEntity.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/UploadLogEntity.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/EventStore.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/NetworkChecker.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/UploadScheduler.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/LogStatus.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/NotificationRepository.kt`
- Create: `app/src/test/java/com/amz/nftlistener/data/FakeEventStore.kt`
- Create: `app/src/test/java/com/amz/nftlistener/data/NotificationRepositoryTest.kt`

**Interfaces:**
- Consumes: `NotificationFields`、`PayloadJson.encode`、`AppSettings`、`WebhookUploader.upload`、`UploadOutcome`
- Produces:
  - `enum class LogStatus { QUEUED, SUCCESS, FAILED }`
  - `data class PendingEventEntity(eventId: String, postedAt: Long, packageName: String, appLabel: String, title: String, payloadJson: String, createdAt: Long)`
  - `data class UploadLogEntity(id: Long = 0, eventId: String, postedAt: Long, appLabel: String, title: String, status: String, reason: String, loggedAt: Long)`
  - `interface EventStore`（见下方完整接口）
  - `fun interface NetworkChecker { fun isOnline(): Boolean }`
  - `fun interface UploadScheduler { fun schedule() }`
  - `class NotificationRepository(...) { val logs; suspend fun handleIncoming(fields: NotificationFields); suspend fun enqueueTestEvent(now: Long = System.currentTimeMillis()); suspend fun drainPending(): DrainResult }`
  - `enum class DrainResult { DONE, HAS_RETRYABLE }`
  - 未配置 URL：只写 `FAILED` / `未配置`，不入队
  - 已配置：入队 + `QUEUED`；在线立刻 upload；成功改 SUCCESS 并删队列；Permanent 改 FAILED 并删队列；Retryable 留队列并 `schedule()`
  - 离线：入队后 `schedule()`
  - 待发已有 1000 条再来新的：删最旧 pending，写 `FAILED` / `队列溢出`，再写入新事件
  - 日志超过 200 条删最旧
  - `enqueueTestEvent`：`packageName = com.amz.nftlistener`，`title` 含 `test`，走 `handleIncoming`

`EventStore` 完整签名：

```kotlin
interface EventStore {
    suspend fun insertPending(entity: PendingEventEntity)
    suspend fun pendingCount(): Int
    suspend fun oldestPending(): PendingEventEntity?
    suspend fun deletePending(eventId: String)
    suspend fun allPendingOldestFirst(): List<PendingEventEntity>
    suspend fun insertLog(entity: UploadLogEntity)
    suspend fun updateLogByEventId(eventId: String, status: String, reason: String)
    suspend fun trimLogs(keep: Int = 200)
    fun observeRecentLogs(limit: Int = 200): Flow<List<UploadLogEntity>>
}
```

- [ ] **Step 1: 先写实体、接口和 Fake，再写失败的 Repository 测试**

`LogStatus.kt`：

```kotlin
package com.amz.nftlistener.data

enum class LogStatus {
    QUEUED,
    SUCCESS,
    FAILED,
}
```

`PendingEventEntity.kt` 与 `UploadLogEntity.kt` 先作为普通 data class（下一任务再加 Room 注解）：

```kotlin
package com.amz.nftlistener.data

data class PendingEventEntity(
    val eventId: String,
    val postedAt: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val payloadJson: String,
    val createdAt: Long,
)
```

```kotlin
package com.amz.nftlistener.data

data class UploadLogEntity(
    val id: Long = 0,
    val eventId: String,
    val postedAt: Long,
    val appLabel: String,
    val title: String,
    val status: String,
    val reason: String,
    val loggedAt: Long,
)
```

`EventStore.kt`、`NetworkChecker.kt`、`UploadScheduler.kt` 按上面接口创建。

`FakeEventStore.kt`：

```kotlin
package com.amz.nftlistener.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEventStore : EventStore {
    val pending = mutableListOf<PendingEventEntity>()
    val logs = mutableListOf<UploadLogEntity>()
    private val logFlow = MutableStateFlow<List<UploadLogEntity>>(emptyList())
    private var nextLogId = 1L

    override suspend fun insertPending(entity: PendingEventEntity) {
        pending.add(entity)
    }

    override suspend fun pendingCount(): Int = pending.size

    override suspend fun oldestPending(): PendingEventEntity? =
        pending.minByOrNull { it.createdAt }

    override suspend fun deletePending(eventId: String) {
        pending.removeAll { it.eventId == eventId }
    }

    override suspend fun allPendingOldestFirst(): List<PendingEventEntity> =
        pending.sortedBy { it.createdAt }

    override suspend fun insertLog(entity: UploadLogEntity) {
        val stored = if (entity.id == 0L) entity.copy(id = nextLogId++) else entity
        logs.add(stored)
        publish()
    }

    override suspend fun updateLogByEventId(eventId: String, status: String, reason: String) {
        val index = logs.indexOfLast { it.eventId == eventId }
        if (index >= 0) {
            logs[index] = logs[index].copy(status = status, reason = reason)
            publish()
        }
    }

    override suspend fun trimLogs(keep: Int) {
        if (logs.size > keep) {
            logs.sortBy { it.loggedAt }
            while (logs.size > keep) {
                logs.removeAt(0)
            }
            publish()
        }
    }

    override fun observeRecentLogs(limit: Int): Flow<List<UploadLogEntity>> = logFlow

    private fun publish() {
        logFlow.value = logs.sortedByDescending { it.loggedAt }.take(200)
    }
}
```

`NotificationRepositoryTest.kt`：

```kotlin
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.data.NotificationRepositoryTest`

Expected: 找不到 `NotificationRepository` / `WebhookSender`。

- [ ] **Step 3: 实现 WebhookSender 接口并让 WebhookUploader 实现它；实现 Repository**

把 Task 6 的 `WebhookUploader` 改成实现接口（保留原有方法体）：

`app/src/main/java/com/amz/nftlistener/upload/WebhookSender.kt`：

```kotlin
package com.amz.nftlistener.upload

import com.amz.nftlistener.domain.UploadOutcome

interface WebhookSender {
    suspend fun upload(url: String, token: String, jsonBody: String): UploadOutcome
}
```

`WebhookUploader` 增加：`class WebhookUploader(...) : WebhookSender`，方法加 `override`。

`NetworkChecker.kt` / `UploadScheduler.kt`：

```kotlin
package com.amz.nftlistener.data

fun interface NetworkChecker {
    fun isOnline(): Boolean
}
```

```kotlin
package com.amz.nftlistener.data

fun interface UploadScheduler {
    fun schedule()
}
```

`NotificationRepository.kt`：

```kotlin
package com.amz.nftlistener.data

import com.amz.nftlistener.domain.NotificationFields
import com.amz.nftlistener.domain.NotificationParser
import com.amz.nftlistener.domain.PayloadJson
import com.amz.nftlistener.domain.UploadOutcome
import com.amz.nftlistener.settings.AppSettings
import com.amz.nftlistener.upload.WebhookSender
import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class DrainResult {
    DONE,
    HAS_RETRYABLE,
}

class NotificationRepository(
    private val store: EventStore,
    private val settings: AppSettings,
    private val sender: WebhookSender,
    private val networkChecker: NetworkChecker,
    private val scheduler: UploadScheduler,
) {
    val logs: Flow<List<UploadLogEntity>> = store.observeRecentLogs(200)

    suspend fun handleIncoming(fields: NotificationFields) {
        if (!settings.isConfigured()) {
            insertLog(fields, LogStatus.FAILED, "未配置")
            store.trimLogs(200)
            return
        }
        if (store.pendingCount() >= MAX_PENDING) {
            val oldest = store.oldestPending()
            if (oldest != null) {
                store.deletePending(oldest.eventId)
                store.insertLog(
                    UploadLogEntity(
                        eventId = oldest.eventId,
                        postedAt = oldest.postedAt,
                        appLabel = oldest.appLabel,
                        title = oldest.title,
                        status = LogStatus.FAILED.name,
                        reason = "队列溢出",
                        loggedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        store.insertPending(
            PendingEventEntity(
                eventId = fields.eventId,
                postedAt = fields.postedAt,
                packageName = fields.packageName,
                appLabel = fields.appLabel,
                title = fields.title,
                payloadJson = PayloadJson.encode(fields),
                createdAt = System.currentTimeMillis(),
            ),
        )
        insertLog(fields, LogStatus.QUEUED, "")
        store.trimLogs(200)
        if (!networkChecker.isOnline()) {
            scheduler.schedule()
            return
        }
        applyOutcome(fields.eventId, sender.upload(settings.webhookUrl(), settings.token(), PayloadJson.encode(fields)))
    }

    suspend fun enqueueTestEvent(now: Long = System.currentTimeMillis()) {
        val fields = NotificationParser.parse(
            eventId = UUID.randomUUID().toString(),
            notificationKey = "test|$now",
            postedAt = now,
            packageName = "com.amz.nftlistener",
            appLabel = "NftListener",
            extrasTitle = "NftListener test",
            extrasText = "This is a test webhook payload",
            extrasSubText = "",
            extrasBigText = null,
            channelId = "test",
            isOngoing = false,
        )
        handleIncoming(fields)
    }

    suspend fun drainPending(): DrainResult {
        if (!settings.isConfigured()) return DrainResult.DONE
        var hasRetryable = false
        for (event in store.allPendingOldestFirst()) {
            when (val outcome = sender.upload(settings.webhookUrl(), settings.token(), event.payloadJson)) {
                UploadOutcome.Success,
                is UploadOutcome.Permanent,
                is UploadOutcome.Retryable,
                -> {
                    val drainOutcome = applyOutcome(event.eventId, outcome)
                    if (drainOutcome == DrainResult.HAS_RETRYABLE) {
                        hasRetryable = true
                    }
                }
            }
        }
        return if (hasRetryable) DrainResult.HAS_RETRYABLE else DrainResult.DONE
    }

    private suspend fun applyOutcome(eventId: String, outcome: UploadOutcome): DrainResult {
        return when (outcome) {
            UploadOutcome.Success -> {
                store.deletePending(eventId)
                store.updateLogByEventId(eventId, LogStatus.SUCCESS.name, "")
                DrainResult.DONE
            }
            is UploadOutcome.Permanent -> {
                store.deletePending(eventId)
                store.updateLogByEventId(eventId, LogStatus.FAILED.name, outcome.reason)
                DrainResult.DONE
            }
            is UploadOutcome.Retryable -> {
                scheduler.schedule()
                DrainResult.HAS_RETRYABLE
            }
        }
    }

    private suspend fun insertLog(fields: NotificationFields, status: LogStatus, reason: String) {
        store.insertLog(
            UploadLogEntity(
                eventId = fields.eventId,
                postedAt = fields.postedAt,
                appLabel = fields.appLabel,
                title = fields.title,
                status = status.name,
                reason = reason,
                loggedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val MAX_PENDING = 1000
    }
}
```

- [ ] **Step 4: 再跑测试**

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.data.NotificationRepositoryTest`

Expected: PASS。若 `WebhookUploader` 的默认构造让 Fake 不好写，保持 `WebhookSender` 接口即可，测试只依赖接口。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/data \
  app/src/main/java/com/amz/nftlistener/upload/WebhookSender.kt \
  app/src/main/java/com/amz/nftlistener/upload/WebhookUploader.kt \
  app/src/test/java/com/amz/nftlistener/data
git commit -m "$(cat <<'EOF'
feat: queue notifications and upload with retry policy

EOF
)"
```

---

### Task 8: Room EventStore

**Files:**
- Modify: `app/src/main/java/com/amz/nftlistener/data/PendingEventEntity.kt`
- Modify: `app/src/main/java/com/amz/nftlistener/data/UploadLogEntity.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/PendingEventDao.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/UploadLogDao.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/AppDatabase.kt`
- Create: `app/src/main/java/com/amz/nftlistener/data/RoomEventStore.kt`

**Interfaces:**
- Consumes: `EventStore`、`PendingEventEntity`、`UploadLogEntity`
- Produces: `class RoomEventStore(db: AppDatabase) : EventStore`；`abstract class AppDatabase : RoomDatabase` version = 1

Room 无法在纯 JVM 单测里可靠启动，本任务用 `:app:compileDebugKotlin` 成功作为验证。`EventStore` 行为已由 Task 7 覆盖。

- [ ] **Step 1: 给实体加 Room 注解**

`PendingEventEntity.kt` 增加 `@Entity(tableName = "pending_events")`，`eventId` 加 `@PrimaryKey`。`UploadLogEntity.kt` 增加 `@Entity(tableName = "upload_logs")`，`id` 加 `@PrimaryKey(autoGenerate = true)`。字段名与 Task 7 保持一致，不要改动。

- [ ] **Step 2: 写 DAO 与 Database**

`PendingEventDao`：`insert`（REPLACE）、`count`、`oldest`（`ORDER BY createdAt ASC LIMIT 1`）、`deleteById`、`allOldestFirst`。

`UploadLogDao`：`insert`、`updateByEventId`、`trim(keep)`（保留 `loggedAt DESC` 最新 keep 条）、`observeRecent(limit)` 返回 `Flow`。

`AppDatabase`：entities 为上述两表，`version = 1`，`exportSchema = false`，`create(context)` 使用库名 `nft_listener.db`。

- [ ] **Step 3: 实现 RoomEventStore**

`RoomEventStore(db)` 把每个 `EventStore` 方法委托给对应 DAO。`observeRecentLogs` 调 `observeRecent`。

- [ ] **Step 4: 编译验证**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/data
git commit -m "$(cat <<'EOF'
feat: persist upload queue and logs in Room

EOF
)"
```

---

### Task 9: Application 装配、WorkManager、监听服务

**Files:**
- Create: `app/src/main/java/com/amz/nftlistener/settings/SharedPreferencesKeyValueStore.kt`
- Create: `app/src/main/java/com/amz/nftlistener/upload/ConnectivityNetworkChecker.kt`
- Create: `app/src/main/java/com/amz/nftlistener/upload/WorkManagerUploadScheduler.kt`
- Create: `app/src/main/java/com/amz/nftlistener/upload/WebhookUploadWorker.kt`
- Create: `app/src/main/java/com/amz/nftlistener/capture/NotificationAccess.kt`
- Create: `app/src/main/java/com/amz/nftlistener/capture/NotificationCaptureService.kt`
- Create: `app/src/main/java/com/amz/nftlistener/NftListenerApp.kt`
- Create: `app/src/main/java/com/amz/nftlistener/MainActivity.kt`（空占位，Task 10 填界面）
- Create: `app/src/main/res/xml/notification_listener_service.xml`
- Create: `app/src/test/java/com/amz/nftlistener/capture/NotificationAccessTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `NotificationRepository`、`NotificationParser`、`NotificationDedupe`、`AppSettings`、`RoomEventStore`、`WebhookUploader`、`DrainResult`
- Produces:
  - `object NotificationAccess { fun isGranted(enabledPackages: Set<String>, packageName: String): Boolean }`
  - `class NftListenerApp : Application { lateinit var repository: NotificationRepository; lateinit var settings: AppSettings }`
  - `class NotificationCaptureService : NotificationListenerService`
  - `class WebhookUploadWorker : CoroutineWorker`：`drainPending()` 为 `HAS_RETRYABLE` 则 `Result.retry()`，否则 `Result.success()`
  - Worker 唯一名 `webhook-upload`；`NetworkType.CONNECTED`；`BackoffPolicy.EXPONENTIAL` 初始 10 秒；`ExistingWorkPolicy.KEEP`

- [ ] **Step 1: NotificationAccess 测试与实现**

测试：包名在集合中为 true，不在为 false。

```kotlin
object NotificationAccess {
    fun isGranted(enabledPackages: Set<String>, packageName: String): Boolean {
        return enabledPackages.contains(packageName)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests com.amz.nftlistener.capture.NotificationAccessTest`

Expected: 先失败后通过。

- [ ] **Step 2: 系统适配类**

`SharedPreferencesKeyValueStore(prefs)` 实现 `KeyValueStore`。

`ConnectivityNetworkChecker`：`activeNetwork` + `NET_CAPABILITY_INTERNET`。

`WorkManagerUploadScheduler.schedule()`：`OneTimeWorkRequestBuilder<WebhookUploadWorker>()`，约束 CONNECTED，指数退避 10 秒，`enqueueUniqueWork("webhook-upload", KEEP, request)`。

`WebhookUploadWorker.doWork()`：`(applicationContext as NftListenerApp).repository.drainPending()` 映射到 WorkManager Result。

`NftListenerApp.onCreate()` 装配：`AppSettings(SharedPreferencesKeyValueStore(getSharedPreferences("nft_listener", MODE_PRIVATE)))` + `NotificationRepository(RoomEventStore(AppDatabase.create(this)), settings, WebhookUploader(), ConnectivityNetworkChecker(this), WorkManagerUploadScheduler(this))`。

- [ ] **Step 3: 监听服务与清单**

`notification_listener_service.xml`：空 `<notification-listener />`。

`NotificationCaptureService.onNotificationPosted`：
1. `dedupe.seen(sbn.key, sbn.postTime)` 为 true 则 return
2. extras 取 `EXTRA_TITLE` / `EXTRA_TEXT` / `EXTRA_SUB_TEXT` / `EXTRA_BIG_TEXT`
3. `appLabel` 用 `PackageManager.getApplicationLabel`，失败回退 `packageName`
4. `NotificationParser.parse(...)`，`eventId = UUID.randomUUID()`
5. `scope.launch(Dispatchers.IO) { repository.handleIncoming(fields) }`
6. 不实现 `onNotificationRemoved`

`AndroidManifest.xml`：
- `INTERNET`、`ACCESS_NETWORK_STATE`
- `android:name=".NftListenerApp"`
- 导出 `MainActivity` + LAUNCHER
- `NotificationCaptureService`：`exported=true`，`permission=BIND_NOTIFICATION_LISTENER_SERVICE`，intent-filter `android.service.notification.NotificationListenerService`，meta-data 指向 `@xml/notification_listener_service`

先放空 `MainActivity : AppCompatActivity()`。

- [ ] **Step 4: 编译 + 权限单测**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests com.amz.nftlistener.capture.NotificationAccessTest`

Expected: `BUILD SUCCESSFUL`，测试 PASS。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener app/src/main/res/xml/notification_listener_service.xml app/src/main/AndroidManifest.xml app/src/test/java/com/amz/nftlistener/capture
git commit -m "$(cat <<'EOF'
feat: capture system notifications and retry uploads in background

EOF
)"
```

---

### Task 10: 主界面

**Files:**
- Modify: `app/src/main/java/com/amz/nftlistener/MainActivity.kt`
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/layout/item_upload_log.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml`

**Interfaces:**
- Consumes: `NftListenerApp.settings`、`NftListenerApp.repository`、`NotificationAccess.isGranted`、`NotificationManagerCompat.getEnabledListenerPackages`
- Produces: 单页。`onResume` 刷新权限。保存调用 `settings.save`。测试上报调用 `repository.enqueueTestEvent()`。`lifecycleScope` collect `repository.logs`。

无新 JVM 单测；验证为编译 + 文末手工清单。

- [ ] **Step 1: 资源**

strings：`permission_on=正在监听`，`permission_off=需要通知使用权`，`permission_action=去系统设置开启`，`webhook_url`，`webhook_token=Bearer Token（可空）`，`save`，`test_upload=测试上报`，`recent_logs=最近上报`，`saved=已保存`。

colors：`status_on=#2E7D32`，`status_off=#C62828`。

`activity_main.xml`：`NestedScrollView` > 垂直 `LinearLayout`：权限卡片（标题 + 跳转按钮）、URL 输入、Token 输入、保存/测试按钮、日志标题、`RecyclerView`（`nestedScrollingEnabled=false`）。

`item_upload_log.xml`：`logTitle`（`appLabel · title`）、`logMeta`（时间 · 状态 · reason）。

- [ ] **Step 2: MainActivity**

- View Binding
- 进入时回填已保存 URL/Token
- 权限按钮：`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`
- 保存：`settings.save`，Toast 错误或「已保存」
- 测试：`lifecycleScope.launch { repository.enqueueTestEvent() }`
- `repeatOnLifecycle(STARTED)` collect logs
- `onResume`：`NotificationAccess.isGranted(NotificationManagerCompat.getEnabledListenerPackages(this), packageName)`，已授权绿字并隐藏跳转按钮，未授权红字并显示按钮
- Adapter：`status` + 非空 `reason`

- [ ] **Step 3: 全量单测 + 打包**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

Expected: 全部单测 PASS，debug APK 成功。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/amz/nftlistener/MainActivity.kt app/src/main/res
git commit -m "$(cat <<'EOF'
feat: add settings screen and recent upload logs

EOF
)"
```

---

### Task 11: 手工验收

**Files:** 无代码。

**Interfaces:**
- Consumes: debug APK
- Produces: 下列清单全部勾过

- [ ] **Step 1: 安装并授予权限**

安装 `app/build/outputs/apk/debug/app-debug.apk`。打开「去系统设置开启」，打开 NftListener 通知使用权。回到 App 应显示绿色「正在监听」。

- [ ] **Step 2: webhook.site 测试上报**

把 webhook.site unique URL 填入并保存，Token 留空。点「测试上报」。站点 JSON 的 `packageName` 为 `com.amz.nftlistener`，`title` 含 `test`。

- [ ] **Step 3: 真通知**

用短信/邮件/其他 App 弹出一条通知。站点出现对应 `packageName`/`title`/`text`，App 日志 SUCCESS。

- [ ] **Step 4: 断网补发**

开飞行模式再触发通知，日志 QUEUED。关飞行模式后站点收到补发，日志 SUCCESS。

- [ ] **Step 5: 非法 URL**

保存 `not-a-url`，Toast「Webhook URL 不合法」，不覆盖已保存的合法 URL。

---

## Self-review

**Spec coverage**

| Spec 项 | 任务 |
| --- | --- |
| NotificationListenerService 全量捕获 | Task 9 |
| 可配置 URL + 可选 Bearer Token | Task 5、Task 10 |
| HTTP POST JSON 字段与截断 | Task 2、Task 4、Task 6 |
| key+postTime 去重 | Task 3、Task 9 |
| 未配置只记日志 | Task 7 |
| 在线立刻发、失败入队 | Task 7 |
| WorkManager 有网重试、指数退避 | Task 9 |
| 4xx 停、5xx/429/超时重试 | Task 3、Task 7 |
| 队列 1000 / 日志 200 / 队列溢出 | Task 7、Task 8 |
| 权限卡片 + 配置 + 最近日志 + 测试上报 | Task 10 |
| 单测：JSON、去重、URL、失败分类 | Task 1–4 |
| 手工：权限、webhook.site、真通知、飞行模式 | Task 11 |
| 不上报二进制、不过滤、不用无障碍/前台服务 | 全计划未引入 |

**Placeholder scan:** 无 TBD。Task 8–10 按 Task 7 已锁定的接口编写，不要另起方法名。

**Type consistency:** `WebhookSender.upload`、`EventStore`、`NotificationFields`、`UploadOutcome`、`DrainResult`、`AppSettings.save`、`NotificationAccess.isGranted(Set, String)` 后续任务名称一致。
