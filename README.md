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
  destination; each entry in `destinations` adds another one with its own credentials.

```kotlin
Tracker.init(
    this,
    "https://asia-southeast2-idm-corp-dev.cloudfunctions.net",
    "fT2vJnJu4dsxTRMphdHE3Z92uwjaBRztGR3ECdRQTEyDDZJGbvGu",
    destinations = listOf(
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
