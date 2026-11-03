package com.gangas.meli.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gangas.core.model.Deal
import com.gangas.meli.GangasApp
import com.gangas.meli.R
import com.gangas.meli.ui.formatPrice
import java.util.concurrent.TimeUnit

object ScanScheduler {
    private const val PERIODIC = "gangas-periodic-scan"
    private const val NOW = "gangas-scan-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun reschedule(context: Context) {
        val app = context.applicationContext as GangasApp
        val s = app.container.settings.state.value
        val wm = WorkManager.getInstance(context)
        if (!s.alertsEnabled || app.container.watches.state.value.none { it.enabled }) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<ScanWorker>(s.scanIntervalMin.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScanWorker>().setConstraints(constraints).build(),
        )
    }
}

class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as GangasApp).container
        val s = c.settings.state.value
        var anyError = false
        for (w in c.watches.state.value.filter { it.enabled }) {
            try {
                val out = c.scanner.scan(s.site, w.query, s.pages, s.mode)
                val hits = out.analysis.deals.filter { d ->
                    d.score >= w.minScore &&
                        (w.maxPrice == null || d.listing.price <= w.maxPrice) &&
                        !c.history.wasNotified(d.listing.id, d.listing.price)
                }
                if (hits.isNotEmpty()) {
                    Notifier.notifyDeals(applicationContext, w.query, hits)
                    c.history.markNotified(hits.map { it.listing.id to it.listing.price })
                }
                c.watches.upsert(w.copy(lastRun = System.currentTimeMillis(), lastFound = hits.size))
            } catch (e: Exception) {
                anyError = true
            }
        }
        return if (anyError && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

object Notifier {
    private const val CHANNEL = "deals"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gangas detectadas", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Alertas de publicaciones con precio anómalo"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun notifyDeals(context: Context, query: String, deals: List<Deal>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(context)
        deals.take(5).forEach { d ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(d.listing.permalink))
            val pi = PendingIntent.getActivity(
                context, d.listing.id.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val pct = (d.belowReference * 100).toInt()
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_deal)
                .setContentTitle("🔥 ${d.score}/100 · -$pct% · ${formatPrice(d.listing.price, d.listing.currency)}")
                .setContentText(d.listing.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    "${d.listing.title}\nReferencia: ${formatPrice(d.referencePrice, d.listing.currency)} · " +
                        d.signals.filter { it.positive }.joinToString { it.label } + "\nBúsqueda: $query"
                ))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup("deals-$query")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            runCatching { nm.notify(d.listing.id.hashCode(), n) }
        }
    }
}
