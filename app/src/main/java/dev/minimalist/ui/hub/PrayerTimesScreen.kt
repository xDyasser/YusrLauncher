package dev.minimalist.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.prayer.PrayerToday
import dev.minimalist.domain.Hijri
import dev.minimalist.domain.Prayer
import dev.minimalist.ui.t
import dev.minimalist.ui.Hairline
import dev.minimalist.ui.home.clock
import dev.minimalist.ui.home.prayerName
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Gold
import dev.minimalist.util.DayClock
import java.time.LocalDate

/**
 * The five prayers of today, read rather than glanced at.
 *
 * The strip on the home screen answers "where am I in the day?" in one line. This answers the
 * questions that need more room: when each one was or will be, and how much of each preferred
 * time is left. Nothing here is editable — the settings for all of it live one screen away, under
 * the madhab, because changing a calculation method is a decision and reading a timetable is not.
 */
@Composable
fun PrayerTimesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    val repository = remember { context.container.prayerRepository }

    val settings by store.settings.collectAsState(initial = null)
    val prayer = settings?.prayer
    val today by produceState<PrayerToday?>(null, prayer) { value = repository.today() }

    val hijri = remember(prayer?.hijriOffsetDays) {
        Hijri.of(LocalDate.now(), prayer?.hijriOffsetDays ?: 0)?.display
    }

    HubPageFrame(
        title = t("Prayer"),
        subtitle = hijri,
        onBack = onBack,
        footer = {
            Text(
                text = t("Computed on this phone · your location never leaves it"),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        },
    ) {
        val snapshot = today
        if (snapshot == null) {
            Text(
                text = t("No location set, so there is no timetable to show."),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            return@HubPageFrame
        }

        snapshot.entries.forEach { entry ->
            val minute = entry.minuteOfDay
            val isNext = entry.covers(snapshot.next.prayer) && !snapshot.next.tomorrow
            val away = minute - snapshot.minuteOfDay

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = if (isNext) 13.dp else 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(if (isNext) Gold else Color.Transparent),
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = prayerName(entry),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isNext) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Faint
                            },
                        )
                        // On a joined line, when the second prayer's own time comes in. Joining is
                        // a permission to pray the later one early, not a claim that its time has
                        // already arrived, and the line would be a small untruth without this.
                        entry.secondMinuteOfDay?.let { second ->
                            Text(
                                text = t("%s from %s", prayerName(entry.with ?: entry.prayer), clock(second)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dim,
                            )
                        }
                        // The preferred window, where there is one and it has not run out. This
                        // is the only line on the screen that is an argument rather than a fact.
                        snapshot.endOfFadila(entry.prayer)?.let { end ->
                            Text(
                                text = t("faḍīla to %s", clock(end)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dim,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = relative(away),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isNext) Gold else Dim,
                    )
                    Text(
                        text = clock(minute),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isNext) MaterialTheme.colorScheme.primary else Faint,
                    )
                }
            }
            Hairline()
        }

        // The three moments that are not prayers but bound them. Sunrise closes fajr; sharʿī
        // midnight closes ʿishāʾ, which is the boundary someone praying ʿishāʾ joined to maghrib
        // has actually been given room by; and the last third is when the night prayer is prayed.
        // Set quieter than the timetable above, because none of them is a prayer time.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Boundary(t("Sunrise · fajr ends"), snapshot.timetable.minuteOfDay(Prayer.SUNRISE))
            Boundary(t("Midnight · ʿishāʾ ends"), snapshot.night.midnightMinuteOfDay)
            Boundary(t("Last third · qiyām al-layl"), snapshot.night.lastThirdMinuteOfDay)
        }
    }
}

/** A named edge of the day, printed as quietly as it deserves. */
@Composable
private fun Boundary(label: String, minuteOfDay: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Dim)
        Text(text = clock(minuteOfDay), style = MaterialTheme.typography.bodySmall, color = Dim)
    }
}

/** "in 2 h 28 m", "1 h 18 m ago", t("now"). */
private fun relative(minutes: Int): String = when {
    minutes > 0 -> t("in %s", DayClock.formatMinutes(minutes))
    minutes < 0 -> t("%s ago", DayClock.formatMinutes(-minutes))
    else -> t("now")
}
