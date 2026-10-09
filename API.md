# IgniteFusionAD 开发文档

Android 广告 SDK。广告位与素材由 IgniteFusionAD 后端下发，接入方通过 **Load → Show** 展示自营广告；当广告位关闭自营（`enableSelfAd=false`）时，SDK 回调接入方自行加载三方广告，并提供漏斗埋点与日上限控制。

- 包名：`com.ignitefusion.ad`
- 入口类：`IgniteFusionAd`
- 当前 library 发布坐标（`library/build.gradle.kts`）：`com.sparkfusionad.sdk:IgniteFusionAd:1.1.0`
- `minSdk`：29；JDK / Kotlin：11 / 1.9+

---

## 1. 能力一览

| 广告类型 | 加载 | 展示 | 说明 |
| --- | --- | --- | --- |
| 开屏 Splash | `loadIFSplashAd` | `showIFSplashAd(ViewGroup)` | 倒计时跳过（默认 5s）、摇一摇触发点击 |
| Banner | `loadIFBannerAd` | `showIFBannerAd(ViewGroup)` | 关闭按钮会清空容器；可用 `removeIFBannerAd` 主动移除 |
| 插屏 Interstitial | `loadIFInterstitialAd` | `showIFInterstitialAd(Activity)` | 全屏半透明 Dialog，约 15s 自动关闭 |
| 激励视频 Rewarded | `loadIFVideoAd` | `showIFVideoAd(Activity)` | 内置 ExoPlayer；约 20s 发奖、30s 倒计时 |

其它：

- 自营广告展示 / 点击由 SDK 自动上报 `POST /sdk/event`（`show` / `click`）
- 三方广告：`LOAD` 由 SDK 自动上报；接入方在真实展示 / 点击时上报 `SHOW` / `CLICK`
- 可选加密通道：RSA-OAEP-SHA-256 + AES-256-GCM

---

## 2. 接入准备

### 2.1 权限

SDK Manifest 已声明：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

下载类广告依赖 `REQUEST_INSTALL_PACKAGES`；激励视频 Activity 已在 library 中注册：

`com.ignitefusion.ad.ui.IgniteFusionRewardVideoActivity`（`exported=false`，全屏）。

### 2.2 依赖

源码依赖（当前仓库）：

```kotlin
implementation(project(":library"))
```

SDK 传递依赖（接入方一般无需再声明）：

- `androidx.media3:media3-exoplayer:1.9.4` / `media3-ui:1.9.4`
- `okhttp:4.12.0`、`gson:2.10.1`、`okio:3.6.0`
- AndroidX core-ktx / appcompat / Material

### 2.3 后端地址

生产 / 联调地址内置在 `IgniteFusionApiClient.PROD_BASE_URL`，初始化时**不能**传入 `baseUrl`。本地联调请改该常量后重新编译 AAR。

请求头：`X-App-Id`（`appId` 非空时自动带上）。网络超时 15s。成功 / 失败回调均切回**主线程**。

---

## 3. 初始化

必须在加载广告前调用。内部会 `configure(appId, encrypted)`、保存 `applicationContext`、清空已缓存广告。

```kotlin
import com.ignitefusion.ad.IgniteFusionAd
import com.ignitefusion.ad.IgniteFusionApiClient

IgniteFusionAd.init(
    context   = applicationContext,
    appId     = "后台 apps.app_id",
    encrypted = false,   // 默认 false；生产建议 true
    debug     = false,   // 默认 false；true 时输出 SDK 内部 Log
)

// 运行时切换加密（关闭加密时会清空 RSA 公钥缓存）
IgniteFusionApiClient.setEncrypted(true)
```

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `context` | `Context` | 使用 `applicationContext` |
| `appId` | `String` | 管理后台应用 ID；未就绪可传 `""` |
| `encrypted` | `Boolean` | `true` 走加密信封；`false` 走明文 GET |
| `debug` | `Boolean` | `true` 开启 SDK 日志（`IgniteFusionAd` / Api / Cipher 等）；默认关闭。`destroy()` 后也会关闭 |

未初始化时，所有 `load*` / `show*` 会失败，错误信息类似：`请先初始化：IgniteFusionAd.init(context, appId)`。

退出时释放：

