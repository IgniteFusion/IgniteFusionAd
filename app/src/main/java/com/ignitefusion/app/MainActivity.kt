package com.ignitefusion.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ignitefusion.app.config.Common
import com.sparkfusionad.app.databinding.ActivityMainBinding
import com.ignitefusion.ad.SdkThirdPartyLoadInfo
import com.ignitefusion.ad.IgniteFusionAd
import com.ignitefusion.ad.IgniteFusionAdLoadListener
import com.ignitefusion.ad.IgniteFusionRewardAdShowListener
import com.ignitefusion.ad.IgniteFusionAdShowListener

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        IgniteFusionAd.loadIFBannerAd(
            context = this,
            adId = Common.POS_ID_BANNER,
            loadThirdPartyAd = { info: SdkThirdPartyLoadInfo ->
                Log.d("IgniteFusionAd", "加载第三方banner广告: adUnitId=${info.adUnitId}, allowLoad=${info.allowLoad}, remainingShows=${info.remainingShows}, remainingClicks=${info.remainingClicks}, reason=${info.reason}")
                info.reportShow()//广告展示上报
                info.reportClick()//广告点击上报
                Toast.makeText(this, "加载第三方banner广告", Toast.LENGTH_SHORT).show()
            },
            listener = IgniteFusionAdLoadListener(
                onAdLoadSuccess = {
                    Log.d("IgniteFusionAd", "banner广告加载成功")
                    IgniteFusionAd.showIFBannerAd(
                        binding.fl,
                        IgniteFusionAdShowListener(
                            onAdShowSuccess = {
                                Log.d("IgniteFusionAd", "banner广告显示成功")
                            },
                            onAdShowFailure = { err ->
                                Log.e("IgniteFusionAd", "banner广告显示失败: ${err.message}", err)
                            },
                            onAdClick = {
                                Log.d("IgniteFusionAd", "banner广告点击")
                            },
                            onAdClose = {
                                Log.d("IgniteFusionAd", "banner广告关闭")
                            }
                        )
                    )
                },
                onAdLoadFailure = { err ->
                    Log.e("IgniteFusionAd", "banner广告加载失败: ${err.message}", err)
                }
            )
        )
        binding.button.setOnClickListener {
            IgniteFusionAd.loadIFInterstitialAd(
                context = this,
                adId = Common.POS_ID_Insert,
                loadThirdPartyAd = { info: SdkThirdPartyLoadInfo ->
                    Log.d("IgniteFusionAd", "加载第三方插屏广告: adUnitId=${info.adUnitId}, allowLoad=${info.allowLoad}, remainingShows=${info.remainingShows}, remainingClicks=${info.remainingClicks}, reason=${info.reason}")
                    info.reportShow()
                    Toast.makeText(this, "加载第三方插屏广告", Toast.LENGTH_SHORT).show()
                },
                listener = IgniteFusionAdLoadListener(
                    onAdLoadSuccess = {
                        Log.d("IgniteFusionAd", "插屏广告加载成功")
                        IgniteFusionAd.showIFInterstitialAd(
                            this,
                            IgniteFusionAdShowListener(
                                onAdShowSuccess = {
                                    Log.d("IgniteFusionAd", "插屏广告显示成功")
                                },
                                onAdShowFailure = { err ->
                                    Log.e("IgniteFusionAd", "插屏广告显示失败: ${err.message}", err)
                                },
                                onAdClick = {
                                    Log.d("IgniteFusionAd", "插屏广告点击")
                                },
                                onAdClose = {
                                    Log.d("IgniteFusionAd", "插屏广告关闭")
                                }
                            )
                        )
                    },
                    onAdLoadFailure = { err ->
                        Log.e("IgniteFusionAd", "插屏广告加载失败: ${err.message}", err)
                    }
                )
            )
        }
        binding.button2.setOnClickListener {
            IgniteFusionAd.loadIFVideoAd(
                context = this,
                adId = Common.POS_ID_REWARD,
                loadThirdPartyAd = { info: SdkThirdPartyLoadInfo ->
                    Log.d("IgniteFusionAd", "加载第三方激励视频广告: adUnitId=${info.adUnitId}, allowLoad=${info.allowLoad}, remainingShows=${info.remainingShows}, remainingClicks=${info.remainingClicks}, reason=${info.reason}")
                    info.reportShow()
                    Toast.makeText(this, "加载第三方激励视频广告", Toast.LENGTH_SHORT).show()
                },
                listener = IgniteFusionAdLoadListener(
                    onAdLoadSuccess = {
                        Log.d("IgniteFusionAd", "激励视频广告加载成功")
                        IgniteFusionAd.showIFVideoAd(
                            activity = this,
                            listener = IgniteFusionRewardAdShowListener(
                                onAdShowSuccess = {
                                    Log.d("IgniteFusionAd", "激励视频广告显示成功")
                                },
                                onAdShowFailure = { err ->
                                    Log.e("IgniteFusionAd", "激励视频广告显示失败: ${err.message}", err)
                                },
                                onAdClick = {
                                    Log.d("IgniteFusionAd", "激励视频广告点击")
                                },
                                onAdClose = {
                                    Log.d("IgniteFusionAd", "激励视频广告关闭")
                                },
                                onReward = {
                                    Log.d("IgniteFusionAd", "激励视频广告奖励成功")
                                }
                            )
                        )
                    },
                    onAdLoadFailure = { err ->
                        Log.e("IgniteFusionAd", "激励视频广告加载失败: ${err.message}", err)
                    }
                )
            )
        }

    }
}
