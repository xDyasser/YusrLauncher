package dev.yusr.ui.hub

import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.domain.Tasbih
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranQuoteStyle
import kotlinx.coroutines.launch

/**
 * The counter.
 *
 * The whole screen is the button, because a misbaḥa is used without being looked at and a target
 * you have to aim for defeats that. What you can see while your eyes are elsewhere is the buzz at
 * the end of each set — the equivalent of the knot in the string.
 *
 * The count resets with the day rather than accumulating. Thirty-three after ʿaṣr is a set you
 * finished; four thousand since March is a statistic, and this app has enough of those.
 *
 * One of the cycles is a sequence rather than a number — the tasbīḥ of al-Zahrāʾ, 34 then 33 then
 * 33 — and for it the screen says which phrase you are on and buzzes, more briefly, where the
 * phrase changes, which is what the dividing beads on a misbaḥa are for.
 */
@Composable
fun TasbihScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val devotions = remember { context.container.devotions }

    val total by devotions.observeTodayCount().collectAsState(initial = 0)
    val cycle by store.tasbihCycle.collectAsState(initial = Tasbih.Cycle.THIRTY_THREE)
    val progress = Tasbih.progress(total, cycle)

    // The knot in the string: minSdk here is well past the point where the manager exists, so
    // there is no older path to keep.
    val vibrator = remember { context.getSystemService(VibratorManager::class.java)?.defaultVibrator }

    HubPageFrame(
        title = t("Tasbīḥ"),
        subtitle = progress.label,
        onBack = onBack,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Tasbih.Cycle.entries.forEach { option ->
                        Text(
                            text = t(option.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == cycle) Gold else Dim,
                            modifier = Modifier
                                .noRippleClickable { scope.launch { store.setTasbihCycle(option) } }
                                .padding(vertical = 6.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(
                        text = t("back one"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Faint,
                        modifier = Modifier
                            .noRippleClickable { scope.launch { devotions.removeBead(cycle) } }
                            .padding(vertical = 6.dp),
                    )
                    Text(
                        text = t("reset"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Faint,
                        modifier = Modifier
                            .noRippleClickable { scope.launch { devotions.resetToday() } }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Everything below the title counts. There is nothing else on this screen to hit.
                .noRippleClickable {
                    scope.launch {
                        val next = devotions.addBead(cycle)
                        // The dividing bead on a misbaḥa: a shorter buzz where the phrase
                        // changes, the full one where the whole set closes. Never both at once.
                        val buzz = when {
                            next.justClosedACycle -> CLOSE_BUZZ_MS
                            next.justClosedAPhase -> PHASE_BUZZ_MS
                            else -> null
                        }
                        if (buzz != null) {
                            runCatching {
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(buzz, VibrationEffect.DEFAULT_AMPLITUDE),
                                )
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // In a sequence the number counts the phrase, not the set: "12 of 34" under
                // Allāhu akbar, then "1 of 33" under al-ḥamdu lillāh. The set's own hundred is
                // what the "sets" line below adds up.
                Text(
                    text = (if (cycle.isSequence) progress.inPhase else progress.inCycle).toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = t("of %s", progress.phase.length),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
                progress.phase.arabic?.let { phrase ->
                    // The phrase is the one thing on this screen worth looking at, so it is set
                    // in the Qur'anic face and large enough to read from the lap.
                    Text(
                        text = phrase,
                        style = QuranQuoteStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    progress.phase.transliteration?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Dim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Hairline(modifier = Modifier.padding(vertical = 14.dp))
                SectionLabel(
                    text = if (progress.completed > 0) {
                        t("%s sets · %s today", progress.completed, total)
                    } else {
                        t("%s today", total)
                    },
                )
                Text(
                    text = t("tap anywhere"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 22.dp),
                )
            }
        }
    }
}

private const val CLOSE_BUZZ_MS = 28L
private const val PHASE_BUZZ_MS = 14L
