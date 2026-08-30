package dev.yusr.ui.theme

import androidx.compose.ui.graphics.Color
import dev.yusr.data.quran.Tajweed
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tajwīd palette, held to the distance the eye actually needs.
 *
 * A colour on this page is a few millimetres of one letter, met on its own and never beside the
 * colour it might be confused with. Hex values that look distinct in a list of eighteen are not
 * distinct there, which is how an earlier set of these ended up with nine pairs inside ΔE2000 10
 * of one another. So the palette is measured rather than eyeballed: every pair of colours a
 * reader has to tell apart is checked in CIE Lab, and every colour is checked against the paper
 * it is printed on.
 *
 * The one pair left out is grey against grey — the joining alif and the letter that is written
 * and not read. Neither is sounded, so confusing them costs nothing, and both have to stay
 * quieter than the text around them, which leaves them nowhere to go.
 */
class TajweedColoursTest {

    private val quiet = setOf(
        Tajweed.Rule.HAMZAT_WASL,
        Tajweed.Rule.LAM_SHAMSIYYAH,
        Tajweed.Rule.SILENT,
    )

    @Test
    fun `every rule has a colour in both sets`() {
        for (rule in Tajweed.Rule.entries) {
            assertTrue("$rule has no night colour", NightTajweed.containsKey(rule))
            assertTrue("$rule has no day colour", DayTajweed.containsKey(rule))
        }
    }

    @Test
    fun `no two colours on the night page are close enough to be guessed at`() {
        assertApart(NightTajweed, floor = 22.0)
    }

    @Test
    fun `no two colours on the day page are close enough to be guessed at`() {
        assertApart(DayTajweed, floor = 18.0)
    }

    @Test
    fun `every colour stands off the ground it is drawn on`() {
        for ((rule, colour) in NightTajweed) {
            val floor = if (rule in quiet) 3.0 else 4.5
            val ratio = contrast(colour, Night)
            assertTrue("night $rule sits at $ratio against the page", ratio >= floor)
        }
        for ((rule, colour) in DayTajweed) {
            val floor = if (rule in quiet) 2.0 else 4.5
            val ratio = contrast(colour, Day)
            assertTrue("day $rule sits at $ratio against the page", ratio >= floor)
        }
    }

    @Test
    fun `what is not read is quieter than what is`() {
        for (rule in quiet) {
            assertTrue(
                "night $rule shouts louder than a rule that is sounded",
                contrast(NightTajweed.getValue(rule), Night) < 6.5,
            )
            assertTrue(
                "day $rule shouts louder than a rule that is sounded",
                contrast(DayTajweed.getValue(rule), Day) < 4.5,
            )
        }
    }

    /**
     * Every pair of distinct colours in a set, less the two greys, is at least [floor] apart.
     *
     * Distinct: three rules can share one colour because a reader meets them as one thing — the
     * three ways of holding a ghunnah, say — and it is the colours that have to differ, not the
     * rules.
     */
    private fun assertApart(set: Map<Tajweed.Rule, Color>, floor: Double) {
        val loud = set.filterKeys { it !in quiet }.values.distinct()
        val hush = set.filterKeys { it in quiet }.values.distinct()
        val all = loud + hush
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                if (all[i] in hush && all[j] in hush) continue
                val d = deltaE(all[i], all[j])
                assertTrue(
                    "two colours sit ${"%.1f".format(d)} apart, which a reader cannot tell",
                    d >= floor,
                )
            }
        }
    }

    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** CIE Lab, D65, from linear sRGB. */
    private fun lab(c: Color): Triple<Double, Double, Double> {
        val r = channel(c.red)
        val g = channel(c.green)
        val b = channel(c.blue)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) =
            if (t > 216.0 / 24389.0) t.pow(1.0 / 3.0) else (841.0 / 108.0) * t + 4.0 / 29.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    /** ΔE2000, which is the distance the eye keeps rather than the distance the numbers do. */
    private fun deltaE(one: Color, two: Color): Double {
        val (l1, a1, b1) = lab(one)
        val (l2, a2, b2) = lab(two)
        val cBar = (hypot(a1, b1) + hypot(a2, b2)) / 2
        val g = 0.5 * (1 - sqrt(cBar.pow(7) / (cBar.pow(7) + 25.0.pow(7))))
        val ap1 = (1 + g) * a1
        val ap2 = (1 + g) * a2
        val cp1 = hypot(ap1, b1)
        val cp2 = hypot(ap2, b2)
        val hp1 = deg(atan2(b1, ap1))
        val hp2 = deg(atan2(b2, ap2))
        val dl = l2 - l1
        val dc = cp2 - cp1
        val dh = when {
            cp1 * cp2 == 0.0 -> 0.0
            abs(hp2 - hp1) <= 180 -> hp2 - hp1
            hp2 - hp1 > 180 -> hp2 - hp1 - 360
            else -> hp2 - hp1 + 360
        }
        val bigH = 2 * sqrt(cp1 * cp2) * sin(rad(dh) / 2)
        val lBar = (l1 + l2) / 2
        val cpBar = (cp1 + cp2) / 2
        val hBar = when {
            cp1 * cp2 == 0.0 -> hp1 + hp2
            abs(hp1 - hp2) <= 180 -> (hp1 + hp2) / 2
            hp1 + hp2 < 360 -> (hp1 + hp2 + 360) / 2
            else -> (hp1 + hp2 - 360) / 2
        }
        val t = 1 - 0.17 * cos(rad(hBar - 30)) + 0.24 * cos(rad(2 * hBar)) +
            0.32 * cos(rad(3 * hBar + 6)) - 0.20 * cos(rad(4 * hBar - 63))
        val turn = 30 * exp(-(((hBar - 275) / 25).pow(2)))
        val rc = 2 * sqrt(cpBar.pow(7) / (cpBar.pow(7) + 25.0.pow(7)))
        val sl = 1 + (0.015 * (lBar - 50).pow(2)) / sqrt(20 + (lBar - 50).pow(2))
        val sc = 1 + 0.045 * cpBar
        val sh = 1 + 0.015 * cpBar * t
        val rt = -sin(rad(2 * turn)) * rc
        return sqrt(
            (dl / sl).pow(2) + (dc / sc).pow(2) + (bigH / sh).pow(2) +
                rt * (dc / sc) * (bigH / sh),
        )
    }

    private fun deg(radians: Double): Double = ((radians * 180 / Math.PI) % 360 + 360) % 360

    private fun rad(degrees: Double): Double = degrees * Math.PI / 180
}
