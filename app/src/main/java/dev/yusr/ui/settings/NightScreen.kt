package dev.yusr.ui.settings

import androidx.compose.foundation.border
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.domain.Khums
import dev.yusr.domain.NightWake
import dev.yusr.service.NightWakeAlarm
import dev.yusr.ui.YusrPage
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.t
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.util.DayClock
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The two things that happen without the phone being picked up: the alarm for the night prayer,
 * and the reminder that the khums year is ending. Neither loosens a rule, so both apply at once.
 */
@Composable
fun NightScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val settings by store.settings.collectAsState(initial = null)
    val current = settings ?: return

    // When the alarm will next ring, read back from the same arithmetic that sets it.
    val next by produceState<String?>(null, current) {
        value = if (current.nightWakeEnabled && current.prayer.configured) {
            val repository = context.container.prayerRepository
            val now = DayClock.localDateTime(System.currentTimeMillis())
            val days = (-1L..1L).associate { offset ->
                val date = now.toLocalDate().plusDays(offset)
                date to repository.timetable(current.prayer, date)
            }
            NightWake.next(now, { days[it] ?: days.getValue(now.toLocalDate()) }, current.nightWakeAnchor, current.nightWakeMinutesBefore)
                .format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault()))
        } else {
            null
        }
    }

    // Every change re-sets the alarm, so what the screen says is what will ring.
    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            NightWakeAlarm.schedule(context)
        }
    }

    YusrPage(title = t("The night and khums"), subtitle = t("an alarm and a reminder; neither loosens a rule")) {
        Caption(t("ṣalāt al-layl"))
        Toggle(
            on = current.nightWakeEnabled,
            onLabel = t("alarm on · turn off"),
            offLabel = t("alarm off · turn on"),
        ) { change { store.setNightWakeEnabled(!current.nightWakeEnabled) } }

        if (!current.prayer.configured) {
            Note(t("set a location under prayer times first: the alarm is worked out from the night."))
        }

        Caption(t("set from"))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NightWake.Anchor.entries.forEach { anchor ->
                Text(
                    text = when (anchor) {
                        NightWake.Anchor.FAJR -> t("before fajr")
                        NightWake.Anchor.LAST_THIRD -> t("the last third")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (anchor == current.nightWakeAnchor) Gold else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (anchor == current.nightWakeAnchor) Gold else Fainter)
                        .noRippleClickable { change { store.setNightWakeAnchor(anchor) } }
                        .padding(vertical = 12.dp),
                )
            }
        }

        Stepper(
            caption = t("how long before it"),
            value = DayClock.formatMinutes(current.nightWakeMinutesBefore),
            onDown = { change { store.setNightWakeMinutesBefore(current.nightWakeMinutesBefore - 15) } },
            onUp = { change { store.setNightWakeMinutesBefore(current.nightWakeMinutesBefore + 15) } },
        )

        next?.let { Note(t("next: %s", it)) }

        Caption(t("khums"))
        Toggle(
            on = current.khumsEnabled,
            onLabel = t("reminder on · turn off"),
            offLabel = t("reminder off · turn on"),
        ) { scope.launch { store.setKhumsEnabled(!current.khumsEnabled) } }

        val shown = LocalDate.of(2024, current.khumsMonth, current.khumsDay.coerceAtMost(java.time.YearMonth.of(2024, current.khumsMonth).lengthOfMonth()))
        Stepper(
            caption = t("your khums year ends on"),
            value = shown.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())),
            onDown = {
                val earlier = shown.minusDays(1)
                scope.launch { store.setKhumsDate(earlier.monthValue, earlier.dayOfMonth) }
            },
            onUp = {
                val later = shown.plusDays(1)
                scope.launch { store.setKhumsDate(later.monthValue, later.dayOfMonth) }
            },
        )
        Note(
            t(
                "a reminder %s days before, and on the day. the app keeps the date; the reckoning is yours.",
                Khums.DAYS_AHEAD,
            ),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Faint,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Faint,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
}

@Composable
private fun Toggle(on: Boolean, onLabel: String, offLabel: String, onClick: () -> Unit) {
    Text(
        text = if (on) onLabel else offLabel,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = if (on) Gold else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
            .border(1.dp, if (on) Gold else Fainter)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun Stepper(caption: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Caption(caption)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("−" to onDown, null, "+" to onUp).forEach { item ->
                if (item == null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(2f).padding(vertical = 12.dp),
                    )
                } else {
                    Text(
                        text = item.first,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Fainter)
                            .noRippleClickable(onClick = item.second)
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
