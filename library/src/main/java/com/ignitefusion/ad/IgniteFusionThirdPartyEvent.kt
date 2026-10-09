package com.ignitefusion.ad

import com.google.gson.annotations.SerializedName

enum class ThirdPartyEventType(
    val value: String
) {
    @SerializedName("load")  LOAD("load"),
    @SerializedName("show")  SHOW("show"),
    @SerializedName("click") CLICK("click");

    override fun toString(): String = value

    companion object {
        fun from(raw: String?): ThirdPartyEventType? =
            values().firstOrNull { it.value.equals(raw, ignoreCase = true) }
    }
}

data class SdkThirdPartyCounters(
    val loads: Int,
    val shows: Int,
    val clicks: Int
)

data class SdkThirdPartyLimits(
    @SerializedName("showLimit") val showLimit: Int,
    @SerializedName("clickLimit") val clickLimit: Int
)

data class SdkThirdPartyRemaining(
    val shows: Int,
    val clicks: Int
)

data class SdkThirdPartyEventOut(
    @SerializedName("allowLoad") val allowLoad: Boolean,
    val counters: SdkThirdPartyCounters,
    val limits: SdkThirdPartyLimits,
    val remaining: SdkThirdPartyRemaining,
    val reason: String? = null
)

data class SdkThirdPartyLoadInfo(
    val adUnitId: String,
    val allowLoad: Boolean,
    val loads: Int,
    val shows: Int,
    val clicks: Int,
    val showLimit: Int,
    val clickLimit: Int,
    val remainingShows: Int,
    val remainingClicks: Int,
    val reason: String?
) {
    fun reportShow() {
        IgniteFusionApiClient.reportThirdPartyEvent(adUnitId, ThirdPartyEventType.SHOW)
    }

    fun reportClick() {
        IgniteFusionApiClient.reportThirdPartyEvent(adUnitId, ThirdPartyEventType.CLICK)
    }

    fun reachedShowLimit(): Boolean = showLimit > 0 && remainingShows <= 0
    fun reachedClickLimit(): Boolean = clickLimit > 0 && remainingClicks <= 0
}

internal fun SdkThirdPartyEventOut.toLoadInfo(adUnitId: String): SdkThirdPartyLoadInfo = SdkThirdPartyLoadInfo(
    adUnitId = adUnitId,
    allowLoad = allowLoad,
    loads = counters.loads,
    shows = counters.shows,
    clicks = counters.clicks,
    showLimit = limits.showLimit,
    clickLimit = limits.clickLimit,
    remainingShows = remaining.shows,
    remainingClicks = remaining.clicks,
    reason = reason
)
