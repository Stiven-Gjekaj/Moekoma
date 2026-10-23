package io.github.stivengjekaj.moekoma.data

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebSettings
import dalvik.system.DelegateLastClassLoader
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.stivengjekaj.moekoma.core.InstalledSource
import io.github.stivengjekaj.moekoma.core.OnlineChapter
import io.github.stivengjekaj.moekoma.core.OnlineDetails
import io.github.stivengjekaj.moekoma.core.OnlinePage
import io.github.stivengjekaj.moekoma.core.OnlineSeries
import io.github.stivengjekaj.moekoma.core.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MihonException(message: String) : Exception(message)

/**
 * Installs and runs the Mihon extensions on the device, as Mihon does with its private extensions. Each APK is a file
 * of the app, and its classes load with a class loader of the app. The extensions use the source API of the module
 * ":mihon" and the libraries of the app.
 */
class MihonHost(private val context: Context, private val http: OkHttpClient) {
    private val folder = File(context.filesDir, "Mihon")

    /**
     * The sources of each APK that loaded, by the path of the APK. One APK can hold many sources. A list can install
     * hundreds of APKs, so only the APKs that the user used last stay in the memory.
     */
    private val loaded = object : LinkedHashMap<String, List<Source>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Source>>) = size > 24
    }

    init {
        setUpInjekt(context.applicationContext as Application)
    }

    /** The file of the APK of a source. Two sources of one extension have the same file. */
    private fun apkFile(info: SourceInfo): File {
        val name = info.code.substringBefore('?').substringAfterLast('/').ifEmpty { "extension.apk" }
        return File(folder, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    /**
     * Downloads the APK of a source, when the device does not have it, and checks it. The APK must be a Mihon
     * extension for a version of the library that the app runs, and the key of the repository must sign it.
     */
    suspend fun install(info: SourceInfo, signingKey: String) = withContext(Dispatchers.IO) {
        val target = apkFile(info)
        if (target.isFile) return@withContext
        folder.mkdirs()
        val part = File(folder, target.name + ".part")
        http.newCall(Request.Builder().url(info.code).build()).execute().use { response ->
            if (!response.isSuccessful) throw MihonException("The APK did not download (HTTP ${response.code}).")
            part.outputStream().use { response.body.byteStream().copyTo(it) }
        }
        try {
            check(part, signingKey)
            // Android 14 and later load code only from a file that the app cannot write.
            if (!part.renameTo(target)) throw MihonException("The APK was not saved.")
            target.setReadOnly()
        } finally {
            part.delete()
        }
    }

    /** Removes the APK of a source when no other installed source uses it. */
    fun uninstall(info: SourceInfo, others: List<SourceInfo>) {
        val file = apkFile(info)
        if (others.any { apkFile(it) == file }) return
        synchronized(loaded) { loaded.remove(file.path) }
        file.setWritable(true)
        file.delete()
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(file: File): PackageInfo {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_CONFIGURATIONS or
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.path, flags) ?: throw MihonException("The file is not an APK.")
        // The info of an archive has no path on Android 13 and later.
        info.applicationInfo?.apply {
            if (sourceDir == null) sourceDir = file.path
            if (publicSourceDir == null) publicSourceDir = file.path
        }
        return info
    }

    private fun check(file: File, signingKey: String) {
        val info = packageInfo(file)
        if (info.reqFeatures.orEmpty().none { it.name == "tachiyomi.extension" }) throw MihonException("The APK is not a Mihon extension.")
        val lib = libVersion(info)
        if (lib == null || lib !in io.github.stivengjekaj.moekoma.core.MihonRepo.supportedLibs) {
            throw MihonException("The extension is for the library $lib. Moekoma runs 1.4 and 1.6.")
        }
        val signatures = signatures(info)
        if (signatures.isEmpty()) throw MihonException("The extension is not signed.")
        if (signingKey.lowercase() !in signatures) throw MihonException("The key of the repository did not sign this extension.")
    }

    private fun libVersion(info: PackageInfo): Double? {
        val meta = info.applicationInfo?.metaData
        val fromMeta = meta?.getFloat("tachiyomix.extensionLib")?.takeIf { it != 0f }?.toString()?.toDouble()
        return fromMeta ?: info.versionName?.substringBeforeLast('.')?.toDoubleOrNull()
    }

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): List<String> {
        val list = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo ?: return emptyList()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            info.signatures
        }
        return list.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    /** Loads the source of an installation. The classes of one APK load one time. */
    suspend fun load(installed: InstalledSource): OnlineSource = withContext(Dispatchers.IO) {
        val file = apkFile(installed.info)
        if (!file.isFile) throw MihonException("The extension of ${installed.info.name} is not on this device. Save the source list again.")
        val sources = synchronized(loaded) { loaded[file.path] } ?: loadApk(file).also { synchronized(loaded) { loaded[file.path] = it } }
        val source = sources.firstOrNull { it.id.toString() == installed.info.id } as? CatalogueSource
            ?: throw MihonException("The extension does not have the source ${installed.info.name}.")
        MihonSource(installed, source)
    }

    private fun loadApk(file: File): List<Source> = try {
        loadClasses(file)
    } catch (e: LinkageError) {
        throw MihonException("The extension did not start: ${e.message ?: e.javaClass.simpleName}")
    }

    private fun loadClasses(file: File): List<Source> {
        if (file.canWrite()) file.setReadOnly()
        val info = packageInfo(file)
        val classes = info.applicationInfo?.metaData?.getString("tachiyomi.extension.class")
            ?: throw MihonException("The extension does not name its sources.")
        val loader = classLoader(file.path, context.classLoader)
        return classes.split(';').map { it.trim() }.filter { it.isNotEmpty() }.flatMap { name ->
            val full = if (name.startsWith(".")) info.packageName + name else name
            when (val made = Class.forName(full, false, loader).getDeclaredConstructor().newInstance()) {
                is Source -> listOf(made)
                is SourceFactory -> made.createSources()
                else -> throw MihonException("The class $full is not a source.")
            }
        }
    }

    companion object {
        @Volatile private var injektReady = false

        /** The objects that the extensions ask Injekt for. These are the bindings of Mihon. */
        fun setUpInjekt(app: Application) = synchronized(this) {
            if (injektReady) return
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
            Injekt.addSingleton<Application>(app)
            Injekt.addSingleton<Context>(app)
            Injekt.addSingleton<Json>(json)
            // The type must be ProtoBuf. The object ProtoBuf alone has the type of its companion.
            Injekt.addSingleton<ProtoBuf>(ProtoBuf)
            Injekt.addSingletonFactory { NetworkHelper(app) { userAgent(app) } }
            injektReady = true
        }

        private fun userAgent(context: Context): String = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrElse {
            "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
        }

        /**
         * A class loader that looks in the APK before the app, as Mihon does, so that an extension gets its own
         * copy of a class that the app also has. The boot classes come first.
         */
        @SuppressLint("NewApi")
        fun classLoader(path: String, parent: ClassLoader): ClassLoader =
            if (Build.VERSION.SDK_INT >= 27) DelegateLastClassLoader(path, null, parent) else ApkFirstClassLoader(path, parent)
    }
}

