# Tracker

![SlidingButton](https://i.ibb.co/GCcGMwH/edtslibs.png)
## Setup
### Gradle

Add this to your project level `build.gradle`:
```groovy
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```
Add this to your app `build.gradle`:
```groovy
dependencies {
    implementation 'com.github.edtslib:tracker:latest'
}
```

### Usage

- Create Class extend Application, and add to manifest android:name.
```xml
<application
    android:name=".App">
</application>
```

- On application oncreate, Initialize the tracker with call Tracker.init()

```kotlin
class App: Application() {
    override fun onCreate() {
        super.onCreate()

        Tracker.init(this,"https://asia-southeast2-idm-corp-dev.cloudfunctions.net",
            "fT2vJnJu4dsxTRMphdHE3Z92uwjaBRztGR3ECdRQTEyDDZJGbvGu")
    }
}
```

if you're already using Koin on your application, you can call init on your application using this method
```kotlin
fun init(baseUrl: String, token: String, koin: KoinApplication) 
```

- To send every event to more than one gateway, pass the extra destinations as the last
  argument of `init`. The `baseUrl`/`token`/`path`/`isLegacy` arguments describe the first
  destination; each entry in `otherDestinations` adds another one with its own credentials.

```kotlin
Tracker.init(
    this,
    "https://asia-southeast2-idm-corp-dev.cloudfunctions.net",
    "fT2vJnJu4dsxTRMphdHE3Z92uwjaBRztGR3ECdRQTEyDDZJGbvGu",
    otherDestinations = listOf(
        TrackerDestination(
            baseUrl = "https://tracker-v2.example.com/",
            token = "another-token",
            path = "apps-tracker-gateway-v2"
        )
    )
)
```

Every event is sent to all destinations in parallel. An event is kept for a later resend
only under the destinations that failed, so the ones that already accepted it never get a
duplicate. The response returned to the caller comes from the first destination that
succeeded.

- To add your own headers to every tracking request, register them with `addHeader` or
  `addHeaders`. They apply to all destinations and can be set at any time, before or
  after `init`.

```kotlin
Tracker.addHeader("x-app-id", "myapp")
Tracker.addHeaders(mapOf("x-client-version" to "2.4.1"))
Tracker.removeHeader("x-app-id")
```

- For a header whose value depends on the request itself — a signature over the body,
  for instance — set `headerCallback`. It is called once per destination right before
  each request is executed; add your entries to the `headers` map it hands you.

```kotlin
Tracker.headerCallback = TrackerHeaderCallback { destination, request, headers ->
    val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
    headers["x-signature"] = hmacSha256(secretFor(destination.baseUrl), body)
    headers["x-timestamp"] = System.currentTimeMillis().toString()
}
```

`request` is the `okhttp3.Request` about to be sent, so the body you read there is
exactly the bytes that go over the wire. `destination` carries that gateway's
`baseUrl`, `path` and `token`, so one callback can sign for several gateways with
different secrets.

Headers from the callback override static headers of the same name, and both override
the `x-api-key`/`Authorization` header the tracker sets itself. The callback runs on the
network thread; if it throws, the request is still sent with whatever headers were set
before the exception rather than failing the event.

- To see the requests in logcat, set `Tracker.debugging = true`. Each request is logged
  as it goes over the wire, so the log shows the full URL, the body, and every header —
  the static ones, the ones your callback added, and the tracker's own `x-api-key`.

```kotlin
Tracker.debugging = true
```

The flag is read per request, so you can turn it on and off at any time after `init`.
Keep it off in production builds: logcat can be read by other apps on a rooted device,
and the log includes your token and signature.

- Here is all static tracker method, call as Tracker.<mehtod_name>

```kotlin

fun setUserId(userId: Long)

fun trackPage(screenName: String)

fun trackPageDetail(name: String, detail: Any?)

fun trackClick(name: String)

fun trackFilters(name: String, filters: List<String>)

fun trackSort(name: String, sortType: String)

fun trackImpression(name: String, data: Any)

fun trackSubmissionSuccess(name: String)

fun trackSubmissionFailed(name: String, reason: String?)

fun trackExitApplication()

fun checkInstallReferrer(activity: FragmentActivity)

fun checkInstallReferrer(utm_raw: String?, intent: Intent?)

```
