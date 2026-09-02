package id.co.edtslib.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackerDestinationTest {

    @Test
    fun `id is stable for the same base url and path`() {
        val a = TrackerDestination("https://a.example.com/", "token-1", "gateway")
        val b = TrackerDestination("https://a.example.com/", "token-2", "gateway", isLegacy = true)

        assertEquals(a.id, b.id)
    }

    @Test
    fun `id differs when the path differs`() {
        val a = TrackerDestination("https://a.example.com/", "token", "gateway-v1")
        val b = TrackerDestination("https://a.example.com/", "token", "gateway-v2")

        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `id differs when the base url differs`() {
        val a = TrackerDestination("https://a.example.com/", "token", "gateway")
        val b = TrackerDestination("https://b.example.com/", "token", "gateway")

        assertNotEquals(a.id, b.id)
    }
}
