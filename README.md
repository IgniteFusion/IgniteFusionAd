# IgniteFusionAd Android SDK

[![](https://jitpack.io/v/Spark-Fusion/SparkFusionAD.svg)](https://jitpack.io/#Spark-Fusion/SparkFusionAD)

轻量级 Android 广告 SDK。**广告位 + 广告素材均通过部署的 SparkFusionAD 后端拉取**，不再依赖任何第三方 BaaS。
ignitefusion.cn
IgniteFusionAD
## 功能

- ✅ 开屏广告（Splash）
- ✅ Banner 广告
- ✅ 插屏广告（Interstitial）
- ✅ 激励视频广告（Rewarded Video，内置 ExoPlayer 播放器）
- ✅ 「广告位 enableSelfAd=false」时，回调接入方自行加载三方广告
- ✅ **三方广告漏斗埋点**（load / show / click 三类事件上报，后端按广告位聚合）
- ✅ **三方广告展示 / 点击日上限**（后台 `third_party_show_limit`、`third_party_click_limit`，`0` 表示不限）
- ✅ 上限保护：达到日上限后，回调的 `SdkThirdPartyLoadInfo.allowLoad` 变为 `false`，接入方直接跳过三方请求即可

## 环境要求

- Android Studio
- JDK 11+
- `minSdk` 29+ (Android 10.0)
- Kotlin 1.9.0+

## 版本

- 当前 SDK 版本：**1.3.0**（新增三方广告埋点上报 + 展示/点击日上限）
- 1.2.0：移除 Freelybase + 内置 BASE_URL + 新增 RSA+AES 可选加密通道
- Breaking Changes 见文末。

---

## 快速开始

### 0. 准备 appId

SDK 内置生产后端地址（`IgniteFusionApiClient.PROD_BASE_URL`）。接入方只需在管理后台创建应用并拿到 `appId`（对应 `apps.app_id`）。

如需本地联调（指向 `http://10.0.2.2:8000` 等测试地址），可在 library 中修改 `PROD_BASE_URL` 常量后重新编译 AAR。

后端广告匿名接口：
- **明文（默认）**：`GET {PROD_BASE_URL}/sdk/ad-unit/{ad_unit_id}`
- **加密（可选，推荐生产开启）**：`POST {PROD_BASE_URL}/sdk/ad-unit/e`，请求/响应均为 `EncEnvelope`（见下文「加密通道」）。

### 1. 添加权限（AndroidManifest.xml）

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- 如果是下载类广告，可按需加 -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

### 2. 添加依赖

#### 方式 A：源码依赖（推荐，当前仓库）

```kotlin
// settings.gradle.kts
include(":library")
project(":library").projectDir = file("$rootDir/../sdk/android/library")

// app/build.gradle.kts
dependencies {
    implementation(project(":library"))
}
```

#### 方式 B：JitPack 远程依赖（发布后）

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.Spark-Fusion:SparkFusionAD:1.2.0")
}
```

### 3. 初始化 SDK

包名：**`com.ignitefusion.ad.IgniteFusionAd`**（从 `1.1.0` 起包名固定为此）。

默认明文（向后兼容 1.1.x）：

```kotlin
import android.app.Application
import com.ignitefusion.ad.IgniteFusionAd

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        IgniteFusionAd.init(
            context = this,
            appId   = "你的应用 appId",
        )
    }
}
```

生产环境推荐开启加密（RSA-OAEP-SHA256 + AES-256-GCM，HTTPS 之上再加一层端到端加密）：

```kotlin
IgniteFusionAd.init(
    context   = this,
    appId     = "你的应用 appId",
    encrypted = true,   // 默认 false；true 时走 POST /sdk/ad-unit/e 加密通道
    debug     = false,  // 默认 false；联调可设 true 查看 SDK Log
)
```

也支持运行时切换模式（会清掉旧的 RSA 公钥缓存）：

```kotlin
import com.ignitefusion.ad.IgniteFusionApiClient

