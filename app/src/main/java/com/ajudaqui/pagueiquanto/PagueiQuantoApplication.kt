package com.ajudaqui.pagueiquanto

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.ajudaqui.pagueiquanto.data.AppDatabase
import com.ajudaqui.pagueiquanto.data.worker.BackupWorker
import com.ajudaqui.pagueiquanto.repository.ShoppingRepository
import java.util.concurrent.TimeUnit

class PagueiQuantoApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ShoppingRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Inicialização centralizada do Room Database
        database = Room.databaseBuilder(this, AppDatabase::class.java, "pagueiquanto-db")
            .fallbackToDestructiveMigration()
            .build()

        // Inicialização centralizada do Repository
        repository = ShoppingRepository(database.shoppingDao(), this)

        // Configuração e agendamento do BackupWorker
        scheduleBackup()
    }

    private fun scheduleBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Apenas com Wi-Fi
            .setRequiresBatteryNotLow(true)               // Evita gastar bateria fraca
            .build()

        // Executa 1 vez ao dia
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "backup_work",
            ExistingPeriodicWorkPolicy.KEEP, // Mantém o agendamento existente
            request
        )
    }
}
