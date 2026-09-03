package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.Tracker
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackerLoggingInterceptorTest {

    private val lines = mutableListOf<String>()

    private val logger = object : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            lines.add(message)
        }
    }

    @Before
    @After
    fun resetDebugging() {
        Tracker.debugging = false
        lines.clear()
    }

    @Test
    fun `logs nothing while debugging is off`() {
        TrackerLoggingInterceptor(logger).intercept(FakeChain(trackingRequest()))

        assertEquals(emptyList<String>(), lines)
    }

    @Test
    fun `logs the headers actually on the request while debugging is on`() {
        Tracker.debugging = true
        val request = trackingRequest { addHeader("x-signature", "sig") }

        TrackerLoggingInterceptor(logger).intercept(FakeChain(request))

        assertTrue(lines.toString(), lines.any { it == "x-signature: sig" })
    }

    @Test
    fun `logs the request body while debugging is on`() {
        Tracker.debugging = true
        val request = trackingRequest(body = """{"events":["a"]}""")

        TrackerLoggingInterceptor(logger).intercept(FakeChain(request))

        assertTrue(lines.toString(), lines.any { it == """{"events":["a"]}""" })
    }

    @Test
    fun `the debugging flag is read per request, not once at construction`() {
        val interceptor = TrackerLoggingInterceptor(logger)

        interceptor.intercept(FakeChain(trackingRequest()))
        assertEquals(emptyList<String>(), lines)

        Tracker.debugging = true
        interceptor.intercept(FakeChain(trackingRequest()))

        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun `the request is passed down the chain unchanged`() {
        Tracker.debugging = true
        val request = trackingRequest()
        val chain = FakeChain(request)

        TrackerLoggingInterceptor(logger).intercept(chain)

        assertEquals(request, chain.proceeded)
    }
}
