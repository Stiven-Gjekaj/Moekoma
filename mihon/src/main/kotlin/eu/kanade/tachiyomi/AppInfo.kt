package eu.kanade.tachiyomi

import android.app.Application
import androidx.core.content.pm.PackageInfoCompat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Used by extensions. Moekoma gives its own version.
 */
@Suppress("UNUSED")
object AppInfo {
    private val info by lazy {
        val app = Injekt.get<Application>()
        app.packageManager.getPackageInfo(app.packageName, 0)
    }

    /**
     * Version code of the host application. May be useful for sharing as User-Agent information.
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode(): Int = PackageInfoCompat.getLongVersionCode(info).toInt()

    /**
     * Version name of the host application. May be useful for sharing as User-Agent information.
     *
     * @since extension-lib 1.3
     */
    fun getVersionName(): String = info.versionName.orEmpty()

    /**
     * A list of supported image MIME types by the reader.
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "image/heif")
}
