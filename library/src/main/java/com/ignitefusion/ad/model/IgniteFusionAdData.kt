package com.ignitefusion.ad.model

import androidx.annotation.DrawableRes

internal data class IgniteFusionAdData(
    val appName: String,
    val description: String,
    val callToAction: String,
    val versionName: String,
    val clickUrl: String? = null,
    val isDownload: Boolean = false,
    val applicationId: String? = null,
    val appLogoUrl: String? = null,
    val promoImageUrl: String? = null,
    val themeColor: String? = null,
    @DrawableRes val appLogoRes: Int,
    @DrawableRes val promoImageRes: Int,
    val splashDurationSeconds: Int = 5
)
