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
import java.security.KeyStore

val networkingModule = module {
    single(named("trackerOkHttp")) { provideOkHttpClient() }
    single { provideGson() }
    single { provideGsonConverterFactory(get()) }
}

private const val TRACKER_PREF_FILE = "edts_tracker_secret_shared_prefs"

// Used only when the shared default master key is unusable. The tracker owns this alias and its
// prefs file, so both can be wiped and regenerated without touching the host app's data.
private const val TRACKER_FALLBACK_PREF_FILE = "edts_tracker_secret_shared_prefs_fallback"
private const val TRACKER_MASTER_KEY_ALIAS = "_edts_tracker_master_key_"

// Last resort when the Android Keystore cannot be used at all. Tracking must never crash the host.
private const val TRACKER_PLAIN_PREF_FILE = "edts_tracker_shared_prefs_plain"

// Unencrypted, holds only a flag: whether the tracker has switched to its own master key.
private const val TRACKER_STORAGE_STATE_FILE = "edts_tracker_storage_state"
private const val KEY_USE_FALLBACK_KEY = "use_fallback_master_key"

val sharedPreferencesModule = module {
    single(named("trackerSharePref")) {
        provideTrackerPreferences(androidContext())
    }
}

private fun provideTrackerPreferences(context: Context): SharedPreferences {
    if (Tracker.debugging && BuildConfig.DEBUG) {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    // Once the default master key has been found broken, later launches go straight to the
    // tracker-owned key instead of repeating the failing Keystore operations on every startup.
    if (!isUsingFallbackKey(context)) {
        // 1. Normal path: shared default master key, existing tracker prefs file.
        try {
            return createSecurePreferences(context, TRACKER_PREF_FILE, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: NoClassDefFoundError) {
            throw IllegalStateException("Secure tracker storage dependency missing", e)
        } catch (_: Throwable) {
        }

        // 2. The stored Tink keyset can no longer be decrypted by the master key (VERIFICATION_FAILED):
        // restored backups, device-to-device transfer, a key invalidated by a lock screen change. The
        // keyset is unrecoverable, so drop the tracker prefs and write a fresh keyset.
        deletePreferencesFile(context, TRACKER_PREF_FILE)
        try {
            return createSecurePreferences(context, TRACKER_PREF_FILE, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Throwable) {
        }
    }

    // 3. The default master key itself is broken in the Keystore (e.g. KeyStoreException
    // "Invalid key blob", code -33). That alias is shared with the host app, so it is left alone;
    // switch to a tracker-owned key in a separate file instead.
    try {
        return createSecurePreferences(context, TRACKER_FALLBACK_PREF_FILE, TRACKER_MASTER_KEY_ALIAS)
            .also { markUsingFallbackKey(context) }
    } catch (e: NoClassDefFoundError) {
        throw IllegalStateException("Secure tracker storage dependency missing", e)
    } catch (_: Throwable) {
    }

    // 4. The tracker-owned key or its prefs are broken too. Both belong to the tracker, so wipe them
    // and regenerate.
    deletePreferencesFile(context, TRACKER_FALLBACK_PREF_FILE)
    deleteKeystoreEntry(TRACKER_MASTER_KEY_ALIAS)
    try {
        return createSecurePreferences(context, TRACKER_FALLBACK_PREF_FILE, TRACKER_MASTER_KEY_ALIAS)
            .also { markUsingFallbackKey(context) }
    } catch (_: Throwable) {
    }

    // 5. The Keystore is unusable on this device. Keep the host app running with private,
    // unencrypted prefs rather than crashing it from a DI graph it does not control.
    return context.getSharedPreferences(TRACKER_PLAIN_PREF_FILE, Context.MODE_PRIVATE)
}

private fun createSecurePreferences(
    context: Context,
    fileName: String,
    masterKeyAlias: String
): SharedPreferences =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val spec = KeyGenParameterSpec.Builder(
            masterKeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
            .build()
        val masterKey = MasterKey.Builder(context, masterKeyAlias)
            .setKeyGenParameterSpec(spec)
            .build()

        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        SecurePreferences(
            context,
            BuildConfig.DB_PASS,
            fileName
        )
    }

private fun isUsingFallbackKey(context: Context): Boolean =
    try {
        context.getSharedPreferences(TRACKER_STORAGE_STATE_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_FALLBACK_KEY, false)
    } catch (_: Throwable) {
        false
    }

private fun markUsingFallbackKey(context: Context) {
    try {
        context.getSharedPreferences(TRACKER_STORAGE_STATE_FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_USE_FALLBACK_KEY, true) }
    } catch (_: Throwable) {
        // Not persisting the flag only costs the failing default-key attempts on the next launch.
    }
}

private fun deletePreferencesFile(context: Context, fileName: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(fileName)
        } else {
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    clear()
                }
        }
    } catch (_: Throwable) {
        // Nothing else to try; the next step handles a persisting failure.
    }
}

private fun deleteKeystoreEntry(alias: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    try {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    } catch (_: Throwable) {
        // Nothing else to try; the next step handles a persisting failure.
    }
}

private fun provideOkHttpClient(): OkHttpClient = TrackerOkHttpClient().get()

private fun provideGson(): Gson = Gson()

private fun provideGsonConverterFactory(gson: Gson): GsonConverterFactory =
    GsonConverterFactory.create(gson)
