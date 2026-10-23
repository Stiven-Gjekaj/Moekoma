package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Finds the browser check of Cloudflare. Some extensions put their own interceptors before this one in the client,
 * so the client must have it, with this name. Moekoma does not pass the check in a web view yet, so a page with
 * the check gives a clear fault.
 */
class CloudflareInterceptor : Interceptor {
    fun shouldIntercept(response: Response): Boolean =
        response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!shouldIntercept(response)) return response
        response.close()
        throw IOException("${chain.request().url.host} shows the browser check of Cloudflare. Moekoma cannot pass it yet.")
    }

    companion object {
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }
}
