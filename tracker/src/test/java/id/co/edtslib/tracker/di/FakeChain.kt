package id.co.edtslib.tracker.di

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

/** Records the request an interceptor hands down the chain. */
internal class FakeChain(
    private val request: Request,
    private val responseBody: String = ""
) : Interceptor.Chain {
    var proceeded: Request? = null

    override fun request() = request

    override fun proceed(request: Request): Response {
        proceeded = request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody.toResponseBody("text/plain".toMediaType()))
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

internal fun trackingRequest(
    body: String = """{"events":[]}""",
    build: Request.Builder.() -> Unit = {}
): Request = Request.Builder()
    .url("https://a.example.com/gateway-a")
    .post(body.toRequestBody("application/json".toMediaType()))
    .apply(build)
    .build()
