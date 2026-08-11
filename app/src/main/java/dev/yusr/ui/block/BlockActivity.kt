package dev.yusr.ui.block

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.domain.PrayerWindow
import dev.yusr.domain.PrayerWindows
import dev.yusr.domain.RefusalReason
import dev.yusr.ui.t
import dev.yusr.ui.AppLauncher
import dev.yusr.util.DayClock
import dev.yusr.ui.YusrButton
import androidx.compose.ui.draw.clip
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.YusrShape
import dev.yusr.ui.theme.YusrTheme
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The wall. It states plainly what stopped you and what it would cost to go through anyway.
 *
 * The emergency bypass is deliberately visible and deliberately countable: three a week, each
 * one logged, so using one is a decision rather than a reflex.
 */
class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val reason = intent.getStringExtra(EXTRA_REASON)?.let { RefusalReason.valueOf(it) }
        if (packageName == null || reason == null) {
            finish()
            return
        }
        val bypassesRemaining = intent.getIntExtra(EXTRA_BYPASSES, 0)
        val sessionOver = intent.getBooleanExtra(EXTRA_SESSION_OVER, false)

        setContent {
            YusrTheme {
                BlockScreen(
                    targetPackage = packageName,
                    reason = reason,
                    bypassesRemaining = bypassesRemaining,
                    sessionOver = sessionOver,
                    onDone = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_BYPASSES = "bypasses"
        private const val EXTRA_SESSION_OVER = "session_over"

        fun newIntent(
            context: Context,
            packageName: String,
            reason: RefusalReason,
            bypassesRemaining: Int = 0,
            sessionOver: Boolean = false,
        ): Intent = Intent(context, BlockActivity::class.java)
            .putExtra(EXTRA_PACKAGE, packageName)
            .putExtra(EXTRA_REASON, reason.name)
            .putExtra(EXTRA_BYPASSES, bypassesRemaining)
            .putExtra(EXTRA_SESSION_OVER, sessionOver)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun BlockScreen(
    targetPackage: String,
    reason: RefusalReason,
    bypassesRemaining: Int,
    sessionOver: Boolean,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }

    val label by produceState(initialValue = "", targetPackage) {
        value = repository.snapshot(targetPackage).label
    }
    var bypassReason by remember { mutableStateOf("") }
    var showBypass by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onDone() }

    // The prayer window is the only refusal that names something outside the app's own rules,
    // so it is worth saying which prayer and how long is left.
    val prayerWindow by produceState<PrayerWindow?>(initialValue = null, reason) {
        value = if (reason == RefusalReason.PRAYER) repository.activePrayerWindow() else null
    }

    val headline = when {
        sessionOver -> t("time is up")
        reason == RefusalReason.PERMANENTLY_BLOCKED -> t("blocked")
        reason == RefusalReason.PRAYER -> prayerWindow?.label ?: t("salah")
        reason == RefusalReason.BLACKOUT -> t("not right now")
        reason == RefusalReason.DAILY_OPENS_SPENT -> t("that was the last one")
        else -> t("budget spent")
    }

    val explanation = when {
        sessionOver -> t("your session on %s has ended.", label.lowercase(Locale.getDefault()))
        reason == RefusalReason.PERMANENTLY_BLOCKED ->
            t("%s is blocked outright. no countdown will open it.", label.lowercase(Locale.getDefault()))
        reason == RefusalReason.PRAYER -> {
            val remaining = prayerWindow?.let { window ->
                val local = DayClock.localDateTime(System.currentTimeMillis())
                PrayerWindows.minutesUntilEnd(window, local.hour * 60 + local.minute)
            }
            if (remaining != null) {
                t("the phone is closed for salah. it opens again in %s.", DayClock.formatMinutes(remaining))
            } else {
                t("the phone is closed for salah.")
            }
        }
        reason == RefusalReason.BLACKOUT ->
            t("a blackout window is in force. only favourites open until it ends.")
        reason == RefusalReason.DAILY_OPENS_SPENT ->
            t("you have used every open you allowed yourself on %s today.", label.lowercase(Locale.getDefault()))
        else ->
            t("you have spent today's minutes on %s.", label.lowercase(Locale.getDefault()))
    }

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
        Text(
            text = headline,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = Faint,
            modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            text = if (reason == RefusalReason.PRAYER) {
                t("go and pray. that is the whole idea.")
            } else {
                t("it resets tomorrow. that is the whole idea.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(top = 24.dp),
        )

        YusrButton(
            label = t("back to home"),
            modifier = Modifier.padding(top = 40.dp),
        ) { onDone() }

        if (reason != RefusalReason.PERMANENTLY_BLOCKED && bypassesRemaining > 0) {
            if (!showBypass) {
                Text(
                    text = t("emergency bypass · %s left this week", bypassesRemaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                )
                YusrButton(
                    label = t("use one"),
                    modifier = Modifier.padding(top = 12.dp),
                ) { showBypass = true }
            } else {
                Text(
                    text = t("this will be logged. say what the emergency is."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 28.dp),
                )
                BasicTextField(
                    value = bypassReason,
                    onValueChange = { bypassReason = it },
                    textStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
                        .copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(YusrShape)
                        .border(1.dp, Fainter, YusrShape)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                YusrButton(
                    label = t("spend a bypass"),
                    enabled = bypassReason.trim().length >= MIN_BYPASS_REASON,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    scope.launch {
                        val settings = context.container.settingsStore.current()
                        repository.recordBypass(targetPackage, bypassReason.trim())
                        AppLauncher.grantAndLaunch(
                            context = context,
                            packageName = targetPackage,
                            sessionMinutes = settings.bypassMinutes,
                            wasBypass = true,
                        )
                        onDone()
                    }
                }
            }
        } else if (reason != RefusalReason.PERMANENTLY_BLOCKED) {
            Text(
                text = t("no bypasses left this week."),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
    }
}

private const val MIN_BYPASS_REASON = 20
