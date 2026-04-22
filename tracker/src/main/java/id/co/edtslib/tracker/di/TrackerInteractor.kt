package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.data.InstallReferer
import id.co.edtslib.tracker.data.TrackerFilterDetail

class TrackerInteractor(private val repository: ITrackerRepository) : TrackerUseCase {
    override fun createSession() = repository.createSession()
    override fun setUserId(userId: Long) = repository.setUserId(userId)
    override fun setLatLng(lat: Double?, lng: Double?) = repository.setLatLng(lat, lng)
    override fun setInstallReferer(installReferer: InstallReferer) =
        repository.setInstallReferer(installReferer)

    override fun getInstallReferer() =
        repository.getInstallReferer()

    override fun setService(service: String) = repository.setService(service)
    override fun getService() = repository.getService()

    override fun trackApplication(eventName: String) = repository.trackApplication(eventName)

    override fun trackPage(pageName: String, pageId: String, pageUrlPath: String) =
        repository.trackPage(pageName, pageId, pageUrlPath)

    override fun trackPageDetail(detail: Any?) = repository.trackPageDetail(detail)

    override fun trackClick(name: String, category: String?, url: String?, details: Any?) =
        repository.trackClick(name, category, url, details)

    override fun trackFilters(filters: List<TrackerFilterDetail>, category: String) =
        repository.trackFilters(filters, category)

    override fun trackSort(sortType: String) = repository.trackSort(sortType)

    override fun <S, T> trackImpression(
        category: String,
        time: Long,
        data: List<*>,
        mapper: ((data: S) -> T)?
    ) = repository.trackImpression<S, T>(category, time, data, mapper)

    override fun trackSubmission(
        name: String,
        category: String,
        status: Boolean,
        reason: String?,
        details: Any?
    ) =
        repository.trackSubmission(name, category, status, reason, details)

    override fun trackDisplayedItems(data: MutableList<Any>) = repository.trackDisplayedItems(data)
    override fun trackSearch(keyword: String, details: Any?) =
        repository.trackSearch(keyword, details)

    override fun getData() = repository.getData()
    override fun getPriorPageName() = repository.getPriorPageName()
    override fun setPrevAndPriorPageName(pageName: String) = repository.setPrevAndPriorPageName(pageName)
}