```kotlin
IgniteFusionAd.destroy()
```

会取消开屏倒计时、注销摇一摇与下载广播、清空已加载广告并重置初始化状态。

Demo 初始化见 `SdkManager.initAd()`（`IgniteFusionAd.init(..., Common.AppId, false)`）。

---

## 4. 统一流程

```
init
        │
        ▼
loadIF*Ad(adId)  ──►  GET/POST 拉取广告位
        │
        ├─ enable=false              → onAdLoadFailure
        ├─ enableSelfAd=false        → 三方流程（见第 6 节），不要再调 showIF*
        └─ enableSelfAd=true         → 按 weight 抽一条素材
                                        ├─ 成功 → onAdLoadSuccess
                                        └─ 无素材 / 权重全 0 / 激励无视频 → onAdLoadFailure
        │
        ▼
showIF*Ad(...)   仅自营广告有效
        ├─ 未 load                    → onAdShowFailure
        ├─ 当前位是三方               → onAdShowFailure（须在 loadThirdPartyAd 里自行展示）
        └─ 成功                       → onAdShowSuccess，并自动上报自营 show
```

每种广告位同一时刻只缓存**一条**已加载素材。再次 `load` 会覆盖；`init` / `destroy` 会清空。

权重：`weight` 限制在 `[0, 100]`，**大于 0** 才参与抽取；按权重随机命中。

---

## 5. 公开 API

### 5.1 监听器（均为 data class，可用具名 lambda）

失败回调参数是 **`Throwable`**，不是 String。

```kotlin
data class IgniteFusionAdLoadListener(
    val onAdLoadSuccess: () -> Unit = {},
    val onAdLoadFailure: (Throwable) -> Unit = {}
)

data class IgniteFusionAdShowListener(
    val onAdShowSuccess: () -> Unit = {},
    val onAdShowFailure: (Throwable) -> Unit = {},
    val onAdClick: () -> Unit = {},
    val onAdClose: () -> Unit = {}
)

data class IgniteFusionRewardAdShowListener(
    val onAdShowSuccess: () -> Unit = {},
    val onAdShowFailure: (Throwable) -> Unit = {},
    val onAdClick: () -> Unit = {},
    val onAdClose: () -> Unit = {},
    val onReward: () -> Unit = {}
)
```

### 5.2 开屏

```kotlin
IgniteFusionAd.loadIFSplashAd(
    context = this,
    adId = splashAdUnitId,
    loadThirdPartyAd = { info -> /* 仅 enableSelfAd=false 时进入 */ },
    listener = IgniteFusionAdLoadListener(
        onAdLoadSuccess = {
            IgniteFusionAd.showIFSplashAd(
                splashContainer,
                IgniteFusionAdShowListener(
                    onAdShowSuccess = {},
                    onAdShowFailure = { err -> /* 进主页等兜底 */ },
                    onAdClick = {},
                    onAdClose = { /* 跳过 / 倒计时结束 */ }
                )
            )
        },
        onAdLoadFailure = { err -> }
    )
)
```

自营展示行为：

- 渲染到传入的 `ViewGroup`（全屏铺满）
- 跳过文案：`跳过N秒`，默认时长 `splashDurationSeconds = 5`
- 点击素材或摇一摇（加速度阈值约 2.2g，防抖 1s）触发点击；同一轮只会处理一次动作
- 点击后按素材类型下载 / 打开落地页 / 拉起已安装包名
- 关闭时移除容器子 View 并回调 `onAdClose`

### 5.3 Banner

```kotlin
IgniteFusionAd.loadIFBannerAd(this, bannerAdUnitId, loadThirdPartyAd = { info -> }, listener)
// onAdLoadSuccess 后：
IgniteFusionAd.showIFBannerAd(bannerContainer, IgniteFusionAdShowListener(...))
// 主动移除：
IgniteFusionAd.removeIFBannerAd(bannerContainer)
```

关闭按钮会先 `removeIFBannerAd` 再 `onAdClose`。

### 5.4 插屏

```kotlin
IgniteFusionAd.loadIFInterstitialAd(this, interstitialAdUnitId, loadThirdPartyAd = { info -> }, listener)
IgniteFusionAd.showIFInterstitialAd(this, IgniteFusionAdShowListener(...))
```

