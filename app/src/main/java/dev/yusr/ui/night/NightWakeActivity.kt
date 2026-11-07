package dev.yusr.ui.night

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.prayer.PrayerToday
import dev.yusr.domain.Prayer
import dev.yusr.service.NightWakeAlarm
import dev.yusr.ui.YusrButton
import dev.yusr.ui.t
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.YusrTheme
import dev.yusr.util.DayClock

/**
 * What the night alarm opens onto: a dark page over the lock screen, saying what time it is in
 * the night and how long is left until fajr, with two answers. Either one stops the sound.
 */
class NightWakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Over the lock screen and with the screen on: an alarm behind a keyguard is no alarm.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            YusrTheme {
                NightWakeScreen(
                    onUp = {
                        NightWakeAlarm.dismiss(this)
                        finish()
                    },
                    onSnooze = {
                        NightWakeAlarm.dismiss(this)
                        NightWakeAlarm.snooze(this)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun NightWakeScreen(onUp: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    val today by produceState<PrayerToday?>(null) { value = context.container.prayerRepository.today() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 26.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = t("The last third of the night"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        today?.let { snapshot ->
            val fajr = snapshot.timetable.minuteOfDay(Prayer.FAJR)
            val left = Math.floorMod(fajr - snapshot.minuteOfDay, 24 * 60)
            Text(
                text = t("fajr at %s · %s left", DayClock.clock(fajr), DayClock.formatMinutes(left)),
                style = MaterialTheme.typography.bodyLarge,
                color = Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Text(
            text = t("eleven rakʿāt: eight of the night prayer, two of al-shafʿ, one of al-witr."),
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 22.dp),
        )
        YusrButton(
            label = t("I'm up"),
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        ) { onUp() }
        YusrButton(
            label = t("ten more minutes"),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { onSnooze() }
    }
}
