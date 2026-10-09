package com.ignitefusion.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.sparkfusionad.app.databinding.ActivityWebViewBinding
import kotlin.apply
import kotlin.jvm.java
import kotlin.text.orEmpty
import kotlin.text.startsWith
import kotlin.toString

class WebViewActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_TITLE = "extra_title"

        fun start(context: Context, title: String, url: String) {
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_URL, url)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var binding: ActivityWebViewBinding
    private val url by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val pageTitle by lazy { intent.getStringExtra(EXTRA_TITLE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWebViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        initToolbar()
        initWebView()
        binding.webView.loadUrl(url)
    }

    private fun initToolbar() {
        binding.toolbar.title = pageTitle

    }

    private fun initWebView() {
        binding.webView.apply {
            // ✅ 设置基本属性
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.allowFileAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportZoom(false)

            // ✅ 可选：模拟 Chrome 的 User-Agent（可解决某些 CDN 拒绝加载）
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Mobile Safari/537.36"

            // ✅ 页面跳转处理
            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val newUrl = request?.url.toString()
                    return if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                        view?.loadUrl(newUrl)
                        true
                    } else {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(newUrl))
                            startActivity(intent)
                        } catch (_: Exception) {
                        }
                        true
                    }
                }

                // ✅ 错误处理
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    //Log.e("WebViewError", "加载失败：${request.url} - ${error.description}")
                    Toast.makeText(this@WebViewActivity, "页面加载失败", Toast.LENGTH_SHORT).show()
                }
            }

            // ✅ 进度条
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.apply {
                        visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                        progress = newProgress
                    }
                }
            }
        }
    }


    // ✅ 返回键支持 WebView 回退
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}