`show` 需要 **Activity**。Dialog 可取消；约 **15 秒**自动 `dismiss`，`onDismiss` 时回调 `onAdClose`。

### 5.5 激励视频

标准两步：

```kotlin
IgniteFusionAd.loadIFVideoAd(this, rewardAdUnitId, loadThirdPartyAd = { info -> }, listener)
// onAdLoadSuccess 后：
IgniteFusionAd.showIFVideoAd(
    activity = this,
    listener = IgniteFusionRewardAdShowListener(
        onAdShowSuccess = {},
        onAdShowFailure = { },
        onAdClick = {},
        onAdClose = {},
        onReward = { /* 发奖 */ }
    )
)
```

加载失败常见原因：广告位未启用、无素材、权重全 0、`video.url` 为空。

自营播放规则（`IgniteFusionRewardVideoActivity`）：

| 项 | 值 |
| --- | --- |
| 倒计时总长 | 30 秒 |
| 发奖门槛 | 观看满 20 秒 → `onReward`，跳过文案变为「已获取奖励\|关闭」 |
| 底部提示条 | 约 10 秒后滑入 |
| 循环播放 | ExoPlayer `REPEAT_MODE_ONE` |
| 关闭 | 结束 Activity 时回调 `onAdClose` |

便捷重载（内部先 load 再 show，**不支持** `loadThirdPartyAd`）：

```kotlin
fun showIFVideoAd(
    activity: Activity,
    adId: String,
    showAd: Boolean = true,
    onAdLoadSuccess: () -> Unit = {},
    onAdLoadError: () -> Unit = {},
    onAdClose: () -> Unit = {},
    onReward: () -> Unit = {}
)
```

`showAd=false` 时加载成功后立即走 `onAdClose`，不展示。

---

## 6. 三方广告

后台将广告位 `enableSelfAd` 设为 `false` 时，SDK **不会**填充自营素材，也不会走 `onAdLoadSuccess` + `showIF*`。

流程：

1. 若未传 `loadThirdPartyAd` → `onAdLoadFailure`（「当前广告位为第三方广告，请传入 loadThirdPartyAd…」）
2. SDK **自动**上报一次 `ThirdPartyEventType.LOAD`
3. 响应 `allowLoad=false` → **不回调** `loadThirdPartyAd`，直接 `onAdLoadFailure`（原因多为 `show_limit_reached` / `click_limit_reached`）
4. `allowLoad=true` → 回调 `loadThirdPartyAd(info)`，接入方在此加载并展示穿山甲 / GroMore 等
5. 上报 LOAD **失败**时兜底：仍回调 `loadThirdPartyAd`，`allowLoad=true`，`remaining*=Int.MAX_VALUE`，`reason="report-failed-fallback"`，避免网络抖动阻断填充

```kotlin
IgniteFusionAd.loadIFSplashAd(
    context = this,
    adId = adUnitId,
    loadThirdPartyAd = { info ->
        // 此处 allowLoad 已为 true（或上报失败兜底）
        // 调用三方 SDK 加载并自行展示……
        // 真实展示：
        info.reportShow()
        // 真实点击：
        info.reportClick()
    },
    listener = IgniteFusionAdLoadListener(
        onAdLoadFailure = { /* 未启用、超限、未传回调等 */ }
    )
)
```

### 6.1 `SdkThirdPartyLoadInfo`

```kotlin
data class SdkThirdPartyLoadInfo(
    val adUnitId: String,
    val allowLoad: Boolean,
    val loads: Int, val shows: Int, val clicks: Int,
    val showLimit: Int, val clickLimit: Int,   // 0 = 不限
    val remainingShows: Int, val remainingClicks: Int,
    val reason: String?
) {
    fun reportShow()   // 等价于 reportThirdPartyEvent(adUnitId, SHOW)
    fun reportClick()
    fun reachedShowLimit(): Boolean
    fun reachedClickLimit(): Boolean
}
```

### 6.2 手动埋点

**不要**再上报 `LOAD`，否则当日 loads 虚高、更容易触达上限。

```kotlin
IgniteFusionAd.reportThirdPartyEvent(
    adUnitId = adUnitId,
    event = ThirdPartyEventType.SHOW,   // 或 CLICK
    onSuccess = { out: SdkThirdPartyEventOut -> },
    onFailure = { t -> }
)
```

