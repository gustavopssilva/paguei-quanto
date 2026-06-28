package com.ajudaqui.pagueiquanto.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ajudaqui.pagueiquanto.PagueiQuantoApplication
import java.lang.Exception

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PagueiQuantoApplication
        val repository = app.repository

        // Se o usuário não configurou email e senha, aborta a sincronização silenciosamente
        if (!repository.hasBackupCredentials()) {
            return Result.success()
        }

        return try {
            val hasChanges = repository.hasPendingChanges()
            if (!hasChanges) {
                return Result.success()
            }

            val data = repository.exportAllData()
            val compressed = repository.compress(data)
            repository.sendBackupToLambda(compressed)
            repository.markBackupSynced()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
