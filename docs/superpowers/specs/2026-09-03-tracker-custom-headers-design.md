# Custom Header pada Request API Tracker

Date: 2026-09-03

## Problem

Header request tracker saat ini terkunci di `AuthInterceptor`: hanya `Authorization`
(mode legacy) atau `x-api-key`. Client tidak punya cara menambahkan header sendiri,
baik yang statis (mis. `x-app-id`, `x-client-version`) maupun yang harus dihitung per
request seperti `x-signature` (HMAC atas body payload). Endpoint dibangun sekali di
`provideEndpoints`, sehingga tidak ada titik masuk bagi client.

## Goal

Dua mekanisme terpisah:

1. Metode untuk menambahkan header **statis** yang ikut di setiap request.
2. Callback yang dipanggil **sebelum request dieksekusi**, menerima objek request, di
   mana client bisa menambahkan header yang dihitung dari request tersebut.

Keduanya berlaku untuk semua destination.

## Decisions

| Aspek | Keputusan |
|---|---|
| Cakupan | Global (satu konfigurasi untuk semua destination), bukan per destination |
| Header statis | `ConcurrentHashMap` di `Tracker`; `headers` mengembalikan snapshot |
| Bentuk callback | Mengisi `MutableMap<String, String>`, tanpa return value |
| Param callback | `destination`, `request` (`okhttp3.Request`), `headers` — 3 param |
| Body | TIDAK dibaca library; client membaca sendiri dari `request.body` |
| Layer | OkHttp `Interceptor` baru, satu instance per destination |
| Penerapan header | `Request.Builder.header()` (replace), bukan `addHeader()` (append) |
| Callback gagal | Ditangkap; request tetap dikirim tanpa header dari callback |
| Waktu konfigurasi | Kapan saja — dibaca saat request berjalan, bukan saat endpoint dibangun |

## Design

### 1. API publik

Di `Tracker.Companion`:

```kotlin
fun addHeader(name: String, value: String)
fun addHeaders(headers: Map<String, String>)
fun removeHeader(name: String)
val headers: Map<String, String>   // immutable snapshot

var headerCallback: TrackerHeaderCallback? = null
```

Header statis disimpan di `ConcurrentHashMap` karena ditulis dari main thread tapi
dibaca dari thread jaringan.

Callback:

```kotlin
fun interface TrackerHeaderCallback {
    fun onRequest(
        destination: TrackerDestination,
        request: Request,                    // okhttp3.Request
        headers: MutableMap<String, String>
    )
}
```

Pemakaian di sisi client:

```kotlin
Tracker.addHeader("x-app-id", "myapp")

Tracker.headerCallback = TrackerHeaderCallback { destination, request, headers ->
    val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
    headers["x-signature"] = hmacSha256(secretFor(destination.baseUrl), body)
    headers["x-timestamp"] = System.currentTimeMillis().toString()
}
```

`destination` diberikan utuh (`baseUrl`, `token`, `path`, `isLegacy`, `id`) supaya
callback global tetap bisa memakai secret yang berbeda per gateway. `path` tidak jadi
parameter terpisah karena sudah tersedia sebagai `destination.path`.

Body tidak dibaca oleh library. Client membacanya sendiri dari `request.body` sehingga
menandatangani byte yang persis akan dikirim; menyerialisasi ulang payload bertipe akan
menghasilkan signature yang mismatch.

### 2. Komponen

**`TrackerHeaders`** (`di/TrackerHeaders.kt`) — fungsi murni penyusun map final:

```kotlin
internal object TrackerHeaders {
    fun build(
        destination: TrackerDestination,
        request: Request,
        staticHeaders: Map<String, String>,
        callback: TrackerHeaderCallback?
    ): Map<String, String>
}
```

Alur: salin `staticHeaders` ke `LinkedHashMap` → kalau `callback != null`, panggil
`onRequest` dengan map itu → kembalikan map. Callback bisa menambah maupun menimpa
header statis.

Seluruh logika dan penanganan error callback ada di sini; ini yang dites.

**`TrackerHeaderInterceptor`** (`di/TrackerHeaderInterceptor.kt`) — glue tipis:

```kotlin
internal class TrackerHeaderInterceptor(
    private val destination: TrackerDestination
) : Interceptor
```

Memanggil `TrackerHeaders.build(destination, chain.request(), Tracker.headers, Tracker.headerCallback)`,
lalu menerapkan tiap entry ke `chain.request().newBuilder()` dan `chain.proceed()`.

Pakai `header()` (replace), bukan `addHeader()` (append): kalau client memakai nama yang
sama dua kali atau bentrok dengan `x-api-key` dari `AuthInterceptor`, hasilnya
deterministik — nilai client menang — bukan dua header bernama sama yang perilakunya
bergantung server.

**Wiring** — di `provideEndpoints` (`di/MainAppModule.kt`), setelah `AuthInterceptor`:

```kotlin
okHttpClient.newBuilder()
    .addInterceptor(AuthInterceptor(destination.token, destination.isLegacy))
    .addInterceptor(TrackerHeaderInterceptor(destination))
    .build()
```

Interceptor dibuat per destination sehingga konteks destination didapat dari konstruktor,
tanpa perlu diteruskan lewat rantai lain.

### 3. Penanganan error

Prinsip: tracking tidak boleh menjatuhkan aplikasi host.

- Callback melempar exception (bug di kode signature client, secret null) → ditangkap di
  `TrackerHeaders.build`. Map dikembalikan apa adanya saat exception terjadi, sehingga
  header statis dan header yang sudah diisi callback sebelum exception tetap terpakai.
  Di-log lewat `Log.e` hanya ketika `Tracker.debugging` aktif, mengikuti pola debugging
  di `TrackerOkHttpClient`.
- Nilai header tidak valid (newline atau karakter non-ASCII) membuat OkHttp melempar
  `IllegalArgumentException`. Tiap entry diterapkan dalam try/catch sendiri di
  interceptor, jadi satu header rusak tidak menggugurkan header lain yang valid.

Konsekuensi yang diterima: signature yang gagal dihitung berarti request terkirim tanpa
`x-signature` dan kemungkinan ditolak server dengan 401/403. Ini bukan gagal senyap —
mekanisme retry per-destination yang sudah ada akan menyimpannya ke cache lokal, dan
error HTTP-nya bisa didiagnosis. Alternatif membatalkan request sama sekali ditolak
karena lebih rumit tanpa manfaat yang jelas.

### 4. Tes

Mengikuti gaya repo: JUnit murni, fake buatan sendiri, tanpa library mocking.

**`TrackerHeadersTest`** — inti, tanpa fake sama sekali (`Request` dibuat dengan
`Request.Builder`):

- header statis ikut di map hasil
- callback bisa menambahkan header baru
- callback bisa menimpa header statis
- callback yang melempar exception tidak menggugurkan header statis
- `destination` dan `request` yang benar diteruskan ke callback
- tanpa callback dan tanpa header statis, hasilnya map kosong

**`TrackerHeaderInterceptorTest`** — satu fake `Interceptor.Chain` minimal, memastikan
header benar-benar menempel di request yang diteruskan ke `proceed()`, dan nilai header
tidak valid tidak menggugurkan header lain.

## Out of Scope

- Konfigurasi header per destination (cakupan sepakat global).
- Kemampuan callback mengubah body, URL, atau method request.
- Header pada response.
