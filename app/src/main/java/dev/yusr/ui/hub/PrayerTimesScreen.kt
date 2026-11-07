package dev.yusr.ui.hub

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.prayer.PrayerToday
import dev.yusr.domain.Hijri
import dev.yusr.domain.Madhab
import dev.yusr.domain.Prayer
import dev.yusr.domain.PrayerLog
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.home.prayerName
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Gold
import dev.yusr.util.DayClock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The five prayers of today, read rather than glanced at.
 *
 * The strip on the home screen answers "where am I in the day?" in one line. This answers the
 * questions that need more room: when each one was or will be, and how much of each preferred
 * time is left. The times are not editable — the settings for all of them live one screen away,
 * under the madhab, because changing a calculation method is a decision and reading a timetable
 * is not. What *is* kept here is the record: a tap on a line marks it prayed, and a line whose
 * time ran out unmarked says that it is owed.
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

    // The day's record beside the day's times: which of the five have been marked, and which
    // have run out. The row is the button, as it is on the home screen.
    val prayerLog = remember { context.container.prayerLog }
    val scope = rememberCoroutineScope()
    val logDate = today?.date
    val prayed by remember(logDate) {
        logDate?.let { prayerLog.observePrayed(it) } ?: flowOf(emptySet<Prayer>())
    }.collectAsState(initial = emptySet<Prayer>())
    val states: Map<Prayer, PrayerLog.State> = remember(today, prayed, prayer) {
        val snapshot = today ?: return@remember emptyMap<Prayer, PrayerLog.State>()
        prayerLog.states(snapshot, prayed, prayer?.effectiveMadhab?.branch ?: Madhab.Branch.SUNNI)
    }
    val owedThisWeek by remember(logDate) { prayerLog.observeMissedSince(7) }.collectAsState(initial = 0)

    HubPageFrame(
        title = t("Prayer"),
        subtitle = when {
            owedThisWeek > 0 && hijri != null -> t("%s · %s owed this week", hijri, owedThisWeek)
            else -> hijri
        },
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
            val onLine = PrayerLog.OWED.filter { entry.covers(it) }
            val linePrayed = onLine.isNotEmpty() && onLine.all { states[it] == PrayerLog.State.PRAYED }
            val lineOwed = onLine.any { states[it] == PrayerLog.State.MISSED }
            val markable = onLine.any { states[it]?.markable == true }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .noRippleClickable(enabled = markable) {
                        val (prayers, mark) = PrayerLog.toggle(entry, states)
                        scope.launch { prayers.forEach { prayerLog.setPrayed(snapshot.date, it, mark) } }
                    }
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
                                text = t("%s from %s", prayerName(entry.with ?: entry.prayer), DayClock.clock(second)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dim,
                            )
                        }
                        // The preferred window, where there is one and it has not run out. This
                        // is the only line on the screen that is an argument rather than a fact.
                        snapshot.endOfFadila(entry.prayer)?.let { end ->
                            Text(
                                text = t("faḍīla to %s", DayClock.clock(end)),
                                style = MaterialTheme.typography.bodySmall,
                                color = Dim,
                            )
                        }
                        // The record, where there is one to keep: prayed, owed, or — while the
                        // time is in — the invitation to say so. A prayer not yet in says nothing.
                        when {
                            linePrayed -> Text(
                                text = t("prayed ✓"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Gold,
                            )
                            lineOwed -> Text(
                                text = t("owed · tap to mark as prayed"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Faint,
                            )
                            markable -> Text(
                                text = t("tap to mark as prayed"),
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
                        text = DayClock.clock(minute),
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
        Text(text = DayClock.clock(minuteOfDay), style = MaterialTheme.typography.bodySmall, color = Dim)
    }
}

/** "in 2 h 28 m", "1 h 18 m ago", t("now"). */
private fun relative(minutes: Int): String = when {
    minutes > 0 -> t("in %s", DayClock.formatMinutes(minutes))
    minutes < 0 -> t("%s ago", DayClock.formatMinutes(-minutes))
    else -> t("now")
}
