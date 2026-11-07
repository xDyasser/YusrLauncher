package dev.yusr.ui.hub

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.prayer.PrayerToday
import dev.yusr.domain.FastingCalendar
import dev.yusr.domain.Madhab
import dev.yusr.domain.Prayer
import dev.yusr.domain.Ramadan
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Gold
import dev.yusr.util.DayClock
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Which days are fasts, and which of them you kept.
 *
 * Nothing here needs setting up. The recurring sunna fasts are calendar facts — the white days,
 * the weekly or monthly days of whichever school is set, ʿArafah, the named days of the year — so
 * the app already knows what today is from the Hijri date and the madhab, and the only thing it
 * cannot work out on its own is whether you actually fasted.
 *
 * The days fasting is forbidden on are shown too, and shown as forbidden rather than quietly
 * omitted: a calendar that went blank on Eid would look like a calendar that had failed.
 */
@Composable
fun FastingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val devotions = remember { context.container.devotions }
    val prayerRepository = remember { context.container.prayerRepository }

    val settings by store.settings.collectAsState(initial = null)
    val offset = settings?.prayer?.hijriOffsetDays ?: 0
    // The two branches keep different days, and the Jaʿfarī list is the one this screen used to
    // get wrong; until the madhab is read off disk the Sunnī one stands in for a frame or two.
    val branch = settings?.prayer?.effectiveMadhab?.branch ?: Madhab.Branch.SUNNI
    val monthCount by devotions.observeFastedThisMonth().collectAsState(initial = 0)

    val today = remember { LocalDate.now() }
    val days = remember(offset, branch) {
        FastingCalendar.upcoming(today, days = 21, hijriOffsetDays = offset, branch = branch)
    }

    // Bumped after every mark so the ticks reload; the marked set is a suspending read rather
    // than a flow, because it is a whole page of dates at once rather than one value.
    var revision by remember { mutableIntStateOf(0) }
    val marked by produceState(initialValue = emptySet<LocalDate>(), revision) {
        value = devotions.fastedDatesSince(today.minusDays(30))
    }

    val prayerToday by produceState<PrayerToday?>(null, settings?.prayer) {
        value = prayerRepository.today()
    }

    val classification = remember(offset, branch) { FastingCalendar.classify(today, offset, branch) }

    HubPageFrame(
        title = t("Fasting"),
        subtitle = when {
            classification.isFast -> fastingLabel(classification)
            // A day with something to say about itself that is not a fast — ʿĀshūrāʾ for a Shia,
            // an Eid for anyone — says it, rather than "nothing due" on a day that is anything but.
            classification.kind != FastingCalendar.Kind.NONE -> fastingLabel(classification)
            else -> t("nothing due today")
        },
        onBack = onBack,
        footer = {
            Text(
                text = t("%s kept this month", monthCount),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        },
    ) {
        // Suhūr and ifṭār come straight off the timetable: the fast begins at fajr and ends at
        // maghrib, which the app already computes to the minute.
        prayerToday?.let { snapshot ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Imsāk first: the margin before fajr every printed Ramaḍān calendar keeps.
                listOf(
                    t("Imsāk") to Ramadan.imsak(snapshot.timetable),
                    t("Suhūr ends") to snapshot.timetable.minuteOfDay(Prayer.FAJR),
                    t("Ifṭār") to snapshot.timetable.minuteOfDay(Prayer.MAGHRIB),
                ).forEach { (label, minute) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel(label)
                        Text(
                            text = DayClock.clock(minute),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Hairline()
        }

        SectionLabel(t("The next three weeks"), modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))

        days.forEach { day ->
            val isToday = day.date == today
            val fasted = day.date in marked
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    // Only a day that is actually a fast can be ticked, and only up to today —
                    // marking next Thursday as kept is a promise, not a record.
                    .noRippleClickable(enabled = day.isFast && !day.date.isAfter(today)) {
                        scope.launch {
                            devotions.setFasted(day.date, !fasted, offset, branch)
                            revision++
                        }
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1f).height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(if (isToday) Gold else Color.Transparent),
                    )
                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = day.date.format(DAY_FORMAT),
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                isToday -> MaterialTheme.colorScheme.primary
                                day.isFast -> MaterialTheme.colorScheme.onBackground
                                else -> Dim
                            },
                        )
                        val label = fastingLabel(day)
                        if (label.isNotBlank()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (day.kind.forbidden) Faint else Dim,
                            )
                        }
                    }
                }
                Text(
                    text = when {
                        fasted -> t("kept")
                        day.kind.forbidden -> "—"
                        day.isFast && !day.date.isAfter(today) -> t("mark")
                        day.isFast -> t("due")
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fasted) Gold else Dim,
                )
            }
            Hairline()
        }
    }
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
