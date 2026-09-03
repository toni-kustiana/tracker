package id.co.edtslib.tracker

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TrackerStaticHeaderTest {

    @Before
    fun reset() {
        Tracker.headers.keys.forEach { Tracker.removeHeader(it) }
    }

    @Test
    fun `starts with no headers`() {
        assertEquals(emptyMap<String, String>(), Tracker.headers)
    }

    @Test
    fun `addHeader registers a header`() {
        Tracker.addHeader("x-app-id", "myapp")

        assertEquals(mapOf("x-app-id" to "myapp"), Tracker.headers)
    }

    @Test
    fun `addHeader replaces an existing value for the same name`() {
        Tracker.addHeader("x-app-id", "old")
        Tracker.addHeader("x-app-id", "new")

        assertEquals(mapOf("x-app-id" to "new"), Tracker.headers)
    }

    @Test
    fun `addHeaders registers every entry`() {
        Tracker.addHeaders(mapOf("x-app-id" to "myapp", "x-client-version" to "2.4.1"))

        assertEquals(
            mapOf("x-app-id" to "myapp", "x-client-version" to "2.4.1"),
            Tracker.headers
        )
    }

    @Test
    fun `removeHeader drops only the named header`() {
        Tracker.addHeaders(mapOf("x-app-id" to "myapp", "x-client-version" to "2.4.1"))

        Tracker.removeHeader("x-app-id")

        assertEquals(mapOf("x-client-version" to "2.4.1"), Tracker.headers)
    }

    @Test
    fun `removeHeader on an unknown name changes nothing`() {
        Tracker.addHeader("x-app-id", "myapp")

        Tracker.removeHeader("x-nope")

        assertEquals(mapOf("x-app-id" to "myapp"), Tracker.headers)
    }

    @Test
    fun `headers is a snapshot that later changes do not mutate`() {
        Tracker.addHeader("x-app-id", "myapp")
        val snapshot = Tracker.headers

        Tracker.addHeader("x-client-version", "2.4.1")

        assertEquals(mapOf("x-app-id" to "myapp"), snapshot)
    }
}
