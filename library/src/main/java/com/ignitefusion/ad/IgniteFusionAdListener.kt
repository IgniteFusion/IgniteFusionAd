package com.ignitefusion.ad

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