`event: String` 的重载已 `@Deprecated`，非法值会 `onFailure(IllegalArgumentException)`。

```kotlin
enum class ThirdPartyEventType(val value: String) {
    LOAD("load"), SHOW("show"), CLICK("click")
}
```

`SdkThirdPartyEventOut`：

| 字段 | 说明 |
| --- | --- |
| `allowLoad` | 是否允许继续请求三方 |
| `counters` | 当日 loads / shows / clicks |
| `limits` | `showLimit` / `clickLimit`，`0` 表示不限 |
| `remaining` | 当日剩余展示 / 点击 |
| `reason` | 超限原因；未超限为 `null` |

后端判定：`showLimit>0 && shows>=showLimit` **或** `clickLimit>0 && clicks>=clickLimit` 时 `allowLoad=false`。日切为 **UTC 自然日**。

---

## 7. 自营点击与下载

`url` 为空：Toast 提示 CTA + 应用名，不跳转。

`isDownload=true`：`DownloadManager` 下载，完成后拉起安装；若带 `applicationId`，安装完成会尝试打开该包。

否则：若 `applicationId` 已安装则直接拉起；否则 `ACTION_VIEW` 打开 http(s) 或包名。

相对媒体路径（`/uploads/...`）由 `IgniteFusionApiClient.resolveMediaUrl()` 拼到 `PROD_BASE_URL`；`http(s)` 绝对地址透传。若绝对地址 host 为 `localhost` / `127.0.0.1` 且 path 以 `/uploads/` 开头，会改写为当前 `baseUrl`。

---

## 8. 后端接口（SDK 已封装）

所有业务接口在 `encrypted=true` 时使用信封；`false` 时明文。`appId` 非空则带 `X-App-Id`。

### 8.1 拉取广告位

明文：

```
GET {BASE}/sdk/ad-unit/{ad_unit_id}
```

加密：

```
POST {BASE}/sdk/ad-unit/e
Content-Type / Accept: application/enc+json
明文载荷: { "ad_unit_id": "..." }
```

成功 JSON（加密通道解密后相同）：

```json
{
  "ad_unit_id": "…",
  "name": "SDK开屏",
  "ad_type": "splash",
  "enable": true,
  "enableSelfAd": true,
  "ads": [
    {
      "id": "…",
      "appname": "…",
      "content": "…",
      "applogo": { "url": "/uploads/logo.png" },
      "promotional": { "url": "/uploads/promo.png" },
      "video": { "url": "/uploads/demo.mp4" },
      "themeColor": "#FF5500",
      "applicationId": "com.example.app",
      "version": "1.0.0",
      "isDownload": true,
      "url": "https://example.com/download",
      "weight": 5
    }
  ]
}
```

- `enable=false`：未启用 / 审核未通过，HTTP 仍可能 200，SDK 走 `onAdLoadFailure`
- 广告位不存在：HTTP 404 → `onAdLoadFailure`
- `ad_type` 由后台配置；SDK 按调用的 `loadIF*` 使用对应渲染器，不在客户端再校验类型字符串

对应模型：`com.ignitefusion.ad.model.AdSpaceResult` / `Addata`。

### 8.2 自营埋点（SDK 自动）

```
POST {BASE}/sdk/event
{ "ad_unit_id": "…", "event": "show" | "click" }
```

失败只打日志，不影响展示。

### 8.3 三方埋点

```
POST {BASE}/sdk/third-party/event
{ "ad_unit_id": "…", "event": "load" | "show" | "click" }
```

其它 `event` 值后端 422。成功体与 `SdkThirdPartyEventOut` 一致。

### 8.4 公钥（仅加密模式，SDK 进程内缓存）

```
GET {BASE}/auth/public-key
→ { "kid": "…", "publicPem": "-----BEGIN PUBLIC KEY-----\n…", "algorithm": "RS256" }
```

---

## 9. 加密通道

`encrypted=true` 时：