IgniteFusionApiClient.setEncrypted(true)   // 改为加密
IgniteFusionApiClient.setEncrypted(false)  // 切回明文 GET
```

并在 `AndroidManifest.xml` 中注册 `Application`（或使用 demo 里的 `AppContextHolder + SdkManager` 模式，原理一致）。

### 4. 加载并展示广告

SDK 统一遵循 **Load → Show** 两步流程。

#### 4.1 开屏广告

```kotlin
val adId = "6fa0f3cecf..."   // 后台创建的 AdUnit.ad_unit_id（或 AdUnit.id，均可）
IgniteFusionAd.loadIFSplashAd(
    context = this,
    adId = adId,
    loadThirdPartyAd = { info ->
        // enableSelfAd=false 时，SDK 会先上报「三方 load 事件」→ 校验 allowLoad →
        // 只有 allowLoad=true 才会回调到这里；接入方无需再写 if (!info.allowLoad) return。
        // （若上报失败，SDK 兜底仍会以 allowLoad=true 放行，避免因网络抖动影响广告填充）
        Log.d("Splash", "loadThirdParty counters loads=${info.loads} shows=${info.shows} clicks=${info.clicks}  remainingShows=${info.remainingShows} remainingClicks=${info.remainingClicks}")
        // TODO: 调用 csj.loadSplashAd(...) / gromore.loadSplashAd(...) 等
        //   —— 三方「请求成功、渲染成功」后：
        //      IgniteFusionAd.reportThirdPartyEvent(adId, ThirdPartyEventType.SHOW)
//   —— 用户点击三方广告后：
//      IgniteFusionAd.reportThirdPartyEvent(adId, ThirdPartyEventType.CLICK) { resp ->
        //          Log.d("Splash", "after click allowLoad=${resp.allowLoad} remaining=${resp.remaining}")
        //      }
    },
    listener = object : IgniteFusionAdLoadListener {
        override fun onAdLoadSuccess() {
            val container = findViewById<ViewGroup>(R.id.splash_container)
            IgniteFusionAd.showIFSplashAd(
                container,
                object : IgniteFusionAdShowListener {
                    override fun onAdShowSuccess() {}
                    override fun onAdShowFailure(msg: String) {}
                    override fun onAdClick() {}
                    override fun onAdClose() { /* jump to main */ }
                },
            )
        }
        override fun onAdLoadFailure(msg: String) {
            // 到达日上限 / 上报失败以外的其它原因都会走这里
        }
    },
)
```

> **三方埋点 & 上限控制规范**：
> 1. SDK 在 `dispatchThirdPartyFlow` 内会**自动上报 load 事件**一次，接入方只需在三方广告「真正展示」和「真正被点击」两个时机分别手动上报 `show` / `click`。事件名必须是小写的 `load` / `show` / `click`，其它值后端 `422` 拒绝。
> 2. **日上限由 SDK 兜底**：上报 load 后如果 `allowLoad=false`（展示或点击任一超限），SDK 直接走 `listener.onAdLoadFailure("…已达上限…")`，**不会回调** `loadThirdPartyAd`；只有 allowLoad=true 才回调接入方；因此接入方的 `loadThirdPartyAd` 内部无需再写 allowLoad 判断。
> 3. **网络异常兜底**：如果上报 load 请求失败，SDK 仍会以 `allowLoad=true` + counter/remaining=MAX 调 `loadThirdPartyAd`，优先保证填充不被网络抖动阻塞。

#### 4.2 Banner 广告

```kotlin
IgniteFusionAd.loadIFBannerAd(
    context = this,
    adId = "...",
    loadThirdPartyAd = { info ->
        // allowLoad 保证为 true（SDK 已在内部完成上限校验；失败兜底也放行）
        // TODO: csj.loadBannerAd(...)
        //   展示后 →  IgniteFusionAd.reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.SHOW)
        //   点击后 →  IgniteFusionAd.reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.CLICK)
    },
    listener = IgniteFusionAdLoadListener(
        onAdLoadSuccess = {
            IgniteFusionAd.showIFBannerAd(
                findViewById(R.id.banner_container),
                IgniteFusionAdShowListener(/* ... */),
            )
        },
        onAdLoadFailure = { /* 未启用/超限/已达上限等情况走这里 */ },
    ),
)
// 销毁
IgniteFusionAd.removeIFBannerAd(bannerContainer)
```

#### 4.3 插屏广告

```kotlin
IgniteFusionAd.loadIFInterstitialAd(
    context = this, adId = "...",
    loadThirdPartyAd = { info ->
        // TODO: csj.loadInterstitialAd(...)
        //   onAdShow  → reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.SHOW)
        //   onAdClick → reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.CLICK)
    },
    listener = IgniteFusionAdLoadListener(
        onAdLoadSuccess = {
            IgniteFusionAd.showIFInterstitialAd(
                this@MainActivity,
                IgniteFusionAdShowListener(/* ... */),
            )
        },
        onAdLoadFailure = { /* 未启用/超限/已达上限 */ },
    ),
)
```

#### 4.4 激励视频广告

```kotlin
IgniteFusionAd.loadIFVideoAd(
    context = this, adId = "...",
    loadThirdPartyAd = { info ->
        // TODO: csj.loadRewardVideoAd(...)
        //   onAdShow       → reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.SHOW)
        //   onAdClick      → reportThirdPartyEvent(info.adUnitId, ThirdPartyEventType.CLICK)
        //   onRewardVerify → 自行判断是否发奖（与 IgniteFusionAd 后端无关）
    },
    listener = IgniteFusionAdLoadListener(
        onAdLoadSuccess = {
            IgniteFusionAd.showIFVideoAd(
                activity = this@MainActivity,
                listener = object : IgniteFusionRewardAdShowListener {
                    override fun onAdShowSuccess() {}
                    override fun onAdShowFailure(msg: String) {}
                    override fun onAdClick() {}
                    override fun onAdClose() {}
                    override fun onReward() { /* 发奖 */ }
                },
            )
        },
        onAdLoadFailure = {},
    ),
)
```

---

## API 摘要

### 初始化

```kotlin
@JvmOverloads
fun init(
    context:   Context,
    appId:     String,
    encrypted: Boolean = false,
    debug:     Boolean = false,
)
```

| 参数 | 说明 |
| --- | --- |
| `context` | Application 或 Activity 上下文；内部会取 applicationContext |
| `appId` | 在管理后台创建应用后拿到的 `apps.app_id`；透传至请求头 `X-App-Id`，未启用前可传 `""` |
| `encrypted` | 可选，默认 `false`。`true` 时走 `POST /sdk/ad-unit/e` 加密通道，`false` 走原 `GET /sdk/ad-unit/{id}` 明文通道（向后兼容） |
| `debug` | 可选，默认 `false`。`true` 时输出 SDK 内部日志；`destroy()` 后关闭 |

后端生产地址内置于 `IgniteFusionApiClient.PROD_BASE_URL`，接入方无需额外传 `baseUrl`。
运行时可调用 `IgniteFusionApiClient.setEncrypted(Boolean)` 动态切换，切换后会清空 RSA 公钥缓存以重新拉取。

### 三方广告手动埋点（1.3.0 新增）

接入方在三方广告「真正展示」和「真正被点击」时，需要手动回调以下 API 把事件同步到 SparkFusion 后端，以便平台统计漏斗与日上限控制。

```kotlin
@JvmStatic
@JvmOverloads
fun reportThirdPartyEvent(
    adUnitId:  String,
    event:     ThirdPartyEventType,          // LOAD | SHOW | CLICK
    onSuccess: (SdkThirdPartyEventOut) -> Unit = {},
    onFailure: (Throwable) -> Unit              = {}
)
```

> ⚠️ **接入方仅需手动上报 `SHOW` 和 `CLICK`**：`LOAD` 事件由 SDK 在 `dispatchThirdPartyFlow` 内自动上报一次。重复上报 `LOAD` 会造成当日计数虚高，更快触达日上限。

```kotlin
enum class ThirdPartyEventType(val value: String) {
    LOAD("load"),     // 三方请求（SDK 自动上报）
    SHOW("show"),     // 三方展示成功（接入方在 onAdShow 里手动上报）
    CLICK("click");   // 三方被点击（接入方在 onAdClicked 里手动上报）
    companion object {
        fun from(raw: String?): ThirdPartyEventType? // 大小写不敏感，按 value 反查
    }
}
```

返回值 `SdkThirdPartyEventOut` 与「三方广告埋点接口」成功响应的 JSON 结构完全一致：

```kotlin
data class SdkThirdPartyEventOut(
    val allowLoad: Boolean,               // false=已达上限，不要再发三方请求
    val counters:  SdkThirdPartyCounters, // loads / shows / clicks 当日累计
    val limits:    SdkThirdPartyLimits,   // showLimit / clickLimit（0=不限）
    val remaining: SdkThirdPartyRemaining,// shows / clicks 当日剩余
    val reason:    String? = null
)
```

回调给接入方的 `SdkThirdPartyLoadInfo`（即 `loadThirdPartyAd = { info -> ... }`）是 SDK 自动上报完 `load` 事件后，把响应 + `adUnitId` 组合成的便捷包装，带两个布尔判断：

```kotlin
data class SdkThirdPartyLoadInfo(
    val adUnitId: String,
    val allowLoad: Boolean,
    val loads: Int, val shows: Int, val clicks: Int,
    val showLimit: Int, val clickLimit: Int,
    val remainingShows: Int, val remainingClicks: Int,
    val reason: String?
) {
    fun reachedShowLimit():  Boolean = showLimit  > 0 && remainingShows  <= 0
    fun reachedClickLimit(): Boolean = clickLimit > 0 && remainingClicks <= 0
}
```

### 后端接口约定

#### 明文版（encrypted=false，默认）

```
GET {PROD_BASE_URL}/sdk/ad-unit/{ad_unit_id}
```

#### 加密版（encrypted=true，推荐生产）

```
POST   {PROD_BASE_URL}/sdk/ad-unit/e
Header Content-Type: application/enc+json
Header Accept:       application/enc+json
```

请求与响应均为同一个信封结构（字段全 Base64 标准编码）：

```json
{
  "kid":           "<后端 /auth/public-key 返回的 kid>",
  "encryptedKey":  "BASE64( RSA-OAEP-SHA-256 加密后的 32B AES key )",
  "iv":            "BASE64( 12B AES-GCM nonce )",
  "payload":       "BASE64( AES-256-GCM(UTF-8 JSON || 16B Tag) )"
}
```

- 请求明文 JSON：`{ "ad_unit_id": "6fa0f3cecf" }`，由 SDK 自动封装后发出。
- 响应 nonce = 请求 nonce **逐字节 XOR 0x01**（保证 (key,nonce) 不重复，SDK 自动解）。
- 解密后的明文响应与下文「明文成功返回结构」完全相同。

拉取公钥（SDK 进程内带缓存自动调用，接入方不用管）：

```
GET  {PROD_BASE_URL}/auth/public-key
→ { "kid": "...", "publicPem": "-----BEGIN PUBLIC KEY-----\n...", "algorithm": "RS256" }
```

明文成功返回 / 加密版解密后的统一 JSON：

```json
{
  "ad_unit_id": "6fa0f3cecf",
  "name": "SDK开屏",
  "ad_type": "splash",
  "enable": true,
  "enableSelfAd": true,
  "ads": [
    {
      "id": "...",
      "appname": "SDK开屏广告",
      "content": "广告正文",
      "applogo":     { "url": "/uploads/logo.png" },
      "promotional": { "url": "/uploads/promo.png" },
      "video":       { "url": "/uploads/demo.mp4" },
      "themeColor": "#FF5500",
      "applicationId": "<apps.id>",
      "version": "1.0.0",
      "isDownload": true,
      "url": "https://example.com/download",
      "weight": 5
    }
  ]
}
```

语义：

- `enable=false`：广告位未启用或审核未通过 → SDK 直接回调 `onAdLoadFailure`。
- `enableSelfAd=false`：广告位开关设置为走三方 → SDK 回调接入方的 `loadThirdPartyAd`，随后 `onAdLoadFailure`。
- `ads[i].applogo / promotional / video`：后端只存相对路径（`/uploads/xxx`），SDK 内部 `IgniteFusionApiClient.resolveMediaUrl()` 统一补全为 `{PROD_BASE_URL}/uploads/xxx`；若后端返回 `http(s)://` 绝对地址则直接透传。
- `weight`：整数，越大权重越高；SDK 在 `load*()` 阶段按权重抽取一条。

