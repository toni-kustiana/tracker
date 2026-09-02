package id.co.edtslib.tracker.data

import java.security.MessageDigest

/**
 * One tracking endpoint. Every event is fanned out to every registered destination,
 * each with its own credentials.
 */
data class TrackerDestination(
    val baseUrl: String,
    val token: String,
    val path: String = DEFAULT_PATH,
    val isLegacy: Boolean = false
) {
    /**
     * Stable identifier derived from [baseUrl] and [path], used as the suffix of this
     * destination's local cache key so a failed send is only retained for the
     * destination that actually failed.
     */
    val id: String by lazy { hash("$baseUrl|$path") }

    companion object {
        const val DEFAULT_PATH = "apps-tracker-gateway"

        private fun hash(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            val builder = StringBuilder()
            for (i in 0 until 8) {
                builder.append(String.format("%02x", digest[i]))
            }
            return builder.toString()
        }
    }
}
