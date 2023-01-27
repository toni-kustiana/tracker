package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.data.InstallReferer

class TrackerInteractor(private val repository: ITrackerRepository): TrackerUseCase {
    override fun createSession() = repository.createSession()
    override fun setUserId(userId: Long) = repository.setUserId(userId)
    override fun setLatLng(lat: Double?, lng: Double?) = repository.setLatLng(lat, lng)
    override fun setInstallReferer(installReferer: InstallReferer) =
        repository.setInstallReferer(installReferer)

    override fun trackStartApplication() = repository.trackStartApplication()
    override fun trackExitApplication() = repository.trackExitApplication()

    override fun trackPage(pageName: String, pageId: String) =
        repository.trackPage(pageName, pageId)
    override fun trackPageDetail(detail: Any?) = repository.trackPageDetail(detail)

    override fun trackClick(name: String, url: String?, details: Any?) =
        repository.trackClick(name, url, details)
    override fun trackFilters(filters: List<String>) = repository.trackFilters(filters)
    override fun trackSort(sortType: String) = repository.trackSort(sortType)

    override fun trackImpression(data: Any) = repository.trackImpression(data)
    override fun trackSubmission(name: String, status: Boolean, reason: String?) = repository.trackSubmission(name, status, reason)

    override fun trackDisplayedItems(data: Any) = repository.trackDisplayedItems(data)
    override fun trackSearch(keyword: String) = repository.trackSearch(keyword)

}