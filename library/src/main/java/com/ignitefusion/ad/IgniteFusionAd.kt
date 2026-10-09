package com.ignitefusion.ad

import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.ignitefusion.ad.model.AdSpaceResult
import com.ignitefusion.ad.model.Addata
import com.ignitefusion.ad.model.IgniteFusionAdData
import com.ignitefusion.ad.ui.IgniteFusionBannerAD
import com.ignitefusion.ad.ui.IgniteFusionInsertAD
import com.ignitefusion.ad.ui.IgniteFusionRewardVideoActivity
import com.ignitefusion.ad.ui.IgniteFusionSplashAD
import com.ignitefusion.ad.util.IgniteFusionLog
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

object IgniteFusionAd {

    private const val TAG = "IgniteFusionAd"
    private const val SHAKE_THRESHOLD_GRAVITY = 2.2f
    private const val SHAKE_DEBOUNCE_MILLIS = 1000L
    private const val INTERSTITIAL_AUTO_CLOSE_MILLIS = 15_000L
    private const val REWARD_VIDEO_DURATION_SECONDS = 30
    private const val REWARD_EARNED_SECONDS = 20

    private var isInitAd = false
    private var appContext: Context? = null
    private var splashTimer: CountDownTimer? = null
    private var splashSensorManager: SensorManager? = null
    private var splashShakeListener: SensorEventListener? = null
    private var lastShakeTriggerAt = 0L
    private var splashActionTriggered = false

    private var currentSplashAd: LoadedAd? = null
    private var currentBannerAd: LoadedAd? = null
    private var currentInsertAd: LoadedAd? = null
    private var currentRewardAd: LoadedRewardAd? = null

    private enum class AdSource {
        SELF,
        THIRD_PARTY
    }

    private var splashAdSource: AdSource = AdSource.SELF
    private var bannerAdSource: AdSource = AdSource.SELF
    private var insertAdSource: AdSource = AdSource.SELF
    private var rewardAdSource: AdSource = AdSource.SELF

    private val splashRenderer = IgniteFusionSplashAD()
    private val bannerRenderer = IgniteFusionBannerAD()
    private val insertRenderer = IgniteFusionInsertAD()

    private var pendingRewardSession: RewardVideoSession? = null

