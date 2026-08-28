package dev.yusr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.quran.SurahNames
import dev.yusr.data.quran.TafsirBlock
import dev.yusr.data.quran.TafsirBook
import dev.yusr.data.quran.TafsirText
import dev.yusr.ui.Hairline
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.t
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranQuoteStyle

/**
 * Al-Mīzān on one ayah — which means, as the book means it, on the passage that ayah is inside.
 *
 * Held down on a word of the mushaf, this is what opens. The header says which āyāt the passage
 * covers and which of them was asked about, because those are two different things and a reader
 * who taps 2:3 and is shown a commentary that opens on 2:1 should be told why.
 *
 * The whole passage is set, in the order the book sets it: the āyāt quoted at its head, then the
 * *bayān*, then the traditions, then whatever discussion the passage carries. Nothing is
 * abridged and nothing is reordered — the passages are long, some of them very long, and a
 * tafsīr that decided for the reader which half of al-Ṭabāṭabāʾī was worth keeping would not be
 * al-Mīzān any more.
 *
 * It is Arabic throughout, whatever the interface language is, and so is the direction it is set
 * in. The English around it is a frame; the book is the book.
 *
 * Back is the reader's own, as it is for the index and the reciters: this is a page of the
 * mushaf's screen rather than a screen of its own.
 */
@Composable
fun TafsirScreen(surah: Int, ayah: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val tafsir = remember { context.container.tafsir }

    val text by produceState<TafsirText?>(initialValue = null, surah, ayah) {
        value = tafsir.at(surah, ayah)
    }
    val book by produceState<TafsirBook?>(initialValue = null) { value = tafsir.book() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("‹ Mushaf"),
                style = MaterialTheme.typography.bodyMedium,
                color = Fainter,
                modifier = Modifier.noRippleClickable(onClick = onBack).padding(vertical = 4.dp),
            )
            Text(
                text = "${SurahNames.arabic(surah).orEmpty()} " +
                    "${SurahNames.arabicDigits(surah)}:${SurahNames.arabicDigits(ayah)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gold,
            )
        }

        val passage = text
        if (passage == null) {
            // A passage is a file read off the disk and parsed: a frame, sometimes two on the
            // longest of them. Nothing is said in the meantime, because a spinner in the middle
            // of a page of tafsīr is a thing that flashes.
            Box(modifier = Modifier.fillMaxSize())
            return@Column
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
            Text(
                text = book?.arabicTitle ?: t("Tafsīr"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = passage.passage.arabicReference,
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Hairline()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            ) {
                items(passage.blocks) { block -> TafsirBlockText(block) }
            }
        }

        Hairline()
        // Where the text came from, printed once at the foot. A commentary with no provenance is
        // one nobody can check, and the licence it is redistributed under asks for the line too.
        Text(
            text = book?.source.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** One block of the passage, set the way the book sets that kind of writing. */
@Composable
private fun TafsirBlockText(block: TafsirBlock) {
    // Read here rather than inside the builder below: the palette is a composable value and the
    // string builder is not a composable scope.
    val accent = Gold
    when (block.kind) {
        TafsirBlock.Kind.HEADING -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleMedium,
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        // The āyāt the passage is about, in the face the rest of the app keeps for revelation.
        TafsirBlock.Kind.AYAH -> Text(
            text = block.text,
            style = QuranQuoteStyle,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )

        TafsirBlock.Kind.PROSE -> Text(
            text = buildAnnotatedString {
                append(block.text)
                // "قوله تعالى" and the like: the book's own bold, kept as the book's emphasis
                // rather than turned into a heading.
                block.emphasis.forEach { run ->
                    addStyle(
                        SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                        run.first,
                        run.last + 1,
                    )
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        TafsirBlock.Kind.FOOTNOTE -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = Faint,
        )
    }
}
