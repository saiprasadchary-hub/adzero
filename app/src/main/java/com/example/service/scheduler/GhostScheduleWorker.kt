package com.example.service.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class GhostScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            GhostScheduleManager(applicationContext).evaluateAndApplyNow("WorkManager boundary")
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
