package com.wjy.foxchat

import android.app.Application
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wjy.foxchat.analysis.SyncWorker
import com.wjy.foxchat.analysis.WeeklyAnalysisWorker
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.wjy.foxchat.notification.ChatNotificationManager
import java.util.concurrent.TimeUnit

class FoxChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 全局启用 GIF 解码，让宠物动图（宠物页 + 悬浮窗）都能播放
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
        )
        ChatNotificationManager.ensureChannel(this)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            "foxchat-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
        )
        workManager.enqueueUniquePeriodicWork(
            "foxchat-weekly-analysis",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyAnalysisWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
        )
    }
}
