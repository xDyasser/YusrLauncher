package dev.yusr.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.yusr.domain.Hijri
import dev.yusr.domain.Occasions
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.isArabic
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.t
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Gold
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Today's occasion and the next few, under the readings on the hub.
 *
 * The tone sets the colour and nothing else: a birth or an Eid takes the gold, a martyrdom never
 * does, and a night of worship sits between them. A line with a text in Mafātīḥ behind it opens
 * onto it; a line without one is still worth knowing about, and is just a line.
 */
@Composable
internal fun OccasionsBlock(
    occasions: List<Occasions.Dated>,
    today: LocalDate,
    hijriOffsetDays: Int,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (occasions.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(t("Occasions"), modifier = Modifier.padding(bottom = 4.dp))
        occasions.forEach { dated ->
            val entryId = dated.occasion.entryId
            val isToday = dated.date == today
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable(enabled = entryId != null) { entryId?.let(onOpen) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = t(dated.occasion.label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = occasionColour(dated.occasion.tone, isToday)
                            .takeOrElse(MaterialTheme.colorScheme.onBackground),
                    )
                    Text(
                        text = whenLine(dated.date, today, hijriOffsetDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
                if (entryId != null) {
                    Text(
                        text = t("open →"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Faint,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Hairline()
        }
    }
}

/**
 * The colour a line about [tone] is set in: gold for a joyful day that is today, the reading
 * colour for anything else that is today, and nothing particular for the days ahead.
 * [Color.Unspecified] means "the screen's own reading colour".
 */
internal fun occasionColour(tone: Occasions.Tone, isToday: Boolean): Color = when {
    isToday && tone == Occasions.Tone.JOY -> Gold
    isToday -> Color.Unspecified
    else -> Faint
}

private fun Color.takeOrElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

/** "today · 13 Rajab", "tomorrow · 14 Rajab", "in 5 days · 18 Rajab". */
private fun whenLine(date: LocalDate, today: LocalDate, hijriOffsetDays: Int): String {
    val hijri = Hijri.of(date, hijriOffsetDays)?.let {
        if (isArabic()) "${Hijri.arabicDigits(it.day)} ${it.monthNameArabic}" else "${it.day} ${it.monthNameTransliterated}"
    }.orEmpty()
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    val relative = when (days) {
        0 -> t("today")
        1 -> t("tomorrow")
        else -> t("in %s days", days)
    }
    return if (hijri.isBlank()) relative else "$relative · $hijri"
}

/** How many lines the hub shows, and how far ahead it looks for them. */
internal const val OCCASIONS_SHOWN = 4
internal const val OCCASIONS_HORIZON_DAYS = 45
