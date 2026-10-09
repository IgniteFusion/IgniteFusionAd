package com.ignitefusion.ad.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.ignitefusion.ad.R
import com.ignitefusion.ad.model.IgniteFusionAdData

internal class IgniteFusionBannerAD {

    fun createView(
        context: Context,
        adData: IgniteFusionAdData,
        onClose: () -> Unit = {},
        onActionClick: () -> Unit = {}
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.banner_ad_view, null, false)
        view.setOnClickListener { onActionClick() }

        IgniteFusionImageLoader.load(
            imageView = view.findViewById(R.id.applogo),
            url = adData.appLogoUrl,
            fallbackResId = adData.appLogoRes
        )
        view.findViewById<TextView>(R.id.appname).text = adData.appName
        view.findViewById<TextView>(R.id.content).text = adData.description
        view.findViewById<TextView>(R.id.download).apply {
            text = adData.callToAction
            setOnClickListener { onActionClick() }
        }
        startScaleAnimation(view.findViewById(R.id.download))
        view.findViewById<ImageView>(R.id.close).setOnClickListener { onClose() }

        return view
    }

    private fun startScaleAnimation(view: View?) {
        if (view == null) {
            return
        }
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.08f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.08f)
        ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = 650L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }
}
