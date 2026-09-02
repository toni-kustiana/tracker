package id.co.edtslib.tracker

import android.app.Application
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import id.co.edtslib.baserecyclerview.BaseRecyclerViewAdapter
import id.co.edtslib.baserecyclerview2.BaseRecyclerView2
import id.co.edtslib.tracker.data.InstallReferer
import id.co.edtslib.tracker.data.TrackerData
import id.co.edtslib.tracker.data.TrackerDestination
import id.co.edtslib.tracker.data.TrackerFilterDetail
import id.co.edtslib.tracker.di.TrackerViewModel
import id.co.edtslib.tracker.di.interactorModule
import id.co.edtslib.tracker.di.mainAppModule
import id.co.edtslib.tracker.di.networkingModule
import id.co.edtslib.tracker.di.repositoryModule
import id.co.edtslib.tracker.di.sharedPreferencesModule
import id.co.edtslib.tracker.di.viewModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import java.util.Date

class Tracker private constructor() : KoinComponent {
    private val trackerViewModel: TrackerViewModel? by inject()

    data class ImpressionData(
        val data: List<Any>,
        val time: Long
    )

    companion object {
        private var tracker: Tracker? = null
        var baseUrl = ""
        var token = ""
        var path = TrackerDestination.DEFAULT_PATH
        var isLegacy = false

        /**
         * Every destination an event is sent to. The first entry is built from the
         * baseUrl/token/path/isLegacy arguments of [init]; the rest come from its
         * `destinations` argument.
         */
        var destinations: List<TrackerDestination> = emptyList()
            private set
        var debugging = false
        var resend = true
        var appVersion = "1.0.0"

        private var firstImpression = -1
        private var lastImpression = -1

        // don't set manual, set with resume fun
        var currentPageName = ""
        var currentPageId = ""

        private fun configure(
            baseUrl: String,
            token: String,
            path: String,
            isLegacy: Boolean,
            others: List<TrackerDestination>
        ) {
            // The legacy statics keep describing the primary destination so host apps
            // reading them keep working.
            Tracker.baseUrl = baseUrl
            Tracker.token = token
            Tracker.path = path
            Tracker.isLegacy = isLegacy

            val primary = TrackerDestination(
                baseUrl = baseUrl,
                token = token,
                path = path,
                isLegacy = isLegacy
            )
            destinations = listOf(primary) + others.filter { it.baseUrl != primary.baseUrl || it.path != primary.path }
        }

        fun init(
            application: Application,
            baseUrl: String,
            token: String,
            path: String = TrackerDestination.DEFAULT_PATH,
            isLegacy: Boolean = false,
            destinations: List<TrackerDestination> = emptyList()
        ) {
            configure(baseUrl, token, path, isLegacy, destinations)

            startKoin {
                androidContext(application.applicationContext)
                modules(
                    listOf(
                        networkingModule,
                        sharedPreferencesModule,
                        mainAppModule,
                        repositoryModule,
                        interactorModule,
                        viewModule
                    )
                )
            }

            if (tracker == null) {
                tracker = Tracker()
            }
        }

        fun init(
            baseUrl: String,
            token: String,
            koin: KoinApplication,
            path: String = TrackerDestination.DEFAULT_PATH,
            isLegacy: Boolean = false,
            destinations: List<TrackerDestination> = emptyList()
        ) {
            configure(baseUrl, token, path, isLegacy, destinations)

            koin.modules(
                listOf(
                    networkingModule,
                    sharedPreferencesModule,
                    mainAppModule,
                    repositoryModule,
                    interactorModule,
                    viewModule
                )
            )

            if (tracker == null) {
                tracker = Tracker()
            }
        }

        fun getInstallReferer() = tracker?.trackerViewModel?.getInstallReferer()

        fun checkInstallReferrer(activity: FragmentActivity) {
            val referrerClient = InstallReferrerClient.newBuilder(activity).build()
            referrerClient.startConnection(object : InstallReferrerStateListener {

                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    try {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                checkInstallReferrer(
                                    referrerClient.installReferrer?.installReferrer!!,
                                    activity.intent
                                )
                            }

                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                                // API not available on the current Play Store app.
                            }

                            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                // Connection couldn't be established.
                            }
                        }
                    } catch (ignore: RuntimeException) {

                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                }
            })
        }

        fun checkInstallReferrer(utm_raw: String?, intent: Intent?) {
            if (intent?.data?.getQueryParameter("utm_source") != null) {
                tracker?.trackerViewModel?.setInstallReferer(InstallReferer(intent.data?.toString()))
            } else {

                tracker?.trackerViewModel?.setInstallReferer(InstallReferer(utm_raw))
            }
        }

        fun setUserId(userId: Long) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.setUserId(userId)
        }

        fun setLatLng(lat: Double, lng: Double) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.setLatLng(lat, lng)
        }

        fun getService() = tracker?.trackerViewModel?.getService()
        fun setService(service: String) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.setService(service)
        }

        fun trackPage(pageName: String, pageId: String, pageUrlPath: String = "") {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackPage(pageName, pageId, pageUrlPath)
            resumePage(pageName, pageId)
        }

        fun trackPageDetail(detail: Any?) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackPageDetail(detail)
        }

        fun trackClick(
            name: String,
            category: String? = null,
            url: String? = null,
            details: Any? = null
        ) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackClick(name, category, url, details)
        }

        fun trackFilters(filters: List<TrackerFilterDetail>, category: String = "") {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackFilters(filters, category)

        }

        fun trackSort(sortType: String) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackSort(sortType)
        }

        fun trackSubmissionSuccess(name: String, category: String, details: Any? = null) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackSubmission(name, category, true, "", details)

        }

        fun trackSubmissionFailed(
            name: String,
            category: String,
            reason: String?,
            details: Any? = null
        ) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackSubmission(name, category, false, reason, details)

        }

        fun <S, T> trackImpression(
            category: String,
            data: List<*>,
            mapper: ((data: S) -> T)? = null
        ) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackImpression<S, T>(category, Date().time, data, mapper)
        }

        fun <S, T> trackImpression(
            category: String,
            time: Long,
            data: List<*>,
            mapper: ((data: S) -> T)? = null
        ) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackImpression<S, T>(category, time, data, mapper)
        }

        fun trackDisplayedItems(data: MutableList<Any>) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackDisplayedItems(data)
        }

        fun trackSearch(keyword: String, details: Any? = null) {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackSearch(keyword, details)
        }

        fun trackOpenApplication() {
            tracker?.trackerViewModel?.createSession()?.observeForever {
                tracker?.trackerViewModel?.trackOpenApplication()
            }
        }

        fun trackCloseApplication() {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackCloseApplication()
        }

        fun trackResumeApplication() {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackResumeApplication()
        }

        fun trackMinimizeApplication() {
            if (tracker == null) {
                tracker = Tracker()
            }

            tracker?.trackerViewModel?.trackMinimizeApplication()
        }

        fun resumePage(pageName: String, pageId: String) {
            tracker?.trackerViewModel?.setPrevAndPriorPageName(currentPageName)

            currentPageName = pageName
            currentPageId = pageId

        }

        fun getData(): TrackerData? {
            if (tracker == null) {
                tracker = Tracker()
            }

            return tracker?.trackerViewModel?.getData()
        }

        /**
         * Returns the actual previous page name before the current one was tracked.
         *
         * Unlike [id.co.edtslib.tracker.di.ConfigurationLocalSource.getPreviousPageName], which may reflect the current page due to overwrite during [id.co.edtslib.tracker.Tracker.Companion.trackPage],
         * this method preserves the last known page before tracking occurred.
         *
         * Useful for scenarios where page context is needed outside the tracking lifecycle,
         * such as in Sentinel.
         */
        fun getPriorPageName(): String? {
            if (tracker == null) {
                tracker = Tracker()
            }

            return tracker?.trackerViewModel?.getPriorPageName()
        }

        fun <S, T> setImpressionRecyclerView(
            category: String,
            recyclerView: RecyclerView,
            mapper: ((data: S) -> T)? = null
        ) {
            firstImpression = -1
            lastImpression = -1

            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (recyclerView.tag != null) {
                            if (recyclerView.tag is List<*>) {
                                val list = recyclerView.tag as List<*>
                                list.forEach {
                                    if (it is ImpressionData) {
                                        trackImpression(
                                            category = category,
                                            time = it.time,
                                            data = it.data,
                                            mapper = mapper
                                        )
                                    }
                                }

                            }
                        }
                        recyclerView.tag = null
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if ((recyclerView.layoutManager is LinearLayoutManager || recyclerView.layoutManager is StaggeredGridLayoutManager) && (recyclerView.adapter is BaseRecyclerViewAdapter<*, *> || recyclerView.adapter is BaseRecyclerView2)) {

                        val first: Int
                        val last: Int
                        if (recyclerView.layoutManager is LinearLayoutManager) {
                            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                            first = layoutManager.findFirstVisibleItemPosition()
                            last = layoutManager.findLastVisibleItemPosition()
                        } else {
                            val layoutManager =
                                recyclerView.layoutManager as StaggeredGridLayoutManager
                            first = layoutManager.findFirstVisibleItemPositions(null)[0]
                            last = layoutManager.findLastVisibleItemPositions(null)[0]
                        }


                        if (firstImpression != first && lastImpression != last) {

                            firstImpression = first
                            lastImpression = last


                            if (recyclerView.adapter is BaseRecyclerViewAdapter<*, *>) {
                                addImpression(
                                    recyclerView,
                                    first,
                                    last,
                                    recyclerView.adapter as BaseRecyclerViewAdapter<*, *>
                                )
                            } else
                                if (recyclerView.adapter is BaseRecyclerView2) {
                                    addImpression(
                                        recyclerView,
                                        first,
                                        last,
                                        recyclerView.adapter as BaseRecyclerView2
                                    )
                                }
                        }
                    }
                }
            })
        }

        private fun addImpression(
            recyclerView: RecyclerView,
            first: Int,
            end: Int,
            adapter: BaseRecyclerViewAdapter<*, *>
        ) {
            val l = mutableListOf<Any>()
            for (i in first until end + 1) {
                if (adapter.list.isNotEmpty()) {
                    val realPosition = i % adapter.list.size
                    if (realPosition >= 0 && realPosition < adapter.list.size) {
                        if (adapter.list[realPosition] != null) {
                            l.add(adapter.list[realPosition]!!)
                        }
                    }
                }
            }

            val newData = ImpressionData(data = l, time = Date().time)
            if (recyclerView.tag == null) {
                recyclerView.tag = listOf(newData)
            } else
                if (recyclerView.tag is List<*>) {
                    val list = (recyclerView.tag as List<ImpressionData>).toMutableList()
                    list.add(newData)

                    recyclerView.tag = list

                }
        }

        private fun addImpression(
            recyclerView: RecyclerView,
            first: Int,
            end: Int,
            adapter: BaseRecyclerView2
        ) {
            val l = mutableListOf<Any>()
            for (i in first until end + 1) {
                if (adapter.list.isNotEmpty()) {
                    val realPosition = i % adapter.list.size
                    if (realPosition >= 0 && realPosition < adapter.list.size) {
                        if (adapter.list[realPosition].data != null) {
                            l.add(adapter.list[realPosition].data!!)
                        }
                    }
                }
            }

            val newData = ImpressionData(data = l, time = Date().time)
            if (recyclerView.tag == null) {
                recyclerView.tag = listOf(newData)
            } else
                if (recyclerView.tag is List<*>) {
                    val list = (recyclerView.tag as List<ImpressionData>).toMutableList()
                    list.add(newData)

                    recyclerView.tag = list

                }
        }
    }

}