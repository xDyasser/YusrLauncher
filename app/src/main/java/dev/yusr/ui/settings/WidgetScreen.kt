package dev.yusr.ui.settings

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.settings.NO_WIDGET
import dev.yusr.ui.t
import dev.yusr.ui.YusrButton
import dev.yusr.ui.YusrPage
import dev.yusr.ui.YusrRow
import dev.yusr.ui.PillPicker
import dev.yusr.ui.home.HomeWidget
import dev.yusr.ui.theme.Faint
import kotlinx.coroutines.launch

/**
 * Choosing the one widget the home screen will carry.
 *
 * The app's own next-prayer line is a compromise: it knows the timetable but nothing about the
 * iqama at your masjid, the adhan you like, or the count you keep. If you already have an app
 * that does, its widget belongs here instead.
 *
 * The list is ours rather than the system's. Asking the system to pick — `ACTION_APPWIDGET_PICK`
 * — only works where AOSP's Settings app is intact, and on the phones this launcher is actually
 * used on it is not: the intent resolves to nothing, launching it throws, and the screen dies.
 * So the widgets are enumerated here, and only the permission to bind one is left to the system.
 */
@Composable
fun WidgetScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val settings by store.settings.collectAsState(initial = null)

    val host = remember { HomeWidget.host(context) }
    // A widget is bound to a listening host, so it has to be listening when the binding lands or
    // the first update is dropped and the widget comes up blank.
    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }

    val chosenId = settings?.homeWidgetId ?: NO_WIDGET
    val info = remember(chosenId) { HomeWidget.info(context, chosenId) }
    val heightDp = settings?.homeWidgetHeightDp ?: 132

    var browsing by remember { mutableStateOf(false) }
    var pendingId by remember { mutableIntStateOf(NO_WIDGET) }
    var problem by remember { mutableStateOf<String?>(null) }

    val adopt: (Int) -> Unit = { widgetId ->
        scope.launch {
            // Any widget already on the home screen is being replaced, not accompanied.
            val previous = store.current().homeWidgetId
            if (previous != NO_WIDGET && previous != widgetId) HomeWidget.release(host, previous)
            store.setHomeWidgetId(widgetId)
        }
        // Its own settings screen, when it has one. Whatever happens in there, the widget is
        // already bound and on the home screen; cancelling leaves it at its defaults.
        (context as? Activity)?.let { activity ->
            HomeWidget.configure(activity, host, widgetId, CONFIGURE_REQUEST)
        }
        browsing = false
        problem = null
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val requested = pendingId
        pendingId = NO_WIDGET
        val widgetId = result.data
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, requested)
            ?: requested

        if (result.resultCode != RESULT_OK || widgetId == NO_WIDGET) {
            // Refused, or backed out: hand the id back rather than leave it allocated forever.
            if (requested != NO_WIDGET) HomeWidget.release(host, requested)
        } else {
            adopt(widgetId)
        }
    }

    YusrPage(
        title = t("Home widget"),
        subtitle = info?.loadLabel(context.packageManager) ?: t("nothing chosen"),
    ) {
        Text(
            text = t("one widget sits under the clock, in place of the line about the next ") +
                t("prayer. a salah app's widget is what this is for, but everything the phone ") +
                t("publishes is listed."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
        )

        problem?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        if (browsing) {
            WidgetChoices(
                onPick = { choice ->
                    when (val binding = HomeWidget.bind(context, host, choice)) {
                        is HomeWidget.Binding.Bound -> adopt(binding.widgetId)
                        is HomeWidget.Binding.NeedsConsent -> {
                            pendingId = binding.widgetId
                            // The dialog is the system's, so it can be absent even after it said
                            // it was there. Losing the screen over it is not worth it.
                            runCatching { consent.launch(binding.intent) }.onFailure {
                                HomeWidget.release(host, binding.widgetId)
                                pendingId = NO_WIDGET
                                problem = cannotBind()
                            }
                        }
                        HomeWidget.Binding.Impossible -> problem = cannotBind()
                    }
                },
                onCancel = { browsing = false },
            )
        } else {
            YusrButton(
                label = if (chosenId == NO_WIDGET) t("choose a widget") else t("choose a different one"),
                modifier = Modifier.padding(top = 24.dp),
            ) {
                problem = null
                browsing = true
            }
        }

        if (!browsing && chosenId != NO_WIDGET) {
            Column(modifier = Modifier.padding(top = 32.dp)) {
                Text(text = t("HEIGHT"), style = MaterialTheme.typography.labelSmall, color = Faint)
                Text(
                    text = t("how much of the home screen it may take. the names below it keep ") +
                        t("whatever is left."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                PillPicker(
                    options = HEIGHTS.map { "$it" },
                    selectedIndex = HEIGHTS.indexOfFirst { it == heightDp }.coerceAtLeast(0),
                ) { index -> scope.launch { store.setHomeWidgetHeightDp(HEIGHTS[index]) } }
            }

            YusrButton(
                label = t("remove it"),
                modifier = Modifier.padding(top = 32.dp),
            ) {
                scope.launch {
                    HomeWidget.release(host, chosenId)
                    store.setHomeWidgetId(NO_WIDGET)
                }
            }

            if (info == null) {
                Text(
                    text = t("the app that published this widget is not answering — it may have ") +
                        t("been uninstalled. choose another one, or remove it."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

/** Every widget on the phone, as a plain list of names. */
@Composable
private fun WidgetChoices(
    onPick: (HomeWidget.Choice) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val choices = remember { HomeWidget.providers(context) }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        if (choices.isEmpty()) {
            Text(
                text = t("no app on this phone publishes a widget."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            choices.forEach { choice ->
                YusrRow(
                    label = choice.widgetLabel,
                    detail = choice.appLabel,
                    onClick = { onPick(choice) },
                )
            }
        }

        YusrButton(label = t("never mind"), modifier = Modifier.padding(top = 24.dp)) { onCancel() }
    }
}

/** Unused by us — the widget's own configuration screen reports back to the system, not here. */
private const val CONFIGURE_REQUEST = 0x57

/**
 * A function rather than a `const val`, which is what it used to be: a constant has to be known at
 * compile time, and this sentence is not known until the app has been told which language it is
 * speaking.
 */
private fun cannotBind(): String =
    t("this phone will not let the launcher hold a widget — the screen that grants it is missing. ") +
        t("nothing to be done here; the next-prayer line stays.")

private val HEIGHTS = listOf(96, 132, 180, 240)
