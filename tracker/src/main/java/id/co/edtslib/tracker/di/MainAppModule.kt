package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerApiService
import id.co.edtslib.tracker.data.TrackerEndpoint
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val mainAppModule = module {
    single { provideEndpoints(get(named("trackerOkHttp")), get()) }
}

/**
 * One Retrofit-backed endpoint per destination. The OkHttp client, Gson and converter
 * are shared; only the base URL and the interceptors differ, so each destination carries
 * its own token, legacy header mode, and the host app's headers for that destination.
 */
private fun provideEndpoints(
    okHttpClient: OkHttpClient,
    converterFactory: GsonConverterFactory
): List<TrackerEndpoint> = Tracker.destinations.map { destination ->
    val retrofit = Retrofit.Builder()
        .baseUrl(destination.baseUrl)
        .client(
            okHttpClient.newBuilder()
                .addInterceptor(AuthInterceptor(destination.token, destination.isLegacy))
                .addInterceptor(TrackerHeaderInterceptor(destination))
                .build()
        )
        .addConverterFactory(converterFactory)
        .build()

    TrackerEndpoint(destination, retrofit.create(TrackerApiService::class.java))
}
