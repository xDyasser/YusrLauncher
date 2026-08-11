package dev.minimalist.ui.hub

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
import dev.minimalist.container
import dev.minimalist.domain.Tasbih
import dev.minimalist.ui.t
import dev.minimalist.ui.Hairline
import dev.minimalist.ui.SectionLabel
import dev.minimalist.ui.noRippleClickable
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Gold
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
                            text = option.label,
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
                        if (next.justClosedACycle) {
                            runCatching {
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(
                                        CLOSE_BUZZ_MS,
                                        VibrationEffect.DEFAULT_AMPLITUDE,
                                    ),
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
                Text(
                    text = progress.inCycle.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = t("of %s", cycle.length),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
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
