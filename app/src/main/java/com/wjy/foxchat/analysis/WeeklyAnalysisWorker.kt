package com.wjy.foxchat.analysis

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wjy.foxchat.data.repository.ChatRepository

class WeeklyAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = ChatRepository.get(applicationContext)
        repository.syncNow()
        if (!repository.hasBothAnalysisConsent()) return Result.success()
        val weekKey = AnalysisRepository.currentWeekKey()
        if (repository.latestReport()?.weekKey == weekKey) return Result.success()

        val since = repository.latestReport()?.createdAt ?: 0L
        val messages = repository.messagesForAnalysis(since)
        if (messages.isEmpty()) return Result.success()

        val generated = AnalysisRepository(applicationContext)
            .generateWeeklyReport(repository.currentConversationId, messages)
        return generated.fold(
            onSuccess = {
                repository.saveReport(it)
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }
}
