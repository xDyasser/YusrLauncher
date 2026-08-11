package dev.yusr.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.quran.SupplicationBlock
import dev.yusr.data.quran.SupplicationBook
import dev.yusr.data.quran.SupplicationEntry
import dev.yusr.data.quran.SupplicationSection
import dev.yusr.data.quran.SupplicationText
import dev.yusr.ui.isArabic
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranStyle

/**
 * The adhkār and the duʿāʾ, out of whichever book the madhab implies.
 *
 * The two books are the same screen because they are the same thing read at different moments —
 * what you say every morning and what you say when you need something. [dua] only changes the
 * word at the top.
 *
 * Mafātīḥ al-Jinān is here in full: three bābs, two appendices and 194 texts, some of them very
 * long. That is a book rather than a list, so it is read as one — the parts, then what is in a
 * part, then the text. Nothing longer than a screen is ever put in front of someone who was
 * looking for something else.
 *
 * The other book is always one tap away at the foot of the screen. Someone reading Ḥiṣn al-Muslim
 * should not have to change their madhab to look at Mafātīḥ, and the reverse; the setting decides
 * which is offered, never which is permitted.
 */
@Composable
fun SupplicationScreen(dua: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    val supplications = remember { context.container.supplications }

    val settings by store.settings.collectAsState(initial = null)
    val madhab = settings?.prayer?.effectiveMadhab

    // Null until the madhab is known, then the book that follows from it; switched by hand below.
    var showingOther by remember { mutableStateOf(false) }
    val book by produceState<SupplicationBook?>(null, madhab, showingOther) {
        val collection = madhab?.collection ?: return@produceState
        value = if (showingOther) supplications.other(collection) else supplications.book(collection)
    }

    // Where in the book we are: nowhere, in a part, or in a text.
    var openSection by remember(book?.id) { mutableStateOf<String?>(null) }
    var openEntry by remember(book?.id) { mutableStateOf<String?>(null) }
    var showTranslation by remember { mutableStateOf(false) }

    // Back walks out of the book the way you walked in, and only leaves once you are at its front.
    BackHandler(enabled = openEntry != null) { openEntry = null }
    BackHandler(enabled = openEntry == null && openSection != null) { openSection = null }

    val current = book
    val section = current?.chapters
        ?.flatMap { it.sections }
        ?.firstOrNull { it.id == openSection }
    val entryId = openEntry

    val text by produceState<SupplicationText?>(null, current?.id, entryId) {
        val bookId = current?.id ?: return@produceState
        val id = entryId ?: return@produceState
        value = supplications.text(bookId, id)
    }

    HubPageFrame(
        title = when {
            entryId != null -> text?.let { it.title.ifBlank { it.english.orEmpty() } } ?: "…"
            section != null -> t(section.title)
            dua -> t("Duʿāʾ")
            else -> t("Adhkār")
        },
        subtitle = when {
            entryId != null -> current?.let(::bookTitle)
            section != null -> t("%s in this part", section.items.size)
            else -> current?.let { "${bookTitle(it)} · ${t(it.attribution)}" }
        },
        scrollKey = entryId ?: openSection ?: "root",
        backLabel = when {
            entryId != null -> t("‹ %s", section?.title?.let(::t) ?: t("back"))
            openSection != null -> t("‹ %s", current?.let(::bookTitle) ?: t("back"))
            else -> t("‹ Hub")
        },
        onBack = {
            when {
                entryId != null -> openEntry = null
                openSection != null -> openSection = null
                else -> onBack()
            }
        },
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Only offered where there is something to show. Mafātīḥ is Arabic and nothing
                // else, so this is Ḥiṣn's English rendering and nowhere else.
                if (entryId != null && text?.hasTranslation == true) {
                    Text(
                        text = if (showTranslation) t("hide the translation") else t("show the translation"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold,
                        modifier = Modifier
                            .noRippleClickable { showTranslation = !showTranslation }
                            .padding(vertical = 2.dp),
                    )
                } else if (entryId == null && openSection == null) {
                    Text(
                        text = if (showingOther) t("back to your school's book") else t("read the other book"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold,
                        modifier = Modifier
                            .noRippleClickable {
                                showingOther = !showingOther
                                openSection = null
                                openEntry = null
                            }
                            .padding(vertical = 2.dp),
                    )
                }
            }
        },
    ) {
        if (current == null) {
            Text(
                text = t("Set a madhab to choose a collection."),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            return@HubPageFrame
        }

        when {
            entryId != null -> SupplicationBody(text, showTranslation)
            section != null -> EntryList(section) { openEntry = it }
            else -> ContentsList(current) { openSection = it }
        }
    }
}

/** The book named in whichever script the interface is in. Both are on it already. */
private fun bookTitle(book: SupplicationBook): String =
    if (isArabic()) book.arabicTitle.ifBlank { book.title } else book.title

/** The book's parts, under the bāb each belongs to. */
@Composable
private fun ContentsList(book: SupplicationBook, onOpen: (String) -> Unit) {
    book.source?.let {
        // What came out of the edition and what did not. Said once, at the front, where someone
        // deciding whether to trust the text will look for it.
        Text(
            text = t(it),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }

    book.chapters.forEach { chapter ->
        if (chapter.title.isNotBlank()) {
            SectionLabel(chapter.title, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp))
        }
        chapter.sections.forEach { section ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable { onOpen(section.id) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = t(section.title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    section.subtitle?.let {
                        Text(text = t(it), style = MaterialTheme.typography.bodySmall, color = Dim)
                    }
                }
                Text(
                    text = section.items.size.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Fainter,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Hairline()
        }
    }

    Text(
        text = t("%s texts", book.count),
        style = MaterialTheme.typography.labelSmall,
        color = Fainter,
        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
    )
}

/** What is in one part, with a mark of how long each is. */
@Composable
private fun EntryList(section: SupplicationSection, onOpen: (String) -> Unit) {
    section.items.forEach { entry ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable { onOpen(entry.id) }
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                // The heading the book prints is the name; a settled English one, where the text
                // has it, goes underneath as a gloss. That way the list reads as the book's
                // contents in one script rather than half in each.
                entry.english?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = Dim)
                }
            }
            Text(
                text = lengthOf(entry),
                style = MaterialTheme.typography.labelSmall,
                color = Fainter,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Hairline()
    }
}

/**
 * One text, block by block.
 *
 * The Arabic is what is here to be read, so it is the only thing set at reading size. Everything
 * around it — when to say this, who narrated it, what it means — is set small and grey, because a
 * page where the commentary is as loud as the supplication is a page you read instead of pray.
 */
@Composable
private fun SupplicationBody(text: SupplicationText?, showTranslation: Boolean) {
    if (text == null) {
        Text(text = "…", style = MaterialTheme.typography.bodyMedium, color = Dim)
        return
    }

    // The English name, under the book's own at the top of the frame.
    if (text.english != null && text.title.isNotBlank()) {
        Text(
            text = text.english,
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }

    text.blocks.forEach { block ->
        when (block.kind) {
            SupplicationBlock.Kind.ARABIC -> Text(
                text = block.text,
                style = QuranStyle,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
            )
            // Ḥiṣn's are English and Mafātīḥ's are al-Qummī's own Arabic, so which way this is
            // set depends on the note rather than on the app's language: a right-to-left note
            // laid out to the left is unreadable however it is worded.
            SupplicationBlock.Kind.NOTE -> Text(
                text = block.text,
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                textAlign = if (isArabicText(block.text)) TextAlign.Right else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            SupplicationBlock.Kind.TRANSLATION -> if (showTranslation) {
                Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Faint,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }

    Hairline(modifier = Modifier.padding(top = 22.dp))
    Text(
        text = t("end"),
        style = MaterialTheme.typography.labelSmall,
        color = Fainter,
        modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
    )
}

/** Whether a note is in the book's language or the interface's. The first letter settles it. */
private fun isArabicText(text: String): Boolean =
    text.firstOrNull { it.isLetter() }?.let { it in '؀'..'ۿ' } == true

/**
 * Roughly how long the text is, in the only unit that matters here: whether you have time for it
 * before the prayer you are about to be called to.
 */
private fun lengthOf(entry: SupplicationEntry): String = when {
    entry.arabicLength >= 20_000 -> t("very long")
    entry.arabicLength >= 6_000 -> t("long")
    entry.arabicLength >= 1_500 -> t("medium")
    else -> t("short")
}
