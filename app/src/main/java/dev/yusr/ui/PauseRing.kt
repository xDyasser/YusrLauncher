package dev.yusr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.yusr.domain.PrayerWindow
import dev.yusr.domain.PrayerWindows
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.util.DayClock
import kotlinx.coroutines.delay

/**
 * The pause, drawn as what it is: a thing with an end.
 *
 * A sentence saying the phone opens again in fourteen minutes is a number printed once that then
 * stands still — it says nothing about how much of the stop is already behind you, and two minutes
 * later it is simply wrong. The ring empties as the window runs down, so the wait is something you
 * can see the size of without reading anything.
 *
 * It is the only moving thing on either screen it appears on, and it moves slowly, which is the
 * point: this is a screen meant to be turned away from.
 */
@Composable
fun PauseRing(window: PrayerWindow, modifier: Modifier = Modifier) {
    // Read off the clock rather than counted down: a loop subtracting a second at a time drifts,
    // and this one can be on screen for the length of a prayer.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(window) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val local = DayClock.localDateTime(now)
    val minutesLeft = PrayerWindows.minutesUntilEnd(window, local.hour * 60 + local.minute) ?: return
    // minutesUntilEnd counts whole minutes to the boundary, so the seconds already spent inside
    // the current one come off it and the ring lands on zero as the phone opens.
    val secondsLeft = (minutesLeft * 60L - local.second).coerceAtLeast(0L)
    val total = (window.lengthMinutes * 60L).coerceAtLeast(1L)
    val left = (secondsLeft.toFloat() / total).coerceIn(0f, 1f)

    // Both colours are read here rather than in the draw lambda: these are composable getters,
    // and a draw scope is not a composition.
    val remaining = MaterialTheme.colorScheme.primary
    val track = Fainter

    Box(modifier = modifier.size(DIAMETER), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(DIAMETER)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arc = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = stroke,
            )
            // Anticlockwise from the top, unwinding the way a thing being spent should.
            drawArc(
                color = remaining,
                startAngle = -90f,
                sweepAngle = -360f * left,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arc,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = DayClock.formatSeconds(secondsLeft),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = t("until it opens"),
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val DIAMETER = 176.dp
