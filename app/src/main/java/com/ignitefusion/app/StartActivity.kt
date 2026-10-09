package com.ignitefusion.app

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.ignitefusion.app.application.SdkManager
import com.ignitefusion.app.config.Common
import com.sparkfusionad.app.databinding.ActivityStartBinding
import com.ignitefusion.ad.SdkThirdPartyLoadInfo
import com.ignitefusion.ad.IgniteFusionAd
import com.ignitefusion.ad.IgniteFusionAdLoadListener
import com.ignitefusion.ad.IgniteFusionAdShowListener
import com.wdit.shrmtfx.dcllk.kllcd.config.AppConfig
import com.ignitefusion.app.dialog.DialogHelper
import com.sparkfusionad.app.R

class StartActivity : AppCompatActivity(){
    private lateinit var binding: ActivityStartBinding
    private lateinit var appConfig: AppConfig
    var canJumpImmediately: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)


        appConfig = AppConfig()
        if (appConfig.getFirst()) {
            //第一次进入
            this.let {
                DialogHelper.showLaunchAgreementDialog(
                    it,
                    getString(R.string.welcome_use),
                    onClickWeb = { title, url ->
                        WebViewActivity.start(this, title, url)
                    },
                    onAgree = {
                        if(SdkManager.initAd()){
                            loadAndShowSplashAd()
                        }else{
                            countdown()
                        }
                        appConfig.setFirst(false)
                    },
                    onCancel = {
                        finish()
                    })
            }
            //}
        } else {
            if(SdkManager.initAd()){
                loadAndShowSplashAd()
            }else{
                countdown()
            }
        }




    }

    private fun loadAndShowSplashAd() {
        IgniteFusionAd.loadIFSplashAd(
            context = this,
            adId = Common.POS_ID_Splash,
            loadThirdPartyAd = { info: SdkThirdPartyLoadInfo ->
                Log.d("IgniteFusionAd", "加载第三方开屏广告: adUnitId=${info.adUnitId}, allowLoad=${info.allowLoad}, remainingShows=${info.remainingShows}, remainingClicks=${info.remainingClicks}, reason=${info.reason}")
                info.reportShow()
                Toast.makeText(this, "加载第三方开屏广告", Toast.LENGTH_SHORT).show()
                countdown()
            },
            listener = IgniteFusionAdLoadListener(
                onAdLoadSuccess = {
                    Log.d("IgniteFusionAd", "开屏广告加载成功")
                    IgniteFusionAd.showIFSplashAd(
                        binding.fl,
                        IgniteFusionAdShowListener(
                            onAdShowSuccess = {
                                Log.d("IgniteFusionAd", "开屏广告显示成功")
                            },
                            onAdShowFailure = { err ->
                                countdown()
                                Log.e("IgniteFusionAd", "开屏广告显示失败: ${err.message}", err)
                            },
                            onAdClick = {
                                Log.d("IgniteFusionAd", "开屏广告被点击")
                            },
                            onAdClose = {
                                startActivity()
                                Log.d("IgniteFusionAd", "开屏广告被关闭")
                            }
                        )
                    )
                },
                onAdLoadFailure = { err ->
                    countdown()
                    Log.e("IgniteFusionAd", "开屏广告加载失败: ${err.message}", err)
                }
            )
        )
    }

    //倒计时
    private fun countdown() {
        val timer: CountDownTimer = object : CountDownTimer(
            2500, 10
        ) {
            override fun onTick(millisUntilFinished: Long) {
                //tv_time.setText(millisUntilFinished/1000+"秒");
            }

            override fun onFinish() {
                startActivity()

            }
        }
        timer.start()
    }
    //跳转到主页
    private fun startActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun jumpWhenCanClick() {
        if (canJumpImmediately) {
            startActivity()
        } else {
            canJumpImmediately = true
        }
    }

    override fun onPause() {
        super.onPause()
        canJumpImmediately = false;
    }

    override fun onResume() {
        super.onResume()
        if (canJumpImmediately) {
            jumpWhenCanClick();
        }
        canJumpImmediately = true;
    }

}
