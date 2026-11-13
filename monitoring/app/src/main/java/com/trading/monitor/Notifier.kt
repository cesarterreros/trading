package com.trading.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.trading.monitor.matrix.DecisionMatrix
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class Notifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("notifier", Context.MODE_PRIVATE)

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Monitoring status", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Signal alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The permanent notification required while the monitoring service runs. */
    fun ongoing(text: String): Notification = NotificationCompat.Builder(context, CHANNEL_MONITOR)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle("BTC signal monitor")
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(openApp())
        .build()

    /** Alerts on a verdict change and when price comes close to a zone shared by two or more accounts. */
    fun check(matrix: DecisionMatrix) {
        val verdict = matrix.verdict.name
        val previous = prefs.getString("verdict", null)
        if (previous != null && previous != verdict) {
            alert(
                ID_VERDICT,
                "Verdict: ${matrix.verdict.label}",
                String.format(Locale.US, "Score %.0f, %d bullish / %d bearish accounts", matrix.score, matrix.bullCount, matrix.bearCount),
            )
        }
        if (previous != verdict) prefs.edit().putString("verdict", verdict).apply()

        val now = System.currentTimeMillis()
        for (zone in matrix.zones) {
            val distance = zone.distancePct ?: continue
            if (zone.accounts.size < 2 || abs(distance) > NEAR_PCT) continue
            val key = "zone_${(zone.center / 100).roundToLong()}"
            if (now - prefs.getLong(key, 0) < ZONE_REPEAT_MS) continue
            prefs.edit().putLong(key, now).apply()
            alert(
                ID_ZONE,
                String.format(Locale.US, "Price is near %,.0f", zone.center),
                "${zone.kinds.joinToString("/")} level from ${zone.accounts.joinToString(", ") { "@$it" }}",
            )
        }
    }

    private fun alert(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    companion object {
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_ALERTS = "alerts"
        private const val ID_VERDICT = 10
        private const val ID_ZONE = 11
        private const val NEAR_PCT = 0.3
        private const val ZONE_REPEAT_MS = 6 * 3_600_000L
    }
}
