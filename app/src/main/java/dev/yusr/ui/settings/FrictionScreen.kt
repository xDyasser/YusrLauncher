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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.MutationResult
import dev.yusr.ui.t
import dev.yusr.ui.YusrPage
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.util.DayClock
import kotlinx.coroutines.launch

/** How expensive the gate is. Every knob here can be tightened now and only loosened later. */
@Composable
fun FrictionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }
    val mutator = remember { context.container.ruleMutator }
    val settings by repository.settings.collectAsState(initial = null)
    var notice by remember { mutableStateOf<String?>(null) }

    fun report(result: MutationResult) {
        notice = when (result) {
            is MutationResult.AppliedNow -> t("applied.")
            is MutationResult.Deferred ->
                t("queued — it takes effect at %s.", DayClock.clockAt(result.applyAtMillis))
        }
    }

    val current = settings ?: return

    YusrPage(title = t("Friction"), subtitle = t("stricter takes effect at once; looser waits %s minutes", current.cooldownMinutes)) {
        Knob(
            caption = t("base wait"),
            value = t("%ss", current.policy.baseDelaySeconds),
            onDown = { scope.launch { report(mutator.setBaseDelay((current.policy.baseDelaySeconds - 5).coerceAtLeast(0))) } },
            onUp = { scope.launch { report(mutator.setBaseDelay(current.policy.baseDelaySeconds + 5)) } },
        )

        Knob(
            caption = t("added per open today"),
            value = t("%ss", current.policy.escalationSecondsPerOpen),
            onDown = { scope.launch { report(mutator.setEscalation((current.policy.escalationSecondsPerOpen - 5).coerceAtLeast(0))) } },
            onUp = { scope.launch { report(mutator.setEscalation(current.policy.escalationSecondsPerOpen + 5)) } },
        )

        Knob(
            caption = t("reason length"),
            value = t("%s chars", current.policy.minReasonLength),
            onDown = { scope.launch { report(mutator.setMinReasonLength((current.policy.minReasonLength - 5).coerceAtLeast(0))) } },
            onUp = { scope.launch { report(mutator.setMinReasonLength(current.policy.minReasonLength + 5)) } },
        )

        Knob(
            caption = t("session length"),
            value = DayClock.formatMinutes(current.policy.defaultSessionMinutes),
            onDown = { scope.launch { report(mutator.setDefaultSessionMinutes((current.policy.defaultSessionMinutes - 1).coerceAtLeast(1))) } },
            onUp = { scope.launch { report(mutator.setDefaultSessionMinutes(current.policy.defaultSessionMinutes + 1)) } },
        )

        Knob(
            caption = t("cooldown on loosening"),
            value = DayClock.formatMinutes(current.cooldownMinutes),
            onDown = { scope.launch { report(mutator.setCooldownMinutes((current.cooldownMinutes - 10).coerceAtLeast(0))) } },
            onUp = { scope.launch { report(mutator.setCooldownMinutes(current.cooldownMinutes + 10)) } },
        )

        Knob(
            caption = t("emergency bypasses per week"),
            value = current.bypassesPerWeek.toString(),
            onDown = { scope.launch { report(mutator.setBypassesPerWeek((current.bypassesPerWeek - 1).coerceAtLeast(0))) } },
            onUp = { scope.launch { report(mutator.setBypassesPerWeek(current.bypassesPerWeek + 1)) } },
        )

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

@Composable
private fun Knob(
    caption: String,
    value: String,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(text = caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = Faint)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KnobButton("−", Modifier.weight(1f), onDown)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(2f).padding(vertical = 12.dp),
            )
            KnobButton("+", Modifier.weight(1f), onUp)
        }
    }
}

@Composable
private fun KnobButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .border(1.dp, Fainter)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}
