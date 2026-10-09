package com.ignitefusion.app.dialog

import android.app.Dialog
import com.sparkfusionad.app.R
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import com.sparkfusionad.app.databinding.DialogLaunchAgreementBinding


object DialogHelper {
    /**
     * 启动页用户须知
     */
    fun showLaunchAgreementDialog(
        context: Context,
        title: String,
        onClickWeb: (String,String) -> Unit,
        onAgree: () -> Unit,
        onCancel: () -> Unit,
    ) {
        // 获取 ViewBinding
        val binding = DialogLaunchAgreementBinding.inflate(LayoutInflater.from(context))
        val dialog = Dialog(context, R.style.dialog_center).apply {
            // 设置对话框内容视图
            setContentView(binding.root)
            // 点击外部不消失
            setCancelable(false)
            // 返回键不消失
            setCanceledOnTouchOutside(false)
        }
        binding.title.text = title


        getHighLightText(context, highlightText2, onClick = {
            onClickWeb(
                highlightText2,
                context.getString(R.string.privacy_policy)
            )
        })

        // 应用到TextView
        binding.content.apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance() // 必须设置
            highlightColor = Color.TRANSPARENT // 移除点击背景色
        }

        binding.btnCancel.setOnClickListener {
            onCancel()
        }

        binding.btnAgree.setOnClickListener {
            onAgree()
            dialog.dismiss()
        }

        // 显示对话框
        dialog.show()
    }
    private val fullText = "\u3000\u3000《SparkFusionAD》由成都萌懂网络科技有限公司开发并运营。为保障您的权益，我们将依据《隐私协议》处理您的个人信息。我们承诺保护您的信息安全。功能需要时（如使用相机等），会向您申请相应权限，不会默认开启。点击“同意”即表示您已阅读并同意《隐私协议》；点击“不同意”仍可浏览基础内容，后续也可单独授权。"

    private val highlightText2 = "《隐私协议》"
    private val spannableString = SpannableString(fullText)
    private fun getHighLightText(
        context: Context,
        text: String,
        onClick: () -> Unit) {

        var lastIndex = 0
        val colorSpan = ForegroundColorSpan(context.getColor(R.color.colorAccent))

        // 循环查找所有匹配项
        while (lastIndex >= 0) {
            val startIndex = fullText.indexOf(text, lastIndex)
            if (startIndex == -1) break // 找不到更多匹配项

            val endIndex = startIndex + text.length

            // 设置颜色
            spannableString.setSpan(
                colorSpan,
                startIndex, endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // 设置点击事件
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false // 去掉下划线
                }
            }
            spannableString.setSpan(
                clickableSpan,
                startIndex, endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            lastIndex = endIndex // 继续向后查找
        }
    }
}