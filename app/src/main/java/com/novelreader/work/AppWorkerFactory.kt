package com.novelreader.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.novelreader.core.AppContainer

/** Manual-DI worker factory: hands AppContainer to each CoroutineWorker. */
class AppWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        DownloadWorker::class.java.name -> DownloadWorker(appContext, workerParameters, container)
        else -> null // fall back to default instantiation
    }
}