### 三方广告埋点接口（1.3.0 新增）

后端提供 `/sdk/third-party/event` 统一上报三方广告的**请求、展示、点击**三类事件，并返回该广告位的**当日上限 / 剩余额度**。SDK 已在 `dispatchThirdPartyFlow` 中**自动封装上报一次 `ThirdPartyEventType.LOAD`**，接入方只需在「真正展示 / 真正点击」时调用 `IgniteFusionAd.reportThirdPartyEvent(adId, ThirdPartyEventType.SHOW / CLICK)` 即可。

#### 请求（加密 / 明文二选一，与 ad-unit 一致，SDK 自动选择）

```
POST  {PROD_BASE_URL}/sdk/third-party/event
Header X-App-Id:     {appId}
Header Content-Type: application/json        (明文)
                   | application/enc+json    (加密，信封结构与 ad-unit/e 完全一致)
Header Accept:       application/json
                   | application/enc+json
Body (JSON 明文 / 加密后的明文):
{
  "ad_unit_id": "6fa0f3cecf",
  "event":       "load" | "show" | "click"   // ThirdPartyEventType.LOAD/SHOW/CLICK 的 value
}
```

字段含义：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `ad_unit_id` | ✅ | 广告位 ID（与 `AdUnit.ad_unit_id` 或 `AdUnit.id` 一致，后端都能解析） |
| `event` | ✅ | 必须是小写的 `"load"` / `"show"` / `"click"`，对应 `ThirdPartyEventType.LOAD/SHOW/CLICK.value`；其它值 `422`。**注意**：`LOAD` 由 SDK 在 `dispatchThirdPartyFlow` 内自动上报一次，接入方不要重复上报 |

