package com.wayfare.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString(KEY_ACCOUNT) ?: return Result.failure()
        val repository = (applicationContext as WayfareApplication).container.repository
        return runCatching { repository.syncOutbox(accountId) }
            .fold(
                onSuccess = { needsRetry -> if (needsRetry) Result.retry() else Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val KEY_ACCOUNT = "account_id"

        fun enqueue(context: Context, accountId: String) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(KEY_ACCOUNT to accountId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "expense-sync-$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, request,
            )
        }
    }
}
