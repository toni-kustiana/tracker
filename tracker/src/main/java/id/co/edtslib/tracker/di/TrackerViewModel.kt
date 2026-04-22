package id.co.edtslib.tracker.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import id.co.edtslib.tracker.data.InstallReferer
import id.co.edtslib.tracker.data.TrackerFilterDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

open class TrackerViewModel(
    private val trackerUseCase: TrackerUseCase
) : ViewModel() {
    fun createSession() = trackerUseCase.createSession().asLiveData()

    fun setUserId(userId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.setUserId(userId).collect()
        }
    }

    fun setLatLng(lat: Double?, lng: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.setLatLng(lat, lng).collect()
        }
    }

    fun setInstallReferer(installReferer: InstallReferer) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.setInstallReferer(installReferer).collect()
        }
    }

    fun getInstallReferer() = trackerUseCase.getInstallReferer()

    fun setService(service: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.setService(service).collect()
        }
    }

    fun getService() = trackerUseCase.getService()

    fun trackOpenApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackApplication("open_app").collect()
        }
    }

    fun trackResumeApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackApplication("resume_app").collect()
        }
    }

    fun trackMinimizeApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackApplication("minimize_app").collect()
        }
    }

    fun trackCloseApplication() {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackApplication("close_app").collect()
        }
    }

    fun trackPage(pageName: String, pageId: String, pageUrlPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackPage(pageName, pageId, pageUrlPath).collect()
        }
    }

    fun trackPageDetail(detail: Any?) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackPageDetail(detail).collect()
        }
    }

    fun trackClick(
        name: String,
        category: String? = null,
        url: String? = null,
        details: Any? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                trackerUseCase.trackClick(name, category, url, details).collect()
            }
            catch (_: Error) {

            }
        }
    }

    fun trackFilters(filters: List<TrackerFilterDetail>, category: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackFilters(filters, category).collect()
        }
    }

    fun trackSort(sortType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackSort(sortType).collect()
        }
    }

    fun trackSubmission(
        name: String,
        category: String,
        status: Boolean,
        reason: String?,
        details: Any? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackSubmission(name, category, status, reason, details).collect()
        }
    }

    fun <S, T> trackImpression(
        category: String,
        time: Long,
        data: List<*>,
        mapper: ((data: S) -> T)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackImpression<S, T>(category, time, data, mapper).collect()
        }
    }

    fun trackDisplayedItems(data: MutableList<Any>) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackDisplayedItems(data).collect()
        }
    }

    fun trackSearch(keyword: String, details: Any? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            trackerUseCase.trackSearch(keyword, details).collect()
        }
    }

    fun getData() = trackerUseCase.getData()

    fun getPriorPageName() = trackerUseCase.getPriorPageName()

    fun setPrevAndPriorPageName(pageName: String) = trackerUseCase.setPrevAndPriorPageName(pageName)
}