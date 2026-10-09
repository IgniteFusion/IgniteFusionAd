package com.ignitefusion.ad.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.ignitefusion.ad.R
import com.ignitefusion.ad.model.IgniteFusionAdData

internal class IgniteFusionRewardAD {

    fun createView(
        context: Context,
        adData: IgniteFusionAdData,
        rewardEligibleSeconds: Int,
        onCloseClick: () -> Unit = {},
        onActionClick: () -> Unit = {}
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.reward_ad_view, null, false)
        view.setOnClickListener { onActionClick() }

        IgniteFusionImageLoader.load(
            imageView = view.findViewById(R.id.applogo),
            url = adData.appLogoUrl,
            fallbackResId = adData.appLogoRes
        )
        IgniteFusionImageLoader.load(
            imageView = view.findViewById(R.id.notice_applogo),
            url = adData.appLogoUrl,
            fallbackResId = adData.appLogoRes
        )

        view.findViewById<TextView>(R.id.appname).text = adData.appName
        view.findViewById<TextView>(R.id.content).text = adData.description
        view.findViewById<TextView>(R.id.notice_appname).text = adData.appName

        view.findViewById<TextView>(R.id.download).apply {
            text = adData.callToAction
            setOnClickListener { onActionClick() }
        }

        view.findViewById<TextView>(R.id.rewardHint).text = "观看${rewardEligibleSeconds}秒获得奖励"
        view.findViewById<ImageView>(R.id.close).setOnClickListener { onCloseClick() }

        view.findViewById<ImageView>(R.id.notice_applogo).setOnClickListener { onActionClick() }
        view.findViewById<TextView>(R.id.notice_appname).setOnClickListener { onActionClick() }

        return view
    }
}
