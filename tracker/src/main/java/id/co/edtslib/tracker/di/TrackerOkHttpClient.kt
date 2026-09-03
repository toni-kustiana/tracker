package id.co.edtslib.tracker.di

import okhttp3.OkHttpClient

/**
 * The client shared by every destination. Interceptors that need to know which
 * destination they serve, or that must see the final request, are added per destination
 * in `provideEndpoints`.
 */
class TrackerOkHttpClient {
    fun get(): OkHttpClient = OkHttpClient.Builder().build()
}
