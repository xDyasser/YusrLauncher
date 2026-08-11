package dev.yusr.ui.settings

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.yusr.container
import dev.yusr.data.settings.Language
import dev.yusr.data.settings.ThemeMode
import dev.yusr.ui.applyLanguage
import dev.yusr.ui.t
import dev.yusr.ui.YusrButton
import dev.yusr.ui.YusrPage
import dev.yusr.ui.noRippleClickable
import androidx.compose.ui.draw.clip
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.YusrShape
import dev.yusr.util.Permissions
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Light or dark, and the navigation strip. Neither of these makes the rules weaker, so neither
 * of them waits out a cooldown.
 */
@Composable
fun AppearanceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val settings by store.settings.collectAsState(initial = null)

    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val navServiceOn = remember(refresh) { Permissions.navServiceEnabled(context) }

    YusrPage(title = t("Appearance and navigation")) {
        Text(text = t("LANGUAGE"), style = MaterialTheme.typography.labelSmall, color = Faint)
        Text(
            text = t("the app follows the phone unless you tell it otherwise. arabic brings the ") +
                t("whole layout round with it."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Language.entries.forEach { language ->
                val active = settings?.language == language
                Text(
                    // Each in its own script, so somebody who cannot read the current one can
                    // still find the way out.
                    text = when (language) {
                        Language.SYSTEM -> t("system")
                        Language.ENGLISH -> "English"
                        Language.ARABIC -> "العربية"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onBackground else Faint,
                    modifier = Modifier
                        .weight(1f)
                        .clip(YusrShape)
                        .border(1.dp, if (active) Faint else Fainter, YusrShape)
                        .noRippleClickable {
                            scope.launch {
                                store.setLanguage(language)
                                // Handed to the system, which recreates this screen in the new
                                // language — so the change is visible where it was made.
                                applyLanguage(context, language)
                            }
                        }
                        .padding(vertical = 13.dp),
                )
            }
        }

        Text(
            text = t("THEME"),
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            modifier = Modifier.padding(top = 36.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val active = settings?.themeMode == mode
                Text(
                    text = t(mode.name.lowercase(Locale.ENGLISH)),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onBackground else Faint,
                    modifier = Modifier
                        .weight(1f)
                        .clip(YusrShape)
                        .border(1.dp, if (active) Faint else Fainter, YusrShape)
                        .noRippleClickable { scope.launch { store.setThemeMode(mode) } }
                        .padding(vertical = 13.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(top = 36.dp)) {
            Text(text = t("NAVIGATION"), style = MaterialTheme.typography.labelSmall, color = Faint)
            Text(
                text = t("the phone's own navigation is what this launcher uses — the swipe from ") +
                    t("the edge for back, the swipe up for home, the one you already know. ") +
                    t("nothing here replaces it, and there is no bar of our own at the bottom."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = t("if your phone is set to gesture navigation and it feels wrong here, it ") +
                    t("is the system setting that decides it: Settings → System → Navigation mode."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 12.dp),
            )

            Text(
                text = "A STRIP OF OUR OWN · OFF",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
                modifier = Modifier.padding(top = 28.dp),
            )
            Text(
                text = t("only for phones where the system navigation has been hidden or cannot be ") +
                    t("reached: a thin strip along the bottom edge — swipe up for home, tap or ") +
                    t("swipe right for back, hold or swipe left for recents. it needs an ") +
                    t("accessibility service, because nothing else may press those. leave it off ") +
                    t("unless you need it."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 8.dp),
            )

            YusrButton(
                label = if (settings?.navOverlayEnabled == true) t("hide the strip") else t("show the strip"),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                val next = settings?.navOverlayEnabled != true
                scope.launch { store.setNavOverlayEnabled(next) }
            }

            if (settings?.navOverlayEnabled == true && !navServiceOn) {
                Text(
                    text = t("the strip needs the Yusr Launcher navigation accessibility service ") +
                        t("switched on before it can appear."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )
                YusrButton(
                    label = t("open accessibility settings"),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    runCatching {
                        context.startActivity(
                            Permissions.accessibilitySettings().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
        }
    }
}
