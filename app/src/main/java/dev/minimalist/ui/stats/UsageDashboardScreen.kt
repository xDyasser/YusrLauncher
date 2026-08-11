package dev.minimalist.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.domain.BudgetCalculator
import dev.minimalist.domain.SessionRecord
import dev.minimalist.ui.t
import dev.minimalist.ui.MinimalPage
import dev.minimalist.ui.MinimalRow
import dev.minimalist.ui.theme.Faint
import dev.minimalist.util.DayClock

/** Where the day actually went. Plain numbers, no charts, nothing to admire. */
@Composable
fun UsageDashboardScreen() {
    val context = LocalContext.current
    val repository = remember { context.container.repository }
    val catalog = remember { context.container.catalog }

    val now = remember { System.currentTimeMillis() }
    val dayStart = remember(now) { DayClock.dayStart(now) }
    val weekStart = remember(now) { DayClock.weekAgo(now) }

    val sessions by repository.observeSessionsSince(weekStart)
        .collectAsState(initial = emptyList())

    val bypassesLeft by produceState(initialValue = -1) {
        value = repository.bypassesRemaining()
    }

    val records = remember(sessions) {
        sessions.map { SessionRecord(it.packageName, it.startMillis, it.endMillis, it.wasBypass) }
    }
    val liveNow = System.currentTimeMillis()

    val todayMinutes = remember(records) { BudgetCalculator.totalMinutes(records, dayStart, liveNow) }
    val weekMinutes = remember(records) { BudgetCalculator.totalMinutes(records, weekStart, liveNow) }
    val todayByApp = remember(records) { BudgetCalculator.minutesByPackage(records, dayStart, liveNow) }
    val opensToday = remember(records) { records.count { it.startMillis >= dayStart } }

    MinimalPage(title = t("Usage")) {
        Text(
            text = DayClock.formatMinutes(todayMinutes),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = t("on the phone today, across %s opens", opensToday),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
        )

        Text(
            text = t("%s over the last seven days", DayClock.formatMinutes(weekMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 12.dp),
        )

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
                MinimalRow(
                    label = catalog.labelFor(packageName),
                    detail = DayClock.formatMinutes(minutes),
                )
            }
        }

        Text(
            text = t("only time recorded while the enforcement service was running is counted."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
