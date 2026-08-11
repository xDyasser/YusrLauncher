package dev.yusr.ui.gate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.yusr.container
import dev.yusr.data.quran.Ayah
import dev.yusr.data.settings.AyahLanguage
import dev.yusr.domain.AppUsageToday
import dev.yusr.domain.Dhikr
import dev.yusr.domain.GateDecision
import dev.yusr.domain.NextPrayer
import dev.yusr.ui.t
import dev.yusr.ui.AppLauncher
import dev.yusr.ui.AyahBlock
import dev.yusr.ui.Hairline
import dev.yusr.ui.YusrButton
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.ThinProgress
import dev.yusr.ui.block.BlockActivity
import dev.yusr.ui.home.prayerName
import androidx.compose.ui.draw.clip
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.YusrShape
import dev.yusr.ui.theme.YusrTheme
import dev.yusr.util.DayClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The toll gate. To reach a gated app you must type its name in full, say why, and then sit
 * through a countdown that grows every time you have already been here today.
 *
 * Leaving the screen resets the countdown, so waiting it out in the background is not a route
 * through.
 */
class GateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName == null) {
            finish()
            return
        }

        setContent {
            YusrTheme {
                GateScreen(
                    targetPackage = packageName,
                    onDone = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun newIntent(context: Context, packageName: String): Intent =
            Intent(context, GateActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun GateScreen(targetPackage: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }

    val decision by produceState<GateDecision?>(initialValue = null, targetPackage) {
        value = repository.decide(targetPackage)
    }
    val label by produceState(initialValue = "", targetPackage) {
        value = repository.snapshot(targetPackage).label
    }

    BackHandler(enabled = true) { onDone() }

    val current = decision
    if (current == null) {
        Column(modifier = Modifier.fillMaxSize().background(Backdrop)) { }
        return
    }

    // The rules can change between the tap and this screen; a refusal wins.
    if (current is GateDecision.Refuse) {
        LaunchedEffect(current) {
            context.startActivity(
                BlockActivity.newIntent(context, targetPackage, current.reason, current.bypassesRemaining),
            )
            onDone()
        }
        return
    }

    if (current is GateDecision.Allow) {
        LaunchedEffect(current) {
            AppLauncher.launchDirect(context, targetPackage)
            onDone()
        }
        return
    }

    val friction = current as GateDecision.RequireFriction
    var typedDhikr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(friction.delaySeconds) }
    var resetToken by remember { mutableIntStateOf(0) }

    val dhikr = remember(targetPackage) {
        Dhikr.forAttempt(targetPackage, LocalDate.now().dayOfYear)
    }
    // Read once per visit to the gate: a new ayah each time you come back, the same one while
    // you are sitting here.
    val ayah by produceState<Ayah?>(initialValue = null, targetPackage) {
        value = context.container.quran.random()
    }
    val ayahLanguage by produceState(initialValue = AyahLanguage.BOTH) {
        value = context.container.settingsStore.current().prayer.ayahLanguage
    }

    // What today already cost, and what is coming. Both are read once, on arrival: a countdown
    // beside a figure that ticks upward would be two clocks arguing.
    val usage by produceState<AppUsageToday?>(null, targetPackage) {
        value = repository.usageToday(targetPackage)
    }
    val nextPrayer by produceState<NextPrayer?>(null, targetPackage) {
        value = context.container.prayerRepository.nextPrayer()
    }

    val dhikrMatches = dhikr.matches(typedDhikr)
    val reasonLongEnough = reason.trim().length >= friction.minReasonLength
    val bothValid = dhikrMatches && reasonLongEnough

    // Backgrounding the gate puts the countdown back to the start.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) resetToken++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(bothValid, resetToken, friction.delaySeconds) {
        remaining = friction.delaySeconds
        if (!bothValid) return@LaunchedEffect
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
    }

    // Typing is not a paste job: the selection toolbar never appears on these fields.
    CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Backdrop)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 26.dp)
                .padding(top = 40.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(t("Kahf pause"), color = Gold)
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
            // What it has already cost today, and what is next. Neither is an obstacle — they are
            // the two facts most likely to make someone put the phone down on their own, which is
            // a better outcome than any countdown can buy.
            Text(
                text = costSoFar(usage, nextPrayer),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 10.dp),
            )

            ayah?.let {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Hairline()
                    AyahBlock(
                        ayah = it,
                        language = ayahLanguage,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                    Hairline()
                }
            }

            GateField(
                caption = t("type: %s", dhikr.transliteration),
                hint = "${dhikr.arabic}  ·  ${dhikr.meaning}",
                value = typedDhikr,
                onValueChange = { typedDhikr = it },
                satisfied = dhikrMatches,
                modifier = Modifier.padding(top = 32.dp),
            )

            GateField(
                caption = t("why, in at least %s characters", friction.minReasonLength),
                value = reason,
                onValueChange = { reason = it },
                satisfied = reasonLongEnough,
                modifier = Modifier.padding(top = 24.dp),
            )

            Text(
                text = when {
                    !bothValid -> t("the wait starts once both are done")
                    remaining > 0 -> DayClock.formatSeconds(remaining.toLong())
                    else -> t("go on then")
                },
                style = if (bothValid && remaining > 0) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = if (bothValid) MaterialTheme.colorScheme.primary else Faint,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp),
            )

            // The countdown, drawn as the one gold line on the screen. It fills as the wait runs
            // down rather than emptying, so what you are watching is the thing you are earning.
            if (bothValid && friction.delaySeconds > 0) {
                ThinProgress(
                    fraction = 1f - remaining.toFloat() / friction.delaySeconds,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }

            YusrButton(
                label = t("open for %s min", friction.sessionMinutes),
                enabled = bothValid && remaining <= 0,
            ) {
                scope.launch {
                    // Last check: a budget or a blackout may have closed the door while you waited.
                    when (val latest = repository.decide(targetPackage)) {
                        is GateDecision.Refuse -> {
                            context.startActivity(
                                BlockActivity.newIntent(
                                    context,
                                    targetPackage,
                                    latest.reason,
                                    latest.bypassesRemaining,
                                ),
                            )
                        }

                        else -> {
                            repository.recordGatePass(targetPackage, reason.trim())
                            AppLauncher.grantAndLaunch(
                                context = context,
                                packageName = targetPackage,
                                sessionMinutes = friction.sessionMinutes,
                                wasBypass = false,
                            )
                        }
                    }
                    onDone()
                }
            }

            Text(
                text = t("or put the phone down"),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            )
        }
    }
}

/**
 * "Opened 6 times today, 41 minutes. ʿAsr is in 2 h 28 m."
 *
 * Both halves are dropped when there is nothing to say — a first open of the day with no
 * timetable set produces no line at all rather than a sentence full of zeroes.
 */
private fun costSoFar(usage: AppUsageToday?, next: NextPrayer?): String {
    val parts = mutableListOf<String>()
    if (usage != null && usage.opens > 0) {
        val opens = if (usage.opens == 1) t("once") else t("%s times", usage.opens)
        parts += if (usage.minutesUsed > 0) {
            t("You have opened it %s today, for %s.", opens, DayClock.formatMinutes(usage.minutesUsed))
        } else {
            t("You have opened it %s today.", opens)
        }
    }
    if (next != null) {
        parts += t("%s is in %s.", t(prayerName(next.prayer)), DayClock.formatMinutes(next.minutesAway))
    }
    return parts.joinToString(" ")
}

@Composable
private fun GateField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    satisfied: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = if (satisfied) MaterialTheme.colorScheme.onBackground else Faint,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
                .copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(YusrShape)
                .border(1.dp, if (satisfied) Faint else Fainter, YusrShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** A text toolbar that never shows, which removes paste as a shortcut past the typing test. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
