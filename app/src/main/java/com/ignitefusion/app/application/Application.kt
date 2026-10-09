package com.ignitefusion.app.application

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tencent.mmkv.MMKV

class Application : Application(){

    companion object {
        lateinit var instance: Application
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        //初始化
        MMKV.initialize(this)
        AppContextHolder.init(this)

    }
}