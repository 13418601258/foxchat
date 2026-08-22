package com.wjy.foxchat

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wjy.foxchat.analysis.SyncWorker
import com.wjy.foxchat.analysis.WeeklyAnalysisWorker
import com.wjy.foxchat.notification.ChatNotificationManager
import java.util.concurrent.TimeUnit

class FoxChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
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