#### 成功响应（`SdkThirdPartyEventOut`）

```json
{
  "allowLoad": true,
  "counters":  { "loads": 101, "shows": 97, "clicks": 3 },
  "limits":    { "showLimit": 5000,          "clickLimit": 200 },
  "remaining": { "shows": 4903,              "clicks": 197 },
  "reason":    null
}
```

| 字段 | 说明 |
| --- | --- |
| `allowLoad` | 允许继续请求三方？`true`=未达上限，可以请求；`false`=今日已达到展示或点击上限，建议直接跳过 |
| `counters.loads/shows/clicks` | 该广告位**当日（UTC 日）**累计上报数 |
| `limits.showLimit / clickLimit` | 后台 `AdUnit.third_party_show_limit` / `third_party_click_limit`；**`0` 表示不限** |
| `remaining.shows / clicks` | 今日还剩多少额度（`limit > 0` 时有效；limit=0 时该值为一个很大的常量，可以忽略） |
| `reason` | 当 `allowLoad=false` 时给出的简要原因（如 `"show_limit_reached"` / `"click_limit_reached"`）；否则 `null` |

> **限制的判定逻辑（后端）**：当且仅当 `showLimit > 0 且 shows >= showLimit` 或 `clickLimit > 0 且 clicks >= clickLimit` 其中一条满足时，`allowLoad=false`；否则 `allowLoad=true`。两者都是 `0` = 完全不限量。

