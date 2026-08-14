package dev.yusr.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.domain.BudgetCalculator
import dev.yusr.ui.t
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.rememberTicker
import dev.yusr.ui.theme.Faint
import dev.yusr.util.DayClock
import dev.yusr.util.Permissions

/**
 * Where the day actually went. Plain numbers, no charts, nothing to admire.
 *
 * The numbers are the phone's own. This screen used to add up the stretches the launcher had
 * recorded itself, which meant it disagreed with the phone's screen-time page in both directions:
 * an app still in front when the screen went off was charged for the whole night, and anything
 * opened while the guard service was dead was not charged at all. Neither is a number worth
 * showing someone. The system has been keeping this all along, to a standard the launcher cannot
 * match from the outside, so it is read rather than reinvented.
 */
@Composable
fun UsageDashboardScreen() {
    val context = LocalContext.current
    val repository = remember { context.container.repository }
    val catalog = remember { context.container.catalog }

    // Refreshed while the screen is up, and only while it is up.
    val now by rememberTicker(periodMillis = 15_000L)
    val dayStart = remember(now / 60_000) { DayClock.dayStart(now) }
    val weekStart = remember(now / 60_000) { DayClock.weekAgo(now) }

    val hasUsageAccess = remember(now / 60_000) { Permissions.hasUsageAccess(context) }

    val day by produceState(initialValue = Day(), now) {
        val snapshot = repository.phoneUsageToday(now)
        value = if (snapshot != null) {
            Day(
                byApp = snapshot.at(now)
                    .mapValues { (_, usage) -> (usage.millis / 60_000L).toInt() }
                    .filterValues { it > 0 },
                opens = snapshot.totalOpens(),
                weekMinutes = repository.phoneMillisSince(weekStart, now)
                    ?.let { (it / 60_000L).toInt() } ?: -1,
            )
        } else {
            // No usage access: all the launcher has is what it managed to record itself.
            val records = repository.sessionRecords(dayStart)
            Day(
                byApp = BudgetCalculator.minutesByPackage(records, dayStart, now).toMap(),
                opens = records.count { it.startMillis >= dayStart },
                weekMinutes = -1,
            )
        }
    }

    val bypassesLeft by produceState(initialValue = -1) {
        value = repository.bypassesRemaining()
    }

    val todayMinutes = day.byApp.values.sum()
    val todayByApp = day.byApp.toList().sortedByDescending { it.second }

    YusrPage(title = t("Usage")) {
        Text(
            text = DayClock.formatMinutes(todayMinutes),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = t("on the phone today, across %s opens", day.opens),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
        )

        if (day.weekMinutes >= 0) {
            Text(
                text = t("%s over the last seven days", DayClock.formatMinutes(day.weekMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (bypassesLeft >= 0) {
            Text(
                text = t("%s emergency bypasses left this week", bypassesLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column(modifier = Modifier.padding(top = 32.dp)) {
            Text(text = t("TODAY, BY APP"), style = MaterialTheme.typography.labelSmall, color = Faint)

            if (todayByApp.isEmpty()) {
                Text(
                    text = t("nothing recorded yet."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            todayByApp.forEach { (packageName, minutes) ->
                YusrRow(
                    label = catalog.labelFor(packageName),
                    detail = DayClock.formatMinutes(minutes),
                )
            }
        }

        Text(
            text = if (hasUsageAccess) {
                t("counted the way the phone counts it: from the moment an app comes to the front ") +
                    t("until it leaves or the screen goes off. time asleep is nobody's.")
            } else {
                t("without usage access the phone's own figures cannot be read, so this is only ") +
                    t("what the launcher managed to record itself.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}

/** One day's numbers, gathered in one pass so the screen reads the system once, not three times. */
private data class Day(
    val byApp: Map<String, Int> = emptyMap(),
    val opens: Int = 0,
    val weekMinutes: Int = -1,
)