/** The class loader of Mihon for Android 8.0, which has no DelegateLastClassLoader. */
private class ApkFirstClassLoader(path: String, parent: ClassLoader) : PathClassLoader(path, null, parent) {
    private val boot: ClassLoader? = Any::class.java.classLoader

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        findLoadedClass(name)?.let { return it }
        boot?.let { runCatching { return it.loadClass(name) } }
        val notHere = try {
            return findClass(name)
        } catch (e: ClassNotFoundException) {
            e
        }
        return try {
            parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
            throw notHere
        }
    }
}

/**
 * A Mihon source as an [OnlineSource]. The ID of a series or a chapter is its URL in the source. A source of
 * library 1.6 can keep facts in a memo, so the memo follows the URL after a line break.
 */
class MihonSource(private val installed: InstalledSource, private val source: CatalogueSource) : OnlineSource {
    override val info: SourceInfo get() = installed.info
    override val canShowLatest: Boolean get() = source.supportsLatest

    override suspend fun popular(page: Int) = io { source.getPopularManga(page).page() }
    override suspend fun latest(page: Int) = io { source.getLatestUpdates(page).page() }
    override suspend fun search(query: String, page: Int) = io { source.getSearchManga(page, query, FilterList()).page() }

    override suspend fun details(series: OnlineSeries): OnlineDetails = io {
        val manga = manga(series)
        val update = source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
        val details = update.manga
        // A Mihon source gives the newest chapter first.
        val chapters = update.chapters.asReversed().map { chapter ->
            OnlineChapter(
                id = id(chapter.url, chapter.memo), title = chapter.name.trim().ifEmpty { "Chapter" },
                number = chapter.chapter_number.takeIf { it >= 0f }?.toDouble(),
                date = chapter.date_upload.takeIf { it > 0 }?.let { dateFormat.format(Date(it)) },
                group = chapter.scanlator?.trim()?.ifEmpty { null },
            )
        }
        OnlineDetails(
            series = OnlineSeries(series.id, details.title.ifEmpty { series.title }, details.thumbnail_url?.ifEmpty { null } ?: series.cover),
            author = listOfNotNull(details.author, details.artist).flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                .joinToString(", ").ifEmpty { null },
            summary = details.description?.trim()?.ifEmpty { null },
            status = status(details.status),
            genres = details.genre?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            chapters = chapters,
            isNovel = false,
        )
    }

