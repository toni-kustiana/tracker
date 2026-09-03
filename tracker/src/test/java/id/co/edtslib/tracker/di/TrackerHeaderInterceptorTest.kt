package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.Tracker
import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TrackerHeaderInterceptorTest {

    private val destination = TrackerDestination(
        baseUrl = "https://a.example.com/",
        token = "token-a",
        path = "gateway-a"
    )

    private fun chain(build: Request.Builder.() -> Unit = {}) =
        FakeChain(trackingRequest(build = build))

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
