package com.ignitefusion.ad.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ignitefusion.ad.R
import com.ignitefusion.ad.model.IgniteFusionAdData

internal class IgniteFusionSplashAD {

    fun createView(
        context: Context,
        adData: IgniteFusionAdData,
        onSkipClick: () -> Unit = {},
        onActionClick: () -> Unit = {}
    ): View {
        val view = LayoutInflater.from(context).inflate(R.layout.splash_ad_view, null, false)
        view.setOnClickListener { onActionClick() }
        applyThemeColor(view, adData.themeColor)

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
        view.findViewById<TextView>(R.id.skip).apply {
            text = "跳过${adData.splashDurationSeconds}秒"
            setOnClickListener { onSkipClick() }
        }
        view.findViewById<View>(R.id.actionContainer).setOnClickListener { onActionClick() }
        startShakeAnimation(view.findViewById(R.id.shakeIcon))
        applyStatusBarClearance(view)

        return view
    }

    private fun applyStatusBarClearance(view: View) {
        val apply = {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val overlap = statusBarInset(view) - location[1]
            if (overlap > view.paddingTop) {
                view.setPadding(view.paddingLeft, overlap, view.paddingRight, view.paddingBottom)
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post(apply)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        if (view.isAttachedToWindow) {
            view.post(apply)
        }
    }

    private fun statusBarInset(view: View): Int {
        val insets = ViewCompat.getRootWindowInsets(view)
        if (insets != null) {
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            ).top
            if (top > 0) {
                return top
            }
        }
        val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) view.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun applyThemeColor(view: View, themeColor: String?) {
        val raw = themeColor?.trim().orEmpty()
        if (raw.isBlank()) {
            return
        }
        val normalized = if (raw.startsWith("#")) raw else "#$raw"
        val color = runCatching { Color.parseColor(normalized) }.getOrNull() ?: return
        view.setBackgroundColor(color)
    }

    private fun startShakeAnimation(imageView: ImageView?) {
        if (imageView == null) {
            return
        }
        ObjectAnimator.ofFloat(imageView, View.TRANSLATION_X, 0f, -12f, 12f, -10f, 10f, 0f).apply {
            duration = 600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }
}
