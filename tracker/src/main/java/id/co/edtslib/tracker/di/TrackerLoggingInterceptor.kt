package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.Tracker
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Logs tracking requests to logcat while [Tracker.debugging] is on.
 *
 * Installed as a network interceptor so it sees the request as it goes over the wire —
 * with the auth header, the host app's own headers, and the ones OkHttp adds itself.
 * The flag is read per request, so the host app can turn logging on after `Tracker.init`.
 */
internal class TrackerLoggingInterceptor(
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) : Interceptor {

    private val delegate = HttpLoggingInterceptor(logger).apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        if (Tracker.debugging) {
            delegate.intercept(chain)
        } else {
            chain.proceed(chain.request())
        }
}
