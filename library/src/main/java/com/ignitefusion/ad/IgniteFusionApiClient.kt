package com.ignitefusion.ad

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ignitefusion.ad.model.AdSpaceResult
import com.ignitefusion.ad.network.EncEnvelope
import com.ignitefusion.ad.network.PublicKeyBundle
import com.ignitefusion.ad.network.decryptEnvelopeTo
import com.ignitefusion.ad.network.encryptJsonToEnvelope
import com.ignitefusion.ad.network.publicKeyBundleFromJson
import com.ignitefusion.ad.util.IgniteFusionLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object IgniteFusionApiClient {

    private const val TAG = "IgniteFusionApi"
    private const val DEFAULT_TIMEOUT_SECONDS = 15L
    const val PROD_BASE_URL: String = "http://www.ignitefusion.cn/api"

    private val ENC_MEDIA_TYPE = "application/enc+json; charset=utf-8".toMediaType()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var baseUrl: String = ""

    @Volatile
    private var appId: String = ""

    @Volatile
    private var encrypted: Boolean = false

    @Volatile
    private var pkCache: PublicKeyBundle? = null

    private val pkLock = Any()

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private inline fun <T> runOnMain(crossinline block: () -> T) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    private inline fun <T> callSuccess(
        crossinline onSuccess: (T) -> Unit,
        value: T
    ) {
        runOnMain { onSuccess(value) }
    }

    private inline fun callFailure(
        crossinline onFailure: (Throwable) -> Unit,
        throwable: Throwable
    ) {
        runOnMain { onFailure(throwable) }
    }

    private val gson: Gson by lazy {
        GsonBuilder()
            .serializeNulls()
            .create()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @JvmStatic
    fun configure(appId: String) {
        configure(appId, false)
    }

    @JvmStatic
    fun configure(appId: String, encrypted: Boolean) {
        val trimmed = PROD_BASE_URL.trim()
        this.baseUrl = if (trimmed.endsWith("/")) trimmed.dropLast(1) else trimmed
        this.appId = appId.trim()
        this.encrypted = encrypted
        IgniteFusionLog.d(TAG, "configure: baseUrl=${this.baseUrl}, appId=${if (this.appId.isBlank()) "(empty)" else "***"}, encrypted=$encrypted")
    }

    fun isConfigured(): Boolean = baseUrl.isNotBlank()

    fun isEncrypted(): Boolean = encrypted

    fun setEncrypted(enabled: Boolean) {
        encrypted = enabled
        if (!enabled) pkCache = null
        IgniteFusionLog.d(TAG, "encrypted mode -> $enabled")
    }

    fun getBaseUrl(): String = baseUrl

    fun getAppId(): String = appId

    fun resolveMediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://", true) || path.startsWith("https://", true)) {
            val uri = android.net.Uri.parse(path)
            val host = uri.host?.lowercase()
            val mediaPath = uri.encodedPath
            if (
                (host == "localhost" || host == "127.0.0.1" || host == "::1") &&
                !mediaPath.isNullOrBlank() &&
                mediaPath.startsWith("/uploads/")
            ) {
                val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
                return "$baseUrl$mediaPath$query"
            }
            return path
        }
        val prefix = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl$prefix"
    }

    private fun fetchPublicKeyBlocking(): PublicKeyBundle {
        pkCache?.let { return it }
        synchronized(pkLock) {
            pkCache?.let { return it }
            val url = "$baseUrl/auth/public-key"
            IgniteFusionLog.d(TAG, "fetchPublicKeyBlocking from $url")
            val req = Request.Builder().url(url).get().build()
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = "HTTP ${resp.code}: $body"
                    IgniteFusionLog.e(TAG, "fetchPublicKey http error: $msg")
                    error(msg)
                }
                val bundle = publicKeyBundleFromJson(body)
                pkCache = bundle
                IgniteFusionLog.d(TAG, "fetchPublicKeyBlocking success kid=${bundle.kid}, pem size=${bundle.publicPem.length}")
                return bundle
            }
        }
    }

    fun fetchAdSpace(
        adUnitId: String,
        onSuccess: (AdSpaceResult) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        if (!isConfigured()) {
            val error = IllegalStateException("SDK 未初始化：请先调用 IgniteFusionAd.init(context, appId)")
            IgniteFusionLog.e(TAG, "fetchAdSpace blocked: ${error.message}")
            callFailure(onFailure, error)
            return
        }
        if (adUnitId.isBlank()) {
            val error = IllegalArgumentException("adUnitId 不能为空")
            IgniteFusionLog.e(TAG, "fetchAdSpace blocked: ${error.message}")
            callFailure(onFailure, error)
            return
        }

        if (encrypted) {
            fetchAdSpaceEncrypted(adUnitId, onSuccess, onFailure)
        } else {
            fetchAdSpacePlain(adUnitId, onSuccess, onFailure)
        }
    }

    private fun fetchAdSpacePlain(
        adUnitId: String,
        onSuccess: (AdSpaceResult) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val url = "$baseUrl/sdk/ad-unit/${java.net.URLEncoder.encode(adUnitId, "UTF-8")}"
        IgniteFusionLog.d(TAG, "fetchAdSpace[plain] start, adUnitId=$adUnitId, url=$url")
        val requestBuilder = Request.Builder().url(url).get()
        if (appId.isNotBlank()) requestBuilder.header("X-App-Id", appId)
        val request = requestBuilder.build()
        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                IgniteFusionLog.e(TAG, "fetchAdSpace[plain] io error, adUnitId=$adUnitId", e)
                callFailure(onFailure, e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                handlePlainResponse(adUnitId, response, onSuccess, onFailure)
            }
        })
    }

    private fun fetchAdSpaceEncrypted(
        adUnitId: String,
        onSuccess: (AdSpaceResult) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val url = "$baseUrl/sdk/ad-unit/e"
        IgniteFusionLog.d(TAG, "fetchAdSpace[enc] start, adUnitId=$adUnitId, url=$url")
        okHttpClient.dispatcher.executorService.execute {
            val pk = runCatching { fetchPublicKeyBlocking() }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "fetchAdSpace[enc] fetchPublicKey failed", e)
                callFailure(onFailure, e)
                return@execute
            }
            val payloadObj = mapOf("ad_unit_id" to adUnitId)
            val encResult = runCatching { encryptJsonToEnvelope(pk, payloadObj) }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "fetchAdSpace[enc] encrypt envelope failed", e)
                callFailure(onFailure, e)
                return@execute
            }
            val reqBody = gson.toJson(encResult.envelope).toRequestBody(ENC_MEDIA_TYPE)
            val reqBuilder = Request.Builder().url(url).post(reqBody)
                .header("Accept", "application/enc+json")
            if (appId.isNotBlank()) reqBuilder.header("X-App-Id", appId)
            try {
                okHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val e = IllegalStateException("HTTP ${resp.code}: $raw")
                        IgniteFusionLog.e(TAG, "fetchAdSpace[enc] http error adUnitId=$adUnitId code=${resp.code}")
                        callFailure(onFailure, e)
                        return@use
                    }
                    val ct = resp.header("Content-Type", "")?.lowercase().orEmpty()
                    if (!ct.contains("application/enc+json")) {
                        // 降级明文
                        val plain = runCatching { gson.fromJson(raw, AdSpaceResult::class.java) }.getOrElse { e ->
                            IgniteFusionLog.e(TAG, "fetchAdSpace[enc] downgrade plain parse fail: $raw", e)
                            throw e
                        }
                        IgniteFusionLog.d(TAG, "fetchAdSpace[enc] server returned plain, fallback ok enable=${plain.enable}")
                        callSuccess(onSuccess, plain)
                        return@use
                    }
                    val envelope = runCatching { gson.fromJson(raw, EncEnvelope::class.java) }.getOrElse { e ->
                        IgniteFusionLog.e(TAG, "fetchAdSpace[enc] envelope parse fail: $raw", e)
                        callFailure(onFailure, e)
                        return@use
                    }
                    val result: AdSpaceResult = runCatching {
                        decryptEnvelopeTo<AdSpaceResult>(encResult.aesKey, encResult.nonce, envelope)
                    }.getOrElse { e ->
                        IgniteFusionLog.e(TAG, "fetchAdSpace[enc] decrypt envelope fail", e)
                        callFailure(onFailure, e)
                        return@use
                    }
                    IgniteFusionLog.d(TAG, "fetchAdSpace[enc] success adUnitId=$adUnitId enable=${result.enable}, enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}")
                    callSuccess(onSuccess, result)
                }
            } catch (e: IOException) {
                IgniteFusionLog.e(TAG, "fetchAdSpace[enc] io error adUnitId=$adUnitId", e)
                callFailure(onFailure, e)
            } catch (t: Throwable) {
                IgniteFusionLog.e(TAG, "fetchAdSpace[enc] error adUnitId=$adUnitId", t)
                callFailure(onFailure, t)
            }
        }
    }

    private fun handlePlainResponse(
        adUnitId: String,
        response: okhttp3.Response,
        onSuccess: (AdSpaceResult) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            val raw = response.body?.string().orEmpty()
            val httpCode = response.code
            IgniteFusionLog.d(TAG, "fetchAdSpace[plain] response, adUnitId=$adUnitId, code=$httpCode, contentLen=${raw.length}, raw-preview=${raw.take(120).replace("\n", "\\n")}")
            if (!response.isSuccessful) {
                val error = IllegalStateException("HTTP ${response.code}: $raw")
                IgniteFusionLog.e(TAG, "fetchAdSpace[plain] http error, adUnitId=$adUnitId, code=$httpCode, raw=$raw")
                callFailure(onFailure, error)
                return
            }
            val result = runCatching {
                gson.fromJson(raw, AdSpaceResult::class.java)
            }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "fetchAdSpace[plain] parse error, adUnitId=$adUnitId, raw=$raw", e)
                throw e
            }
            IgniteFusionLog.d(TAG,
                "fetchAdSpace[plain] success, adUnitId=$adUnitId, enable=${result.enable}, " +
                    "enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}"
            )
            callSuccess(onSuccess, result)
        } catch (e: Throwable) {
            IgniteFusionLog.e(TAG, "fetchAdSpace[plain] handle failed, adUnitId=$adUnitId: ${e.message}", e)
            callFailure(onFailure, e)
        } finally {
            runCatching { response.close() }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun reportThirdPartyEvent(
        adUnitId: String,
        event: ThirdPartyEventType,
        onSuccess: (SdkThirdPartyEventOut) -> Unit = {},
        onFailure: (Throwable) -> Unit              = {}
    ) {
        if (!isConfigured()) {
            val error = IllegalStateException("SDK 未初始化：请先调用 IgniteFusionAd.init(context, appId)")
            IgniteFusionLog.e(TAG, "reportThirdPartyEvent blocked: ${error.message}")
            callFailure(onFailure, error)
            return
        }
        val eventValue = event.value
        if (encrypted) {
            reportThirdPartyEventEncrypted(adUnitId, eventValue, onSuccess, onFailure)
        } else {
            reportThirdPartyEventPlain(adUnitId, eventValue, onSuccess, onFailure)
        }
    }

    @JvmStatic
    @Deprecated(
        message = "Use reportThirdPartyEvent(String, ThirdPartyEventType, ...) instead",
        replaceWith = ReplaceWith(
            "reportThirdPartyEvent(adUnitId, ThirdPartyEventType.from(event) ?: error(\"bad event\"), onSuccess, onFailure)"
        )
    )
    fun reportThirdPartyEvent(
        adUnitId: String,
        event: String,
        onSuccess: (SdkThirdPartyEventOut) -> Unit = {},
        onFailure: (Throwable) -> Unit              = {}
    ) {
        val t = ThirdPartyEventType.from(event)
        if (t == null) {
            val e = IllegalArgumentException("非法 event=$event，仅允许 load/show/click")
            IgniteFusionLog.e(TAG, "reportThirdPartyEvent bad param: ${e.message}")
            callFailure(onFailure, e)
            return
        }
        reportThirdPartyEvent(adUnitId, t, onSuccess, onFailure)
    }

    private fun reportThirdPartyEventPlain(
        adUnitId: String,
        event: String,
        onSuccess: (SdkThirdPartyEventOut) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val url = "$baseUrl/sdk/third-party/event"
        IgniteFusionLog.d(TAG, "reportThirdPartyEvent[plain] adUnitId=$adUnitId event=$event")
        val bodyObj = mapOf("ad_unit_id" to adUnitId, "event" to event)
        val requestBody = gson.toJson(bodyObj).toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder().url(url).post(requestBody)
        if (appId.isNotBlank()) builder.header("X-App-Id", appId)
        okHttpClient.newCall(builder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                IgniteFusionLog.e(TAG, "reportThirdPartyEvent[plain] io error adUnitId=$adUnitId event=$event", e)
                callFailure(onFailure, e)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val e = IllegalStateException("HTTP ${response.code}: $raw")
                        IgniteFusionLog.e(TAG, "reportThirdPartyEvent[plain] HTTP ${response.code} adUnitId=$adUnitId event=$event raw=$raw")
                        callFailure(onFailure, e)
                        return
                    }
                    val out = gson.fromJson(raw, SdkThirdPartyEventOut::class.java)
                    IgniteFusionLog.d(TAG, "reportThirdPartyEvent[plain] success adUnitId=$adUnitId event=$event allowLoad=${out.allowLoad} counters=${out.counters} remaining=${out.remaining}")
                    callSuccess(onSuccess, out)
                } catch (t: Throwable) {
                    IgniteFusionLog.e(TAG, "reportThirdPartyEvent[plain] parse/handle error adUnitId=$adUnitId event=$event", t)
                    callFailure(onFailure, t)
                } finally {
                    runCatching { response.close() }
                }
            }
        })
    }

    private fun reportThirdPartyEventEncrypted(
        adUnitId: String,
        event: String,
        onSuccess: (SdkThirdPartyEventOut) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val url = "$baseUrl/sdk/third-party/event"
        IgniteFusionLog.d(TAG, "reportThirdPartyEvent[enc] adUnitId=$adUnitId event=$event")
        okHttpClient.dispatcher.executorService.execute {
            val pk = runCatching { fetchPublicKeyBlocking() }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] fetchPublicKey failed adUnitId=$adUnitId", e)
                callFailure(onFailure, e)
                return@execute
            }
            val payloadObj = mapOf("ad_unit_id" to adUnitId, "event" to event)
            val encResult = runCatching { encryptJsonToEnvelope(pk, payloadObj) }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] encrypt envelope failed adUnitId=$adUnitId event=$event", e)
                callFailure(onFailure, e)
                return@execute
            }
            val reqBody = gson.toJson(encResult.envelope).toRequestBody(ENC_MEDIA_TYPE)
            val builder = Request.Builder().url(url).post(reqBody)
                .header("Accept", "application/enc+json")
            if (appId.isNotBlank()) builder.header("X-App-Id", appId)
            try {
                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val e = IllegalStateException("HTTP ${resp.code}: $raw")
                        IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] HTTP ${resp.code} adUnitId=$adUnitId event=$event raw=$raw")
                        callFailure(onFailure, e)
                        return@use
                    }
                    val ct = resp.header("Content-Type", "")?.lowercase().orEmpty()
                    if (!ct.contains("application/enc+json")) {
                        val out = runCatching { gson.fromJson(raw, SdkThirdPartyEventOut::class.java) }.getOrElse { e ->
                            IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] plain parse fail adUnitId=$adUnitId raw=$raw", e)
                            callFailure(onFailure, e)
                            return@use
                        }
                        IgniteFusionLog.d(TAG, "reportThirdPartyEvent[enc] server returned plain fallback adUnitId=$adUnitId allowLoad=${out.allowLoad}")
                        callSuccess(onSuccess, out)
                        return@use
                    }
                    val envelope = runCatching { gson.fromJson(raw, EncEnvelope::class.java) }.getOrElse { e ->
                        IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] envelope parse fail adUnitId=$adUnitId raw=$raw", e)
                        callFailure(onFailure, e)
                        return@use
                    }
                    val out = runCatching {
                        decryptEnvelopeTo<SdkThirdPartyEventOut>(encResult.aesKey, encResult.nonce, envelope)
                    }.getOrElse { e ->
                        IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] decrypt envelope fail adUnitId=$adUnitId event=$event", e)
                        callFailure(onFailure, e)
                        return@use
                    }
                    IgniteFusionLog.d(TAG, "reportThirdPartyEvent[enc] success adUnitId=$adUnitId event=$event allowLoad=${out.allowLoad} counters=${out.counters} remaining=${out.remaining}")
                    callSuccess(onSuccess, out)
                }
            } catch (e: IOException) {
                IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] io error adUnitId=$adUnitId event=$event", e)
                callFailure(onFailure, e)
            } catch (t: Throwable) {
                IgniteFusionLog.e(TAG, "reportThirdPartyEvent[enc] error adUnitId=$adUnitId event=$event", t)
                callFailure(onFailure, t)
            }
        }
    }

    fun reportSelfEvent(adUnitId: String, event: String) {
        if (!isConfigured() || adUnitId.isBlank()) {
            IgniteFusionLog.w(TAG, "reportSelfEvent skipped, configured=${isConfigured()} adUnitId=$adUnitId event=$event")
            return
        }
        if (event != "show" && event != "click") {
            IgniteFusionLog.w(TAG, "reportSelfEvent skipped, illegal event=$event")
            return
        }
        if (encrypted) {
            reportSelfEventEncrypted(adUnitId, event)
        } else {
            reportSelfEventPlain(adUnitId, event)
        }
    }

    private fun reportSelfEventPlain(adUnitId: String, event: String) {
        val url = "$baseUrl/sdk/event"
        val bodyObj = mapOf("ad_unit_id" to adUnitId, "event" to event)
        val requestBody = gson.toJson(bodyObj).toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder().url(url).post(requestBody)
        if (appId.isNotBlank()) builder.header("X-App-Id", appId)
        okHttpClient.newCall(builder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                IgniteFusionLog.e(TAG, "reportSelfEvent[plain] io error adUnitId=$adUnitId event=$event", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        IgniteFusionLog.e(TAG, "reportSelfEvent[plain] HTTP ${it.code} adUnitId=$adUnitId event=$event raw=$raw")
                        return
                    }
                    IgniteFusionLog.d(TAG, "reportSelfEvent[plain] success adUnitId=$adUnitId event=$event raw=$raw")
                }
            }
        })
    }

    private fun reportSelfEventEncrypted(adUnitId: String, event: String) {
        val url = "$baseUrl/sdk/event"
        okHttpClient.dispatcher.executorService.execute {
            val pk = runCatching { fetchPublicKeyBlocking() }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "reportSelfEvent[enc] fetchPublicKey failed adUnitId=$adUnitId", e)
                return@execute
            }
            val payloadObj = mapOf("ad_unit_id" to adUnitId, "event" to event)
            val encResult = runCatching { encryptJsonToEnvelope(pk, payloadObj) }.getOrElse { e ->
                IgniteFusionLog.e(TAG, "reportSelfEvent[enc] encrypt failed adUnitId=$adUnitId event=$event", e)
                return@execute
            }
            val reqBody = gson.toJson(encResult.envelope).toRequestBody(ENC_MEDIA_TYPE)
            val builder = Request.Builder().url(url).post(reqBody)
                .header("Accept", "application/enc+json")
            if (appId.isNotBlank()) builder.header("X-App-Id", appId)
            try {
                okHttpClient.newCall(builder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        IgniteFusionLog.e(TAG, "reportSelfEvent[enc] HTTP ${resp.code} adUnitId=$adUnitId event=$event raw=$raw")
                        return@use
                    }
                    IgniteFusionLog.d(TAG, "reportSelfEvent[enc] success adUnitId=$adUnitId event=$event")
                }
            } catch (t: Throwable) {
                IgniteFusionLog.e(TAG, "reportSelfEvent[enc] error adUnitId=$adUnitId event=$event", t)
            }
        }
    }
}
