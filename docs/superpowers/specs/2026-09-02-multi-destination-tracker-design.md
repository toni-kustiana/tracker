# Multi-Destination Tracker (multiple base URL + path)

Date: 2026-09-02

## Problem

`Tracker` hanya mendukung satu tujuan pengiriman: `Tracker.baseUrl`, `Tracker.path`,
`Tracker.token`, `Tracker.isLegacy` bersifat tunggal dan hanya ada satu instance
Retrofit. Setiap event tracking dikirim ke satu endpoint saja, sehingga tidak bisa
melakukan dual-write (mis. saat migrasi gateway lama ke gateway baru).

## Goal

Mengizinkan beberapa destination, masing-masing dengan `baseUrl`, `path`, `token`, dan
`isLegacy` sendiri. Setiap event di-fan-out ke SEMUA destination.

## Decisions

| Aspek | Keputusan |
|---|---|
| Perilaku | Fan-out ke semua destination |
| Config per destination | `baseUrl` + `path` + `token` + `isLegacy` |
| Cache lokal | Per-destination, hanya destination yang gagal |
| ID cache | SHA-256 dari `baseUrl` + `path`, 16 hex pertama |
| Eksekusi | Paralel (`async` / `awaitAll`) |
| `TrackerResponse` | Body dari destination pertama yang sukses |
| API | Parameter `destinations` baru di akhir `init`; field statis lama dipertahankan |

## Design

### 1. API publik

```kotlin
data class TrackerDestination(
    val baseUrl: String,
    val token: String,
    val path: String = "apps-tracker-gateway",
    val isLegacy: Boolean = false
) {
    val id: String   // SHA-256("$baseUrl|$path"), 16 hex pertama
}
```

Kedua overload `Tracker.init` mendapat parameter terakhir
`destinations: List<TrackerDestination> = emptyList()`. Argumen lama membentuk
destination pertama; `destinations` menambah destinasi berikutnya.
`Tracker.destinations = listOf(primary) + destinations`.

Field statis `baseUrl` / `token` / `path` / `isLegacy` dipertahankan dan diisi dari
destination pertama, sehingga host app yang membacanya tidak berubah. Pemanggilan
`init` lama (tanpa parameter baru) berperilaku persis seperti sebelumnya.

### 2. Wiring DI

OkHttp, Gson, dan converter factory tetap dibagi ke semua destination. Provider
Retrofit tunggal (`named("tracker")`) dan `provideTrackerApiService` diganti satu
provider yang mengembalikan `List<TrackerEndpoint>`:

```kotlin
data class TrackerEndpoint(val destination: TrackerDestination, val service: TrackerApiService)
```

Tiap destination memakai `okHttpClient.newBuilder().addInterceptor(AuthInterceptor(token, isLegacy))`
sehingga kredensial benar-benar terpisah. `AuthInterceptor` tidak berubah.

### 3. Pengiriman fan-out

```kotlin
data class TrackerSendResult(val destinationId: String, val result: Result<String>)

suspend fun send(trackers: TrackerDataList): List<TrackerSendResult>
```

Implementasi `coroutineScope { endpoints.map { async { ... } }.awaitAll() }`. `path`
diambil per-destination, bukan dari `Tracker.path`. `getResult` menangkap semua
exception sehingga kegagalan satu destination tidak membatalkan destination lain.

### 4. Cache lokal per destination

`TrackerLocalDataSource` menyimpan referensi `SharedPreferences` dan menambah:

```kotlin
fun add(destinationId: String, trackerData: TrackerDataList)
fun getCached(destinationId: String): List<TrackerData>?
```

Key: `"trackers_<destinationId>"`. Guard `Tracker.resend` tetap berlaku. Key lama
`"trackers"` dibiarkan apa adanya (tidak ada yang membacanya saat ini); tidak ada
migrasi agar data lama tidak salah-alamat ke destination yang sudah sukses.

### 5. Dedup di TrackerRepository

Blok `when(response.status)` yang identik di 10 method diganti satu helper:

```kotlin
private suspend fun dispatch(trackerData: TrackerData): TrackerResponse {
    val list = TrackerDataList(mutableListOf(trackerData))
    val results = remoteSource.send(list)
    results.filter { it.result.status != Result.Status.SUCCESS }
           .forEach { localSource.add(it.destinationId, list) }
    val firstSuccess = results.firstOrNull { it.result.status == Result.Status.SUCCESS }
    return TrackerResponse(Gson().toJson(trackerData), firstSuccess?.result?.data)
}
```

Cabang `else -> {}` untuk status `LOADING` hilang; `getResult` tidak pernah
mengembalikan `LOADING` sehingga tidak ada dampak perilaku.

### 6. Testing

`kotlinx-coroutines-test` ditambahkan ke `testImplementation`. Tes JVM murni:

- `TrackerDestination.id` stabil untuk input sama, berbeda saat `baseUrl`/`path` berbeda
- fan-out memanggil semua endpoint
- sebagian gagal -> hanya destination yang gagal yang masuk cache
- `TrackerResponse.response` diambil dari destination sukses pertama

`TrackerRepository` bergantung pada `SharedPreferences` dan `Application`, sehingga
verifikasi end-to-end dilakukan lewat app sample.
