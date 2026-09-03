package id.co.edtslib.tracker.di

import id.co.edtslib.tracker.data.TrackerDestination
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerHeadersTest {

    private val destination = TrackerDestination(
        baseUrl = "https://a.example.com/",
        token = "token-a",
        path = "gateway-a"
    )

    private fun request(body: String = """{"events":[]}""") = Request.Builder()
        .url("https://a.example.com/gateway-a")
        .post(body.toRequestBody("application/json".toMediaType()))
        .build()

    private fun build(
        staticHeaders: Map<String, String> = emptyMap(),
        request: Request = request(),
        callback: TrackerHeaderCallback? = null
    ) = TrackerHeaders.build(destination, request, staticHeaders, callback)

    @Test
    fun `no static headers and no callback produces no headers`() {
        assertEquals(emptyMap<String, String>(), build())
    }

    @Test
    fun `static headers are included`() {
        val headers = build(staticHeaders = mapOf("x-app-id" to "myapp"))

        assertEquals(mapOf("x-app-id" to "myapp"), headers)
    }

    @Test
    fun `callback can add a header`() {
        val headers = build(
            staticHeaders = mapOf("x-app-id" to "myapp"),
            callback = TrackerHeaderCallback { _, _, headers -> headers["x-signature"] = "sig" }
        )

        assertEquals(mapOf("x-app-id" to "myapp", "x-signature" to "sig"), headers)
    }

    @Test
    fun `callback can override a static header`() {
        val headers = build(
            staticHeaders = mapOf("x-app-id" to "static"),
            callback = TrackerHeaderCallback { _, _, headers -> headers["x-app-id"] = "dynamic" }
        )

        assertEquals(mapOf("x-app-id" to "dynamic"), headers)
    }

    @Test
    fun `a throwing callback keeps static headers and whatever it already set`() {
        val headers = build(
            staticHeaders = mapOf("x-app-id" to "myapp"),
            callback = TrackerHeaderCallback { _, _, headers ->
                headers["x-timestamp"] = "1"
                throw IllegalStateException("secret missing")
            }
        )

        assertEquals(mapOf("x-app-id" to "myapp", "x-timestamp" to "1"), headers)
    }

    @Test
    fun `callback receives the destination it is sending to`() {
        var seen: TrackerDestination? = null

        build(callback = TrackerHeaderCallback { destination, _, _ -> seen = destination })

        assertSame(destination, seen)
    }

    @Test
    fun `callback receives the request whose body is about to be sent`() {
        val request = request("""{"events":["a"]}""")
        var seenUrl: String? = null
        var seenBody: String? = null

        build(request = request, callback = TrackerHeaderCallback { _, seenRequest, _ ->
            seenUrl = seenRequest.url.toString()
            seenBody = Buffer().also { seenRequest.body?.writeTo(it) }.readUtf8()
        })

        assertEquals("https://a.example.com/gateway-a", seenUrl)
        assertEquals("""{"events":["a"]}""", seenBody)
    }

    @Test
    fun `the map handed to the callback does not leak the static header map`() {
        val staticHeaders = mutableMapOf("x-app-id" to "myapp")

        build(
            staticHeaders = staticHeaders,
            callback = TrackerHeaderCallback { _, _, headers -> headers["x-signature"] = "sig" }
        )

        assertTrue(staticHeaders.keys.toString(), "x-signature" !in staticHeaders)
    }
}
