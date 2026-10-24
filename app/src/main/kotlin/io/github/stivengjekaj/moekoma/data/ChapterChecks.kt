package io.github.stivengjekaj.moekoma.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.stivengjekaj.moekoma.MainActivity
import io.github.stivengjekaj.moekoma.MoekomaApp
import io.github.stivengjekaj.moekoma.R
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.ui.Words
import java.util.concurrent.TimeUnit

/**
 * The checks for new chapters in the background, and the notifications of their results, as in Asabunko. Android runs
 * a check about every six hours when the device has a network.
 */
object ChapterChecks {
    internal const val WORK = "chapter-check"
    const val CHANNEL = "chapters"
    /** The ID of the book that a tap on a notification opens. */
    const val EXTRA_BOOK = "io.github.stivengjekaj.moekoma.book"

    /** Asks Android for the checks, or removes them when the user turned the checks off. */
    fun schedule(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<ChapterCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** True when Android lets the app show notifications. */
    fun mayNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One notification for each book with new chapters. A tap on it opens the book. */
    fun notify(context: Context, found: List<Pair<Book, Int>>) {
        if (!mayNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, Words.of(context, "New chapters"), NotificationManager.IMPORTANCE_DEFAULT))
        for ((book, added) in found) {
            if (added <= 0) continue
            val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_BOOK, book.id.toString()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(context, book.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_chapters)
                .setContentTitle(book.title)
                .setContentText(Words.of(context, if (added == 1) "1 new chapter" else "$added new chapters"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify("chapters", book.id.hashCode(), notification)
        }
    }

    /** False when the user allows the downloads of new chapters only on Wi-Fi and the network of the device costs money. */
    fun mayDownloadNow(context: Context, wifiOnly: Boolean): Boolean =
        !wifiOnly || context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == false
}

/** The check of the read list in the background. Each book with new chapters gets a notification. */
class ChapterCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MoekomaApp).container
        val check = container.sources.refreshAll(listedOnly = true)
        // Android can stop the app after the work, so the library writes at once.
        container.model.flush()
        ChapterChecks.notify(applicationContext, check.found)
        return Result.success()
    }
}