---

## 项目结构

```
sdk/android/
├── app/                    # Demo App（含 Common.kt 中的 BASE_URL + ad_unit_id 常量）
└── library/                # SDK AAR
    ├── build.gradle.kts
    └── src/main/java/com/ignitefusion/ad/
        ├── IgniteFusionAd.kt              # 入口：init + load*/show* + reportThirdPartyEvent
        ├── IgniteFusionApiClient.kt       # OkHttp+Gson：广告位拉取 / 埋点（公开）
        ├── IgniteFusionAdListener.kt
        ├── IgniteFusionThirdPartyEvent.kt
        ├── model/                         # AdSpaceResult / Addata / IgniteFusionAdData
        ├── network/                       # IgniteFusionCipher（RSA+AES）
        ├── ui/                            # Splash/Banner/Insert/Reward + RewardVideoActivity
        └── util/                          # IgniteFusionLog（debug 开关）
```

---

## 加密通道说明（1.2.0 新增）

为减少「端侧 HTTPS 被中间人代理」的风险，1.2.0 起 SDK 提供**可选**的第二层加密：**RSA 2048 / OAEP-SHA-256 + AES-256-GCM**。默认关闭（明文 GET），生产环境推荐开启。

### 流程（SDK 内部自动完成）

1. `init(ctx, appId, encrypted=true)` 或 `setEncrypted(true)`。
2. SDK 首次请求广告前，阻塞调用 `GET /auth/public-key` 拉 `{kid, publicPem}`，进程内缓存（带并发锁）。
3. 每次 `load*Ad` 走加密：
   - 生成**一次性** 32B AES key + 12B nonce。
   - RSA-OAEP-256 加密 AES key → `encryptedKey`。
   - AES-256-GCM 加密 `{ad_unit_id}` JSON → `payload`（末尾自带 16B GCM Tag）。
   - 组装 `EncEnvelope { kid, encryptedKey, iv, payload }`，`Content-Type: application/enc+json` 发出。
4. 后端自解包，生成业务响应（JSON）后**用同一个 AES key + `nonce XOR 0x01`** 重新 AES-GCM 加密返回 `application/enc+json`。
5. SDK 按 `nonce XOR 0x01` 解密得到明文 `AdSpaceResult`。

