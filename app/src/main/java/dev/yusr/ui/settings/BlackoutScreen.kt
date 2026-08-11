package dev.yusr.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.MutationResult
import dev.yusr.data.db.BlackoutWindowEntity
import dev.yusr.domain.BlackoutSchedule
import dev.yusr.ui.t
import dev.yusr.ui.YusrButton
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.theme.Faint
import dev.yusr.util.DayClock
import kotlinx.coroutines.launch

private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
private val EVERY_DAY = setOf(1, 2, 3, 4, 5, 6, 7)

/** Hours when the phone is simply not available for anything but favourites. */
@Composable
fun BlackoutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }
    val mutator = remember { context.container.ruleMutator }
    val windows by repository.blackoutWindows.collectAsState(initial = emptyList())
    var notice by remember { mutableStateOf<String?>(null) }

    fun report(result: MutationResult) {
        notice = when (result) {
            is MutationResult.AppliedNow -> t("in force now.")
            is MutationResult.Deferred ->
                "queued — takes effect at ${DayClock.localDateTime(result.applyAtMillis).toLocalTime().withSecond(0).withNano(0)}."
        }
    }

    YusrPage(title = t("Blackout windows"), subtitle = t("adding one is instant; removing one waits out the cooldown")) {
        if (windows.isEmpty()) {
            Text(
                text = t("no windows yet."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
            )
        }

        windows.forEach { window ->
            val days = BlackoutSchedule.maskToDays(window.daysMask)
            YusrRow(
                // The three ready-made windows are stored under their English names, so a
                // phone that changes language shows them in the new one rather than keeping the
                // old. A window named by hand is shown as it was typed.
                label = t(window.label) + if (!window.enabled) t(" · off") else "",
                detail = "${BlackoutSchedule.formatMinuteOfDay(window.startMinuteOfDay)}–" +
                    "${BlackoutSchedule.formatMinuteOfDay(window.endMinuteOfDay)} · ${describeDays(days)}",
            ) {
                scope.launch { report(mutator.deleteBlackout(window.id, window.label)) }
            }
        }

        Text(
            text = t("tap a window to remove it"),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 8.dp),
        )

        Column(modifier = Modifier.padding(top = 28.dp)) {
            Text(text = t("ADD"), style = MaterialTheme.typography.labelSmall, color = Faint)

            YusrButton(label = t("sleep") + " · 22:00–07:00 " + t("every day"), modifier = Modifier.padding(top = 12.dp)) {
                scope.launch { report(mutator.upsertBlackout(window(t("sleep"), 22 * 60, 7 * 60, EVERY_DAY))) }
            }
            YusrButton(label = t("work") + " · 09:00–17:00 " + t("weekdays"), modifier = Modifier.padding(top = 12.dp)) {
                scope.launch { report(mutator.upsertBlackout(window(t("work"), 9 * 60, 17 * 60, WEEKDAYS))) }
            }
            YusrButton(label = t("evening") + " · 19:00–22:00 " + t("every day"), modifier = Modifier.padding(top = 12.dp)) {
                scope.launch { report(mutator.upsertBlackout(window(t("evening"), 19 * 60, 22 * 60, EVERY_DAY))) }
            }
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

private fun window(label: String, start: Int, end: Int, days: Set<Int>) = BlackoutWindowEntity(
    label = label,
    startMinuteOfDay = start,
    endMinuteOfDay = end,
    daysMask = BlackoutSchedule.daysToMask(days),
)

private fun describeDays(days: Set<Int>): String = when (days) {
    EVERY_DAY -> t("every day")
    WEEKDAYS -> t("weekdays")
    else -> days.sorted().joinToString(" ") { dayName(it) }
}

/** Read on every call rather than held in a `val`: a top-level one would be built once, in
 *  whatever language the app started in, and keep saying it after the language changed. */
private fun dayName(isoDay: Int): String =
    t(listOf(t("mon"), t("tue"), t("wed"), t("thu"), t("fri"), t("sat"), t("sun"))[isoDay - 1])
