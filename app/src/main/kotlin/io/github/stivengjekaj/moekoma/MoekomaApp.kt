package io.github.stivengjekaj.moekoma

import android.app.Application
import io.github.stivengjekaj.moekoma.data.AppModel
import io.github.stivengjekaj.moekoma.data.Settings
import io.github.stivengjekaj.moekoma.update.UpdateChecker
import io.github.stivengjekaj.moekoma.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class MoekomaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The parts of the app that live as long as the app. A UI test gives an empty folder in [root]. */
class AppContainer(app: Application, root: File = File(app.filesDir, "Moekoma")) {
    /** Work that continues when the user leaves a screen, such as an import or an update. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val model = AppModel(app, scope, root)
    val settings = Settings(app)
    val updates = UpdateChecker(http)
    val installer = UpdateInstaller(app, http, scope)
}
