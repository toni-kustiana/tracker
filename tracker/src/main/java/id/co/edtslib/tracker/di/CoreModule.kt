package id.co.edtslib.tracker.di

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.securepreferences.SecurePreferences
import id.co.edtslib.tracker.BuildConfig
import id.co.edtslib.tracker.Tracker
import org.koin.android.ext.koin.androidContext
import java.lang.Exception

val networkingModule = module {
    single(named("trackerOkHttp")) { provideOkHttpClient() }
    single { provideGson() }
    single { provideGsonConverterFactory(get()) }

    single(named("tracker")) { provideRetrofit(get(named("trackerOkHttp")), get()) }
}

val sharedPreferencesModule = module {
    single(named("trackerSharePref")) {
        val isDebugStorageEnabled = Tracker.debugging && BuildConfig.DEBUG
        try {
            if (isDebugStorageEnabled) {
                PreferenceManager.getDefaultSharedPreferences(androidContext())
            }
            else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val spec = KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                    .build()
                val masterKey = MasterKey.Builder(androidContext())
                    .setKeyGenParameterSpec(spec)
                    .build()

                EncryptedSharedPreferences.create(
                    androidContext(),
                    "edts_tracker_secret_shared_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                SecurePreferences(
                    androidContext(),
                    BuildConfig.DB_PASS,
                    "edts_tracker_secret_shared_prefs"
                )
            }
        }

        catch (e: Exception) {
            if (isDebugStorageEnabled) {
                PreferenceManager.getDefaultSharedPreferences(androidContext())
            } else {
                throw IllegalStateException("Unable to initialize secure tracker storage", e)
            }
        }
        catch (e: NoClassDefFoundError) {
            if (isDebugStorageEnabled) {
                PreferenceManager.getDefaultSharedPreferences(androidContext())
            } else {
                throw IllegalStateException("Secure tracker storage dependency missing", e)
            }
        }
    }
}

private fun provideOkHttpClient(): OkHttpClient = TrackerOkHttpClient().get()

private fun provideGson(): Gson = Gson()

private fun provideGsonConverterFactory(gson: Gson): GsonConverterFactory =
    GsonConverterFactory.create(gson)

private fun provideRetrofit(
    okHttpClient: OkHttpClient,
    converterFactory: GsonConverterFactory
): Retrofit {
    return Retrofit.Builder()
        .baseUrl(Tracker.baseUrl)
        .client(okHttpClient.newBuilder().addInterceptor(AuthInterceptor(Tracker.token, Tracker.isLegacy)).build())
        .addConverterFactory(converterFactory)
        .build()
}
