package dev.yusr.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.yusr.admin.PolicyTier
import dev.yusr.container
import dev.yusr.domain.Madhab
import dev.yusr.service.GuardService
import dev.yusr.ui.t
import dev.yusr.ui.YusrButton
import dev.yusr.ui.YusrPage
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Faint
import dev.yusr.util.Permissions
import kotlinx.coroutines.launch

/**
 * Everything this app needs that only the user can grant, in the order that matters, plus the
 * OEM-specific settings that decide whether the guard service survives the night.
 */
@Composable
fun SetupChecklistScreen(onReviewApps: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val policyManager = remember { context.container.policyManager }
    val settingsStore = remember { context.container.settingsStore }

    // Re-check every time we come back from a Settings screen.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showOwnerHelp by remember { mutableStateOf(false) }
    val settings by settingsStore.settings.collectAsState(initial = null)
    val rulesLocked = settings?.rulesLocked == true

    val isLauncher = remember(refresh) { Permissions.isDefaultLauncher(context) }
    val gestures = remember(refresh) { Permissions.gestureNavigation(context) }
    val hasUsage = remember(refresh) { Permissions.hasUsageAccess(context) }
    val canOverlay = remember(refresh) { Permissions.canDrawOverlays(context) }
    val hasListener = remember(refresh) { Permissions.notificationListenerEnabled(context) }
    val batteryFree = remember(refresh) { Permissions.batteryUnrestricted(context) }
    val tier = remember(refresh) { policyManager.tier() }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    YusrPage(title = t("Setup"), subtitle = t("the first three are what make the limits real")) {
        // Asked before anything else and with nothing chosen for you. Everything about salah in
        // this app follows from it — the asr rule, the calculation method, whether the prayers are
        // combined, which book of supplications the hub reads from — and a launcher that guessed
        // a school and then quietly timed someone's prayers by it would be worse than one that
        // asked. An install from before this question existed keeps working: the school is read
        // off the settings already in force rather than demanded again.
        if (settings?.prayer?.needsMadhab != false) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = t("WHICH SCHOOL DO YOU FOLLOW?"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = t("this sets the ʿasr rule, the calculation method and whether the prayers ") +
                        t("are combined. all three stay editable afterwards."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Madhab.entries.forEach { madhab ->
                    Text(
                        text = "${t(madhab.label)}  ·  ${t(madhab.note)} · ${t(madhab.branch.label)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .noRippleClickable { scope.launch { settingsStore.setMadhab(madhab) } }
                            .padding(vertical = 11.dp),
                    )
                }
            }
        }

        ChecklistItem(
            done = isLauncher,
            title = t("be the home screen"),
            detail = t("otherwise the old launcher is one press away."),
            action = t("open home settings"),
        ) { open(Permissions.homeSettings()) }

        // Directly under the home screen, because the two are one decision: this is the launcher
        // now, and this is how you move around it. The three buttons are not broken here — back
        // and home and recents all work — they simply cost a strip of every screen to a bar the
        // launcher itself never draws in, on an app whose whole argument is the empty space.
        //
        // Nothing is enforced on the answer and a phone that will not say which mode it is in
        // does not get told off: the item appears only where Android answered.
        if (gestures != null) {
            ChecklistItem(
                done = gestures,
                title = t("navigate by gestures"),
                detail = if (gestures) {
                    t("swipe up for home, in from the edge for back. the launcher uses the phone's ") +
                        t("own gestures and adds none of its own.")
                } else {
                    t("the phone is on the three buttons. gestures give that strip back to the ") +
                        t("screen: swipe up for home, in from the edge for back. ") +
                        t("Settings → System → Navigation mode, if the button below lands elsewhere.")
                },
                action = t("open navigation settings"),
            ) { open(Permissions.navigationModeSettings(context)) }
        }

        ChecklistItem(
            done = hasUsage,
            title = t("usage access"),
            detail = t("lets the app see which app is in front, which is how limits are enforced."),
            action = t("grant usage access"),
        ) { open(Permissions.usageAccessSettings()) }

        ChecklistItem(
            done = canOverlay,
            title = t("display over other apps"),
            detail = t("without it the block screen cannot appear on top of the app you just opened."),
            action = t("grant display over apps"),
        ) { open(Permissions.overlaySettings(context)) }

        ChecklistItem(
            done = hasListener,
            title = t("notification access"),
            detail = t("used only to hold back notifications from gated apps. calls and alarms always ring."),
            action = t("grant notification access"),
        ) { open(Permissions.notificationListenerSettings()) }

        ChecklistItem(
            done = tier != PolicyTier.NONE,
            title = t("device admin"),
            detail = t("makes uninstalling take deliberate steps rather than a long press."),
            action = t("activate device admin"),
        ) { open(policyManager.addAdminIntent(t("Keeps Yusr Launcher from being removed on impulse."))) }

        // The strip of our own is not on this list and should not be: it is for the phones where
        // the system navigation has been hidden or cannot be reached, it needs an accessibility
        // service, and it lives in Appearance where someone goes looking for it.

        ChecklistItem(
            done = batteryFree,
            title = t("battery: no restrictions"),
            detail = if (batteryFree) {
                // Ticked, and still worth saying: Android's own exemption is the only part of this
                // any API can see, and on some phones it is not the part that kills the service.
                t("granted. on Xiaomi/HyperOS also turn on Autostart and lock the app in recents — ") +
                    t("no API can report those, so this line cannot check them for you.")
            } else {
                t("on Xiaomi/HyperOS also turn on Autostart and lock the app in recents, or the ") +
                    t("enforcement service will be killed while you sleep.")
            },
            action = t("allow unrestricted battery"),
        ) { open(Permissions.batteryOptimizationSettings(context)) }

        Column(modifier = Modifier.padding(top = 32.dp)) {
            Text(
                text = t("DEVICE OWNER · OPTIONAL"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
            Text(
                text = when (tier) {
                    PolicyTier.OWNER -> t("active. blocked apps are suspended by the system itself.")
                    else -> t("not set. this is the strongest tier: the OS refuses to open blocked apps, ") +
                        t("and the app cannot be uninstalled without ADB or a factory reset.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (tier != PolicyTier.OWNER) {
                YusrButton(
                    label = if (showOwnerHelp) t("hide the command") else t("how to enable it"),
                    modifier = Modifier.padding(top = 12.dp),
                ) { showOwnerHelp = !showOwnerHelp }

                if (showOwnerHelp) {
                    Text(
                        text = t("with the phone connected over USB and no other accounts on the device:") +
                            "\n\nadb shell dpm set-device-owner dev.yusr/.admin.YusrAdminReceiver\n\n" +
                            t("removing it later needs adb, or a factory reset. read docs/SETUP.md first."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(top = 32.dp)) {
            Text(
                text = if (rulesLocked) t("YOUR RULES · LOCKED") else t("YOUR RULES · STILL OPEN"),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
            Text(
                text = if (rulesLocked) {
                    t("loosening a rule now waits out the cooldown.")
                } else {
                    t("go through the app list and decide each one. while the rules are unlocked ") +
                        t("every change applies immediately — no waiting.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!rulesLocked) {
                YusrButton(
                    label = t("decide app by app"),
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = onReviewApps,
                )
            }
        }

        YusrButton(
            label = t("start enforcing"),
            modifier = Modifier.padding(top = 36.dp),
        ) {
            GuardService.start(context)
            scope.launch {
                settingsStore.setOnboardingComplete(true)
                context.container.repository.syncCatalog()
            }
        }

        // Where the long press is taught. The gesture is the only way into settings and it is not
        // one a phone advertises, so it is said here — on the screen someone is looking at when
        // they have just found out there is something to come back to.
        Text(
            text = t("you can come back here whenever something stops working.") + " " +
                t("settings are a long press on the clock."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun ChecklistItem(
    done: Boolean,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = (if (done) "· " else "— ") + title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!done) {
            YusrButton(label = action, modifier = Modifier.padding(top = 12.dp), onClick = onAction)
        }
    }
}
