package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class TrackerHeaderInterceptorTest {

    private val destination = TrackerDestination(
        baseUrl = "https://a.example.com/",
        token = "token-a",
        path = "gateway-a"
    )

    /** Records the request the interceptor hands down the chain. */
    private class FakeChain(private val request: Request) : Interceptor.Chain {
        var proceeded: Request? = null

        override fun request() = request

        override fun proceed(request: Request): Response {
            proceeded = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun readTimeoutMillis(): Int = 0
        override fun writeTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    private fun chain(build: Request.Builder.() -> Unit = {}): FakeChain {
        val request = Request.Builder()
            .url("https://a.example.com/gateway-a")
            .post("""{"events":[]}""".toRequestBody("application/json".toMediaType()))
            .apply(build)
            .build()
        return FakeChain(request)
    }

    @Before
    fun reset() {
        Tracker.headers.keys.forEach { Tracker.removeHeader(it) }
        Tracker.headerCallback = null
    }

    @Test
    fun `sends the request unchanged when nothing is configured`() {
        val chain = chain()

        TrackerHeaderInterceptor(destination).intercept(chain)

        assertNull(chain.proceeded?.header("x-signature"))
    }

    @Test
    fun `applies static headers to the outgoing request`() {
        Tracker.addHeader("x-app-id", "myapp")
        val chain = chain()

        TrackerHeaderInterceptor(destination).intercept(chain)

        assertEquals("myapp", chain.proceeded?.header("x-app-id"))
    }

    @Test
    fun `applies headers set by the callback`() {
        Tracker.headerCallback = TrackerHeaderCallback { _, _, headers ->
            headers["x-signature"] = "sig"
        }
        val chain = chain()

        TrackerHeaderInterceptor(destination).intercept(chain)

        assertEquals("sig", chain.proceeded?.header("x-signature"))
    }

    @Test
    fun `a header replaces rather than duplicates one already on the request`() {
        Tracker.addHeader("x-api-key", "from-client")
        val chain = chain { addHeader("x-api-key", "from-auth") }

        TrackerHeaderInterceptor(destination).intercept(chain)

        assertEquals(listOf("from-client"), chain.proceeded?.headers("x-api-key"))
    }

    @Test
    fun `an invalid header value does not drop the valid ones`() {
        Tracker.addHeaders(
            linkedMapOf(
                "x-signature" to "line\nbreak",
                "x-app-id" to "myapp"
            )
        )
        val chain = chain()

        TrackerHeaderInterceptor(destination).intercept(chain)

        assertNull(chain.proceeded?.header("x-signature"))
        assertEquals("myapp", chain.proceeded?.header("x-app-id"))
    }
}
