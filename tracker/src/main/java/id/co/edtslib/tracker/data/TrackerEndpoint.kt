package id.co.edtslib.tracker.data

/**
 * A [TrackerDestination] paired with the Retrofit service bound to its base URL and
 * credentials.
 */
data class TrackerEndpoint(
    val destination: TrackerDestination,
    val service: TrackerApiService
)

/** Outcome of sending one batch to one destination. */
data class TrackerSendResult(
    val destinationId: String,
    val result: Result<String>
)

/** Ids of the destinations that did not accept the batch. */
fun List<TrackerSendResult>.failedDestinationIds(): List<String> =
    filter { it.result.status != Result.Status.SUCCESS }.map { it.destinationId }

/** Body of the first destination that accepted the batch, or null if none did. */
fun List<TrackerSendResult>.firstSuccessBody(): String? =
    firstOrNull { it.result.status == Result.Status.SUCCESS }?.result?.data
