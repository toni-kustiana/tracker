package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.data.InstallReferer
import id.co.edtslib.tracker.data.TrackerFilterDetail
import id.co.edtslib.tracker.data.TrackerData
import id.co.edtslib.tracker.data.TrackerResponse
import kotlinx.coroutines.flow.Flow

interface ITrackerRepository {
    fun createSession(): Flow<String?>
    fun setUserId(userId: Long): Flow<Long>
    fun setLatLng(lat: Double?, lng: Double?): Flow<Boolean>

    fun setInstallReferer(installReferer: InstallReferer): Flow<Boolean>
    fun getInstallReferer(): InstallReferer?

    fun setService(service: String): Flow<Boolean>
    fun getService(): String?

    fun trackApplication(eventName: String): Flow<Boolean>

    fun trackPage(pageName: String, pageId: String, pageUrlPath: String): Flow<TrackerResponse>
    fun trackPageDetail(detail: Any?): Flow<TrackerResponse>

    fun trackClick(name: String, category: String? = null, url: String? = null, details: Any? = null): Flow<TrackerResponse>
    fun trackFilters(filters: List<TrackerFilterDetail>, category: String = ""): Flow<TrackerResponse>
    fun trackSort(sortType: String): Flow<TrackerResponse>

    fun <S, T> trackImpression(category: String, time: Long, data: List<*>, mapper: ((data: S) -> T)? = null): Flow<TrackerResponse>
    fun trackSubmission(name: String, category: String, status: Boolean, reason: String?, details: Any? = null): Flow<TrackerResponse>

    fun trackDisplayedItems(data: MutableList<Any>): Flow<TrackerResponse>
    fun trackSearch(keyword: String, details: Any? = null): Flow<TrackerResponse>

    fun getData(): TrackerData
    fun getPriorPageName(): String?
    fun setPrevAndPriorPageName(pageName: String)
}