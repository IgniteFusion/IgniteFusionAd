package com.ignitefusion.ad.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.ignitefusion.ad.R
import com.ignitefusion.ad.model.IgniteFusionAdData

internal class IgniteFusionInsertAD {

    fun createView(
        context: Context,
        adData: IgniteFusionAdData,
        onClose: () -> Unit = {},
        onActionClick: () -> Unit = {}
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.insert_ad_view, null, false)
        view.setOnClickListener { onActionClick() }

        IgniteFusionImageLoader.load(
            imageView = view.findViewById(R.id.applogo),
            url = adData.appLogoUrl,
            fallbackResId = adData.appLogoRes
        )
        IgniteFusionImageLoader.load(
            imageView = view.findViewById(R.id.promotional),
            url = adData.promoImageUrl,
            fallbackResId = adData.promoImageRes
        )
        view.findViewById<TextView>(R.id.appname).text = adData.appName
        view.findViewById<TextView>(R.id.content).text = adData.description
        view.findViewById<TextView>(R.id.version).text = adData.versionName
        view.findViewById<TextView>(R.id.download).apply {
            text = adData.callToAction
            setOnClickListener { onActionClick() }
        }
        view.findViewById<ImageView>(R.id.close).setOnClickListener { onClose() }

        return view
    }
}
