package com.ignitefusion.ad.ui

import android.graphics.BitmapFactory
import android.widget.ImageView
import com.ignitefusion.ad.util.IgniteFusionLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object IgniteFusionImageLoader {

    private const val TAG = "IgniteFusionImageLoader"
    private val executor = Executors.newCachedThreadPool()

    fun load(imageView: ImageView, url: String?, fallbackResId: Int) {
        imageView.setImageResource(fallbackResId)
        if (url.isNullOrBlank()) {
            return
        }

        imageView.tag = url
        executor.execute {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    doInput = true
                }
                connection.connect()
                connection.inputStream.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                    imageView.post {
                        if (imageView.tag == url) {
                            imageView.setImageBitmap(bitmap)
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                IgniteFusionLog.w(TAG, "load image failed: $url", e)
            }
        }
    }
}
