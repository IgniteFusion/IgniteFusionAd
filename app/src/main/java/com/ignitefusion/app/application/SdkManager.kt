package com.ignitefusion.app.application

import android.util.Log
import com.ignitefusion.app.config.Common
import com.ignitefusion.ad.IgniteFusionAd


/**
 *作者：daboluo on 2025/7/8 23:03
 *Email:daboluo719@gmail.com
 * SDK· 初始化
 */
object SdkManager {
    private val TAG = "SdkManager"
    /**
     * 初始化广告
     */
    fun initAd(): Boolean {
        try {
            IgniteFusionAd.init(AppContextHolder.getApplication(), Common.AppId, false,true)
            Log.d(TAG, "initAd success, appId=${if (Common.AppId.isBlank()) "(empty)" else "***"}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "initAd failed: ${e.message}", e)
            return false
        }
    }
}
