package id.co.edtslib.tracker.di

import com.google.gson.Gson
import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.*
import kotlinx.coroutines.flow.flow
import java.util.*

class TrackerRepository(
    private val remoteSource: TrackerRemoteDataSource,
    private val localSource: TrackerLocalDataSource,
    private val configurationLocalSource: ConfigurationLocalSource
) : ITrackerRepository {
    override fun createSession() = flow {
        val sessionId = UUID.randomUUID().toString()
        var configuration = configurationLocalSource.getCached()
        if (configuration == null) {
            configuration = Configuration(sessionId, 0L, null)
        }
        else {
            configuration.sessionId = sessionId
        }
        configuration.previousPageName = null
        configuration.service = null

        configurationLocalSource.save(configuration)

        emit(sessionId)
    }

    override fun setUserId(userId: Long) = flow {
        var configuration = configurationLocalSource.getCached()
        if (configuration == null) {
            configuration = Configuration(
                sessionId = "",
                userId = userId,
                installReferer = null)
        }

        configuration.userId = userId
        configurationLocalSource.save(configuration)

        emit(userId)
    }

    override fun setLatLng(lat: Double?, lng: Double?) = flow {
        var configuration = configurationLocalSource.getCached()
        if (configuration == null) {
            configuration = Configuration(
                sessionId = "",
                userId = 0L,
                installReferer = null,
                latitude = lat,
                longitude = lng)
        }

        configuration.latitude = lat
        configuration.longitude = lng
        configurationLocalSource.save(configuration)

        emit(true)
    }

    override fun setInstallReferer(installReferer: InstallReferer) = flow {
        var configuration = configurationLocalSource.getCached()
        if (configuration == null) {
            configuration = Configuration(
                sessionId = "",
                userId = 0L,
                installReferer = installReferer)
        }
        configuration.installReferer = installReferer
        configurationLocalSource.save(configuration)

        emit(true)
    }

    override fun getInstallReferer(): InstallReferer? {
        val configuration = configurationLocalSource.getCached()
        return configuration?.installReferer
    }

    override fun setService(service: String) = flow {
        configurationLocalSource.setService(service)
        emit(true)
    }

    override fun getService(): String? {
        return configurationLocalSource.getService()
    }

    override fun trackApplication(eventName: String) = flow {
        val trackerCore = TrackerActivityCore.createPageActivity(
            eventId = configurationLocalSource.getEventId(),
            eventName = eventName,
            pageViewId = Tracker.currentPageId,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> {}
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
            }
            else -> {}
        }

        emit(true)
    }

    override fun getData(): TrackerData {
        val user = TrackerUser.create(configurationLocalSource.getSessionId(),
            configurationLocalSource.getUserId())
        val application = localSource.apps
        val marketing = configurationLocalSource.getCached()?.installReferer
        val network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
            configurationLocalSource.getLongitude())

        return TrackerData(core = false,
            user = user,
            application = application,
            marketing = marketing,
            network = network)
    }

    override fun getPriorPageName() = configurationLocalSource.getPriorPageName()

    override fun trackPage(pageName: String, pageId: String, pageUrlPath: String) = flow {
        val previousPageName = configurationLocalSource.getPreviousPageName()
        val prevPageUrlPath = configurationLocalSource.getPrevPageUrlPath()
        val service = configurationLocalSource.getService()

        val trackerCore = TrackerPageViewCore.create(
            eventId = configurationLocalSource.getEventId(),
            pageName = pageName,
            pageId = pageId,
            previousPage = previousPageName ?: "",
            pageUrlPath = pageUrlPath,
            prevPageUrlPath = prevPageUrlPath ?: "",
            service = service ?: ""
        )
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer
        )

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)

        configurationLocalSource.setPreviousPageName(pageName)
        configurationLocalSource.setPrevPageUrlPath(pageUrlPath)

        when (response.status) {
            Result.Status.SUCCESS -> emit(
                TrackerResponse(
                    Gson().toJson(trackerData),
                    response.data
                )
            )
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {
            }
        }
    }

    override fun trackPageDetail(detail: Any?) = flow {
        val trackerCore = TrackerPageDetailCore.create(
            eventId = configurationLocalSource.getEventId(),
            details = detail,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun trackClick(name: String, category: String?, url: String?, details: Any?) = flow {
        val prevService = configurationLocalSource.getService()
        val trackerCore = TrackerClickLinkCore.create(
            eventId = configurationLocalSource.getEventId(),
            name = name,
            category = category,
            url = url,
            details = details,
            service = prevService ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun trackFilters(filters: List<TrackerFilterDetail>, category: String) = flow {
        val trackerCore = TrackerFilterCore.create(
            eventId = configurationLocalSource.getEventId(),
            list = filters,
            category = category,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            application = localSource.apps,
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun trackSort(sortType: String) = flow {
        val trackerCore = TrackerSortCore.create(
            eventId = configurationLocalSource.getEventId(),
            sortType = sortType,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun <S, T> trackImpression(category: String, time: Long, data: List<*>, mapper: ((data: S) -> T)?) = flow {
        val mappedData = mutableListOf<T>()
        if (mapper != null) {
            for (d in data) {

                val newD = mapper.invoke(d as S)
                if (newD != null) mappedData.add(newD)
            }
        }
        val trackerCore = TrackerImpressionCore.create(
            eventId = configurationLocalSource.getEventId(),
            time = time,
            category= category,
            data = if (mapper != null) mappedData else data,
            service = configurationLocalSource.getService() ?: ""
        )
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun trackSubmission(
        name: String,
        category: String,
        status: Boolean,
        reason: String?,
        details: Any?
    ) = flow {
        val map = if (details is MutableMap<*, *>) {
            details.toMap()
        }
        else
            if (details is MutableList<*>) {
                details.toList()
            }
            else {
                null
            }
        val trackerCore = TrackerSubmissionCore.create(
            eventId = configurationLocalSource.getEventId(),
            label = name,
            category = category,
            status = status,
            reason = reason,
            details = map ?: details,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        try {
            val response = remoteSource.send(trackerDataList)
            when(response.status) {
                Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
                Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                    localSource.add(trackerDataList)
                    emit(
                        TrackerResponse(Gson().toJson(trackerData), null)
                    )
                }
                else -> {}
            }
        }
        catch (ignore: OutOfMemoryError) {

        }

    }

    override fun trackDisplayedItems(
        data: MutableList<Any>
    ) = flow {
        val trackerCore = TrackerDisplayedItemCore.create(
            eventId = configurationLocalSource.getEventId(),
            data = data,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

    override fun trackSearch(
        keyword: String,
        details: Any?
    ) = flow {
        val trackerCore = TrackerSearchCore.create(
            eventId = configurationLocalSource.getEventId(),
            keyword = keyword,
            details = details,
            service = configurationLocalSource.getService() ?: "")
        val trackerData = TrackerData(
            core = trackerCore,
            user = TrackerUser.create(configurationLocalSource.getSessionId(),
                configurationLocalSource.getUserId()),
            application = localSource.apps,
            network = TrackerNetwork.create(configurationLocalSource.getLatitude(),
                configurationLocalSource.getLongitude()),
            marketing = configurationLocalSource.getCached()?.installReferer)

        val trackerDataList = TrackerDataList(mutableListOf(trackerData))

        val response = remoteSource.send(trackerDataList)
        when(response.status) {
            Result.Status.SUCCESS -> emit(TrackerResponse(Gson().toJson(trackerData), response.data))
            Result.Status.ERROR, Result.Status.UNAUTHORIZED -> {
                localSource.add(trackerDataList)
                emit(
                    TrackerResponse(Gson().toJson(trackerData), null)
                )
            }
            else -> {}
        }
    }

}