package com.trading.monitor

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.trading.monitor.db.AppDb
import com.trading.monitor.db.MIGRATION_1_2
import com.trading.monitor.db.MonitorDao
import com.trading.monitor.ingest.MonitorService
import com.trading.monitor.ingest.Pipeline
import com.trading.monitor.llm.DeepSeek
import com.trading.monitor.market.Market
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Holds the app's long-lived objects; screens and the service reach them through the Application. */
class App : Application() {
    lateinit var settings: Settings
    lateinit var dao: MonitorDao
    lateinit var market: Market
    lateinit var pipeline: Pipeline
    lateinit var notifier: Notifier

    /** Emitting here refreshes now: the service starts a work cycle immediately. */
    val pollNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The post being added by hand; shared by the Add post screen and the share-sheet entry. */
    val draft = PostDraft()

    /** For work that must outlive screens, such as saving a post. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        dao = Room.databaseBuilder(this, AppDb::class.java, "monitor.db")
            .addMigrations(MIGRATION_1_2)
            .build()
            .dao()
        val http = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        market = Market(http)
        pipeline = Pipeline(dao, settings, DeepSeek(http, settings), market, http)
        notifier = Notifier(this)
        notifier.ensureChannels()
    }

    /** Starts or stops the monitoring service to match the setting. Call while the app is in front. */
    fun applyMonitoring() {
        val intent = Intent(this, MonitorService::class.java)
        if (settings.monitoringEnabled) ContextCompat.startForegroundService(this, intent) else stopService(intent)
    }
}
