package dev.yusr.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.MutationResult
import dev.yusr.data.db.AppRuleEntity
import dev.yusr.ui.t
import dev.yusr.ui.tierName
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.PillPicker
import dev.yusr.ui.TierPicker
import dev.yusr.ui.noRippleClickable
import androidx.compose.ui.draw.clip
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.YusrShape
import dev.yusr.util.DayClock
import kotlinx.coroutines.launch
import java.util.Locale

/** The rule book: what each app costs, and what it is capped at. */
@Composable
fun AppRulesScreen() {
    val context = LocalContext.current
    val repository = remember { context.container.repository }
    val rules by repository.allRules.collectAsState(initial = emptyList())

    // A broadcast can be missed — the launcher may have been force-stopped when the install
    // happened — so the page that lists the apps checks for itself every time it is opened.
    LaunchedEffect(Unit) { repository.syncCatalog() }

    var selected by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

    val chosen = rules.firstOrNull { it.packageName == selected }
    if (chosen != null) {
        AppRuleDetail(rule = chosen, onDone = { selected = null })
        return
    }

    val visible = remember(rules, filter) {
        if (filter.isBlank()) {
            rules
        } else {
            val needle = filter.lowercase(Locale.getDefault())
            rules.filter { it.label.lowercase(Locale.getDefault()).contains(needle) }
        }
    }

    YusrPage(title = t("Apps and limits"), subtitle = t("%s apps · anything you installed yourself starts gated", rules.size)) {
        PlainField(value = filter, onValueChange = { filter = it }, placeholder = t("filter"))

        Column(modifier = Modifier.padding(top = 16.dp)) {
            visible.forEach { rule ->
                YusrRow(
                    label = rule.label,
                    detail = describe(rule),
                ) { selected = rule.packageName }
            }
        }
    }
}

private fun describe(rule: AppRuleEntity): String {
    val tier = tierName(rule.tier)
    val caps = buildList {
        rule.dailyMinutes?.let { add(DayClock.formatMinutes(it) + t("/day")) }
        rule.dailyOpens?.let { add(t("%s opens/day", it)) }
    }
    return if (caps.isEmpty()) tier else "$tier · ${caps.joinToString(" · ")}"
}

@Composable
private fun AppRuleDetail(rule: AppRuleEntity, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mutator = remember { context.container.ruleMutator }
    var notice by remember { mutableStateOf<String?>(null) }

    fun report(result: MutationResult) {
        notice = when (result) {
            is MutationResult.AppliedNow -> t("done.")
            is MutationResult.Deferred ->
                t("queued — it takes effect at %s.", DayClock.clockAt(result.applyAtMillis))
        }
    }

    YusrPage(title = rule.label, subtitle = rule.packageName) {
        Text(text = t("TIER"), style = MaterialTheme.typography.labelSmall, color = Faint)
        TierPicker(
            selected = rule.tier,
            modifier = Modifier.padding(top = 12.dp),
        ) { tier ->
            scope.launch { report(mutator.setTier(rule.packageName, tier)) }
        }

        Stepper(
            caption = t("daily minutes"),
            value = rule.dailyMinutes,
            step = 5,
            onChange = { scope.launch { report(mutator.setDailyMinutes(rule.packageName, it)) } },
            modifier = Modifier.padding(top = 28.dp),
        )

        Stepper(
            caption = t("daily opens"),
            value = rule.dailyOpens,
            step = 1,
            onChange = { scope.launch { report(mutator.setDailyOpens(rule.packageName, it)) } },
            modifier = Modifier.padding(top = 20.dp),
        )

        Text(
            text = t("DURING SALAH"),
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        Text(
            text = if (rule.prayerExempt) {
                t("%s still opens while a prayer window is in force.", rule.label.lowercase(Locale.getDefault()))
            } else {
                t("closed during a prayer window, like everything else.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        PillPicker(
            options = listOf(t("closed"), t("opens")),
            selectedIndex = if (rule.prayerExempt) 1 else 0,
        ) { index ->
            scope.launch { report(mutator.setPrayerExempt(rule.packageName, index == 1)) }
        }

        Text(
            text = t("WHEN ANOTHER APP OPENS IT"),
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        Text(
            text = if (rule.openableByHandoff) {
                t("a link, a sign-in page or a web app opens straight into %s. ", rule.label.lowercase(Locale.getDefault())) +
                    t("the day's allowance is not spent on it. ") +
                    t("going to it yourself still ") +
                    t("costs the gate.")
            } else {
                t("the gate stands whichever way you arrive. on a browser that means every ") +
                    t("web-backed app on the phone waits behind it too.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        PillPicker(
            options = listOf(t("gated"), t("opens")),
            selectedIndex = if (rule.openableByHandoff) 1 else 0,
        ) { index ->
            scope.launch { report(mutator.setOpenableByHandoff(rule.packageName, index == 1)) }
        }

        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        Text(
            text = t("back"),
            style = MaterialTheme.typography.bodyLarge,
            color = Faint,
            modifier = Modifier
                .padding(top = 36.dp)
                .noRippleClickable { onDone() },
        )
    }
}

/** Tightening a cap is instant; loosening one is queued. The stepper does not say which. */
@Composable
private fun Stepper(
    caption: String,
    value: Int?,
    step: Int,
    onChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = Faint)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton("−", Modifier.weight(1f)) {
                val next = ((value ?: step) - step).coerceAtLeast(0)
                onChange(if (next <= 0) 0 else next)
            }
            Text(
                text = value?.toString() ?: t("none"),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(2f).padding(vertical = 12.dp),
            )
            StepperButton("+", Modifier.weight(1f)) { onChange((value ?: 0) + step) }
            StepperButton(t("none"), Modifier.weight(1.4f)) { onChange(null) }
        }
    }
}

@Composable
private fun StepperButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .clip(YusrShape)
            .border(1.dp, Fainter, YusrShape)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

@Composable
internal fun PlainField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
            .copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clip(YusrShape)
            .border(1.dp, Fainter, YusrShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(text = placeholder, style = MaterialTheme.typography.bodyLarge, color = Faint)
            }
            inner()
        },
    )
}
