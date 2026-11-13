package com.trading.monitor.ingest

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.trading.monitor.App
import com.trading.monitor.computeMatrix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps market data fresh, interprets waiting posts and sends alerts while the app is in the background. */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as App
        try {
            val notification = app.notifier.ongoing("Price and verdict alerts active")
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // The system refused a background start; monitoring resumes when the app is opened.
            stopSelf()
            return
        }
        scope.launch { marketLoop(app) }
        scope.launch { workLoop(app) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Price, funding and notifications: once a minute. */
    private suspend fun marketLoop(app: App) {
        while (scope.isActive) {
            try {
                app.market.refresh()
                val matrix = computeMatrix(
                    app.dao.accountsOnce(), app.dao.signalsOnce(), app.market.state.value,
                    app.settings.matrixConfig(), System.currentTimeMillis(),
                )
                app.notifier.check(matrix)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Transient failure: the next pass retries.
            }
            delay(60_000)
        }
    }

    /** Interprets waiting posts and resolves signal outcomes; wakes every 5 minutes or on a refresh request. */
    private suspend fun workLoop(app: App) {
        var outcomesAtMs = 0L
        while (scope.isActive) {
            try {
                app.pipeline.processPending()
                val now = System.currentTimeMillis()
                if (now - outcomesAtMs > 3_600_000) {
                    app.pipeline.resolveOutcomes()
                    outcomesAtMs = now
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.pipeline.status.value = "Processing failed: ${e.message}"
            }
            val status = app.pipeline.status
            // Keep a problem message visible; otherwise show idle.
            if (!status.value.contains("missing") && !status.value.contains("failed") && !status.value.contains("cap reached")) {
                status.value = "Idle"
            }
            withTimeoutOrNull(5 * 60_000L) { app.pollNow.first() }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
