package com.ignitefusion.ad.util

import android.util.Log

/**
 * SDK 内部日志。仅当 [IgniteFusionAd.init] 传入 `debug = true` 时输出。
 */
internal object IgniteFusionLog {

    @Volatile
    var enabled: Boolean = false

    fun d(tag: String, msg: String) {
        if (enabled) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String) {
        if (enabled) Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        if (enabled) Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String) {
        if (enabled) Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (enabled) Log.e(tag, msg, tr)
    }
}
