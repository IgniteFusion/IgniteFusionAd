package com.ignitefusion.ad.ui

import android.app.Activity
import android.app.Dialog
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ignitefusion.ad.IgniteFusionAd
import com.ignitefusion.ad.R

class IgniteFusionRewardVideoActivity : Activity() {

    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rewardRunnable: Runnable? = null
    private var rewardSent = false
    private var rewardEligibleSeconds = 0
    private var playbackStartTime = 0L
    private var pausedAt = 0L
    private var confirmDialog: Dialog? = null
    private var abandonReward = false
    private var onReward: (() -> Unit)? = null
    private var onAdClose: (() -> Unit)? = null
    private var noticeView: ConstraintLayout? = null
    private var noticeShown = false
    private var downloadView: ImageView? = null
    private var downloadAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val session = IgniteFusionAd.consumeRewardSession()
        if (session == null) {
            finish()
            return
        }

        onAdClose = session.onClose
        onReward = session.onReward

        val rewardView = IgniteFusionRewardAD().createView(
            context = this,
            adData = session.adData,
            rewardEligibleSeconds = session.rewardEligibleSeconds,
            onCloseClick = { requestClose() },
            onActionClick = { session.onClick() }
        )

        setContentView(rewardView)

        noticeView = rewardView.findViewById(R.id.notice)
        downloadView = rewardView.findViewById(R.id.notice_download)
        noticeView?.post {
            val view = noticeView ?: return@post
            view.translationY = -view.height.toFloat()
        }
        handler.postDelayed({ showNotice() }, 10_000L)

        val playerView = rewardView.findViewById<PlayerView>(R.id.player_view)
        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(session.videoUrl)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }

        val rewardHint = rewardView.findViewById<TextView>(R.id.rewardHint)
        rewardEligibleSeconds = session.rewardEligibleSeconds
        playbackStartTime = SystemClock.elapsedRealtime()

        rewardRunnable = object : Runnable {
            override fun run() {
                if (!rewardSent) {
                    val remaining = remainingRewardSeconds()
                    if (remaining <= 0) {
                        rewardHint.text = "已获得奖励"
                        deliverReward()
                    } else {
                        rewardHint.text = "观看${remaining}秒获得奖励"
                    }
                }
                handler.postDelayed(this, 500L)
            }
        }.also { handler.post(it) }
    }

    override fun onBackPressed() {
        val dialog = confirmDialog
        if (dialog?.isShowing == true) {
            dialog.dismiss()
            return
        }
        requestClose()
    }

    override fun onDestroy() {
        if (isFinishing) {
            onAdClose?.invoke()
            onAdClose = null
        }

        val currentAnimator = downloadAnimator
        if (currentAnimator != null) {
            currentAnimator.cancel()
        }
        downloadAnimator = null

        rewardRunnable?.let { handler.removeCallbacks(it) }
        rewardRunnable = null
        confirmDialog?.dismiss()
        confirmDialog = null

        val currentPlayer = player
        if (currentPlayer != null) {
            currentPlayer.release()
        }
        player = null

        super.onDestroy()
    }

    private fun remainingRewardSeconds(): Int {
        val now = if (pausedAt > 0L) pausedAt else SystemClock.elapsedRealtime()
        val elapsedSeconds = ((now - playbackStartTime) / 1000L).toInt()
        return (rewardEligibleSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    private fun deliverReward() {
        if (rewardSent) {
            return
        }
        rewardSent = true
        val callback = onReward
        onReward = null
        callback?.invoke()
    }

    private fun requestClose() {
        if (isFinishing || confirmDialog?.isShowing == true) {
            return
        }
        if (remainingRewardSeconds() <= 0) {
            deliverReward()
            finish()
            return
        }
        showCloseConfirm(remainingRewardSeconds())
    }

    private fun showCloseConfirm(remainingSeconds: Int) {
        pausePlayback()
        val content = layoutInflater.inflate(R.layout.reward_close_confirm, null)
        content.findViewById<TextView>(R.id.confirmMessage).text =
            "再观看${remainingSeconds}秒即可获得奖励，现在离开将无法获得奖励"
        val dialog = Dialog(this, R.style.IgniteFusionInsertDialog)
        content.setOnClickListener { dialog.dismiss() }
        content.findViewById<View>(R.id.giveUp).setOnClickListener {
            abandonReward = true
            dialog.dismiss()
            finish()
        }
        content.findViewById<View>(R.id.continueWatch).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setCancelable(true)
        dialog.setContentView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        dialog.setOnDismissListener {
            confirmDialog = null
            if (!abandonReward && !isFinishing) {
                resumePlayback()
            }
        }
        confirmDialog = dialog
        applyConfirmDialogWindow(dialog)
        dialog.show()
        applyConfirmDialogWindow(dialog)
    }

    private fun applyConfirmDialogWindow(dialog: Dialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.setPadding(0, 0, 0, 0)
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window.setDimAmount(0.6f)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun pausePlayback() {
        if (pausedAt == 0L) {
            pausedAt = SystemClock.elapsedRealtime()
        }
        player?.pause()
    }

    private fun resumePlayback() {
        if (pausedAt > 0L) {
            playbackStartTime += SystemClock.elapsedRealtime() - pausedAt
            pausedAt = 0L
        }
        player?.play()
    }

    private fun showNotice() {
        if (noticeShown) {
            return
        }
        noticeShown = true

        val view = noticeView ?: return
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450L)
            .setInterpolator(OvershootInterpolator())
            .start()

        startDownloadAnimation()
    }

    private fun startDownloadAnimation() {
        val view = downloadView ?: return
        val travel = dp(14f)
        view.translationY = -travel
        downloadAnimator = ValueAnimator.ofFloat(-travel, travel).apply {
            duration = 700L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener { animator ->
                view.translationY = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }
}
