package com.wjy.foxchat.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.wjy.foxchat.data.repository.ChatRepository
import com.wjy.foxchat.notification.ChatNotificationManager

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val outcome = ChatRepository.get(applicationContext).syncNowWithOutcome()
        return outcome.fold(
            onSuccess = { result ->
                val appIsForeground = ProcessLifecycleOwner.get()
                    .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!appIsForeground && result.incomingMessages.isNotEmpty()) {
                    ChatNotificationManager.notifyIncoming(
                        applicationContext,
                        result.incomingMessages
                    )
                }
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }
}
