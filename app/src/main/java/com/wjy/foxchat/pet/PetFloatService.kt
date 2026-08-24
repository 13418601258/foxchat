package com.wjy.foxchat.pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import coil.load
import com.wjy.foxchat.R
import com.wjy.foxchat.data.local.FoxChatDatabase
import com.wjy.foxchat.data.local.PetEntity
import com.wjy.foxchat.data.pet.PetManager
import com.wjy.foxchat.ui.PetActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 悬浮宠物前台服务：可拖动悬浮窗。
 * 点击弹出菜单：投喂 / 陪伴（直接操作宠物状态）、最大化（回到宠物页并收起悬浮窗）。
 */
class PetFloatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var menuPopup: PopupWindow? = null

    private var startX = 0
    private var startY = 0
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showFloatView()
        return START_STICKY
    }

    private fun showFloatView() {
        if (floatView != null) return
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val imageView = ImageView(this)
        imageView.load(R.drawable.pet_sleep)
        imageView.contentDescription = "悬浮宠物"
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        // ★ 悬浮窗尺寸：改这里的数字（单位 dp）即可控制悬浮窗大小
        val size = (60 * resources.displayMetrics.density).toInt()

        imageView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params?.x ?: 0
                    startY = params?.y ?: 0
                    startTouchX = event.rawX
                    startTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) {
                        isDragging = true
                    }
                    params?.let {
                        it.x = startX + dx
                        it.y = startY + dy
                        windowManager?.updateViewLayout(floatView, it)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) showMenu()
                    true
                }
                else -> false
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }

        windowManager?.addView(imageView, layoutParams)
        floatView = imageView
    }

    private val params: WindowManager.LayoutParams?
        get() = (floatView?.layoutParams as? WindowManager.LayoutParams)

    /** 点击悬浮窗：弹出菜单 */
    private fun showMenu() {
        menuPopup?.dismiss()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(menuButton("🍖 投喂") { feedPet() })
            addView(menuButton("💕 陪伴") { playPet() })
            addView(menuButton("⤢ 最大化") { maximize() })
        }
        val popup = PopupWindow(
            content,
            dp(130),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.WHITE))
        popup.elevation = 20f
        menuPopup = popup
        floatView?.let { popup.showAsDropDown(it, 0, dp(4)) }
    }

    private fun menuButton(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.parseColor("#2B2B2B"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER
            setOnClickListener {
                menuPopup?.dismiss()
                onClick()
            }
        }

    private fun feedPet() {
        scope.launch {
            val db = FoxChatDatabase.get(this@PetFloatService)
            val pet = db.petDao().get()
            val updated = pet?.let { PetManager.feed(it) } ?: PetEntity()
            db.petDao().upsert(updated)
            // 悬浮窗切换到喂食动画，3 秒后恢复待机
            switchGif(R.drawable.pet_feed)
            delay(3000)
            switchGif(R.drawable.pet_sleep)
            showToast("已投喂，小狐狸很开心！")
        }
    }

    private fun switchGif(resId: Int) {
        Handler(Looper.getMainLooper()).post {
            (floatView as? ImageView)?.load(resId)
        }
    }

    private fun playPet() {
        scope.launch {
            val db = FoxChatDatabase.get(this@PetFloatService)
            val pet = db.petDao().get()
            val updated = pet?.let { PetManager.play(it) } ?: PetEntity()
            db.petDao().upsert(updated)
            showToast("最喜欢你了！")
        }
    }

    /** 最大化：回到宠物页并收起悬浮窗 */
    private fun maximize() {
        val intent = Intent(this, PetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        stopSelf()
    }

    private fun openPetActivity() {
        val intent = Intent(this, PetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        menuPopup?.dismiss()
        floatView?.let { windowManager?.removeView(it) }
        floatView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "悬浮宠物",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "宠物悬浮窗运行中"
        }
        manager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PetActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("小狐狸陪着你")
            .setContentText("点击悬浮窗可以选择投喂、陪伴或最大化")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "foxchat_pet"
        private const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            val intent = Intent(context, PetFloatService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PetFloatService::class.java))
        }
    }
}
