package dev.yusr.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.settings.NO_WIDGET
import dev.yusr.ui.t
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.onboarding.AppSetupScreen
import dev.yusr.ui.onboarding.SetupChecklistScreen
import dev.yusr.ui.stats.UsageDashboardScreen
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.YusrTheme

private enum class SettingsRoute { Menu, Setup, Decide, Apps, Blackouts, Prayer, Friction, Appearance, Widget, Pending, Usage }

/**
 * Settings live behind a long press on the clock, which is deliberate: they are the one place
 * where the rules can be weakened, and they should not be a thumb's reach away.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YusrTheme {
                SettingsHost(onExit = { finish() })
            }
        }
    }
}

@Composable
private fun SettingsHost(onExit: () -> Unit) {
    var route by rememberSaveable { mutableStateOf(SettingsRoute.Menu) }

    BackHandler(enabled = true) {
        if (route == SettingsRoute.Menu) onExit() else route = SettingsRoute.Menu
    }

    when (route) {
        SettingsRoute.Menu -> SettingsMenu(onNavigate = { route = it })
        SettingsRoute.Setup -> SetupChecklistScreen(onReviewApps = { route = SettingsRoute.Decide })
        SettingsRoute.Decide -> AppSetupScreen(onDone = { route = SettingsRoute.Menu })
        SettingsRoute.Apps -> AppRulesScreen()
        SettingsRoute.Blackouts -> BlackoutScreen()
        SettingsRoute.Prayer -> PrayerScreen()
        SettingsRoute.Friction -> FrictionScreen()
        SettingsRoute.Appearance -> AppearanceScreen()
        SettingsRoute.Widget -> WidgetScreen()
        SettingsRoute.Pending -> PendingChangesScreen()
        SettingsRoute.Usage -> UsageDashboardScreen()
    }
}

@Composable
private fun SettingsMenu(onNavigate: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val repository = remember { context.container.repository }
    val pending by repository.pendingChanges.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = null)

    YusrPage(title = t("Settings")) {
        if (settings?.onboardingComplete == false) {
            Text(
                text = t("setup is unfinished — the limits are not fully enforced yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        YusrRow(
            label = t("Setup and permissions"),
            detail = t("what the app still needs to do its job"),
        ) { onNavigate(SettingsRoute.Setup) }

        if (settings?.rulesLocked == false) {
            YusrRow(
                label = t("Decide app by app"),
                detail = t("sort the whole list out now, while nothing has to wait"),
            ) { onNavigate(SettingsRoute.Decide) }
        }

        YusrRow(
            label = t("Apps and limits"),
            detail = t("favourites, gated apps, daily budgets"),
        ) { onNavigate(SettingsRoute.Apps) }

        YusrRow(
            label = t("Blackout windows"),
            detail = t("hours when only favourites open"),
        ) { onNavigate(SettingsRoute.Blackouts) }

        YusrRow(
            label = t("Prayer times and salah"),
            detail = with(settings?.prayer) {
                when {
                    this == null || !configured -> t("set a location to work out the times")
                    enabled -> t("the phone stops for salah · %s", method.name.lowercase())
                    else -> t("times shown, nothing blocked yet")
                }
            },
        ) { onNavigate(SettingsRoute.Prayer) }

        YusrRow(
            label = t("Friction"),
            detail = t("how long the countdown is, how long the cooldown is"),
        ) { onNavigate(SettingsRoute.Friction) }

        YusrRow(
            label = t("Appearance and navigation"),
            detail = t("light or dark, and how you get around"),
        ) { onNavigate(SettingsRoute.Appearance) }

        YusrRow(
            label = t("Home widget"),
            detail = if ((settings?.homeWidgetId ?: NO_WIDGET) == NO_WIDGET) {
                t("nothing under the clock but the next prayer")
            } else {
                t("one widget under the clock")
            },
        ) { onNavigate(SettingsRoute.Widget) }

        YusrRow(
            label = t("Pending changes"),
            detail = if (pending.isEmpty()) t("nothing waiting") else t("%s waiting out the cooldown", pending.size),
        ) { onNavigate(SettingsRoute.Pending) }

        YusrRow(
            label = t("Usage"),
            detail = t("where the day actually went"),
        ) { onNavigate(SettingsRoute.Usage) }

        Text(
            text = if (settings?.rulesLocked == false) {
                t("nothing waits yet — the cooldown starts once you lock the rules in.")
            } else {
                t("changes that loosen the rules wait out the cooldown before they take effect.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 32.dp),
        )
    }
}
