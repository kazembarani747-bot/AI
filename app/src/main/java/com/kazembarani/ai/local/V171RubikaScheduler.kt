package com.kazembarani.ai.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object V171RubikaScheduler {
    private const val UNIQUE = "v171_rubika_polling"

    fun start(context: Context) {
        if (V171RubikaStore.token(context).isNullOrBlank() || V171RubikaStore.ownerId(context).isBlank()) return
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<V171RubikaWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
    }
}
