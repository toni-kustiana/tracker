package id.co.edtslib.tracker.di

import android.app.Application
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerApps
import id.co.edtslib.tracker.data.TrackerData
import id.co.edtslib.tracker.data.TrackerDataList
import java.lang.Exception

class TrackerLocalDataSource(
    private val sharedPreferences: SharedPreferences,
    app: Application
): LocalDataSource<List<TrackerData>>(sharedPreferences) {
    override fun getKeyName(): String = KEY_NAME
    override fun getValue(json: String): List<TrackerData> = Gson().fromJson(json, object : TypeToken<List<TrackerData>>() {}.type)

    val apps = TrackerApps.create(app.applicationContext)

    /**
     * Retains [trackerData] for a single destination. Events fan out to every
     * destination, so a batch is only cached under the destinations that actually
     * failed - the ones that succeeded must not receive it again.
     */
    fun add(destinationId: String, trackerData: TrackerDataList) {
        if (!Tracker.resend) return

        try {
            val list = getCached(destinationId)?.toMutableList() ?: mutableListOf()
            list.addAll(trackerData.data)

            save(destinationId, list)
        } catch (e: Exception) {
            // nothing to do
        }
    }

    fun getCached(destinationId: String): List<TrackerData>? {
        return try {
            val json = sharedPreferences.getString(keyOf(destinationId), null)
            if (json.isNullOrEmpty()) null else getValue(json)
        } catch (ignore: Exception) {
            null
        }
    }

    fun clear(destinationId: String) {
        sharedPreferences.edit().remove(keyOf(destinationId)).apply()
    }

    private fun save(destinationId: String, data: List<TrackerData>) {
        try {
            sharedPreferences.edit()
                .putString(keyOf(destinationId), Gson().toJson(data))
                .apply()
        } catch (ignore: OutOfMemoryError) {
        } catch (ignore: Exception) {
        }
    }

    private fun keyOf(destinationId: String) = "${KEY_NAME}_$destinationId"

    companion object {
        private const val KEY_NAME = "trackers"
    }
}
