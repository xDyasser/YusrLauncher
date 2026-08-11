package dev.minimalist.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.MutationResult
import dev.minimalist.data.settings.AyahLanguage
import dev.minimalist.data.settings.LocationSource
import dev.minimalist.data.settings.PrayerSettings
import dev.minimalist.domain.AsrMethod
import dev.minimalist.domain.CalculationMethod
import dev.minimalist.domain.Prayer
import dev.minimalist.domain.PrayerTimetable
import dev.minimalist.ui.t
import dev.minimalist.ui.MinimalButton
import dev.minimalist.ui.MinimalPage
import dev.minimalist.ui.PillPicker
import dev.minimalist.ui.noRippleClickable
import dev.minimalist.ui.theme.Faint
import dev.minimalist.util.DayClock
import dev.minimalist.util.LocationFetcher
import dev.minimalist.work.WorkScheduler
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Everything about salah in one place.
 *
 * The computed times for today sit at the top, because a calculation method is an abstraction
 * and a timetable is not: if these do not match the masjid down the road, that is visible here
 * before it is ever enforced.
 */
@Composable
fun PrayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val mutator = remember { context.container.ruleMutator }
    val prayerRepository = remember { context.container.prayerRepository }
    val quran = remember { context.container.quran }

    val settings by store.settings.collectAsState(initial = null)
    val prayer = settings?.prayer ?: return

    var notice by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val timetable by produceState<PrayerTimetable?>(null, prayer, refresh) {
        value = if (prayer.configured) prayerRepository.timetable(prayer) else null
    }
    val ayatDownloaded by produceState(0, refresh) { value = quran.downloadedCount() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            scope.launch {
                notice = useDeviceLocation(context, store)
                refresh++
            }
        } else {
            notice = t("location refused — type the coordinates instead.")
        }
    }

    MinimalPage(
        title = t("Prayer times and salah"),
        subtitle = if (prayer.configured) {
            "%.4f, %.4f · %s".format(prayer.latitude, prayer.longitude, prayer.method.label)
        } else {
            t("set a location and the rest follows")
        },
    ) {
        TodaysTimes(timetable = timetable, prayer = prayer)

        // ---- location ----------------------------------------------------------------
        Section(t("LOCATION")) {
            MinimalButton(label = t("use my location")) {
                if (LocationFetcher.hasPermission(context)) {
                    scope.launch {
                        notice = useDeviceLocation(context, store)
                        refresh++
                    }
                } else {
                    locationPermission.launch(LocationFetcher.permissions)
                }
            }
            ManualCoordinates(
                latitude = prayer.latitude,
                longitude = prayer.longitude,
                modifier = Modifier.padding(top = 12.dp),
            ) { latitude, longitude ->
                scope.launch {
                    store.setLocation(latitude, longitude, LocationSource.MANUAL)
                    prayerRepository.clearSyncedDays()
                    WorkScheduler.syncPrayerTimesNow(context)
                    notice = t("location set.")
                    refresh++
                }
            }
        }

        // ---- method ------------------------------------------------------------------
        Section(t("CALCULATION")) {
            Text(
                text = t("the school and authority you follow. jafari is the shia calculation, ") +
                    t("with maghrib after sunset rather than at it."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            CalculationMethod.entries.chunked(4).forEach { row ->
                PillPicker(
                    options = row.map { it.shortLabel },
                    selectedIndex = row.indexOf(prayer.method),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) { index -> scope.launch { store.setMethod(row[index]); refresh++ } }
            }

            Text(
                text = t("ASR"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            PillPicker(
                options = listOf(t("standard"), t("hanafi")),
                selectedIndex = if (prayer.asr == AsrMethod.HANAFI) 1 else 0,
            ) { index ->
                scope.launch {
                    store.setAsrMethod(if (index == 1) AsrMethod.HANAFI else AsrMethod.STANDARD)
                    refresh++
                }
            }
        }

        // ---- the windows -------------------------------------------------------------
        Section(t("WHEN THE PHONE STOPS")) {
            Text(
                text = t("during a prayer window nothing opens but calls and whatever you have ") +
                    t("marked as opening during salah. favourites included."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            MinimalButton(
                label = if (prayer.enabled) t("stop pausing for salah") else t("pause for salah"),
                enabled = prayer.configured,
            ) {
                scope.launch {
                    notice = report(mutator.setPrayerEnabled(!prayer.enabled))
                    refresh++
                }
            }
            if (!prayer.configured) {
                Text(
                    text = t("a location comes first — the times cannot be worked out without one."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            WindowStepper(
                caption = t("minutes before the adhan"),
                value = prayer.windowBeforeMinutes,
                modifier = Modifier.padding(top = 20.dp),
            ) { next ->
                scope.launch {
                    notice = report(mutator.setPrayerWindowMinutes(next, prayer.windowAfterMinutes))
                    refresh++
                }
            }
            WindowStepper(
                caption = t("minutes after"),
                value = prayer.windowAfterMinutes,
                modifier = Modifier.padding(top = 16.dp),
            ) { next ->
                scope.launch {
                    notice = report(mutator.setPrayerWindowMinutes(prayer.windowBeforeMinutes, next))
                    refresh++
                }
            }

            Text(
                text = t("COMBINING"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            Text(
                text = t("one window for dhuhr and asr, one for maghrib and isha — two pauses a ") +
                    t("day rather than four."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PillPicker(
                options = listOf(t("separate"), t("combined")),
                selectedIndex = if (prayer.combineDhuhrAsr && prayer.combineMaghribIsha) 1 else 0,
            ) { index ->
                val combined = index == 1
                scope.launch { store.setCombine(combined, combined); refresh++ }
            }
        }

        // ---- the ayah ----------------------------------------------------------------
        Section(t("THE AYAH AT THE GATE")) {
            Text(
                text = if (ayatDownloaded > 0) {
                    "$ayatDownloaded ayat on the phone. shown at random when you try to open " +
                        t("something gated.")
                } else {
                    t("a bundled handful is in use. downloading the qur'an replaces it with all ") +
                        t("6,236, and then the network is never needed again.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PillPicker(
                options = listOf(t("arabic"), t("english"), t("both")),
                selectedIndex = AyahLanguage.entries.indexOf(prayer.ayahLanguage),
            ) { index -> scope.launch { store.setAyahLanguage(AyahLanguage.entries[index]) } }

            MinimalButton(
                label = if (ayatDownloaded > 0) t("download again") else t("download the qur'an"),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                WorkScheduler.downloadQuran(context, replaceExisting = true)
                notice = t("downloading — it carries on in the background.")
            }
        }

        // ---- corrections -------------------------------------------------------------
        Section(t("CORRECTIONS")) {
            Text(
                text = t("these make the times right rather than weaker, so they take effect at once."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Prayer.entries.filter { it.isPrayer }.forEach { which ->
                OffsetRow(
                    prayer = which,
                    minutes = prayer.offsetMinutes[which] ?: 0,
                    at = timetable?.minuteOfDay(which),
                ) { next -> scope.launch { store.setPrayerOffset(which, next); refresh++ } }
            }

            Text(
                text = t("HIJRI DATE"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Text(
                text = t("the calendar is calculated, not sighted, so it can sit a day either side ") +
                    t("of the announcement where you are."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PillPicker(
                options = listOf("−1", "0", "+1"),
                selectedIndex = prayer.hijriOffsetDays.coerceIn(-1, 1) + 1,
            ) { index -> scope.launch { store.setHijriOffsetDays(index - 1) } }

            Text(
                text = t("NETWORK"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Text(
                text = t("with sync off, the times are computed on this phone and nothing is ever ") +
                    t("fetched. everything still works."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            PillPicker(
                options = listOf(t("compute only"), t("sync too")),
                selectedIndex = if (prayer.syncOverNetwork) 1 else 0,
            ) { index ->
                scope.launch {
                    store.setPrayerSyncOverNetwork(index == 1)
                    if (index == 1) WorkScheduler.syncPrayerTimesNow(context) else prayerRepository.clearSyncedDays()
                    refresh++
                }
            }
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}

@Composable
private fun TodaysTimes(timetable: PrayerTimetable?, prayer: PrayerSettings) {
    if (timetable == null) {
        Text(
            text = t("no times yet."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Prayer.entries.forEach { which ->
            val minute = timetable.minuteOfDay(which)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    text = which.name.lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (which.isPrayer) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        Faint
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%02d:%02d".format(minute / 60, minute % 60),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (which.isPrayer) MaterialTheme.colorScheme.onBackground else Faint,
                )
            }
        }
        if (!prayer.enabled) {
            Text(
                text = t("shown only — nothing is being blocked for salah yet."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun ManualCoordinates(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    onSet: (Double, Double) -> Unit,
) {
    var latitudeText by remember(latitude) { mutableStateOf(if (latitude == 0.0) "" else latitude.toString()) }
    var longitudeText by remember(longitude) { mutableStateOf(if (longitude == 0.0) "" else longitude.toString()) }

    val parsedLatitude = latitudeText.trim().toDoubleOrNull()
    val parsedLongitude = longitudeText.trim().toDoubleOrNull()
    val valid = parsedLatitude != null && parsedLongitude != null &&
        parsedLatitude in -90.0..90.0 && parsedLongitude in -180.0..180.0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                PlainField(value = latitudeText, onValueChange = { latitudeText = it }, placeholder = t("latitude"))
            }
            Column(modifier = Modifier.weight(1f)) {
                PlainField(value = longitudeText, onValueChange = { longitudeText = it }, placeholder = t("longitude"))
            }
        }
        MinimalButton(
            label = t("set coordinates"),
            enabled = valid,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (parsedLatitude != null && parsedLongitude != null) onSet(parsedLatitude, parsedLongitude)
        }
    }
}

@Composable
private fun WindowStepper(
    caption: String,
    value: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "−",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.noRippleClickable { onChange((value - 5).coerceAtLeast(0)) },
        )
        Text(
            text = "${value}m",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.noRippleClickable { onChange((value + 5).coerceAtMost(120)) },
        )
    }
}

@Composable
private fun OffsetRow(prayer: Prayer, minutes: Int, at: Int?, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = prayer.name.lowercase(Locale.getDefault()) +
                (at?.let { "  %02d:%02d".format(it / 60, it % 60) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "−",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.noRippleClickable { onChange((minutes - 1).coerceAtLeast(-30)) },
        )
        Text(
            text = if (minutes > 0) "+$minutes" else minutes.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (minutes == 0) Faint else MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.noRippleClickable { onChange((minutes + 1).coerceAtMost(30)) },
        )
    }
}

private fun report(result: MutationResult): String = when (result) {
    is MutationResult.AppliedNow -> t("done.")
    is MutationResult.Deferred ->
        t("queued — it takes effect at ") +
            DayClock.localDateTime(result.applyAtMillis).toLocalTime().withSecond(0).withNano(0) + "."
}

private suspend fun useDeviceLocation(
    context: android.content.Context,
    store: dev.minimalist.data.settings.SettingsStore,
): String {
    val location = LocationFetcher.current(context)
        ?: return t("no fix — location may be off, or type the coordinates instead.")
    store.setLocation(location.latitude, location.longitude, LocationSource.DEVICE)
    context.container.prayerRepository.clearSyncedDays()
    WorkScheduler.syncPrayerTimesNow(context)
    return t("location set from the device.")
}

/** Full names for the subtitle, short ones for the pills. */
private val CalculationMethod.label: String
    get() = when (this) {
        CalculationMethod.MWL -> t("muslim world league")
        CalculationMethod.ISNA -> t("isna")
        CalculationMethod.EGYPTIAN -> t("egyptian authority")
        CalculationMethod.UMM_AL_QURA -> t("umm al-qura")
        CalculationMethod.KARACHI -> t("karachi")
        CalculationMethod.JAFARI -> t("jafari (qum)")
        CalculationMethod.TEHRAN -> t("tehran")
    }

private val CalculationMethod.shortLabel: String
    get() = when (this) {
        CalculationMethod.MWL -> t("mwl")
        CalculationMethod.ISNA -> t("isna")
        CalculationMethod.EGYPTIAN -> t("egypt")
        CalculationMethod.UMM_AL_QURA -> t("makkah")
        CalculationMethod.KARACHI -> t("karachi")
        CalculationMethod.JAFARI -> t("jafari")
        CalculationMethod.TEHRAN -> t("tehran")
    }
