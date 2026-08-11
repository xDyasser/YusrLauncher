package dev.yusr.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.yusr.data.db.AppRuleEntity
import dev.yusr.domain.AppTier
import dev.yusr.ui.t
import dev.yusr.ui.YusrButton
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.TierPicker
import dev.yusr.ui.settings.PlainField
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Faint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The one screen where you get to decide everything at once.
 *
 * While the rules are unlocked every tap here takes effect immediately — no cooldown, no pending
 * queue. That is the whole point: sorting out a phone's worth of apps thirty minutes at a time
 * would be absurd, and the friction is supposed to protect a decision you have already made, not
 * stop you making it.
 *
 * Locking is a one-way door, and the screen says so before you walk through it.
 */
@Composable
fun AppSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }
    val mutator = remember { context.container.ruleMutator }
    val settingsStore = remember { context.container.settingsStore }

    val rules by repository.allRules.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = null)
    val locked = settings?.rulesLocked == true

    var filter by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    val visible = remember(rules, filter) {
        if (filter.isBlank()) {
            rules
        } else {
            val needle = filter.lowercase(Locale.getDefault())
            rules.filter { it.label.lowercase(Locale.getDefault()).contains(needle) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 32.dp, bottom = 20.dp),
    ) {
        Text(text = t("DECIDE ONCE"), style = MaterialTheme.typography.labelSmall, color = Faint)
        Text(
            text = if (locked) {
                t("the rules are locked. changes that loosen them now wait out the cooldown.")
            } else {
                t("nothing here waits while you are still setting up. %s", counts(rules))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 8.dp),
        )

        PlainField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = t("filter"),
        )

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(visible, key = { it.packageName }) { rule ->
                AppSetupRow(rule = rule) { tier ->
                    scope.launch { mutator.setTier(rule.packageName, tier) }
                }
            }
        }

        if (!locked) {
            if (confirming) {
                Text(
                    text = t("after this, loosening any rule waits out the cooldown before it ") +
                        t("takes effect. there is no way back to this screen."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                YusrButton(label = t("yes, lock it in")) {
                    scope.launch {
                        settingsStore.lockRules()
                        onDone()
                    }
                }
                Text(
                    text = t("not yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .noRippleClickable { confirming = false },
                )
            } else {
                YusrButton(label = t("lock these rules in")) { confirming = true }
            }
        }

        Text(
            text = t("done"),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .noRippleClickable { onDone() },
        )
    }
}

@Composable
private fun AppSetupRow(rule: AppRuleEntity, onSelect: (AppTier) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = rule.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TierPicker(
            selected = rule.tier,
            modifier = Modifier.padding(top = 8.dp),
            onSelect = onSelect,
        )
    }
}

private fun counts(rules: List<AppRuleEntity>): String {
    val byTier = rules.groupingBy { it.tier }.eachCount()
    val favorites = byTier[AppTier.FAVORITE] ?: 0
    val allowed = byTier[AppTier.ALLOWED] ?: 0
    val gated = byTier[AppTier.GATED] ?: 0
    val blocked = byTier[AppTier.BLOCKED] ?: 0
    val order = if (favorites > 1) t(" the mark in the home screen's top corner reorders them.") else ""
    return t("%s on the home screen · %s open freely · %s gated · %s blocked.", favorites, allowed, gated, blocked) +
        order
}