    private var downloadReceiverRegistered = false
    private val downloadIdToPackage = mutableMapOf<Long, String>()
    private val pendingInstallPackages = mutableSetOf<String>()
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                return
            }
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId == -1L) {
                return
            }
            val packageName = downloadIdToPackage.remove(downloadId)
            if (packageName != null) {
                pendingInstallPackages.add(packageName)
            }
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val uri = manager.getUriForDownloadedFile(downloadId) ?: return
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(installIntent) }
        }
    }
    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
            if (!pendingInstallPackages.remove(packageName)) {
                return
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(launchIntent) }
        }
    }

    internal data class RewardVideoSession(
        val adData: IgniteFusionAdData,
        val videoUrl: String,
        val durationSeconds: Int,
        val rewardEligibleSeconds: Int,
        val onReward: () -> Unit,
        val onClose: () -> Unit,
        val onClick: () -> Unit
    )

    internal fun consumeRewardSession(): RewardVideoSession? {
        val session = pendingRewardSession
        pendingRewardSession = null
        return session
    }

    internal fun performRewardAdClick(context: Context, adData: IgniteFusionAdData) {
        handleAdClick(context, adData)
    }

    @JvmOverloads
    fun init(
        context: Context,
        appId: String,
        encrypted: Boolean = false,
        debug: Boolean = false,
    ) {
        IgniteFusionLog.enabled = debug
        IgniteFusionApiClient.configure(appId, encrypted)
        appContext = context.applicationContext
        isInitAd = true
        clearLoadedAds()
        IgniteFusionLog.d(
            TAG,
            "init success, baseUrl=${IgniteFusionApiClient.getBaseUrl()}, appId=${if (appId.isBlank()) "(empty)" else "***"}, encrypted=$encrypted, debug=$debug"
        )
    }

    @JvmStatic
    @JvmOverloads
    fun reportThirdPartyEvent(
        adUnitId: String,
        event: ThirdPartyEventType,
        onSuccess: (SdkThirdPartyEventOut) -> Unit = {},
        onFailure: (Throwable) -> Unit              = {}
    ) {
        IgniteFusionApiClient.reportThirdPartyEvent(adUnitId, event, onSuccess, onFailure)
    }

    @JvmStatic
    @Deprecated(
        message = "Use reportThirdPartyEvent(String, ThirdPartyEventType, ...) instead",
        replaceWith = ReplaceWith(
            "reportThirdPartyEvent(adUnitId, ThirdPartyEventType.from(event) ?: error(\"bad event\"), onSuccess, onFailure)"
        )
    )
    @JvmOverloads
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
            onFailure(e)
            return
        }
        IgniteFusionApiClient.reportThirdPartyEvent(adUnitId, t, onSuccess, onFailure)
    }

    private fun dispatchThirdPartyFlow(
        adUnitId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)?,
        listener: IgniteFusionAdLoadListener,
        setAdSource: (AdSource) -> Unit
    ) {
        setAdSource(AdSource.THIRD_PARTY)
        if (loadThirdPartyAd == null) {
            listener.onAdLoadFailure(IllegalStateException("当前广告位为第三方广告，请传入 loadThirdPartyAd 自行加载/展示"))
            return
        }
        IgniteFusionApiClient.reportThirdPartyEvent(
            adUnitId = adUnitId,
            event = ThirdPartyEventType.LOAD,
            onSuccess = { out ->
                if (!out.allowLoad) {
                    val reason = out.reason?.takeIf { it.isNotBlank() } ?: "三方广告已达展示/点击上限，不再加载"
                    IgniteFusionLog.w(TAG, "dispatchThirdPartyFlow[adUnitId=$adUnitId] allowLoad=false (show=${out.counters.shows}/${out.limits.showLimit}, click=${out.counters.clicks}/${out.limits.clickLimit}) reason=$reason")
                    listener.onAdLoadFailure(IllegalStateException(reason))
                } else {
                    val info = out.toLoadInfo(adUnitId)
                    IgniteFusionLog.d(TAG, "dispatchThirdPartyFlow[adUnitId=$adUnitId] allowLoad=true remainingShows=${info.remainingShows} remainingClicks=${info.remainingClicks}")
                    loadThirdPartyAd(info)
                }
            },
            onFailure = { t ->
                IgniteFusionLog.w(TAG, "dispatchThirdPartyFlow[adUnitId=$adUnitId] report load event failed, fallback invoke loadThirdPartyAd directly", t)
                val fallbackInfo = SdkThirdPartyLoadInfo(
                    adUnitId = adUnitId,
                    allowLoad = true,
                    loads = 0, shows = 0, clicks = 0,
                    showLimit = 0, clickLimit = 0,
                    remainingShows = Int.MAX_VALUE,
                    remainingClicks = Int.MAX_VALUE,
                    reason = "report-failed-fallback"
                )
                loadThirdPartyAd(fallbackInfo)
            }
        )
    }

    fun loadIFSplashAd(
        context: Context,
        adId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)? = null,
        listener: IgniteFusionAdLoadListener = IgniteFusionAdLoadListener()
    ) {
        IgniteFusionLog.d(TAG, "loadIFSplashAd start, adId=$adId, hasThirdPartyCallback=${loadThirdPartyAd != null}")
        if (!checkInit(context, listener.onAdLoadFailure)) {
            return
        }
        loadSelfAd(
            adType = "splash",
            adId = adId,
            loadThirdPartyAd = loadThirdPartyAd,
            listener = listener,
            onSpaceResult = { _, sourceSetter, result, onFetch ->
                IgniteFusionLog.d(TAG, "loadIFSplashAd onSpaceResult, adId=$adId, enable=${result.enable}, enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}")
                if (!result.enable) {
                    sourceSetter(AdSource.SELF)
                    currentSplashAd = null
                    val e = IllegalStateException("当前开屏广告位未启用")
                    IgniteFusionLog.w(TAG, "loadIFSplashAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@loadSelfAd
                }
                if (!result.enableSelfAd) {
                    currentSplashAd = null
                    IgniteFusionLog.d(TAG, "loadIFSplashAd dispatch to third-party, adId=$adId")
                    dispatchThirdPartyFlow(
                        adUnitId = adId,
                        loadThirdPartyAd = loadThirdPartyAd,
                        listener = listener,
                        setAdSource = sourceSetter
                    )
                    return@loadSelfAd
                }
                sourceSetter(AdSource.SELF)
                currentSplashAd = null
                onFetch(result.ads) { loadedAd ->
                    sourceSetter(AdSource.SELF)
                    currentSplashAd = loadedAd
                    IgniteFusionLog.d(TAG, "loadIFSplashAd success: ${loadedAd.adData.appName}, adId=$adId")
                    listener.onAdLoadSuccess()
                }
            }
        )
    }

    fun loadIFBannerAd(
        context: Context,
        adId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)? = null,
        listener: IgniteFusionAdLoadListener = IgniteFusionAdLoadListener()
    ) {
        IgniteFusionLog.d(TAG, "loadIFBannerAd start, adId=$adId, hasThirdPartyCallback=${loadThirdPartyAd != null}")
        if (!checkInit(context, listener.onAdLoadFailure)) {
            return
        }
        loadSelfAd(
            adType = "banner",
            adId = adId,
            loadThirdPartyAd = loadThirdPartyAd,
            listener = listener,
            onSpaceResult = { _, sourceSetter, result, onFetch ->
                IgniteFusionLog.d(TAG, "loadIFBannerAd onSpaceResult, adId=$adId, enable=${result.enable}, enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}")
                if (!result.enable) {
                    sourceSetter(AdSource.SELF)
                    currentBannerAd = null
                    val e = IllegalStateException("当前 Banner 广告位未启用")
                    IgniteFusionLog.w(TAG, "loadIFBannerAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@loadSelfAd
                }
                if (!result.enableSelfAd) {
                    currentBannerAd = null
                    IgniteFusionLog.d(TAG, "loadIFBannerAd dispatch to third-party, adId=$adId")
                    dispatchThirdPartyFlow(
                        adUnitId = adId,
                        loadThirdPartyAd = loadThirdPartyAd,
                        listener = listener,
                        setAdSource = sourceSetter
                    )
                    return@loadSelfAd
                }
                sourceSetter(AdSource.SELF)
                currentBannerAd = null
                onFetch(result.ads) { loadedAd ->
                    sourceSetter(AdSource.SELF)
                    currentBannerAd = loadedAd
                    IgniteFusionLog.d(TAG, "loadIFBannerAd success: ${loadedAd.adData.appName}, adId=$adId")
                    listener.onAdLoadSuccess()
                }
            }
        )
    }

    fun loadIFInterstitialAd(
        context: Context,
        adId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)? = null,
        listener: IgniteFusionAdLoadListener = IgniteFusionAdLoadListener()
    ) {
        IgniteFusionLog.d(TAG, "loadIFInterstitialAd start, adId=$adId, hasThirdPartyCallback=${loadThirdPartyAd != null}")
        if (!checkInit(context, listener.onAdLoadFailure)) {
            return
        }
        loadSelfAd(
            adType = "insert",
            adId = adId,
            loadThirdPartyAd = loadThirdPartyAd,
            listener = listener,
            onSpaceResult = { _, sourceSetter, result, onFetch ->
                IgniteFusionLog.d(TAG, "loadIFInterstitialAd onSpaceResult, adId=$adId, enable=${result.enable}, enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}")
                if (!result.enable) {
                    sourceSetter(AdSource.SELF)
                    currentInsertAd = null
                    val e = IllegalStateException("当前插屏广告位未启用")
                    IgniteFusionLog.w(TAG, "loadIFInterstitialAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@loadSelfAd
                }
                if (!result.enableSelfAd) {
                    currentInsertAd = null
                    IgniteFusionLog.d(TAG, "loadIFInterstitialAd dispatch to third-party, adId=$adId")
                    dispatchThirdPartyFlow(
                        adUnitId = adId,
                        loadThirdPartyAd = loadThirdPartyAd,
                        listener = listener,
                        setAdSource = sourceSetter
                    )
                    return@loadSelfAd
                }
                sourceSetter(AdSource.SELF)
                currentInsertAd = null
                onFetch(result.ads) { loadedAd ->
                    sourceSetter(AdSource.SELF)
                    currentInsertAd = loadedAd
                    IgniteFusionLog.d(TAG, "loadIFInterstitialAd success: ${loadedAd.adData.appName}, adId=$adId")
                    listener.onAdLoadSuccess()
                }
            }
        )
    }

    fun loadIFVideoAd(
        context: Context,
        adId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)? = null,
        listener: IgniteFusionAdLoadListener = IgniteFusionAdLoadListener()
    ) {
        IgniteFusionLog.d(TAG, "loadIFVideoAd start, adId=$adId, hasThirdPartyCallback=${loadThirdPartyAd != null}")
        if (!checkInit(context, listener.onAdLoadFailure)) {
            return
        }
        if (adId.isBlank()) {
            val e = IllegalArgumentException("广告 id 不能为空")
            IgniteFusionLog.w(TAG, "loadIFVideoAd fail: ${e.message}")
            listener.onAdLoadFailure(e)
            return
        }
        IgniteFusionApiClient.fetchAdSpace(
            adUnitId = adId,
            onSuccess = { result ->
                IgniteFusionLog.d(TAG, "loadIFVideoAd onSpaceResult, adId=$adId, enable=${result.enable}, enableSelfAd=${result.enableSelfAd}, adCount=${result.ads.size}")
                if (!result.enable) {
                    rewardAdSource = AdSource.SELF
                    currentRewardAd = null
                    val e = IllegalStateException("当前激励视频广告位未启用")
                    IgniteFusionLog.w(TAG, "loadIFVideoAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@fetchAdSpace
                }
                if (!result.enableSelfAd) {
                    currentRewardAd = null
                    IgniteFusionLog.d(TAG, "loadIFVideoAd dispatch to third-party, adId=$adId")
                    dispatchThirdPartyFlow(
                        adUnitId = adId,
                        loadThirdPartyAd = loadThirdPartyAd,
                        listener = listener,
                        setAdSource = { src: AdSource -> rewardAdSource = src }
                    )
                    return@fetchAdSpace
                }
                rewardAdSource = AdSource.SELF
                currentRewardAd = null
                val adList = result.ads
                if (adList.isEmpty()) {
                    val e = IllegalStateException("激励视频广告位没有可展示的数据")
                    IgniteFusionLog.w(TAG, "loadIFVideoAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@fetchAdSpace
                }
                val selectedRawAd = selectRawAdData(adList)
                if (selectedRawAd == null) {
                    val e = IllegalStateException("激励视频广告位没有权重大于0的可展示广告")
                    IgniteFusionLog.w(TAG, "loadIFVideoAd fail: ${e.message}, adId=$adId")
                    listener.onAdLoadFailure(e)
                    return@fetchAdSpace
                }
                val videoUrl = IgniteFusionApiClient.resolveMediaUrl(selectedRawAd.video?.url)
                    ?.takeIf { it.isNotBlank() }
                if (videoUrl.isNullOrBlank()) {
                    val e = IllegalStateException("激励视频广告视频地址为空")
                    IgniteFusionLog.w(TAG, "loadIFVideoAd fail: ${e.message}, adId=$adId, rawVideoUrl=${selectedRawAd.video?.url}")
                    listener.onAdLoadFailure(e)
                    return@fetchAdSpace
                }
                val adData = mapToIgniteFusionAdData(selectedRawAd)
                rewardAdSource = AdSource.SELF
                currentRewardAd = LoadedRewardAd(
                    adId = adId,
                    adData = adData,
                    videoUrl = videoUrl
                )
                IgniteFusionLog.d(TAG, "loadIFVideoAd success, adId=$adId, appName=${adData.appName}, videoUrl=$videoUrl")
                listener.onAdLoadSuccess()
            },
            onFailure = { error ->
                IgniteFusionLog.e(TAG, "loadIFVideoAd fetchAdSpace failed, adId=$adId: ${error.message}", error)
                listener.onAdLoadFailure(error)
            }
        )
    }

    fun showIFSplashAd(
        view: ViewGroup,
        listener: IgniteFusionAdShowListener = IgniteFusionAdShowListener()
    ) {
        IgniteFusionLog.d(TAG, "showIFSplashAd start, adSource=$splashAdSource, hasLoadedAd=${currentSplashAd != null}")
        if (!checkInit(view.context, listener.onAdShowFailure)) {
            return
        }
        if (splashAdSource == AdSource.THIRD_PARTY) {
            val e = IllegalStateException("当前开屏广告为第三方广告，请在 loadThirdPartyAd 中自行展示")
            IgniteFusionLog.w(TAG, "showIFSplashAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        val loadedAd = currentSplashAd
        if (loadedAd == null) {
            val e = IllegalStateException("开屏广告未加载，请先调用 loadIFSplashAd")
            IgniteFusionLog.w(TAG, "showIFSplashAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        splashTimer?.cancel()
        unregisterSplashShakeListener()
        splashActionTriggered = false

        try {
            IgniteFusionLog.d(TAG, "showIFSplashAd rendering, appName=${loadedAd.adData.appName}, splashDuration=${loadedAd.adData.splashDurationSeconds}s")
            val splashView = splashRenderer.createView(
                context = view.context,
                adData = loadedAd.adData,
                onSkipClick = { dismissSplash(view, listener.onAdClose) },
                onActionClick = {
                    if (!splashActionTriggered) {
                        splashActionTriggered = true
                        IgniteFusionLog.d(TAG, "showIFSplashAd action click, appName=${loadedAd.adData.appName}")
                        listener.onAdClick()
                        reportSelfClick(loadedAd.adId)
                        handleAdClick(view.context, loadedAd.adData)
                    }
                }
            )

            view.removeAllViews()
            view.addView(
                splashView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            registerSplashShakeListener(
                context = view.context,
                onShake = {
                    if (!splashActionTriggered) {
                        splashActionTriggered = true
                        IgniteFusionLog.d(TAG, "showIFSplashAd shake trigger, appName=${loadedAd.adData.appName}")
                        listener.onAdClick()
                        reportSelfClick(loadedAd.adId)
                        handleAdClick(view.context, loadedAd.adData)
                    }
                }
            )

            val skipView = splashView.findViewById<TextView>(R.id.skip)
            val totalMillis = loadedAd.adData.splashDurationSeconds * 1000L
            splashTimer = object : CountDownTimer(totalMillis, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = ceil(millisUntilFinished / 1000.0).toInt().coerceAtLeast(1)
                    skipView.text = "跳过${seconds}秒"
                }

                override fun onFinish() {
                    IgniteFusionLog.d(TAG, "showIFSplashAd countdown finished, dismiss")
                    dismissSplash(view, listener.onAdClose)
                }
            }.also { it.start() }

            listener.onAdShowSuccess()
            reportSelfShow(loadedAd.adId)
            IgniteFusionLog.d(TAG, "showIFSplashAd success, appName=${loadedAd.adData.appName}")
        } catch (e: Exception) {
            IgniteFusionLog.e(TAG, "showIFSplashAd failed: ${e.message}", e)
            listener.onAdShowFailure(e)
        }
    }

    fun showIFBannerAd(
        view: ViewGroup,
        listener: IgniteFusionAdShowListener = IgniteFusionAdShowListener()
    ) {
        IgniteFusionLog.d(TAG, "showIFBannerAd start, adSource=$bannerAdSource, hasLoadedAd=${currentBannerAd != null}")
        if (!checkInit(view.context, listener.onAdShowFailure)) {
            return
        }
        if (bannerAdSource == AdSource.THIRD_PARTY) {
            val e = IllegalStateException("当前 Banner 广告为第三方广告，请在 loadThirdPartyAd 中自行展示")
            IgniteFusionLog.w(TAG, "showIFBannerAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        val loadedAd = currentBannerAd
        if (loadedAd == null) {
            val e = IllegalStateException("Banner 广告未加载，请先调用 loadIFBannerAd")
            IgniteFusionLog.w(TAG, "showIFBannerAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        try {
            IgniteFusionLog.d(TAG, "showIFBannerAd rendering, appName=${loadedAd.adData.appName}")
            val bannerView = bannerRenderer.createView(
                context = view.context,
                adData = loadedAd.adData,
                onClose = {
                    IgniteFusionLog.d(TAG, "showIFBannerAd close button clicked, appName=${loadedAd.adData.appName}")
                    removeIFBannerAd(view)
                    listener.onAdClose()
                },
                onActionClick = {
                    IgniteFusionLog.d(TAG, "showIFBannerAd action click, appName=${loadedAd.adData.appName}")
                    listener.onAdClick()
                    reportSelfClick(loadedAd.adId)
                    handleAdClick(view.context, loadedAd.adData)
                }
            )

            view.removeAllViews()
            view.addView(bannerView)
            listener.onAdShowSuccess()
            reportSelfShow(loadedAd.adId)
            IgniteFusionLog.d(TAG, "showIFBannerAd success, appName=${loadedAd.adData.appName}")
        } catch (e: Exception) {
            IgniteFusionLog.e(TAG, "showIFBannerAd failed: ${e.message}", e)
            listener.onAdShowFailure(e)
        }
    }

    fun showIFInterstitialAd(
        activity: Activity,
        listener: IgniteFusionAdShowListener = IgniteFusionAdShowListener()
    ) {
        IgniteFusionLog.d(TAG, "showIFInterstitialAd start, adSource=$insertAdSource, hasLoadedAd=${currentInsertAd != null}")
        if (!checkInit(activity, listener.onAdShowFailure)) {
            return
        }
        if (insertAdSource == AdSource.THIRD_PARTY) {
            val e = IllegalStateException("当前插屏广告为第三方广告，请在 loadThirdPartyAd 中自行展示")
            IgniteFusionLog.w(TAG, "showIFInterstitialAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        val loadedAd = currentInsertAd
        if (loadedAd == null) {
            val e = IllegalStateException("插屏广告未加载，请先调用 loadIFInterstitialAd")
            IgniteFusionLog.w(TAG, "showIFInterstitialAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        try {
            IgniteFusionLog.d(TAG, "showIFInterstitialAd rendering, appName=${loadedAd.adData.appName}, autoCloseMs=$INTERSTITIAL_AUTO_CLOSE_MILLIS")
            var dialog: Dialog? = null
            val adView = insertRenderer.createView(
                context = activity,
                adData = loadedAd.adData,
                onClose = {
                    IgniteFusionLog.d(TAG, "showIFInterstitialAd close button clicked, appName=${loadedAd.adData.appName}")
                    val currentDialog = dialog
                    if (currentDialog?.isShowing == true) {
                        currentDialog.dismiss()
                    }
                },
                onActionClick = {
                    IgniteFusionLog.d(TAG, "showIFInterstitialAd action click, appName=${loadedAd.adData.appName}")
                    listener.onAdClick()
                    reportSelfClick(loadedAd.adId)
                    handleAdClick(activity, loadedAd.adData)
                }
            )

            val horizontalMargin = (28 * activity.resources.displayMetrics.density).toInt()
            val dialogContainer = FrameLayout(activity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = false
                addView(
                    adView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER
                    ).apply {
                        marginStart = horizontalMargin
                        marginEnd = horizontalMargin
                    }
                )
                setOnClickListener {
                    val currentDialog = dialog
                    if (currentDialog?.isShowing == true) {
                        currentDialog.dismiss()
                    }
                }
            }

            val insertDialog = Dialog(activity, R.style.IgniteFusionInsertDialog).apply {
                setCancelable(true)
                setCanceledOnTouchOutside(true)
                setContentView(
                    dialogContainer,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                setOnDismissListener {
                    IgniteFusionLog.d(TAG, "showIFInterstitialAd dialog dismissed, appName=${loadedAd.adData.appName}")
                    listener.onAdClose()
                }
            }
            dialog = insertDialog
            applyInsertDialogWindow(insertDialog)
            insertDialog.show()
            applyInsertDialogWindow(insertDialog)
            listener.onAdShowSuccess()
            reportSelfShow(loadedAd.adId)

            Handler(Looper.getMainLooper()).postDelayed({
                val currentDialog = dialog
                if (currentDialog?.isShowing == true) {
                    IgniteFusionLog.d(TAG, "showIFInterstitialAd auto-close triggered, appName=${loadedAd.adData.appName}")
                    currentDialog.dismiss()
                }
            }, INTERSTITIAL_AUTO_CLOSE_MILLIS)

            IgniteFusionLog.d(TAG, "showIFInterstitialAd success, appName=${loadedAd.adData.appName}")
        } catch (e: Exception) {
            IgniteFusionLog.e(TAG, "showIFInterstitialAd failed: ${e.message}", e)
            listener.onAdShowFailure(e)
        }
    }

    private fun applyInsertDialogWindow(dialog: Dialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.setPadding(0, 0, 0, 0)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window.setDimAmount(0.6f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    fun showIFVideoAd(
        activity: Activity,
        listener: IgniteFusionRewardAdShowListener = IgniteFusionRewardAdShowListener()
    ) {
        IgniteFusionLog.d(TAG, "showIFVideoAd start, adSource=$rewardAdSource, hasLoadedAd=${currentRewardAd != null}")
        if (!checkInit(activity, listener.onAdShowFailure)) {
            return
        }
        if (rewardAdSource == AdSource.THIRD_PARTY) {
            val e = IllegalStateException("当前激励视频为第三方广告，请在 loadThirdPartyAd 中自行展示")
            IgniteFusionLog.w(TAG, "showIFVideoAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        val loadedAd = currentRewardAd
        if (loadedAd == null) {
            val e = IllegalStateException("激励视频广告未加载，请先调用 loadIFVideoAd")
            IgniteFusionLog.w(TAG, "showIFVideoAd fail: ${e.message}")
            listener.onAdShowFailure(e)
            return
        }

        pendingRewardSession = RewardVideoSession(
            adData = loadedAd.adData,
            videoUrl = loadedAd.videoUrl,
            durationSeconds = REWARD_VIDEO_DURATION_SECONDS,
            rewardEligibleSeconds = REWARD_EARNED_SECONDS,
            onReward = {
                IgniteFusionLog.d(TAG, "showIFVideoAd reward earned, appName=${loadedAd.adData.appName}")
                listener.onReward()
            },
            onClose = {
                IgniteFusionLog.d(TAG, "showIFVideoAd close, appName=${loadedAd.adData.appName}")
                listener.onAdClose()
            },
            onClick = {
                IgniteFusionLog.d(TAG, "showIFVideoAd click, appName=${loadedAd.adData.appName}")
                listener.onAdClick()
                reportSelfClick(loadedAd.adId)
                handleAdClick(activity, loadedAd.adData)
            }
        )

        try {
            IgniteFusionLog.d(TAG, "showIFVideoAd launching RewardVideoActivity, appName=${loadedAd.adData.appName}, videoUrl=${loadedAd.videoUrl}, duration=${REWARD_VIDEO_DURATION_SECONDS}s, rewardAfter=${REWARD_EARNED_SECONDS}s")
            activity.startActivity(Intent(activity, IgniteFusionRewardVideoActivity::class.java))
            listener.onAdShowSuccess()
            reportSelfShow(loadedAd.adId)
            IgniteFusionLog.d(TAG, "showIFVideoAd success, appName=${loadedAd.adData.appName}")
        } catch (e: Exception) {
            IgniteFusionLog.e(TAG, "showIFVideoAd failed to launch activity: ${e.message}", e)
            pendingRewardSession = null
            listener.onAdShowFailure(e)
        }
    }

    fun showIFVideoAd(
        activity: Activity,
        adId: String,
        showAd: Boolean = true,
        onAdLoadSuccess: () -> Unit = {},
        onAdLoadError: () -> Unit = {},
        onAdClose: () -> Unit = {},
        onReward: () -> Unit = {}
    ) {
        loadIFVideoAd(
            context = activity,
            adId = adId,
            listener = IgniteFusionAdLoadListener(
                onAdLoadSuccess = {
                    onAdLoadSuccess()
                    if (!showAd) {
                        onAdClose()
                        return@IgniteFusionAdLoadListener
                    }
                    showIFVideoAd(
                        activity = activity,
                        listener = IgniteFusionRewardAdShowListener(
                            onAdShowSuccess = {},
                            onAdShowFailure = { onAdLoadError() },
                            onAdClick = {},
                            onAdClose = onAdClose,
                            onReward = onReward
                        )
                    )
                },
                onAdLoadFailure = { onAdLoadError() }
            )
        )
    }

    fun removeIFBannerAd(view: ViewGroup) {
        view.removeAllViews()
        IgniteFusionLog.d(TAG, "removeIFBannerAd success")
    }

    fun destroy() {
        splashTimer?.cancel()
        splashTimer = null
        unregisterSplashShakeListener()
        if (downloadReceiverRegistered) {
            val context = appContext
            if (context != null) {
                runCatching { context.unregisterReceiver(downloadCompleteReceiver) }
                runCatching { context.unregisterReceiver(packageAddedReceiver) }
            }
            downloadReceiverRegistered = false
        }
        downloadIdToPackage.clear()
        pendingInstallPackages.clear()
        isInitAd = false
        appContext = null
        clearLoadedAds()
        IgniteFusionLog.d(TAG, "destroy success")
        IgniteFusionLog.enabled = false
    }

    private fun loadSelfAd(
        adType: String,
        adId: String,
        loadThirdPartyAd: ((SdkThirdPartyLoadInfo) -> Unit)?,
        listener: IgniteFusionAdLoadListener,
        onSpaceResult: (
            adId: String,
            setSource: (AdSource) -> Unit,
            result: AdSpaceResult,
            fetch: (adList: MutableList<Addata>, onLoaded: (LoadedAd) -> Unit) -> Unit
        ) -> Unit
    ) {
        IgniteFusionApiClient.fetchAdSpace(
            adUnitId = adId,
            onSuccess = { result ->
                IgniteFusionLog.d(TAG, "loadSelfAd[$adType] adSpace result, adId=$adId, enable=${result.enable}, self=${result.enableSelfAd}, n=${result.ads.size}")
                onSpaceResult(adId, { src ->
                    when (adType) {
                        "splash" -> splashAdSource = src
                        "banner" -> bannerAdSource = src
                        "insert" -> insertAdSource = src
                        else -> Unit
                    }
                }, result) { adList, onLoaded ->
                    selectAndMapAd(adType = adType, adId = adId, adList = adList,
                        onSelected = onLoaded,
                        onEmpty = { err ->
                            when (adType) {
                                "splash" -> currentSplashAd = null
                                "banner" -> currentBannerAd = null
                                "insert" -> currentInsertAd = null
                            }
                            IgniteFusionLog.w(TAG, "loadSelfAd[$adType] selectAndMapAd empty, adId=$adId: ${err.message}")
                            listener.onAdLoadFailure(err)
                        },
                        onFailure = { err ->
                            IgniteFusionLog.e(TAG, "loadSelfAd[$adType] selectAndMapAd failed, adId=$adId: ${err.message}", err)
                            listener.onAdLoadFailure(err)
                        }
                    )
                }
            },
            onFailure = { error ->
                IgniteFusionLog.e(TAG, "loadSelfAd[$adType] fetchAdSpace failed, adId=$adId: ${error.message}", error)
                listener.onAdLoadFailure(error)
            }
        )
    }

    private fun selectAndMapAd(
        adType: String,
        adId: String,
        adList: MutableList<Addata>,
        onSelected: (LoadedAd) -> Unit,
        onEmpty: (Throwable) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        try {
            IgniteFusionLog.d(
                TAG,
                "loadSelfAd fetched, type=$adType, adId=$adId, adCount=${adList.size}, ads=${summarizeAds(adList)}"
            )
            if (adList.isEmpty()) {
                onEmpty(IllegalStateException("${adType} 广告位没有可展示的数据"))
                return
            }
            val selectedAd = selectAdData(adList)
            if (selectedAd == null) {
                onEmpty(IllegalStateException("${adType} 广告位没有权重大于0的可展示广告"))
                return
            }
            IgniteFusionLog.d(
                TAG,
                "loadSelfAd selected, type=$adType, adId=$adId, appName=${selectedAd.appName}, clickUrl=${selectedAd.clickUrl.orEmpty()}"
            )
            onSelected(LoadedAd(adId = adId, adData = selectedAd))
        } catch (e: Throwable) {
            onFailure(e)
        }
    }

    private fun clearLoadedAds() {
        currentSplashAd = null
        currentBannerAd = null
        currentInsertAd = null
        currentRewardAd = null
        splashAdSource = AdSource.SELF
        bannerAdSource = AdSource.SELF
        insertAdSource = AdSource.SELF
        rewardAdSource = AdSource.SELF
    }

    private fun dismissSplash(view: ViewGroup, onAdClose: () -> Unit) {
        splashTimer?.cancel()
        splashTimer = null
        unregisterSplashShakeListener()
        splashActionTriggered = false
        view.removeAllViews()
        onAdClose()
    }

    private fun registerSplashShakeListener(
        context: Context,
        onShake: () -> Unit
    ) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

        unregisterSplashShakeListener()
        splashSensorManager = sensorManager
        lastShakeTriggerAt = 0L
        splashShakeListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val values = event.values ?: return
                if (values.size < 3) {
                    return
                }

                val x = values[0] / SensorManager.GRAVITY_EARTH
                val y = values[1] / SensorManager.GRAVITY_EARTH
                val z = values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(x * x + y * y + z * z)

                if (gForce < SHAKE_THRESHOLD_GRAVITY) {
                    return
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastShakeTriggerAt < SHAKE_DEBOUNCE_MILLIS) {
                    return
                }
                lastShakeTriggerAt = now
                onShake()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }.also {
            sensorManager.registerListener(it, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSplashShakeListener() {
        val sensorManager = splashSensorManager
        val listener = splashShakeListener
        if (sensorManager != null && listener != null) {
            sensorManager.unregisterListener(listener)
        }
        splashShakeListener = null
        splashSensorManager = null
        lastShakeTriggerAt = 0L
    }

    private fun checkInit(context: Context, onFailure: ((Throwable) -> Unit)? = null): Boolean {
        if (isInitAd && IgniteFusionApiClient.isConfigured()) {
            return true
        }
        val error = IllegalStateException("请先初始化：IgniteFusionAd.init(context, appId) [isInitAd=$isInitAd, apiConfigured=${IgniteFusionApiClient.isConfigured()}]")
        IgniteFusionLog.w(TAG, error.message.orEmpty())
        onFailure?.invoke(error)
        return false
    }

    private fun checkInit(context: Context): Boolean {
        return checkInit(context, null)
    }

    private fun reportSelfShow(adUnitId: String) {
        IgniteFusionApiClient.reportSelfEvent(adUnitId, "show")
    }

    private fun reportSelfClick(adUnitId: String) {
        IgniteFusionApiClient.reportSelfEvent(adUnitId, "click")
    }

    private fun handleAdClick(context: Context, adData: IgniteFusionAdData) {
        val clickUrl = adData.clickUrl
        if (clickUrl.isNullOrBlank()) {
            Toast.makeText(context, "${adData.callToAction}：${adData.appName}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (adData.isDownload) {
                startDownload(
                    context = context,
                    title = adData.appName,
                    downloadUrl = clickUrl,
                    applicationId = adData.applicationId
                )
            } else {
                context.startActivity(buildAdIntent(context, clickUrl))
            }
        } catch (e: ActivityNotFoundException) {
            IgniteFusionLog.e(TAG, "handleAdClick failed: $clickUrl", e)
            Toast.makeText(context, "暂时无法打开广告目标", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            IgniteFusionLog.e(TAG, "handleAdClick unexpected error: $clickUrl", e)
            Toast.makeText(context, "广告跳转失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureDownloadReceivers(context: Context) {
        if (downloadReceiverRegistered) {
            return
        }
        context.registerReceiver(
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
        context.registerReceiver(
            packageAddedReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        )
        downloadReceiverRegistered = true
    }

    private fun startDownload(
        context: Context,
        title: String,
        downloadUrl: String,
        applicationId: String?
    ) {
        ensureDownloadReceivers(context.applicationContext)
        val uri = Uri.parse(downloadUrl)
        val request = DownloadManager.Request(uri)
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (manager == null) {
            context.startActivity(buildAdIntent(context, downloadUrl))
            return
        }

        val downloadId = manager.enqueue(request)
        val packageName = applicationId?.takeIf { it.isNotBlank() }
        if (packageName != null) {
            downloadIdToPackage[downloadId] = packageName
        }
        Toast.makeText(context, "开始下载：$title", Toast.LENGTH_SHORT).show()
    }

    private fun buildAdIntent(context: Context, clickUrl: String): Intent {
        val isWebUrl = clickUrl.startsWith("http://", true) || clickUrl.startsWith("https://", true)
        val intent = if (isWebUrl) {
            Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))
        } else {
            context.packageManager.getLaunchIntentForPackage(clickUrl)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse(clickUrl))
        }

        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    private fun selectAdData(adList: List<Addata>): IgniteFusionAdData? {
        val selected = selectRawAdData(adList) ?: return null
        return mapToIgniteFusionAdData(selected)
    }

    private fun selectRawAdData(adList: List<Addata>): Addata? {
        val weightedAds = adList
            .map { it to it.weight.coerceIn(0, 100) }
            .filter { (_, weight) -> weight > 0 }
        if (weightedAds.isEmpty()) {
            IgniteFusionLog.w(TAG, "selectAdData skipped, no ad has weight > 0")
            return null
        }

        val totalWeight = weightedAds.sumOf { (_, weight) -> weight }
        val weightedSummary = weightedAds.joinToString(
            prefix = "[",
            postfix = "]"
        ) { (ad, weight) ->
            "{app=${ad.appname.orEmpty()}, rawWeight=${ad.weight}, usedWeight=$weight}"
        }
        IgniteFusionLog.d(
            TAG,
            "selectAdData candidates=$weightedSummary, totalWeight=$totalWeight"
        )

        var hit = Random.nextInt(totalWeight)
        val originalHit = hit
        weightedAds.forEach { (ad, weight) ->
            hit -= weight
            if (hit < 0) {
                IgniteFusionLog.d(
                    TAG,
                    "selectAdData hit=$originalHit, selectedApp=${ad.appname.orEmpty()}, selectedWeight=$weight"
                )
                return ad
            }
        }

        IgniteFusionLog.d(
            TAG,
            "selectAdData fallback selectedApp=${weightedAds.last().first.appname.orEmpty()}"
        )
        return weightedAds.last().first
    }

    private fun mapToIgniteFusionAdData(adData: Addata): IgniteFusionAdData {
        val clickUrl = adData.url?.takeIf { it.isNotBlank() }
        return IgniteFusionAdData(
            appName = adData.appname?.takeIf { it.isNotBlank() } ?: "IgniteFusion Ad",
            description = adData.content?.takeIf { it.isNotBlank() } ?: "广告内容加载中",
            callToAction = when {
                clickUrl.isNullOrBlank() -> "查看详情"
                adData.isDownload == true -> "立即下载"
                else -> "立即打开"
            },
            versionName = adData.version?.takeIf { it.isNotBlank() } ?: "v1.0.0",
            clickUrl = clickUrl,
            isDownload = adData.isDownload == true,
            applicationId = adData.applicationId?.takeIf { it.isNotBlank() },
            appLogoUrl = IgniteFusionApiClient.resolveMediaUrl(adData.applogo?.url),
            promoImageUrl = IgniteFusionApiClient.resolveMediaUrl(adData.promotional?.url),
            themeColor = adData.themeColor?.takeIf { it.isNotBlank() },
            appLogoRes = R.drawable.applogo,
            promoImageRes = R.drawable.a
        )
    }

    private data class LoadedAd(
        val adId: String,
        val adData: IgniteFusionAdData
    )

    private data class LoadedRewardAd(
        val adId: String,
        val adData: IgniteFusionAdData,
        val videoUrl: String
    )

    private fun summarizeAds(adList: List<Addata>): String {
        if (adList.isEmpty()) {
            return "[]"
        }
        return adList.joinToString(
            prefix = "[",
            postfix = "]"
        ) { ad ->
            "{app=${ad.appname.orEmpty()}, weight=${ad.weight}, url=${ad.url.orEmpty()}, download=${ad.isDownload}}"
        }
    }
}
