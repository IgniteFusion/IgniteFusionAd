package com.wdit.shrmtfx.dcllk.kllcd.config

import com.tencent.mmkv.MMKV

class AppConfig {
    var mmkv: MMKV = MMKV.defaultMMKV()

    object appconfig {
        val FIRST="FIRST"//是否第一次启动
    }
    //设置是否第一次启动
    fun setFirst(first:Boolean){
        mmkv.encode(appconfig.FIRST,first)
    }
    //获取是否第一次启动
    fun getFirst():Boolean{
        return mmkv.decodeBool(appconfig.FIRST,true)
    }
}