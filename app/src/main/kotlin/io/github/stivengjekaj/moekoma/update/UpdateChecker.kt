package io.github.stivengjekaj.moekoma.update

import io.github.stivengjekaj.moekoma.core.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Asks GitHub for the newest release. It sends no data about the user. */
class UpdateChecker(private val http: OkHttpClient) {
    /** The newest version, when it is newer than [current]. Null with no network. */
    suspend fun newerVersion(current: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(LATEST_API).header("Accept", "application/vnd.github+json").build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val root = Json.parseToJsonElement(response.body.string()) as JsonObject
                    (root["tag_name"] as? JsonPrimitive)?.contentOrNull?.removePrefix("v")
                }
        }.getOrNull()?.takeIf { AppVersion.isNewer(it, current) }
    }

    companion object {
        const val REPOSITORY = "https://github.com/Stiven-Gjekaj/Moekoma"
        const val LATEST_API = "https://api.github.com/repos/Stiven-Gjekaj/Moekoma/releases/latest"
        const val DOWNLOAD_URL = "$REPOSITORY/releases/latest/download/Moekoma.apk"
        const val KOFI = "https://ko-fi.com/stivengjekaj"
    }
}
