package id.co.edtslib.tracker.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerRemoteDataSourceTest {

    private class FakeApiService(private val response: () -> Response<String>) : TrackerApiService {
        val paths = mutableListOf<String>()

        override suspend fun sendTracks(path: String, track: TrackerDataList): Response<String> {
            paths.add(path)
            return response()
        }
    }

    private fun endpoint(
        baseUrl: String,
        path: String,
        response: () -> Response<String>
    ): Pair<TrackerEndpoint, FakeApiService> {
        val service = FakeApiService(response)
        val destination = TrackerDestination(baseUrl, "token", path)
        return TrackerEndpoint(destination, service) to service
    }

    private fun ok(body: String) = { Response.success(body) }
    private fun serverError(): () -> Response<String> = {
        Response.error(500, "boom".toResponseBody("text/plain".toMediaType()))
    }
    private fun boom(): () -> Response<String> = { throw IllegalStateException("network down") }

    private val batch get() = TrackerDataList(mutableListOf())

    @Test
    fun `sends to every destination using its own path`() = runTest {
        val (first, firstService) = endpoint("https://a.example.com/", "gateway-a", ok("a"))
        val (second, secondService) = endpoint("https://b.example.com/", "gateway-b", ok("b"))

        val results = TrackerRemoteDataSource(listOf(first, second)).send(batch)

        assertEquals(listOf("gateway-a"), firstService.paths)
        assertEquals(listOf("gateway-b"), secondService.paths)
        assertEquals(2, results.size)
        assertEquals(emptyList<String>(), results.failedDestinationIds())
    }

    @Test
    fun `only the failing destination is reported as failed`() = runTest {
        val (first, _) = endpoint("https://a.example.com/", "gateway-a", ok("a"))
        val (second, _) = endpoint("https://b.example.com/", "gateway-b", serverError())

        val results = TrackerRemoteDataSource(listOf(first, second)).send(batch)

        assertEquals(listOf(second.destination.id), results.failedDestinationIds())
    }

    @Test
    fun `a throwing destination does not stop the others`() = runTest {
        val (first, _) = endpoint("https://a.example.com/", "gateway-a", boom())
        val (second, secondService) = endpoint("https://b.example.com/", "gateway-b", ok("b"))

        val results = TrackerRemoteDataSource(listOf(first, second)).send(batch)

        assertEquals(listOf("gateway-b"), secondService.paths)
        assertEquals(listOf(first.destination.id), results.failedDestinationIds())
        assertEquals("b", results.firstSuccessBody())
    }

    @Test
    fun `response body comes from the first successful destination`() = runTest {
        val (failing, _) = endpoint("https://a.example.com/", "gateway-a", serverError())
        val (first, _) = endpoint("https://b.example.com/", "gateway-b", ok("first"))
        val (second, _) = endpoint("https://c.example.com/", "gateway-c", ok("second"))

        val results = TrackerRemoteDataSource(listOf(failing, first, second)).send(batch)

        assertEquals("first", results.firstSuccessBody())
    }

    @Test
    fun `body is null when every destination fails`() = runTest {
        val (first, _) = endpoint("https://a.example.com/", "gateway-a", serverError())
        val (second, _) = endpoint("https://b.example.com/", "gateway-b", boom())

        val results = TrackerRemoteDataSource(listOf(first, second)).send(batch)

        assertEquals(2, results.failedDestinationIds().size)
        assertNull(results.firstSuccessBody())
    }
}
