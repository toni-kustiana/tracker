package id.co.edtslib.tracker.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TrackerRemoteDataSource(
    private val endpoints: List<TrackerEndpoint>
) : BaseDataSource() {

    /**
     * Sends [trackers] to every endpoint in parallel and reports each outcome. A failure
     * on one endpoint never cancels the others - [getResult] turns every throwable into
     * an error result.
     */
    suspend fun send(trackers: TrackerDataList): List<TrackerSendResult> = coroutineScope {
        endpoints.map { endpoint ->
            async {
                TrackerSendResult(
                    destinationId = endpoint.destination.id,
                    result = getResult {
                        endpoint.service.sendTracks(endpoint.destination.path, trackers)
                    }
                )
            }
        }.awaitAll()
    }

}
