package dev.yusr.ui.settings

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
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.ui.t
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.theme.Faint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything you have asked to loosen, and how long it still has to wait. Cancelling is free and
 * immediate — the cooldown exists to delay weakening, not to force it through.
 */
@Composable
fun PendingChangesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }
    val pending by repository.pendingChanges.collectAsState(initial = emptyList())

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    YusrPage(title = t("Pending changes"), subtitle = t("tap one to cancel it")) {
        if (pending.isEmpty()) {
            Text(
                text = t("nothing waiting."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
            )
        }

        pending.forEach { change ->
            val secondsLeft = ((change.applyAtMillis - now) / 1000).coerceAtLeast(0)
            YusrRow(
                label = change.description,
                detail = if (secondsLeft <= 0) {
                    t("due — applies at the next check")
                } else {
                    "in ${format(secondsLeft)}"
                },
            ) {
                scope.launch { repository.cancelPendingChange(change.id) }
            }
        }

        Text(
            text = t("changes are applied by a background check that runs about every fifteen minutes."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 28.dp),
        )
    }
}

private fun format(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
