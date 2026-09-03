package id.co.edtslib.tracker.di

import android.util.Log
import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies the host app's headers — the static ones plus whatever [Tracker.headerCallback]
 * adds for this request — to every request sent to [destination].
 *
 * Both are read here, as the request runs, so the host app can configure them at any
 * time, before or after `Tracker.init`.
 */
internal class TrackerHeaderInterceptor(
    private val destination: TrackerDestination
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = TrackerHeaders.build(
            destination = destination,
            request = request,
            staticHeaders = Tracker.headers,
            callback = Tracker.headerCallback
        )
        if (headers.isEmpty()) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()
        headers.forEach { (name, value) ->
            try {
                // header() rather than addHeader(): a name the host app sets wins over
                // one already on the request instead of being sent twice.
                builder.header(name, value)
            } catch (e: IllegalArgumentException) {
                // A value OkHttp rejects (newline, non-ASCII) drops that header only.
                if (Tracker.debugging) {
                    Log.e(TAG, "header $name was rejected", e)
                }
            }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        private const val TAG = "Tracker"
    }
}
