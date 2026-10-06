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
import retrofit2.converter.gson.GsonConverterFactory
import com.securepreferences.SecurePreferences
import id.co.edtslib.tracker.BuildConfig
import id.co.edtslib.tracker.Tracker
import org.koin.android.ext.koin.androidContext
import androidx.core.content.edit
import android.util.Log
import java.security.KeyStore

val networkingModule = module {
    single(named("trackerOkHttp")) { provideOkHttpClient() }
    single { provideGson() }
    single { provideGsonConverterFactory(get()) }
}

// The tracker owns its own master key so it can be deleted and regenerated when the Keystore
// reports it unusable. The default androidx alias is shared with the host app and other edtslib
// modules, so it must never be deleted from here.
private const val TRACKER_MASTER_KEY_ALIAS = "_edts_tracker_master_key_"
private const val TRACKER_PREF_FILE = "edts_tracker_secure_prefs"
// Encrypted with MasterKey.DEFAULT_MASTER_KEY_ALIAS by earlier versions. Unreadable with the
// tracker key, so it is dropped instead of migrated (it only holds pending tracking events).
private const val LEGACY_TRACKER_PREF_FILE = "edts_tracker_secret_shared_prefs"
// Last resort when the Keystore cannot produce a working key at all, so tracking never crashes
// the host app.
private const val FALLBACK_TRACKER_PREF_FILE = "edts_tracker_prefs"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TAG = "Tracker"

val sharedPreferencesModule = module {
    single(named("trackerSharePref")) {
        provideTrackerPreferences(androidContext())
    }
}

private fun provideTrackerPreferences(context: Context): SharedPreferences {
    if (Tracker.debugging && BuildConfig.DEBUG) {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    deletePreferences(context, LEGACY_TRACKER_PREF_FILE)

    return try {
        createSecurePreferences(context)
    } catch (e: NoClassDefFoundError) {
        throw IllegalStateException("Secure tracker storage dependency missing", e)
    } catch (e: Throwable) {
        // Either the stored Tink keyset can no longer be decrypted by the master key (restored
        // backups, device-to-device transfer, lock screen change) or the master key itself is
        // unusable, e.g. KeyMint failing to upgrade the key blob after an OS update
        // ("master key ... exists but is unusable"). Both are unrecoverable, so start over with
        // a fresh master key and keyset.
        Log.w(TAG, "secure tracker storage unusable, resetting", e)
        deletePreferences(context, TRACKER_PREF_FILE)
        deleteMasterKey()
        try {
            createSecurePreferences(context)
        } catch (retry: Throwable) {
            Log.e(TAG, "secure tracker storage unavailable, using private preferences", retry)
            context.getSharedPreferences(FALLBACK_TRACKER_PREF_FILE, Context.MODE_PRIVATE)
        }
    }
}

private fun createSecurePreferences(context: Context): SharedPreferences =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val spec = KeyGenParameterSpec.Builder(
            TRACKER_MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
            .build()
        val masterKey = MasterKey.Builder(context, TRACKER_MASTER_KEY_ALIAS)
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

private fun deletePreferences(context: Context, name: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(name)
        } else {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    clear()
                }
        }
    } catch (_: Throwable) {
        // Nothing else to try; a later create call surfaces any real failure.
    }
}

private fun deleteMasterKey() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            .deleteEntry(TRACKER_MASTER_KEY_ALIAS)
    } catch (_: Throwable) {
        // Nothing else to try; the retry falls back to private preferences if the key is stuck.
    }
}

private fun provideOkHttpClient(): OkHttpClient = TrackerOkHttpClient().get()

private fun provideGson(): Gson = Gson()

private fun provideGsonConverterFactory(gson: Gson): GsonConverterFactory =
    GsonConverterFactory.create(gson)
