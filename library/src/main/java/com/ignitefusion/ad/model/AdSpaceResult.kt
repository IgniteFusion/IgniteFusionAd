package com.ignitefusion.ad.model

import androidx.annotation.Keep

@Keep
data class AdSpaceResult(
    val ad_unit_id: String? = null,
    val name: String? = null,
    val ad_type: String? = null,
    val enable: Boolean = false,
    val enableSelfAd: Boolean = true,
    val ads: MutableList<Addata> = mutableListOf()
)
