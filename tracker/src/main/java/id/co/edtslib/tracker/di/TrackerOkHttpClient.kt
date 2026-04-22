package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.BuildConfig
import id.co.edtslib.tracker.Tracker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class TrackerOkHttpClient {
    fun get(): OkHttpClient {
        val isDebugInstrumentationEnabled = Tracker.debugging && BuildConfig.DEBUG
        val interceptor = HttpLoggingInterceptor().apply {
            level = if (isDebugInstrumentationEnabled) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
    }

}