    override suspend fun content(chapter: OnlineChapter, series: OnlineSeries): ChapterContent = io {
        val (url, memo) = split(chapter.id)
        val item = SChapter.create().apply {
            this.url = url
            name = chapter.title
            memo?.let { this.memo = it }
        }
        val pages = source.getPageList(item)
        ChapterContent.Pages(pages.mapIndexed { index, page -> RemoteImage(installed.id, page, "${installed.id}|${chapter.id}|$index") })
    }

    override suspend fun image(image: RemoteImage): Response = io {
        val http = source as? HttpSource ?: throw MihonException("${info.name} gives no images.")
        when (val item = image.item) {
            is Page -> {
                if (item.imageUrl.isNullOrEmpty()) item.imageUrl = http.getImageUrl(item)
                http.getImage(item)
            }
            is String -> http.client.newCall(GET(item, http.headers)).awaitSuccess()
            else -> throw MihonException("The image is not of this source.")
        }
    }

    private fun MangasPage.page() = OnlinePage(
        mangas.map { OnlineSeries(id(it.url, it.memo), it.title, it.thumbnail_url?.ifEmpty { null }) },
        hasNextPage,
    )

    private fun manga(series: OnlineSeries): SManga {
        val (url, memo) = split(series.id)
        return SManga.create().apply {
            this.url = url
            title = series.title
            thumbnail_url = series.cover
            memo?.let { this.memo = it }
        }
    }

    /** Runs the code of the extension. An error of the extension, such as a missing class, becomes a fault of this source. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: LinkageError) {
            throw MihonException("${info.name} failed: ${e.cause?.message ?: e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

        fun id(url: String, memo: JsonObject): String = if (memo.isEmpty()) url else url + "\n" + memo.toString()

        fun split(id: String): Pair<String, JsonObject?> {
            val at = id.indexOf('\n')
            if (at < 0) return id to null
            val memo = runCatching { Json.parseToJsonElement(id.substring(at + 1)) as JsonObject }.getOrNull()
            return id.substring(0, at) to memo
        }

        fun status(value: Int): String? = when (value) {
            SManga.ONGOING -> "Ongoing"
            SManga.COMPLETED -> "Completed"
            SManga.LICENSED -> "Licensed"
            SManga.PUBLISHING_FINISHED -> "Publishing finished"
            SManga.CANCELLED -> "Cancelled"
            SManga.ON_HIATUS -> "On hiatus"
            else -> null
        }
    }
}
