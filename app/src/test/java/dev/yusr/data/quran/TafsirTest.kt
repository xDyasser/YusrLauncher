package dev.yusr.data.quran

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Al-Mīzān as it is committed, read the way the app reads it.
 *
 * Twenty-five megabytes of Arabic in 594 files is not something anybody is going to eyeball, so
 * what is checked here is what a reader would notice if it were wrong: that every ayah of the
 * book has a passage, that the passages are the book's own divisions rather than a mechanical
 * slicing, and that the Markdown they are stored in comes back out as headings, quoted āyāt and
 * prose rather than as asterisks and backticks on the screen.
 */
class TafsirTest {

    private val assets = File("src/main/assets/almizan")

    private val book: TafsirBook by lazy {
        Tafsir.parseIndex(File(assets, "index.json").readText())
    }

    @Test
    fun `the book is al-Mizan and says whose it is`() {
        assertEquals("الميزان في تفسير القرآن", book.arabicTitle)
        assertTrue(book.attribution.contains("Ṭabāṭabāʾī"))
        // The source is redistributed under a licence that asks for attribution, and the app
        // prints it. If the index ever loses it, the screen loses it too.
        assertNotNull(book.source)
    }

    @Test
    fun `every ayah of the Qur'an is inside a passage`() {
        var found = 0
        (1..114).forEach { surah ->
            (1..SurahNames.ayahCount(surah)).forEach { ayah ->
                assertNotNull(
                    "$surah:$ayah has no tafsīr",
                    book.passages.firstOrNull { it.covers(surah, ayah) },
                )
                found++
            }
        }
        assertEquals(6236, found)
    }

    @Test
    fun `the passages tile the book without gap or overlap`() {
        assertEquals(594, book.passages.size)
        var previous: TafsirPassage? = null
        book.passages.forEach { passage ->
            assertTrue("${passage.id} runs backwards", passage.from <= passage.to)
            val last = previous
            if (last != null) {
                val expected = if (last.surah == passage.surah) {
                    last.surah to last.to + 1
                } else {
                    last.surah + 1 to 1
                }
                assertEquals(
                    "${passage.id} does not follow ${last.id}",
                    expected,
                    passage.surah to passage.from,
                )
            }
            previous = passage
        }
        assertEquals(114 to 6, previous!!.let { it.surah to it.to })
    }

    @Test
    fun `every passage has a file and every file has a passage`() {
        val files = assets.list()!!.filter { it.endsWith(".md") }.toSet()
        assertEquals(book.passages.size, files.size)
        book.passages.forEach { passage ->
            assertTrue("${passage.id} has no text", files.contains("${passage.id}.md"))
        }
    }

    @Test
    fun `every passage carries the bayan al-Mizan is written in`() {
        book.passages.forEach { passage ->
            assertTrue(
                "${passage.id} (${passage.arabicReference}) has no bayān",
                passage.headings.any { it.contains("بيان") },
            )
        }
    }

    @Test
    fun `al-Fatiha opens on the passage the book opens on`() {
        val passage = book.passages.first { it.covers(1, 1) }
        // Al-Mīzān takes al-Fātiḥa in two: the first five āyāt, then the last two.
        assertEquals(1, passage.surah)
        assertEquals(1, passage.from)
        assertEquals(5, passage.to)
        assertEquals("الفاتحة ١:١-٥", passage.arabicReference)
        // And the ayah after the passage ends is in the next one, not this one.
        assertTrue(book.passages.first { it.covers(1, 6) }.id != passage.id)
    }

    @Test
    fun `a passage reads as headings, ayat and prose`() {
        val passage = book.passages.first { it.covers(1, 1) }
        val blocks = Tafsir.parse(File(assets, "${passage.id}.md").readText())

        // The five āyāt of the passage, quoted at its head as the book prints them.
        assertEquals(5, blocks.count { it.kind == TafsirBlock.Kind.AYAH })
        assertTrue(blocks.any { it.kind == TafsirBlock.Kind.HEADING && it.text.contains("بيان") })
        assertTrue(blocks.count { it.kind == TafsirBlock.Kind.PROSE } > 20)

        // Nothing of the markup is left in the text itself.
        blocks.forEach { block ->
            assertTrue("markup left in ${block.text.take(40)}", !block.text.contains("```"))
            assertTrue("markup left in ${block.text.take(40)}", !block.text.contains("**"))
            assertTrue("a heading kept its hashes", !block.text.startsWith("#"))
            assertTrue("an empty block", block.text.isNotBlank())
        }

        // The bold the edition sets on "قوله تعالى" survives as a position rather than markup.
        val emphasised = blocks.first { it.emphasis.isNotEmpty() }
        val run = emphasised.emphasis.first()
        assertTrue(emphasised.text.substring(run).contains("قوله"))
    }

    @Test
    fun `a quoted ayah comes back as one line however the file wraps it`() {
        val blocks = Tafsir.parse(
            """
            ## ( بيان )

            ```arabic
            ذَٰلِكَ ٱلْكِتَـٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى
                        لِّلْمُتَّقِينَ (٢)
            ```

            **قوله تعالى :** كلام في المتقين
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertEquals(TafsirBlock.Kind.HEADING, blocks[0].kind)
        assertEquals("( بيان )", blocks[0].text)
        assertEquals(TafsirBlock.Kind.AYAH, blocks[1].kind)
        assertTrue("the wrap is still in it", !blocks[1].text.contains("\n"))
        assertTrue(blocks[1].text.contains("هُدًى لِّلْمُتَّقِينَ (٢)"))
        assertEquals(TafsirBlock.Kind.PROSE, blocks[2].kind)
        assertEquals("قوله تعالى : كلام في المتقين", blocks[2].text)
        assertEquals(listOf(0 until "قوله تعالى :".length), blocks[2].emphasis)
    }

    @Test
    fun `a line the edition left half-bold is set as it stands`() {
        val blocks = Tafsir.parse("و **قوله تعالى")
        assertEquals(1, blocks.size)
        assertEquals("و **قوله تعالى", blocks.first().text)
        assertEquals(emptyList<IntRange>(), blocks.first().emphasis)
    }
}
