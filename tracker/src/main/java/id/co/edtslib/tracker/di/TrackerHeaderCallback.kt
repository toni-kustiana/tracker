package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.Request

/**
 * Called once per destination right before a tracking request is executed, so the host
 * app can attach headers derived from the request itself — a signature over the body,
 * for instance.
 *
 * Add entries to [headers]; they are applied on top of the static headers registered
 * with `Tracker.addHeader`, overriding any of the same name. The request body is not
 * read for you: take it from [request] so what you sign is exactly what is sent.
 *
 * The callback runs on the network thread. Throwing from it does not fail the request —
 * the headers set before the exception are still applied, the rest are not.
 */
fun interface TrackerHeaderCallback {
    fun onRequest(
        destination: TrackerDestination,
        request: Request,
        headers: MutableMap<String, String>
    )
}