1. 阻塞拉取公钥并缓存（并发锁）
2. 每次请求生成一次性 32B AES key + 12B GCM nonce
3. RSA-OAEP-SHA-256 加密 AES key → `encryptedKey`
4. AES-256-GCM 加密 UTF-8 JSON → `payload`（含 16B tag）
5. 信封：`{ kid, encryptedKey, iv, payload }`，字段均为标准 Base64
6. 响应 nonce = 请求 nonce **逐字节 XOR 0x01** 后解密

若服务端仍返回 `application/json`，SDK **降级按明文解析**，便于灰度。纯 JCA 实现，无 BouncyCastle。

运行时 `setEncrypted(false)` 会清空公钥缓存。

---

## 10. 调用约定与注意

1. **主线程调用** `init` / `load` / `show`；网络在后台，回调回主线程。
2. 必须先 `init` 再 `load`，自营必须先 `load` 成功再 `show`。
3. 三方广告**禁止**再调对应 `showIF*`，必须在 `loadThirdPartyAd` 内展示。
4. 三方只手动报 `SHOW` / `CLICK`，不要报 `LOAD`。
5. `limit=0` 表示不限；`remaining` 可忽略。
6. 激励视频必须有可解析的 `video.url`。
7. 证书须被系统信任；模拟器访问本机可用 `http://10.0.2.2:端口`（需改 `PROD_BASE_URL`）。
8. 同一类型广告位全局单槽缓存，不要并发 load 同一类型后期望同时持有多条。

---

## 11. 混淆规则（ProGuard / R8）

SDK 已通过 `library/consumer-rules.pro` 声明消费者规则，并以 `consumerProguardFiles` 打进 AAR。接入方开启 `minifyEnabled` 后，**一般无需再抄一份**。

规则覆盖：

- 公开 API：`IgniteFusionAd`、各类 Listener、`ThirdPartyEventType`、`SdkThirdParty*`、`IgniteFusionApiClient`
- Manifest Activity：`com.ignitefusion.ad.ui.IgniteFusionRewardVideoActivity`
- Gson 模型：`model.**`、`network.EncEnvelope`、带 `@SerializedName` 的字段
- 依赖：OkHttp / Okio / Gson / Media3（keep + dontwarn）
- Kotlin：`Metadata`、注解与签名属性

若使用源码依赖且规则未自动合并，可在 app 的 `proguard-rules.pro` 中追加：

```proguard
-keep class com.ignitefusion.ad.IgniteFusionAd { *; }
-keep class com.ignitefusion.ad.IgniteFusionAdLoadListener { *; }
-keep class com.ignitefusion.ad.IgniteFusionAdShowListener { *; }
-keep class com.ignitefusion.ad.IgniteFusionRewardAdShowListener { *; }
-keep class com.ignitefusion.ad.ThirdPartyEventType { *; }
-keep class com.ignitefusion.ad.SdkThirdParty** { *; }
-keep class com.ignitefusion.ad.ui.IgniteFusionRewardVideoActivity { *; }
-keep class com.ignitefusion.ad.model.** { *; }
```

完整内容见 `library/consumer-rules.pro`。

---

## 12. 源码结构（library）

```
library/src/main/java/com/ignitefusion/ad/
├── IgniteFusionAd.kt                 # 对外入口
├── IgniteFusionAdListener.kt         # Load / Show / Reward 监听器
├── IgniteFusionApiClient.kt          # OkHttp + 明文/加密 + 埋点（公开）
├── IgniteFusionThirdPartyEvent.kt    # 三方枚举与数据类
├── model/                            # 数据模型
│   ├── AdSpaceResult.kt
│   ├── Addata.kt
│   └── IgniteFusionAdData.kt         # 内部渲染数据
├── network/                          # 加密工具
│   └── IgniteFusionCipher.kt         # RSA + AES-GCM
├── ui/                               # 广告 UI / Activity
│   ├── IgniteFusionSplashAD.kt
│   ├── IgniteFusionBannerAD.kt
│   ├── IgniteFusionInsertAD.kt
│   ├── IgniteFusionRewardAD.kt
│   ├── IgniteFusionRewardVideoActivity.kt
│   └── IgniteFusionImageLoader.kt
└── util/
    └── IgniteFusionLog.kt            # debug 日志开关
```

Demo：`app/.../StartActivity`（开屏）、`MainActivity`（Banner / 插屏 / 激励）、`SdkManager`（初始化）。
