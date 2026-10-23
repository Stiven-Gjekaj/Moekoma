package io.github.stivengjekaj.moekoma

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.stivengjekaj.moekoma.data.PdfPageFetcher
import io.github.stivengjekaj.moekoma.data.PdfPageKeyer
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Folders
import io.github.stivengjekaj.moekoma.data.RemoteImageFetcher
import io.github.stivengjekaj.moekoma.data.RemoteImageKeyer
import io.github.stivengjekaj.moekoma.data.Settings
import io.github.stivengjekaj.moekoma.data.Sources
import io.github.stivengjekaj.moekoma.update.UpdateChecker
import io.github.stivengjekaj.moekoma.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class MoekomaApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** The image loader of the app also draws the pages of a PDF and the images of the sources. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.http }))
                add(PdfPageFetcher.Factory())
                add(PdfPageKeyer())
                add(RemoteImageFetcher.Factory { container.sources.load(it) })
                add(RemoteImageKeyer())
            }
            .build()
}

/** The parts of the app that live as long as the app. A UI test gives an empty folder in [root]. */
class AppContainer(app: Application, root: File = File(app.filesDir, "Moekoma")) {
    /** Work that continues when the user leaves a screen, such as an import or an update. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val model = AppModel(app, scope, root, http)
    val folders = Folders(app, model, root, scope)
    val settings = Settings(app)
    val sources = Sources(app, model, settings, http, scope)
    val updates = UpdateChecker(http)
    val installer = UpdateInstaller(app, http, scope)
}
