package dev.minimalist.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Coordinates for the prayer solver, and nothing else.
 *
 * The location is read once, when asked, and written straight into settings as two numbers.
 * Nothing here runs in the background, nothing is logged, and refusing the permission costs
 * only the convenience of not typing the coordinates yourself.
 */
object LocationFetcher {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    val permissions: Array<String> = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    /**
     * The last known fix if there is a usable one, otherwise a single fresh reading. Null when
     * the permission is missing, the providers are off, or nothing answers in time.
     */
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null

        lastKnown(manager)?.let { return it }

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val executor = Executors.newSingleThreadExecutor()
            continuation.invokeOnCancellation {
                runCatching { signal.cancel() }
                executor.shutdown()
            }
            runCatching {
                manager.getCurrentLocation(provider, signal, executor) { location ->
                    executor.shutdown()
                    if (continuation.isActive) continuation.resume(location)
                }
            }.onFailure {
                executor.shutdown()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun lastKnown(manager: LocationManager): Location? = runCatching {
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { manager.getLastKnownLocation(it) }
            // A prayer timetable does not care about last week's fix, but it barely cares about
            // a hundred kilometres either — an hour old is plenty fresh.
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MILLIS }
            .maxByOrNull { it.time }
    }.getOrNull()

    private const val MAX_AGE_MILLIS = 60L * 60 * 1000
}