> 注：`(AES key, nonce)` 只用一次；请求用 `N`，响应用 `N ^ 0x01`，严格避免 GCM nonce 重用。

### 降级行为

如果开启了 encrypted，但后端某次返回 Content-Type 是普通 `application/json`（例如本地调试没装加密中间件），SDK 会**自动降级**按明文解析 `AdSpaceResult`，不会抛错。因此代码可以先 `encrypted=true`，服务端再逐步灰度打开。

### 依赖

- 加密完全基于 Android/Java 标准库：
  - `javax.crypto.Cipher`（`RSA/ECB/OAEPWithSHA-256AndMGF1Padding` + 显式 `OAEPParameterSpec(MGF1-SHA256)`，避开旧机型 MGF1-SHA-1 兼容坑）
  - `AES/GCM/NoPadding` + `GCMParameterSpec(128, 12B nonce)`（128 位 tag）
  - `android.util.Base64`
- **无需** 引入 BouncyCastle / Conscrypt；Gradle 无新增传递依赖。

---

## 依赖库（传递依赖，接入方无需重复声明）

- `androidx.media3:media3-exoplayer:1.9.4` / `media3-ui:1.9.4`
- `com.squareup.okhttp3:okhttp:4.12.0` + `logging-interceptor`
- `com.squareup.okio:okio:3.6.0`
- `com.google.code.gson:gson:2.10.1`
- AndroidX core-ktx / appcompat / Material

## Breaking Changes（1.0.2 → 1.1.0）

1. **初始化签名变更**：`init(context, appKey)` → `init(context, appId)`。
   - 第二参名称与含义明确为「后台创建应用后拿到的 `apps.app_id`」，请求头从 `X-App-Key` 改为 `X-App-Id`。
   - 生产 BASE_URL 已内置于 library（`IgniteFusionApiClient.PROD_BASE_URL`），**接入方不再需要传 `baseUrl`**；本地联调请修改该常量重新编译 AAR。
2. **包名统一**：SDK 顶层包固定为 `com.ignitefusion.ad`（入口类 `com.ignitefusion.ad.IgniteFusionAd`），示例与 AAR 发布均以此为准。
3. **移除 Freelybase 依赖**：`com.github.freelybase:freelybase-android` 已从 `library` 中移除，不再需要 `Freelybase.initialize(context, appKey)`。
4. **审核未通过/未启用**的广告位，接口正常 `200` 但 `enable=false` + `ads=[]`；SDK 按 `onAdLoadFailure` 处理，而不是 `404`。只有 `ad_unit_id` 真实不存在时才返回 `404`。

## 升级说明（1.1.0 → 1.2.0，非破坏性）

1.2.0 全部 API 向下兼容 1.1.0，旧代码直接升级即可；推荐按以下步骤开启加密：

| 项 | 1.1.0 | 1.2.0 |
| --- | --- | --- |
| 初始化签名 | `init(ctx, appId)` | **保留**；新增 `@JvmOverloads init(ctx, appId, encrypted=false)` |
| 默认请求通道 | `GET /sdk/ad-unit/{id}` 明文 | **不变**（`encrypted=false` 仍走此通道） |
| 可选加密通道 | — | `POST /sdk/ad-unit/e`，`Content-Type/Accept: application/enc+json`，`EncEnvelope{kid,encryptedKey,iv,payload}` |
| 公钥分发 | — | `GET /auth/public-key` 返回 `{kid, publicPem, algorithm=RS256}`，SDK 进程内缓存 + pkLock 并发锁 |
| 新增文件 | — | `IgniteFusionCipher.kt`（JCA 原生 RSA+AES 工具，零额外依赖） |
| JitPack 依赖版本 | `1.1.0` | `1.2.0` |

推荐接入：

```kotlin
IgniteFusionAd.init(this, appId,
  encrypted = BuildConfig.BUILD_TYPE != "debug",   // Release 加密，Debug 明文便于抓包
)
```

## 升级说明（1.2.0 → 1.3.0，非破坏性）

1.3.0 全部 API 向下兼容 1.2.0。旧代码无需任何改动即可运行；如果接入方使用了「后台广告位 enableSelfAd=false → 回调 loadThirdPartyAd 接入穿山甲/Gromore 等」的路径，强烈建议补上 `show` / `click` 埋点上报，后端才能按日控制展示/点击上限：

