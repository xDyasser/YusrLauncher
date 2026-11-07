package dev.yusr.ui.hub

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.YusrRepository
import dev.yusr.domain.PrayerLog
import dev.yusr.domain.WeeklyReview
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.t
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.util.DayClock

/**
 * The muḥāsaba: the last seven days, read back.
 *
 * Two halves. What the week gave to worship — prayers marked against the thirty-five owed, fasts
 * kept, dhikr counted — and what it gave to the phone: how long it was in use, how often the gate
 * was passed, how many emergencies were declared, and the reasons typed at the gate, most
 * frequent first. Nothing here is scored or ranked against anyone; it is the week as it was, in the
 * user's own words where there are any.
 */
@Composable
fun WeeklyReviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { context.container.repository }
    val prayerLog = remember { context.container.prayerLog }

    val week by produceState<YusrRepository.Week?>(null) { value = repository.week() }
    val owed by remember { prayerLog.observeMissedSince(WeeklyReview.DAYS) }.collectAsState(initial = 0)

    HubPageFrame(
        title = t("Your week"),
        subtitle = t("the last %s days", WeeklyReview.DAYS),
        onBack = onBack,
    ) {
        val current = week ?: return@HubPageFrame

        SectionLabel(t("Worship"), modifier = Modifier.padding(bottom = 2.dp))
        DetailRow(
            t("Prayers marked"),
            t("%s of %s", current.prayersMarked, WeeklyReview.DAYS * PrayerLog.OWED.size),
            accent = owed == 0 && current.prayersMarked > 0,
        )
        Hairline()
        DetailRow(t("Owed as qaḍāʾ"), owed.toString())
        Hairline()
        DetailRow(t("Fasts kept"), current.fastsKept.toString())
        Hairline()
        DetailRow(t("Dhikr counted"), current.beads.toString())
        Hairline()

        SectionLabel(t("The phone"), modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        current.screenMinutes?.let { minutes ->
            DetailRow(
                t("Screen time"),
                t("%s · %s a day", DayClock.formatMinutes(minutes), DayClock.formatMinutes(WeeklyReview.perDay(minutes))),
            )
            Hairline()
        }
        DetailRow(t("Times through the gate"), current.gatePasses.toString())
        Hairline()
        DetailRow(t("Emergency bypasses"), current.bypasses.toString())
        Hairline()

        SectionLabel(t("What you said you wanted it for"), modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        if (current.reasons.isEmpty()) {
            Text(
                text = t("nothing typed at the gate this week."),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        current.reasons.forEach { reason ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Quoted, because these are the user's words and not the app's.
                    text = "“${reason.text}”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = t("× %s", reason.times),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Hairline()
        }
    }
}
