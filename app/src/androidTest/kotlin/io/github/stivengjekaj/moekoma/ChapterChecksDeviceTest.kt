package io.github.stivengjekaj.moekoma

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.stivengjekaj.moekoma.core.Book
import io.github.stivengjekaj.moekoma.data.ChapterChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The checks for new chapters in the background: the request to Android, and the notification of each book. */
@RunWith(AndroidJUnit4::class)
class ChapterChecksDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun states() = WorkManager.getInstance(app).getWorkInfosForUniqueWork(ChapterChecks.WORK).get().map { it.state }

    @Test fun asksAndroidForTheChecksOnlyWhileTheyAreOn() {
        ChapterChecks.schedule(app, on = true)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states())
        // A second request keeps the first, so that each start of the app does not move the next check.
        ChapterChecks.schedule(app, on = true)
        assertEquals(listOf(WorkInfo.State.ENQUEUED), states())
        ChapterChecks.schedule(app, on = false)
        assertTrue(states().none { it == WorkInfo.State.ENQUEUED })
    }

    @Test fun showsANotificationForEachBookWithNewChapters() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = app.getSystemService(NotificationManager::class.java)
        val tea = Book(title = "Dawn Tea", fileName = "Quiet", pageCount = 3)
        val sea = Book(title = "Sea Glass", fileName = "Quiet", pageCount = 3)
        ChapterChecks.notify(app, listOf(tea to 2, sea to 0))
        // Android posts a notification on its own thread, so the test waits for it.
        var shown = manager.activeNotifications.filter { it.tag == "chapters" }
        repeat(50) { if (shown.isEmpty()) { Thread.sleep(100); shown = manager.activeNotifications.filter { it.tag == "chapters" } } }
        assertEquals(listOf("Dawn Tea"), shown.map { it.notification.extras.getString("android.title") })
        assertEquals("2 new chapters", shown.single().notification.extras.getCharSequence("android.text").toString())
        manager.cancel("chapters", tea.id.hashCode())
    }
}