| 项 | 1.2.0 | 1.3.0 |
| --- | --- | --- |
| 4 种广告位 `load*Ad` 签名 | `loadIFSplashAd(ctx, adId, listener)` | **保留**；新增可选参数 `loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)? = null`（Banner/插屏/激励视频同样新增） |
| `loadThirdPartyAd` 回调签名 | `{ }`（裸 lambda，无参） | `{ info: SdkThirdPartyLoadInfo -> ... }`，info 带 allowLoad / counters / limits / remaining |
| 三方埋点能力 | 无，仅靠接入方自行打印日志 | `IgniteFusionAd.reportThirdPartyEvent(adUnitId, ThirdPartyEventType.SHOW/CLICK)` 手动补报展示/点击；`LOAD` SDK 在回调 `loadThirdPartyAd` 前自动上报 |
| 日上限控制 | 无 | 后台 `AdUnit.third_party_show_limit` / `third_party_click_limit`（0=不限），达到后 `allowLoad=false` |
| 新增后端接口 | — | `POST /sdk/third-party/event`（明文/加密双通道，与 ad-unit 自动对齐） |
| 新增文件 | — | `IgniteFusionThirdPartyEvent.kt`（`ThirdPartyEventType` 枚举 + `SdkThirdPartyLoadInfo` / `EventOut` / `Counters` / `Limits` / `Remaining`） |
| JitPack 依赖版本 | `1.2.0` | `1.3.0` |

推荐接入（最小改动）：

```kotlin
// 只需要：在原来 4 个 load*Ad 里加 loadThirdPartyAd 参数，
//         在三方 onAdShow / onAdClick 里分别 report show / click 事件。
// SDK 已先保证：达到上限时自动 onAdLoadFailure，不会回调这里。
IgniteFusionAd.loadIFSplashAd(
    context = this,
    adId = splashAdUnitId,
    loadThirdPartyAd = { info ->
        // 调用原来的三方 SDK 加载逻辑……（无需再判断 info.allowLoad）
    },
    listener = object : IgniteFusionAdLoadListener { /* 不变 */ },
)
```

## 注意事项

1. **后端地址可从设备访问**：生产 `PROD_BASE_URL` 域名证书要在 Android 信任链中；本地联调请在 library 修改 `PROD_BASE_URL`（模拟器用 `http://10.0.2.2:<port>`，真机需内网穿透/局域网 IP）后重新编译 AAR。
2. **媒体路径拼接**：所有 `/uploads/*` 路径，SDK 会自动拼 baseUrl；请不要在数据库里写入带域名的完整 URL（后端工程规范）。
3. **激励视频必填 `video`**：若抽到的广告 `video.url` 为空，SDK 会回调 `onAdLoadFailure`（旧行为保持不变）。
4. **所有 API 调用建议在主线程进行**；内部 OkHttp 请求为异步，主线程回调。
5. **退出时释放资源**：应用退出时调用 `IgniteFusionAd.destroy()`。
6. **三方 `LOAD` 事件不要重复上报**：SDK 在 `dispatchThirdPartyFlow` 里已自动触发一次 `reportThirdPartyEvent(adUnitId, ThirdPartyEventType.LOAD)`，接入方如再手动上报会导致「当日 loads 虚高 → allowLoad 提前变为 false」。仅需手动上报 **SHOW**（三方广告渲染回调，如 Pangle 的 `onAdShow`）和 **CLICK**（`onAdClicked`）。
7. **SDK 已提前做 allowLoad 判定**：`dispatchThirdPartyFlow` 在上报 load 事件的成功回调里，如果 `allowLoad=false` 会直接走 `listener.onAdLoadFailure("…已达上限…")`，**不会调用** `loadThirdPartyAd`；只有 allowLoad=true 或上报失败兜底放行时才回调，因此 `loadThirdPartyAd` 内部**无需**再写 `if (!info.allowLoad) return`。
8. **日上限以 UTC 自然日重置**：后端统计维度是 UTC day。若业务按北京时间 0 点清 0，可在后台把 limit 设得略大一点；如需切换时区，请在后端部署时调整 `TZ` 环境变量（不影响 SDK 逻辑）。
9. **`limit = 0` 代表不限**：如果后台把 `third_party_show_limit` 或 `third_party_click_limit` 设成 0，SDK 在 `allowLoad` 会一直返回 `true`，`remaining` 字段无意义可忽略。

---

IgniteFusionAd SDK - 先 Load 后 Show，让广告集成更简单 🚀
