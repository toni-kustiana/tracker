package id.co.edtslib.tracker.di

import android.content.Context
import android.content.SharedPreferences
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
import androidx.core.content.edit

val networkingModule = module {
    single(named("trackerOkHttp")) { provideOkHttpClient() }
    single { provideGson() }
    single { provideGsonConverterFactory(get()) }

    single(named("tracker")) { provideRetrofit(get(named("trackerOkHttp")), get()) }
}

private const val TRACKER_PREF_FILE = "edts_tracker_secret_shared_prefs"

val sharedPreferencesModule = module {
    single(named("trackerSharePref")) {
        provideTrackerPreferences(androidContext())
    }
}

private fun provideTrackerPreferences(context: Context): SharedPreferences {
    if (Tracker.debugging && BuildConfig.DEBUG) {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    return try {
        createSecurePreferences(context)
    } catch (e: NoClassDefFoundError) {
        throw IllegalStateException("Secure tracker storage dependency missing", e)
    } catch (_: Throwable) {
        // The stored Tink keyset can no longer be decrypted by the master key in the Android
        // Keystore (Keystore reports VERIFICATION_FAILED). This happens when the prefs file
        // outlives the keystore key - restored backups, device-to-device transfer, or a key
        // invalidated by a lock screen change. The keyset is unrecoverable, so drop the tracker
        // prefs and let a fresh keyset be written with the current master key. Only the tracker
        // file is removed; the master key alias is shared with the host app and is left alone.
        deleteTrackerPreferences(context)
        try {
            createSecurePreferences(context)
        } catch (retry: Throwable) {
            throw IllegalStateException("Unable to initialize secure tracker storage", retry)
        }
    }
}

private fun createSecurePreferences(context: Context): SharedPreferences =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val spec = KeyGenParameterSpec.Builder(
            MasterKey.DEFAULT_MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
            .build()
        val masterKey = MasterKey.Builder(context)
            .setKeyGenParameterSpec(spec)
            .build()

        EncryptedSharedPreferences.create(
            context,
            TRACKER_PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        SecurePreferences(
            context,
            BuildConfig.DB_PASS,
            TRACKER_PREF_FILE
        )
    }

private fun deleteTrackerPreferences(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(TRACKER_PREF_FILE)
        } else {
            context.getSharedPreferences(TRACKER_PREF_FILE, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    clear()
                }
        }
    } catch (_: Throwable) {
        // Nothing else to try; the retry below will surface the original failure.
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
