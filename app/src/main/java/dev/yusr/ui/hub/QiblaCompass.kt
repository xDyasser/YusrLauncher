package dev.yusr.ui.hub

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.yusr.ui.t
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Where the phone is pointing, in degrees clockwise from true north, or null on a phone with no
 * usable compass.
 *
 * The rotation vector is used rather than the raw magnetometer because it is already fused with
 * the gyroscope and accelerometer, which is the difference between a needle that settles and one
 * that swims. Declination is applied, so this is true north rather than magnetic — the qibla is
 * computed against true north and a needle off by the local declination would be wrong by up to
 * twenty degrees at the extremes.
 */
@Composable
fun rememberHeading(): Float? {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    var heading by remember { mutableFloatStateOf(Float.NaN) }

    DisposableEffect(sensor) {
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                heading = (degrees + 360f) % 360f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }

    return heading.takeUnless { it.isNaN() }
}

/**
 * The qibla as a dial.
 *
 * [bearing] is the direction of the Kaʿba from here, and [heading] is where the phone is pointing.
 * With a compass the dial turns under the needle so the needle points at the Kaʿba in the room you
 * are standing in; without one the dial is drawn north-up and the needle simply shows the bearing,
 * which is still usable by anyone who knows which way north is.
 */
@Composable
fun QiblaDial(
    bearing: Double,
    heading: Float?,
    ring: Color,
    innerRing: Color,
    needle: Color,
    tick: Color,
    modifier: Modifier = Modifier,
) {
    // The needle's angle on screen. With no compass the dial is north-up, so the needle sits at
    // the bearing itself.
    val target = (bearing.toFloat() - (heading ?: 0f) + 360f) % 360f

    // Smoothed, and the wrap from 359° to 1° is taken the short way round rather than as a
    // 358° sweep. Without this the needle spins the length of the dial every time you face north.
    var continuous by remember { mutableFloatStateOf(target) }
    remember(target) {
        val delta = ((target - continuous + 540f) % 360f) - 180f
        continuous += delta
        continuous
    }
    val angle by animateFloatAsState(targetValue = continuous, label = "qibla")

    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = tick)

    Box(modifier = modifier.size(196.dp)) {
        Canvas(modifier = Modifier.size(196.dp)) {
            val radius = min(size.width, size.height) / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = ring, radius = radius - 1f, center = centre, style = Stroke(width = 1f))
            drawCircle(
                color = innerRing,
                radius = radius - 24.dp.toPx(),
                center = centre,
                style = Stroke(width = 1f),
            )

            // The needle, fading out towards the hub so the eye is pulled to the pointing end.
            val radians = Math.toRadians((angle - 90f).toDouble())
            val tip = Offset(
                x = centre.x + (radius - 16.dp.toPx()) * cos(radians).toFloat(),
                y = centre.y + (radius - 16.dp.toPx()) * sin(radians).toFloat(),
            )
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(needle.copy(alpha = 0.25f), needle),
                    start = centre,
                    end = tip,
                ),
                start = centre,
                end = tip,
                strokeWidth = 1.5f,
            )
            drawCircle(color = needle, radius = 2.5.dp.toPx(), center = centre)

            // The four cardinal points, which turn with the dial when there is a compass.
            listOf(t("N") to 0f, t("E") to 90f, t("S") to 180f, t("W") to 270f).forEach { (name, at) ->
                val rotated = Math.toRadians((at - (heading ?: 0f) - 90f).toDouble())
                val distance = radius - 10.dp.toPx()
                val position = Offset(
                    x = centre.x + distance * cos(rotated).toFloat(),
                    y = centre.y + distance * sin(rotated).toFloat(),
                )
                val laid = measurer.measure(name, labelStyle)
                drawText(
                    textLayoutResult = laid,
                    topLeft = Offset(
                        x = position.x - laid.size.width / 2f,
                        y = position.y - laid.size.height / 2f,
                    ),
                )
            }
        }
    }
}
