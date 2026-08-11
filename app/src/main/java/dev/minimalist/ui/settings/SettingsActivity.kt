package dev.minimalist.ui.settings

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
import dev.minimalist.container
import dev.minimalist.data.settings.NO_WIDGET
import dev.minimalist.ui.t
import dev.minimalist.ui.MinimalPage
import dev.minimalist.ui.MinimalRow
import dev.minimalist.ui.onboarding.AppSetupScreen
import dev.minimalist.ui.onboarding.SetupChecklistScreen
import dev.minimalist.ui.stats.UsageDashboardScreen
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.MinimalTheme

private enum class SettingsRoute { Menu, Setup, Decide, Apps, Blackouts, Prayer, Friction, Appearance, Widget, Pending, Usage }

/**
 * Settings live behind a long press on the clock, which is deliberate: they are the one place
 * where the rules can be weakened, and they should not be a thumb's reach away.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinimalTheme {
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

    MinimalPage(title = t("Settings")) {
        if (settings?.onboardingComplete == false) {
            Text(
                text = t("setup is unfinished — the limits are not fully enforced yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        MinimalRow(
            label = t("Setup and permissions"),
            detail = t("what the app still needs to do its job"),
        ) { onNavigate(SettingsRoute.Setup) }

        if (settings?.rulesLocked == false) {
            MinimalRow(
                label = t("Decide app by app"),
                detail = t("sort the whole list out now, while nothing has to wait"),
            ) { onNavigate(SettingsRoute.Decide) }
        }

        MinimalRow(
            label = t("Apps and limits"),
            detail = t("favourites, gated apps, daily budgets"),
        ) { onNavigate(SettingsRoute.Apps) }

        MinimalRow(
            label = t("Blackout windows"),
            detail = t("hours when only favourites open"),
        ) { onNavigate(SettingsRoute.Blackouts) }

        MinimalRow(
            label = t("Prayer times and salah"),
            detail = with(settings?.prayer) {
                when {
                    this == null || !configured -> t("set a location to work out the times")
                    enabled -> t("the phone stops for salah · %s", method.name.lowercase())
                    else -> t("times shown, nothing blocked yet")
                }
            },
        ) { onNavigate(SettingsRoute.Prayer) }

        MinimalRow(
            label = t("Friction"),
            detail = t("how long the countdown is, how long the cooldown is"),
        ) { onNavigate(SettingsRoute.Friction) }

        MinimalRow(
            label = t("Appearance and navigation"),
            detail = t("light or dark, and how you get around"),
        ) { onNavigate(SettingsRoute.Appearance) }

        MinimalRow(
            label = t("Home widget"),
            detail = if ((settings?.homeWidgetId ?: NO_WIDGET) == NO_WIDGET) {
                t("nothing under the clock but the next prayer")
            } else {
                t("one widget under the clock")
            },
        ) { onNavigate(SettingsRoute.Widget) }

        MinimalRow(
            label = t("Pending changes"),
            detail = if (pending.isEmpty()) t("nothing waiting") else t("%s waiting out the cooldown", pending.size),
        ) { onNavigate(SettingsRoute.Pending) }

        MinimalRow(
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
