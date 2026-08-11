package dev.minimalist.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The whole networking layer.
 *
 * There is no HTTP library here on purpose: two endpoints, both GET, both JSON. Adding a
 * dependency to fetch two documents would be more machinery than the job needs, and every
 * caller here can be told "no" without anything breaking.
 */
object Http {

    /** Fetches a document as text, or null if anything at all goes wrong. */
    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (text.length > MAX_BYTES) null else text
            }
        } catch (_: Exception) {
            // No signal, DNS failure, a captive portal, a server having a bad day: all the same
            // answer here, because every caller already has an offline path.
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Streams a document to [sink], reporting bytes written as it goes, and answers whether it
     * arrived whole.
     *
     * Recitation is the one thing this app fetches that will not fit in memory twice over, and
     * the one thing slow enough that a progress figure is worth showing. A partial file is
     * reported as a failure so the caller can delete it: half a sūra that plays and then stops is
     * a worse outcome than no sūra at all.
     */
    suspend fun download(
        url: String,
        sink: OutputStream,
        onProgress: (bytes: Long, total: Long) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) return@withContext false
            val total = connection.contentLengthLong
            if (total > MAX_AUDIO_BYTES) return@withContext false

            var written = 0L
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    written += read
                    if (written > MAX_AUDIO_BYTES) return@withContext false
                    onProgress(written, total)
                }
            }
            sink.flush()
            // A server that closed early leaves a file that looks fine until it is played.
            total <= 0 || written == total
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /** Whether something is actually there, used to check a reciter before offering them. */
    suspend fun exists(url: String): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** The Qur'an with a translation is a few megabytes; anything far past that is not our file. */
    private const val MAX_BYTES = 24 * 1024 * 1024

    /** A sūra of recitation. Al-Baqarah at a high bitrate is the ceiling worth allowing for. */
    private const val MAX_AUDIO_BYTES = 220L * 1024 * 1024

    private const val USER_AGENT = "Yusr Launcher"
}
