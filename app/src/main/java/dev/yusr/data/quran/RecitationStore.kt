package dev.yusr.data.quran

import android.content.Context
import dev.yusr.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Recitation on the phone rather than off the network.
 *
 * A sūrah is downloaded once, in whole, and then belongs to the phone: it plays on a train, in a
 * masjid basement, and after this app's two other endpoints have gone the way of all APIs. That
 * is the same bargain the Qur'an text download makes, and it is the only one consistent with an
 * app whose whole claim is that it works with the network permanently unavailable.
 *
 * What it costs is honesty about size. A sūrah at 128 kbps is roughly a megabyte a minute, so
 * al-Baqara is a couple of hours and a couple of hundred megabytes; [estimateBytes] exists so the
 * screen can say so before the download starts rather than after.
 */
class RecitationStore(private val context: Context) {

    /** How a sūrah's download is going, for the line under the reader's transport controls. */
    sealed interface Progress {
        data object Idle : Progress

        data class Running(val ayah: Int, val ayatTotal: Int, val bytes: Long) : Progress {
            val fraction: Float get() = if (ayatTotal <= 0) 0f else ayah.toFloat() / ayatTotal
        }

        data class Done(val bytes: Long) : Progress

        /** [ayah] is where it stopped, which is the useful half of a failure. */
        data class Failed(val ayah: Int, val reason: String) : Progress
    }

    private fun root(reciter: Reciter): File = File(context.filesDir, "recitation/${reciter.id}")

    private fun file(reciter: Reciter, surah: Int, ayah: Int): File =
        File(root(reciter), "${pad(surah)}${pad(ayah)}.mp3")

    /** The local file for an ayah, or null if it has not been downloaded. */
    fun localAyah(reciter: Reciter, surah: Int, ayah: Int): File? =
        file(reciter, surah, ayah).takeIf { it.isFile && it.length() > 0 }

    /** Whether every ayah of [surah] is on the phone, which is what makes it playable offline. */
    fun isDownloaded(reciter: Reciter, surah: Int): Boolean {
        val total = SurahNames.ayahCount(surah)
        if (total <= 0) return false
        return (1..total).all { localAyah(reciter, surah, it) != null }
    }

    /** How far a partial download got, so the screen can offer to finish it rather than restart. */
    fun downloadedAyat(reciter: Reciter, surah: Int): Int =
        (1..SurahNames.ayahCount(surah)).count { localAyah(reciter, surah, it) != null }

    /** Bytes actually on disk for [surah], for the storage line in the reciter screen. */
    fun bytesOnDisk(reciter: Reciter, surah: Int): Long =
        (1..SurahNames.ayahCount(surah)).sumOf { localAyah(reciter, surah, it)?.length() ?: 0L }

    fun bytesOnDisk(reciter: Reciter): Long =
        root(reciter).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * A rough size for [surah] before anything is fetched — bitrate against a spoken-word estimate
     * of about three seconds an ayah per ten words. It is an estimate and is shown as one; the
     * point is to distinguish "a few megabytes" from "most of your remaining storage".
     */
    fun estimateBytes(reciter: Reciter, surah: Int): Long {
        val ayat = SurahNames.ayahCount(surah)
        val seconds = ayat * SECONDS_PER_AYAH
        return seconds * reciter.kbps * 1000L / 8L
    }

    /**
     * Fetches every ayah of [surah] not already on the phone, reporting as it goes.
     *
     * Resumable by construction: an ayah already downloaded is skipped, so a cancelled download
     * costs nothing but the ayah it was in the middle of. Cancelling the coroutine stops it
     * between āyāt, and the partial file for the one in flight is deleted rather than left to
     * play as a fragment later.
     */
    suspend fun downloadSurah(
        reciter: Reciter,
        surah: Int,
        onProgress: (Progress) -> Unit = {},
    ): Progress = withContext(Dispatchers.IO) {
        val total = SurahNames.ayahCount(surah)
        if (total <= 0) return@withContext Progress.Failed(0, "not a sūrah")

        val directory = root(reciter)
        if (!directory.isDirectory && !directory.mkdirs()) {
            return@withContext Progress.Failed(0, "could not write to storage")
        }

        var bytes = 0L
        for (ayah in 1..total) {
            coroutineContext.ensureActive()

            val destination = file(reciter, surah, ayah)
            if (destination.isFile && destination.length() > 0) {
                bytes += destination.length()
                onProgress(Progress.Running(ayah, total, bytes))
                continue
            }

            // Written beside the real name and moved into place only once whole, so a file that
            // exists is a file that plays.
            val partial = File(destination.parentFile, destination.name + ".part")
            val ok = runCatching {
                partial.outputStream().use { sink ->
                    Http.download(reciter.url(surah, ayah), sink)
                }
            }.getOrDefault(false)

            if (!ok || partial.length() == 0L) {
                partial.delete()
                return@withContext Progress.Failed(ayah, "could not fetch ayah $ayah")
                    .also(onProgress)
            }
            if (!partial.renameTo(destination)) {
                partial.delete()
                return@withContext Progress.Failed(ayah, "could not save ayah $ayah").also(onProgress)
            }

            bytes += destination.length()
            onProgress(Progress.Running(ayah, total, bytes))
        }

        Progress.Done(bytes).also(onProgress)
    }

    /** Gives a sūrah's storage back. */
    fun deleteSurah(reciter: Reciter, surah: Int) {
        (1..SurahNames.ayahCount(surah)).forEach { file(reciter, surah, it).delete() }
    }

    fun deleteAll(reciter: Reciter) {
        root(reciter).deleteRecursively()
    }

    /** Whether the host has this reciter at all, asked before the picker offers them. */
    suspend fun isReachable(reciter: Reciter): Boolean = Http.exists(reciter.url(1, 1))

    private fun pad(value: Int): String = value.toString().padStart(3, '0')

    private companion object {
        const val SECONDS_PER_AYAH = 12L
    }
}
