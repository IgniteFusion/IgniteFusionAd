package com.ignitefusion.app.application

import android.annotation.SuppressLint

/**
 *作者：daboluo on 2025/7/8 23:03
 *Email:daboluo719@gmail.com
 * 保存全局Application
 */
@SuppressLint("StaticFieldLeak")
object AppContextHolder {
    private lateinit var application: Application

    fun init(app: Application) {
        this.application = app
    }

    fun getApplication(): Application {
        if (!::application.isInitialized) {
            throw IllegalStateException("AppContextHolder is not initialized")
        }
        return application
    }
}
