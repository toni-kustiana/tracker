package id.co.edtslib.tracker.di

import com.facebook.stetho.okhttp3.StethoInterceptor
import id.co.edtslib.tracker.BuildConfig
import id.co.edtslib.tracker.Tracker
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class TrackerOkHttpClient {
    fun get(): OkHttpClient {
        val isDebugInstrumentationEnabled = Tracker.debugging && BuildConfig.DEBUG
        val builder = OkHttpClient.Builder()
        val interceptor = HttpLoggingInterceptor().apply {
            level = if (isDebugInstrumentationEnabled) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        builder.addInterceptor(interceptor)

        if (isDebugInstrumentationEnabled) {
            builder.addNetworkInterceptor(StethoInterceptor())
        }

        return builder.build()
    }
}
