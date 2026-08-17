package dev.yusr.data.quran

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled layout, read as the app reads it.
 *
 * This is the one asset in the app whose contents nobody can eyeball: a hundred kilobytes saying
 * where six hundred and four pages break their lines. So it is checked against the book — against
 * the totals the mushaf is known to have, and against the pages that every printed copy agrees
 * on. If the asset is ever rebuilt from a different edition, this is what notices.
 */
class MushafLayoutTest {

    private val layout: MushafLayout by lazy {
        // Read off the disk rather than through a Context: these run on the JVM, and the file is
        // the same file that is packed into the APK.
        MushafLayout.parse(File("src/main/assets/mushaf_layout.json").readText())
    }

    @Test
    fun `the book has six hundred and four pages`() {
        assertNotNull(layout.page(1))
        assertNotNull(layout.page(MushafLayout.PAGES))
        assertNull(layout.page(0))
        assertNull(layout.page(MushafLayout.PAGES + 1))
    }

    @Test
    fun `every page is fifteen lines but the two framed ones`() {
        (1..MushafLayout.PAGES).forEach { number ->
            val page = layout.page(number)!!
            val expected = if (number <= 2) 8 else 15
            assertEquals("page $number", expected, page.lines.size)
        }
    }

    @Test
    fun `there is a heading for every surah and a basmala for all but two`() {
        val lines = (1..MushafLayout.PAGES).flatMap { layout.page(it)!!.lines }
        // One band per sūrah. Al-Tawba has no basmala at all, and al-Fātiḥa's is an ayah of it
        // rather than a heading over it — hence a hundred and twelve.
        assertEquals(114, lines.count { it is MushafLayout.Line.Heading })
        assertEquals(112, lines.count { it is MushafLayout.Line.Basmala })
        assertEquals(8820, lines.count { it is MushafLayout.Line.Text })
    }

    @Test
    fun `the pages run on from one to the next without a gap`() {
        // A page starts where the one before it left off. If that ever fails, the reader either
        // repeats a word across a turn or drops one, which is the worst thing a mushaf can do.
        (1 until MushafLayout.PAGES).forEach { number ->
            val page = layout.page(number)!!
            val next = layout.page(number + 1)!!
            val last = page.lines.filterIsInstance<MushafLayout.Line.Text>().last().end
            val runsOn = last.surah < next.start.surah ||
                (last.surah == next.start.surah && last.ayah <= next.start.ayah)
            assertTrue("page $number ends at $last, page ${number + 1} starts at ${next.start}", runsOn)
        }
    }

    @Test
    fun `the pages every mushaf agrees on`() {
        assertEquals(1, layout.pageOf(1, 1))
        assertEquals(2, layout.pageOf(2, 1))
        // Āyat al-Kursī, and the sūrahs a ḥāfiẓ can place by page number.
        assertEquals(42, layout.pageOf(2, 255))
        assertEquals(293, layout.pageOf(18, 1))
        assertEquals(440, layout.pageOf(36, 1))
        assertEquals(562, layout.pageOf(67, 1))
        assertEquals(604, layout.pageOf(114, 1))
    }

    @Test
    fun `the juz open where they open`() {
        assertEquals(MushafLayout.Reference(2, 142), layout.startOfJuz(2))
        assertEquals(MushafLayout.Reference(2, 253), layout.startOfJuz(3))

        // Roughly twenty pages to the juz but not exactly — juz 7 opens on 121 where the pattern
        // would say 122 — so the pages are written out rather than worked out.
        val opens = listOf(
            1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
            201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
            402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
        )
        assertEquals(opens, (1..MushafLayout.JUZ).map { layout.pageOfJuz(it) })
    }

    @Test
    fun `the hizbs are the quarters they are made of`() {
        assertEquals(1, layout.hizbOf(1))
        assertEquals(MushafLayout.Reference(2, 75), layout.startOfHizb(2))

        // A page is named for the ḥizb it *opens* in, which is what somebody reading the top of
        // it is in. Twenty-two of the sixty ḥizbs begin partway down a page, so that page is
        // still the last quarter of the ḥizb before — and says so until the leaf is turned.
        (1..MushafLayout.HIZB).forEach { hizb ->
            val at = layout.hizbOf(layout.pageOfHizb(hizb))
            assertTrue("ḥizb $hizb reads as $at", at == hizb || at == hizb - 1)
        }

        // What must hold everywhere: the ḥizb never goes backwards as the pages turn, and the
        // book runs from the first to the sixtieth.
        val across = (1..MushafLayout.PAGES).map { layout.hizbOf(it) }
        assertEquals(1, across.first())
        assertEquals(MushafLayout.HIZB, across.last())
        assertTrue(across.zipWithNext().all { (before, after) -> before <= after })
        assertTrue((1..MushafLayout.PAGES).all { layout.quarterOf(it) in 0..3 })
    }

    @Test
    fun `the fifteen sajdas are where they are`() {
        assertTrue(layout.hasSajda(32, 15))
        assertTrue(layout.hasSajda(96, 19))
        assertTrue(layout.hasSajda(7, 206))
        assertFalse(layout.hasSajda(2, 255))
    }

    @Test
    fun `a rub opens two hundred and forty times`() {
        val marks = (1..SurahNames.COUNT).sumOf { surah ->
            (1..SurahNames.ayahCount(surah)).count { layout.rubOpensAt(surah, it) }
        }
        assertEquals(240, marks)
        // The first is the opening of the book itself.
        assertTrue(layout.rubOpensAt(1, 1))
    }

    @Test
    fun `the first ayah of a page is the ayah the page opens in`() {
        assertEquals(MushafLayout.Reference(1, 1), layout.firstAyahOn(1))
        assertEquals(MushafLayout.Reference(2, 1), layout.firstAyahOn(2))
        // Every page opens somewhere in the book, and on an ayah that exists.
        (1..MushafLayout.PAGES).forEach { number ->
            val first = layout.firstAyahOn(number)!!
            assertTrue("page $number", first.ayah in 1..SurahNames.ayahCount(first.surah))
        }
    }
}
