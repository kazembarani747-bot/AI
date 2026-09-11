package com.kazembarani.ai.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

/** Queues a durable foreground build job and survives the UI being closed. */
object BuildJobLauncher {
    fun enqueue(context: Context, request: String, budgetMinutes: Int = 30, install: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        val data = Data.Builder()
            .putString(AutonomousBuildWorker.KEY_JOB_ID, id)
            .putString(AutonomousBuildWorker.KEY_REQUEST, request.trim())
            .putInt(AutonomousBuildWorker.KEY_BUDGET, budgetMinutes)
            .putBoolean(AutonomousBuildWorker.KEY_INSTALL, install)
            .build()
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = OneTimeWorkRequestBuilder<AutonomousBuildWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "ai-build-$id", ExistingWorkPolicy.REPLACE, work
        )
        return id
    }

    const val TAG = "ai-autonomous-build"
}
