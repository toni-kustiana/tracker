package id.co.edtslib.tracker.di

import android.util.Log
import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.Request

/**
 * Builds the header set for one outgoing tracking request: the static headers first,
 * then whatever [callback] adds on top of them.
 */
internal object TrackerHeaders {
    private const val TAG = "Tracker"

    fun build(
        destination: TrackerDestination,
        request: Request,
        staticHeaders: Map<String, String>,
        callback: TrackerHeaderCallback?
    ): Map<String, String> {
        val headers = LinkedHashMap(staticHeaders)
        if (callback != null) {
            try {
                callback.onRequest(destination, request, headers)
            } catch (e: Exception) {
                // A bug in the host app's callback must not stop the event from being
                // sent; the headers it managed to set are kept.
                if (Tracker.debugging) {
                    Log.e(TAG, "header callback failed for ${destination.baseUrl}", e)
                }
            }
        }
        return headers
    }
}
