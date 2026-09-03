# NftListener

捕获手机上各 App 的系统通知，以 HTTP POST JSON 转发到你配置的 Webhook。断网时本地排队，联网后自动补发。

适合自己接服务端，或先用 [webhook.site](https://webhook.site) 验收。

- 包名：`com.amz.nftlistener`
- minSdk 24 / targetSdk 37
- Kotlin + AppCompat / Material + Room + WorkManager + OkHttp

## 能做什么

1. 用户在系统设置里打开「通知使用权」后，监听新弹出的通知。
2. 解析标题、正文等字段，POST 到配置的 URL。
3. 可选 Bearer Token：`Authorization: Bearer {token}`。
4. 主界面查看权限状态、保存配置、测试上报、最近成功/失败日志。
5. 断网、超时、5xx、429 会入队重试；进程被杀后队列不丢。

## 不做什么

- 不上报图标、大图等二进制内容。
- 不按包名或关键词过滤（全量转发）。
- 不用无障碍服务，不常驻前台服务。
- 不处理通知撤掉、点击或操作按钮。

## 使用

1. 安装 debug 包并打开应用。
2. 点击「去系统设置开启」，在通知使用权列表中打开 **NftListener**。
3. 填写 Webhook URL（必填）和 Bearer Token（可空），点保存。
4. 点「测试上报」，对端应收到一条 `packageName` 为 `com.amz.nftlistener`、标题含 `test` 的 JSON。
5. 用微信等 App 发一条真通知，对端应收到对应数据。
6. 可开飞行模式再触发通知，关闭飞行模式后会补发。

若界面显示服务未连接，可点击状态尝试修复，或在系统设置里关掉再打开通知使用权。

Logcat 过滤标签：`NftListener`。

```bash
adb logcat -s NftListener
```

## 后台与进程

**不需要把 App 留在前台。** 正常用法是：打开通知使用权、填好 Webhook，然后切走。系统会单独绑定 `NotificationListenerService`，不依赖主界面是否可见。

| 情况 | 结果 |
| --- | --- |
| 退到后台 / 开别的 App | 继续监听并上报 |
| 从最近任务划掉 / 系统杀进程 | 权限还在。AOSP 上系统通常会重新绑定监听；部分国产机会延迟，或需要允许自启动、忽略电池优化后才稳 |
| 系统设置里「强制停止」 | 在再次打开本 App 之前，基本不会再起来 |

断档期间弹出的通知**不会补扫**：重连时不会把已有通知再上报一遍，这一段可能漏。已经写入本地队列的事件，进程起来后会由 WorkManager 接着发。

本应用不做前台常驻通知。要长期后台转发，请在系统设置里把 NftListener 设为允许自启动 / 忽略电池优化。

## 上报 JSON

`POST`，`Content-Type: application/json; charset=utf-8`。空字符串仍保留键；`title` / `text` / `subText` 各最多 4096 字符。

```json
{
  "eventId": "uuid",
  "postedAt": 1730000000000,
  "packageName": "com.xxx.app",
  "appLabel": "微信",
  "title": "标题",
  "text": "正文",
  "subText": "",
  "channelId": "msg",
  "isOngoing": false
}
```

| 字段 | 说明 |
| --- | --- |
| `eventId` | 本条事件 ID |
| `postedAt` | 通知弹出时间，毫秒时间戳 |
| `packageName` | 来源应用包名 |
| `appLabel` | 应用显示名，解析失败则回退为包名 |
| `title` / `text` / `subText` | 通知文案 |
| `channelId` | 通知渠道 |
| `isOngoing` | 是否为持续通知 |

## 失败策略

| 情况 | 行为 |
| --- | --- |
| 未开通知使用权 | 收不到通知，界面持续引导 |
| 未配置 URL | 只记日志「未配置」，不入队 |
| URL 非法 | 保存时拦截 |
| 断网 / 超时 / 5xx / 429 | 留在队列，指数退避重试 |
| 其他 4xx | 视为配置错误，该条停止重试 |
| 队列超过 1000 条 | 丢最旧待发项，记「队列溢出」 |
| 日志 | 最多保留约 200 条 |

## 构建

需要 JDK 17（或 Android Studio 自带 JDK）。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

单元测试：

```bash
./gradlew :app:testDebugUnitTest
```

## 隐私

通知使用权必须由用户在系统设置中手动授予。数据只发往用户自己填写的 Webhook，应用不内置服务端、不做账号系统。
