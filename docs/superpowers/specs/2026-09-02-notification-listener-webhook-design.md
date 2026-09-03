# 系统通知捕获并上报 Webhook

日期：2026-09-02  
状态：待实现  
应用：NftListener（`com.amz.nftlistener`）  
minSdk 24 / targetSdk 37

## 目标

在用户授予系统「通知使用权」后，捕获安卓手机上所有应用的推送通知，以 HTTP POST JSON 转发到用户配置的 Webhook。断网时本地排队，联网后自动补发。应用内提供配置页和最近上报日志，便于无自建服务端时用 webhook.site 验收。

成功标准：

1. 打开通知使用权后，任意第三方 App 的新弹出通知都能被记录。
2. 配置合法 Webhook URL 后，通知以约定 JSON 发出。
3. 飞行模式期间到达的通知，关闭飞行模式后会补发。
4. 主界面能看到权限状态、已保存的 URL，以及最近成功/失败日志。

## 非目标（第一版）

- 不上报通知大图、图标、附件等二进制内容。
- 不按包名或关键词过滤（全量上报）。
- 不做账号系统，不内置服务端。
- 不用无障碍服务读取通知栏。
- 不处理通知撤掉 / 点击 / 操作按钮回调。

## 架构

采用官方 `NotificationListenerService` + Room 队列 + WorkManager 上报。

```
系统通知
  → NotificationListenerService.onNotificationPosted
  → 解析并去重
  → Room（待上报队列 + 最近日志）
  → 在线则立刻 POST；失败交给 WorkManager
  → 主界面：权限引导、Webhook 配置、最近日志
```

在线时先同步试发一次，失败再入队重试，兼顾实时和可靠。监听服务由系统在权限仍开启时拉起；队列落盘，进程被杀后可补发。

不使用前台常驻服务（耗电、厂商易杀），不使用无障碍服务。

## 组件

| 单元 | 职责 | 依赖 |
| --- | --- | --- |
| `MainActivity` | 权限状态、URL/Token 配置、测试上报、日志列表 | Settings、Repository、NotificationAccess |
| `NotificationCaptureService` | 接收系统通知，解析后交给 Repository | Repository |
| `NotificationParser` | 从 `StatusBarNotification` 抽出字段并截断 | 无 |
| `NotificationRepository` | 写入队列与日志、触发上报、暴露日志 Flow | Room、Uploader、WorkManager |
| `WebhookUploader` | HTTP POST JSON，返回成功或失败分类 | OkHttp |
| `WebhookUploadWorker` | 有网时消费队列，指数退避 | Repository、Uploader |
| Room：`pending_events` / `upload_logs` | 持久化待发事件与最近日志 | 无 |
| DataStore / SharedPreferences | 保存 Webhook URL 与可选 Token | 无 |

语言：Kotlin。UI：现有 AppCompat + Material + View Binding，不引入 Compose。

## 权限与清单

- `BIND_NOTIFICATION_LISTENER_SERVICE`：服务声明，用户必须在系统设置中手动打开。
- `INTERNET`：上报。
- `ACCESS_NETWORK_STATE`：判断是否立刻试发。
- 不申请无障碍、不申请通知发送权限（本应用自身不发业务通知）。

主界面用 `NotificationManagerCompat.getEnabledListenerPackages`（或等价 API）检测授权；未授权显示跳转 `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`。用户中途关闭权限时，界面回到未授权状态。

## 界面

单 Activity，无底部导航。

1. **权限卡片**：未授权红色提示 + 跳转按钮；已授权绿色「正在监听」。
2. **上报配置**：Webhook URL、可选 Token（`Authorization: Bearer {token}`，可空）、保存、测试上报。
3. **最近日志**：时间、App 名、标题摘要、状态（成功 / 排队中 / 失败+原因）。最多保留约 200 条。

测试上报发送一条可识别的假数据（`packageName = com.amz.nftlistener`，标题标明 test），走与真通知相同的上报路径。

## 上报 JSON

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

- `postedAt`：毫秒时间戳，取自 `StatusBarNotification.postTime`。
- `appLabel`：由 `PackageManager` 解析，失败则回退为 `packageName`。
- `title` / `text` / `subText`：各最多 4KB，超出截断。
- 空字段传空字符串，不省略键。
- Content-Type：`application/json; charset=utf-8`。
- Token 非空时加请求头 `Authorization: Bearer {token}`。

## 数据流

1. 用户打开通知使用权，填写并保存 URL。
2. 任意 App 弹出通知 → `onNotificationPosted`。
3. `NotificationParser` 抽出字段；用 `key + postTime` 去重，重复则丢弃。
4. 未配置 URL：只写日志「未配置」，不入队。
5. 已配置：写入 `pending_events` 和一条「排队中」日志。
6. 有网则立刻 POST；2xx 则删除队列项并将日志改为成功。
7. 失败按错误策略入队或停重试；WorkManager 在 `NetworkType.CONNECTED` 时继续消费。
8. 只处理新弹出。`onNotificationRemoved` 与通知内容更新不重复上报。

## 错误处理

| 情况 | 行为 |
| --- | --- |
| 未开通知使用权 | 收不到通知；界面持续引导 |
| URL 为空 | 不入队；日志「未配置」 |
| URL 非法 | 保存时拦截 |
| 断网 / 超时 / 5xx / 429 | 留在队列，WorkManager 指数退避重试直到成功 |
| 4xx（除 429） | 视为配置错误，标失败，该条停止重试 |
| 标题/正文过长 | 各截断到 4KB |
| 队列超过 1000 条 | 丢最旧待发项，并记失败日志「队列溢出」 |
| 进程被杀 | Room + WorkManager 重启后补发 |

## 测试

单元测试（不依赖真机监听）：

- JSON 字段组装与截断
- `key + postTime` 去重
- URL / Token 校验
- 失败分类：4xx 停重试，5xx / 429 / 超时继续重试

手工验收：

1. 授予通知使用权。
2. 配置 [https://webhook.site](https://webhook.site) 的 URL。
3. 点「测试上报」，站点能看到假数据。
4. 用微信或其他 App 发一条真通知，站点能看到对应 JSON。
5. 开飞行模式再触发通知，关闭飞行模式后站点收到补发。

## 风险与约束

- 通知使用权必须用户手动授予，应用无法静默读取。
- 部分系统/厂商通知或标记为敏感的内容可能只有标题、没有正文。
- 国内厂商可能延迟重启被杀进程，补发会变慢，但队列不丢。
- 本应用仅在本机、经用户授权后工作；Webhook 由用户自己配置，数据发往用户指定地址。
