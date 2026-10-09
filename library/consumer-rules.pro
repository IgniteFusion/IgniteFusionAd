# IgniteFusionAD SDK — 随 AAR 自动合并到接入方 ProGuard / R8
# 覆盖：公开 API、Gson 模型、Manifest Activity、Kotlin 元数据、依赖库

# ---------- Kotlin ----------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes SourceFile, LineNumberTable
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ---------- 公开 API（入口 / 监听器 / 三方埋点）----------
-keep class com.ignitefusion.ad.IgniteFusionAd { *; }
-keep class com.ignitefusion.ad.IgniteFusionAdLoadListener { *; }
-keep class com.ignitefusion.ad.IgniteFusionAdShowListener { *; }
-keep class com.ignitefusion.ad.IgniteFusionRewardAdShowListener { *; }
-keep class com.ignitefusion.ad.ThirdPartyEventType { *; }
-keep class com.ignitefusion.ad.SdkThirdPartyCounters { *; }
-keep class com.ignitefusion.ad.SdkThirdPartyLimits { *; }
-keep class com.ignitefusion.ad.SdkThirdPartyRemaining { *; }
-keep class com.ignitefusion.ad.SdkThirdPartyEventOut { *; }
-keep class com.ignitefusion.ad.SdkThirdPartyLoadInfo { *; }
-keep class com.ignitefusion.ad.IgniteFusionApiClient { *; }

# @JvmOverloads / companion / 默认参数生成的 synthetic
-keepclassmembers class com.ignitefusion.ad.IgniteFusionAd {
    public <methods>;
}
-keepclassmembers class com.ignitefusion.ad.IgniteFusionApiClient {
    public <methods>;
}

# ---------- Manifest 注册的 Activity ----------
-keep class com.ignitefusion.ad.ui.IgniteFusionRewardVideoActivity { *; }

# ---------- Gson / 网络 JSON 模型（字段名不可混淆）----------
-keep class com.ignitefusion.ad.model.** { *; }
-keepclassmembers class com.ignitefusion.ad.model.** { *; }
-keep class com.ignitefusion.ad.network.EncEnvelope { *; }
-keep class com.ignitefusion.ad.network.PublicKeyBundle { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- 资源 / 反射相关（布局 R.id）----------
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ---------- Gson ----------
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ---------- Media3 / ExoPlayer（激励视频）----------
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
