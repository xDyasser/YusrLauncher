package dev.minimalist.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.settings.LocationSource
import dev.minimalist.domain.HighLatitudeRule
import dev.minimalist.domain.Madhab
import dev.minimalist.domain.Prayer
import dev.minimalist.ui.t
import dev.minimalist.ui.Hairline
import dev.minimalist.ui.SectionLabel
import dev.minimalist.ui.theme.Dim
import kotlinx.coroutines.launch

/**
 * The school, and the conventions that follow from it.
 *
 * Choosing a madhab here rewrites the asr rule, the calculation method, and whether the prayers
 * are combined — because those are what the school *means*, and asking about them separately is
 * asking the same question four times in words most people cannot answer. Everything it set is
 * still visible underneath and still editable, so this is a starting point rather than a lock.
 *
 * None of it goes through the cooldown queue. A calculation method is not a rule about phone use:
 * it makes the times *right* or *wrong*, and there is nothing to be gained by making someone wait
 * half an hour to correct their own timetable.
 */
@Composable
fun MadhabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val settings by store.settings.collectAsState(initial = null)
    val prayer = settings?.prayer

    HubPageFrame(
        title = t("Madhab & calculation"),
        onBack = onBack,
        footer = {
            Text(
                text = t("Computed on this phone · your location never leaves it"),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        },
    ) {
        SectionLabel(t("School"))
        Column(modifier = Modifier.padding(top = 6.dp)) {
            Madhab.entries.forEach { madhab ->
                ChoiceRow(
                    title = madhab.label,
                    subtitle = "${madhab.note} · ${madhab.branch.label}",
                    selected = prayer?.madhab == madhab,
                    onSelect = { scope.launch { store.setMadhab(madhab) } },
                )
                Hairline()
            }
        }

        // The school picked defaults; these are what it picked, and any of them can be moved
        // without abandoning the school.
        SectionLabel(t("Convention"), modifier = Modifier.padding(top = 22.dp))
        Column(modifier = Modifier.padding(top = 4.dp)) {
            DetailRow(
                label = t("Method"),
                value = prayer?.method?.let { methodLabel(it.name) } ?: "—",
            )
            Hairline()
            DetailRow(
                label = t("ʿAsr"),
                value = when (prayer?.asr?.name) {
                    "HANAFI" -> t("Second shadow")
                    null -> "—"
                    else -> t("First shadow")
                },
            )
            Hairline()
            DetailRow(
                label = t("High-latitude rule"),
                value = highLatitudeLabel(prayer?.highLatitude),
            )
            Hairline()
            DetailRow(
                label = t("Manual offsets"),
                value = offsetSummary(prayer?.offsetMinutes.orEmpty()),
            )
            Hairline()
            DetailRow(
                label = t("Show faḍīla windows"),
                value = if (prayer?.showFadila == true) t("On") else t("Off"),
                accent = prayer?.showFadila == true,
                onClick = { scope.launch { store.setShowFadila(prayer?.showFadila != true) } },
            )
            Hairline()
            DetailRow(
                label = t("Combine prayers"),
                value = when {
                    prayer == null -> "—"
                    prayer.combineDhuhrAsr && prayer.combineMaghribIsha -> t("Both pairs")
                    prayer.combineDhuhrAsr -> t("Dhuhr & ʿasr")
                    prayer.combineMaghribIsha -> t("Maghrib & ʿishāʾ")
                    else -> t("Off")
                },
            )
            Hairline()
            DetailRow(
                label = t("Location"),
                value = when (prayer?.locationSource) {
                    null, LocationSource.UNSET -> t("Not set")
                    LocationSource.MANUAL -> t("%.3f, %.3f · typed", prayer.latitude, prayer.longitude)
                    LocationSource.DEVICE -> t("%.3f, %.3f · from the phone", 
                        prayer.latitude,
                        prayer.longitude,
                    )
                },
            )
        }

        Text(
            text = t("Offsets, location and the salah pause are set under Settings → Prayer times. ") +
                t("Changing the school here rewrites the method, the ʿasr rule and the combining to ") +
                t("match it; anything you change afterwards stays as you left it."),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
        )
    }
}

private fun methodLabel(name: String): String = when (name) {
    "MWL" -> t("Muslim World League")
    "ISNA" -> t("Islamic Society of North America")
    "EGYPTIAN" -> t("Egyptian General Authority")
    "UMM_AL_QURA" -> t("Umm al-Qurā")
    "KARACHI" -> t("University of Karachi")
    "JAFARI" -> t("Jaʿfarī · Leva, Qum")
    "TEHRAN" -> t("University of Tehran")
    else -> name
}

private fun highLatitudeLabel(rule: HighLatitudeRule?): String = when (rule) {
    HighLatitudeRule.MIDDLE_OF_NIGHT -> t("Middle of night")
    HighLatitudeRule.SEVENTH_OF_NIGHT -> t("Seventh of night")
    HighLatitudeRule.ANGLE_BASED -> t("Angle-based")
    HighLatitudeRule.NONE -> t("None")
    null -> "—"
}

/** "Fajr +2 m", or "Fajr +2, ʿIshāʾ −1" when more than one has been nudged. */
private fun offsetSummary(offsets: Map<Prayer, Int>): String {
    val set = offsets.filterValues { it != 0 }
    if (set.isEmpty()) return t("None")
    return set.entries.joinToString(", ") { (prayer, minutes) ->
        val sign = if (minutes > 0) "+" else "−"
        "${dev.minimalist.ui.home.prayerName(prayer)} $sign${kotlin.math.abs(minutes)} m"
    }
}
