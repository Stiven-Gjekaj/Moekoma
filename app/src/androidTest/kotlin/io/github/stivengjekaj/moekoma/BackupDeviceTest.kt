package io.github.stivengjekaj.moekoma

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stivengjekaj.moekoma.core.Backup
import io.github.stivengjekaj.moekoma.core.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/** A backup of a library with an online book, restored into a new library, and the import of a Mihon backup. */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private suspend fun container(name: String) = withContext(Dispatchers.Main) {
        AppContainer(app, File(app.cacheDir, name).apply { deleteRecursively() })
    }

    /** It needs scripts/test-server.py, so it runs only with the argument server=1. */
    @Test fun restoresALibraryWithItsOnlineBookAndItsSource() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("server") == "1")
        val first = container("backup-first")
        first.sources.save("http://10.0.2.2:8765/aidoku/index.min.json")
        repeat(60) { if (first.model.library.value.installed.none { it.info.id == "en.test" }) delay(500) }
        val installed = first.model.library.value.installed.first { it.info.id == "en.test" }
        val source = first.sources.load(installed)
        val book = first.sources.add(source.details(source.popular(1).items.single()), installed, listed = true)
        first.model.update(book.id) { it.read(chapter = 0, position = 0.5) }
        val bytes = first.model.backup("Moekoma test")

        val second = container("backup-second")
        val (plan, failed) = second.sources.restore(Backup.decode(bytes))
        assertEquals(emptyList<String>(), failed)
        assertEquals(emptyList<String>(), plan.skipped)
        val restored = second.model.library.value.books.single()
        assertEquals("Aidoku Sunrise", restored.title)
        assertEquals(0.5, restored.position, 0.0)
        assertEquals(listOf("Opening"), Novel.load(second.model.folder(restored)).chapters.map { it.title?.substringAfter(": ") })
        // The code of the source is on the device again, so the book opens.
        assertEquals("Aidoku Sunrise", second.sources.load(second.model.library.value.installed.single { it.info.id == "en.test" }).popular(1).items.single().title)
        File(app.cacheDir, "backup-first").deleteRecursively()
        File(app.cacheDir, "backup-second").deleteRecursively()
        Unit
    }

    @Test fun keepsTheSeriesOfAMihonBackupUntilTheirSourceInstalls() = runBlocking {
        val container = container("backup-mihon")
        // A backup of one series of the source 4242, which is not installed, in the category "Reading".
        fun field(number: Int, bytes: ByteArray) = byteArrayOf(((number shl 3) or 2).toByte()) + byteArrayOf(bytes.size.toByte()) + bytes
        fun text(number: Int, value: String) = field(number, value.toByteArray())
        val manga = byteArrayOf(0x08) + byteArrayOf(0x92.toByte(), 0x21) + text(2, "/m/1") + text(3, "Quiet Tea") + byteArrayOf(0x88.toByte(), 0x01, 0x01)
        val file = field(1, manga) + field(2, text(1, "Reading") + byteArrayOf(0x10, 0x01))
        val gzip = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(file) } }.toByteArray()
        val result = container.sources.importMihon(gzip)
        assertEquals(0, result.added)
        assertEquals(mapOf("4242" to 1), result.waiting)
        assertEquals(listOf("Reading"), container.model.library.value.categories.map { it.name })
        assertTrue("The backup waits for the source.", File(container.model.root, "mihon-pending.tachibk").isFile)
        File(app.cacheDir, "backup-mihon").deleteRecursively()
        Unit
    }
}
