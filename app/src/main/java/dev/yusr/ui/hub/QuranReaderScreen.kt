package dev.yusr.ui.hub

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yusr.container
import dev.yusr.data.quran.Ayah
import dev.yusr.data.quran.Basmala
import dev.yusr.data.quran.RecitationStore
import dev.yusr.data.quran.Reciter
import dev.yusr.data.quran.Reciters
import dev.yusr.data.quran.SurahNames
import dev.yusr.data.settings.AyahLanguage
import dev.yusr.domain.Hijri
import dev.yusr.ui.isArabic
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.ThinProgress
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The mushaf.
 *
 * Three pages behind one back gesture: the sūrah you are reading, the index of all hundred and
 * fourteen, and the list of reciters. They are one screen rather than three because the reader is
 * where you spend the time and the other two are things you pass through.
 *
 * Recitation plays from the phone, never streamed. A sūrah is fetched once, ayah by ayah, and
 * then belongs to you — which is the same bargain the rest of this app makes with the network,
 * and the only one that survives a masjid basement.
 */
@Composable
fun QuranReaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    // Deliberately the scope of this screen rather than of the index below it. Picking a sūrah
    // writes the bookmark and leaves the index in the same breath, and a write launched from the
    // index's own scope is cancelled as that page goes — which is a tap that appears to do
    // nothing. This scope outlives all three pages, so the write lands.
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(ReaderPage.READER) }
    BackHandler(enabled = page != ReaderPage.READER) { page = ReaderPage.READER }

    // Where the reader is, held above all three pages.
    //
    // The bookmark on disk is the record of this rather than the thing that drives it. Moving by
    // writing to DataStore and waiting to read the write back meant a page turn did not land on
    // the frame it was asked for: the sūrah you were leaving stayed on the screen — title, text
    // and all — until the store came back, which is the pause that made turning a page feel like
    // the app had stopped to think about it. The move happens here, at once; the write follows.
    val stored by store.bookmark.collectAsState(initial = null)
    var place by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // What the store still owes us: the last move made here, until it comes back around. A
    // bookmark set anywhere else — at the gate, most of the time — is news and is followed, but
    // an echo of a move we have already moved past would turn the page back under the reader.
    var awaiting by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(stored) {
        val current = stored ?: return@LaunchedEffect
        when (awaiting) {
            null -> place = current
            current -> awaiting = null
            else -> Unit
        }
    }

    fun goTo(surah: Int, ayah: Int) {
        place = surah to ayah
        awaiting = place
        scope.launch { store.setBookmark(surah, ayah) }
    }

    // The mushaf opens where it was left, and not before. Standing at al-Fātiḥa for the moment it
    // takes to read the bookmark and then turning to the real place would be a page turn the
    // reader never asked for — and now that turns are animated, a long one.
    val here = place
    if (here == null) {
        Box(modifier = Modifier.fillMaxSize().background(Backdrop))
        return
    }

    when (page) {
        ReaderPage.READER -> Reader(
            place = here,
            onGoTo = { surah, ayah -> goTo(surah, ayah) },
            onBack = onBack,
            onOpenIndex = { page = ReaderPage.INDEX },
            onOpenReciters = { page = ReaderPage.RECITERS },
        )
        ReaderPage.INDEX -> SurahIndex(
            current = here.first,
            onPick = { number ->
                goTo(number, 1)
                page = ReaderPage.READER
            },
            onBack = { page = ReaderPage.READER },
        )
        ReaderPage.RECITERS -> ReciterList(onBack = { page = ReaderPage.READER })
    }
}

private enum class ReaderPage { READER, INDEX, RECITERS }

@Composable
private fun Reader(
    place: Pair<Int, Int>,
    onGoTo: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenReciters: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val recitation = remember { context.container.recitation }

    val settings by store.settings.collectAsState(initial = null)
    val reciterId by store.reciterId.collectAsState(initial = null)
    val reciter = remember(reciterId) { Reciters.byId(reciterId) }

    // One leaf per sūrah, all hundred and fourteen of them, and the sūrah being read is whichever
    // leaf the pager is on. The page follows the thumb and settles where it is let go, the way a
    // book does; nothing waits for a gesture to finish before it starts to move.
    val pager = rememberPagerState(initialPage = place.first - 1) { SurahNames.COUNT }

    // Read off the pager rather than the bookmark, so the screen turns with the paper: the title
    // and the footer change over as the new leaf takes the middle of the screen, which is the
    // moment the page has turned.
    val surah = pager.currentPage + 1
    // Until the bookmark catches up with a page that has just been turned to, the new sūrah is at
    // its first ayah — which is where a turned page starts.
    val reciting = if (place.first == surah) place.second else 1

    // Downloading and playing are both about a sūrah rather than an ayah, so they live here and
    // the pages below only report what they are told.
    var download by remember(surah, reciter) { mutableStateOf<RecitationStore.Progress>(RecitationStore.Progress.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var playing by remember { mutableStateOf(false) }

    // The bookmark follows the paper, once it has come to rest. Writing it at the halfway point
    // where the title changes would leave a sūrah bookmarked because a drag passed over it.
    val current by rememberUpdatedState(place)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page + 1 != current.first) onGoTo(page + 1, 1)
        }
    }

    // And the paper follows anything that moves the reader without touching it — the chevrons at
    // the foot, a bookmark set at the gate. Animated rather than snapped, so that walking off the
    // end of a sūrah with the chevron turns the leaf in front of you instead of replacing it.
    // (The index is not one of these: picking from it leaves and re-enters the reader, which
    // opens at the sūrah picked, the way opening a book at a bookmark is not a page turn.)
    LaunchedEffect(place.first) {
        if (pager.currentPage != place.first - 1) pager.animateScrollToPage(place.first - 1)
    }

    // Recitation belongs to the sūrah it was started in. Turning the page stops it: the next
    // sūrah's audio is a separate download and may not be on the phone at all, and a player that
    // is silently playing nothing is worse than one that has plainly stopped.
    LaunchedEffect(surah) { playing = false }

    PlayCurrentAyah(
        reciter = reciter,
        recitation = recitation,
        surah = surah,
        ayah = reciting,
        playing = playing,
        onFinishedAyah = {
            // Runs on to the end of the sūrah, and stops rather than wrapping to the next one:
            // where to go after al-Kahf is a decision, not a default.
            if (reciting < SurahNames.ayahCount(surah)) {
                onGoTo(surah, reciting + 1)
            } else {
                playing = false
            }
        },
    )

    // Counting a partial download is one `stat` per ayah — nearly three hundred of them for
    // al-Baqara — so it is done off the main thread, and only once a download has settled rather
    // than on every ayah it fetches. Composed straight, it was disk I/O on the frame that turned
    // the page, and it showed.
    val settled = download !is RecitationStore.Progress.Running
    val downloaded by produceState(initialValue = 0, surah, reciter, settled) {
        val chosen = reciter
        value = if (chosen == null) 0 else withContext(Dispatchers.IO) {
            recitation.downloadedAyat(chosen, surah)
        }
    }
    val total = SurahNames.ayahCount(surah)

    val language = settings?.prayer?.ayahLanguage ?: AyahLanguage.BOTH

    HubPageChrome(
        // Named in whichever script the interface is in; the index below shows both, side by
        // side, whatever happens up here.
        title = (if (isArabic()) SurahNames.arabic(surah) else SurahNames.transliterated(surah))
            ?: t("Qur'an"),
        subtitle = SurahNames.subtitle(surah),
        onBack = onBack,
        trailing = {
            Text(
                text = t("All sūras"),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.noRippleClickable(onClick = onOpenIndex).padding(8.dp),
            )
        },
        footer = {
            ReaderFooter(
                reciter = reciter,
                playing = playing,
                complete = reciter != null && downloaded >= total && total > 0,
                downloaded = downloaded,
                total = total,
                download = download,
                onOpenReciters = onOpenReciters,
                onTogglePlay = { playing = !playing },
                // The chevrons walk the mushaf, not the sūrah: the ayah after the last of
                // al-Kahf is the first of Maryam. Stopping dead at the end of a sūrah is what
                // made the end of one a place you could not get out of.
                onStep = { step ->
                    val (toSurah, toAyah) = if (step > 0) {
                        SurahNames.next(surah, reciting)
                    } else {
                        SurahNames.previous(surah, reciting)
                    }
                    onGoTo(toSurah, toAyah)
                },
                onDownload = {
                    val chosen = reciter ?: return@ReaderFooter
                    if (downloadJob?.isActive == true) {
                        downloadJob?.cancel()
                        downloadJob = null
                        download = RecitationStore.Progress.Idle
                    } else {
                        downloadJob = scope.launch {
                            download = recitation.downloadSurah(chosen, surah) { download = it }
                        }
                    }
                },
            )
        },
    ) { modifier ->
        // Which way is forward is the layout's business rather than this screen's: on an Arabic
        // interface the pager is already mirrored, and so is the mushaf — the next sūrah comes in
        // from the left, which is the way the binding opens.
        HorizontalPager(
            state = pager,
            modifier = modifier,
            // The leaf either side is composed and its text fetched before it is ever dragged
            // into view. This is the whole of what makes the turn look like paper rather than a
            // load: by the time the edge of the next sūrah appears, it is already written.
            beyondViewportPageCount = 1,
            // A gutter between the leaves, so that mid-drag the two sūras read as two pages
            // rather than one column of text sliding over another.
            pageSpacing = 22.dp,
            key = { it },
        ) { index ->
            val pageSurah = index + 1
            SurahPage(
                surah = pageSurah,
                language = language,
                // Only the sūrah being read carries the bookmark; the leaves either side of it
                // are pages you have not arrived at yet.
                marked = if (pageSurah == surah) reciting else 0,
                onMark = { ayah -> onGoTo(pageSurah, ayah) },
            )
        }
    }
}

/**
 * One leaf of the mushaf: a sūrah, with its own text and its own place in it.
 *
 * The text is fetched by the page rather than by the reader around it, which is what lets the
 * pager have the next sūrah ready before the drag that asks for it — and what keeps the sūrah you
 * are leaving whole and on the screen while it slides off, instead of blanking as the number
 * under it changes.
 */
@Composable
private fun SurahPage(
    surah: Int,
    language: AyahLanguage,
    marked: Int,
    onMark: (Int) -> Unit,
) {
    val context = LocalContext.current
    val quran = remember { context.container.quran }

    // Null is "not read yet" and empty is "not on the phone" — the two look nothing alike to
    // whoever is holding it, and telling them apart is what keeps the download notice from
    // flashing up on a page that is only a moment from having its text.
    var ayat by remember(surah) { mutableStateOf<List<Ayah>?>(null) }
    LaunchedEffect(surah) { ayat = quran.surah(surah) }

    val text = ayat ?: return
    if (text.isEmpty()) {
        Text(
            text = t("The Qur'an has not been downloaded yet. Settings → Prayer times and salah ") +
                t("→ download the Qur'an fetches all 6,236 āyāt once, and then never again."),
            style = MaterialTheme.typography.bodyMedium,
            color = Dim,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 20.dp),
    ) {
        // The basmala heads every sūrah but al-Tawba, and is counted as an ayah in none of
        // them except al-Fātiḥa — where it is the first, and so already in the list below.
        // Everywhere else it is printed here, once; the stored text of ayah 1 no longer
        // carries it, so there is no chance of reading it twice.
        //
        // Where it does not belong, the room it would take is left empty rather than closed up.
        // The same line of the same text, in no colour at all, is the only way to be sure the
        // space is exactly the space — so the first ayah of al-Tawba begins where the first ayah
        // of every other sūrah begins, and turning between them does not shift the page under
        // the eye.
        item(key = "basmala") {
            val heads = Basmala.headingBelongsAbove(surah, ayah = 1)
            Text(
                text = Basmala.ARABIC,
                style = QuranStyle,
                color = if (heads) Faint else Color.Transparent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // Invisible is not absent: a sūrah with no basmala must not have one read out
                    // over it, so where the line is only holding the space it is taken out of the
                    // screen reader's hands entirely.
                    .then(if (heads) Modifier else Modifier.clearAndSetSemantics { })
                    .padding(bottom = 18.dp),
            )
        }

        items(text, key = { it.ayah }) { ayah ->
            AyahRow(
                ayah = ayah,
                language = language,
                marked = ayah.ayah == marked,
                onMark = { onMark(ayah.ayah) },
            )
        }
    }
}

/**
 * One ayah: the Arabic, the translation under it, and a gold bar down the side of the one you are
 * on. Tapping an ayah moves the bookmark there, which is also where recitation resumes from.
 */
@Composable
private fun AyahRow(
    ayah: Ayah,
    language: AyahLanguage,
    marked: Boolean,
    onMark: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .noRippleClickable(onClick = onMark)
            .padding(vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(if (marked) Gold else Color.Transparent),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (language != AyahLanguage.ENGLISH) {
                Text(
                    // The ayah number sits at the end of the line in Arabic-Indic digits, the way
                    // it does in a printed mushaf.
                    text = "${ayah.arabic} ${Hijri.arabicDigits(ayah.ayah)}",
                    style = QuranStyle,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val english = ayah.english
            if (language != AyahLanguage.ARABIC && english != null) {
                Text(
                    text = english,
                    style = MaterialTheme.typography.bodySmall,
                    color = Faint,
                )
            }
            if (marked) {
                Text(
                    text = t("Bookmarked"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Dim,
                )
            }
        }
    }
}

/** The reciter, the transport, and — when the sūrah is not on the phone yet — the download. */
@Composable
private fun ReaderFooter(
    reciter: Reciter?,
    playing: Boolean,
    complete: Boolean,
    downloaded: Int,
    total: Int,
    download: RecitationStore.Progress,
    onOpenReciters: () -> Unit,
    onTogglePlay: () -> Unit,
    onStep: (Int) -> Unit,
    onDownload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (download as? RecitationStore.Progress.Running)?.let {
            ThinProgress(fraction = it.fraction)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reciter?.let { t("Ḥafṣ · %s", it.name) } ?: t("choose a reciter"),
                style = MaterialTheme.typography.bodySmall,
                color = if (reciter == null) Gold else Dim,
                modifier = Modifier.noRippleClickable(onClick = onOpenReciters).weight(1f),
            )

            // The chevrons are always here. They move the bookmark, which is a thing you do while
            // reading; hanging them off a finished recitation download meant the mushaf could
            // only be walked by whoever had also downloaded the audio for it.
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Faint,
                    modifier = Modifier.noRippleClickable { onStep(-1) }.padding(6.dp),
                )
                if (complete) {
                    Text(
                        text = if (playing) t("Pause") else t("Play"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.noRippleClickable(onClick = onTogglePlay).padding(6.dp),
                    )
                } else if (reciter != null) {
                    Text(
                        text = when (download) {
                            is RecitationStore.Progress.Running ->
                                t("downloading %s/%s · stop", download.ayah, download.ayatTotal)
                            is RecitationStore.Progress.Failed -> t("failed · try again")
                            else -> if (downloaded > 0) {
                                t("resume · %s/%s", downloaded, total)
                            } else {
                                t("download")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (download is RecitationStore.Progress.Failed) Faint else Gold,
                        modifier = Modifier.noRippleClickable(onClick = onDownload).padding(6.dp),
                    )
                }
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Faint,
                    modifier = Modifier.noRippleClickable { onStep(1) }.padding(6.dp),
                )
            }
        }
    }
}

/**
 * Plays the current ayah from disk, and calls back when it finishes.
 *
 * The player is created and torn down with the ayah rather than kept and re-pointed: an ayah is a
 * few seconds long, a MediaPlayer takes microseconds to build, and one player per ayah cannot get
 * into the state where it is playing something other than what the screen says it is.
 */
@Composable
private fun PlayCurrentAyah(
    reciter: Reciter?,
    recitation: RecitationStore,
    surah: Int,
    ayah: Int,
    playing: Boolean,
    onFinishedAyah: () -> Unit,
) {
    DisposableEffect(reciter, surah, ayah, playing) {
        if (!playing || reciter == null) return@DisposableEffect onDispose { }

        val file = recitation.localAyah(reciter, surah, ayah)
        val created = file?.let {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(it.absolutePath)
                    setOnCompletionListener { onFinishedAyah() }
                    prepare()
                    start()
                }
            }.getOrNull()
        }

        onDispose {
            runCatching { created?.stop() }
            created?.release()
        }
    }
}

/** All hundred and fourteen, with the one being read marked. */
@Composable
private fun SurahIndex(current: Int, onPick: (Int) -> Unit, onBack: () -> Unit) {
    // A hundred and fourteen rows are laid out as they are scrolled to rather than all at once,
    // for the same reason the reader's āyāt are: the whole of it measured on the frame that opens
    // the index is a stall on the way in and another on the way back out.
    val numbers = remember { SurahNames.all() }
    HubPageListFrame(title = t("Sūras"), subtitle = "114", onBack = onBack) {
        items(numbers, key = { it }) { number ->
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .noRippleClickable { onPick(number) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = number.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Dim,
                        )
                        Text(
                            text = SurahNames.transliterated(number).orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (number == current) {
                                Gold
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            },
                        )
                    }
                    Text(
                        text = SurahNames.arabic(number).orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Faint,
                    )
                }
                Hairline()
            }
        }
    }
}

/**
 * Who is reciting, grouped by tradition with the user's own first.
 *
 * Each name is checked against the audio host before it can be chosen. The app has no way of
 * knowing from the inside whether a folder still exists on someone else's server, and the
 * alternative to asking is a download that dies two hundred āyāt into al-Baqara.
 */
@Composable
private fun ReciterList(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val recitation = remember { context.container.recitation }

    val settings by store.settings.collectAsState(initial = null)
    val chosen by store.reciterId.collectAsState(initial = null)
    val madhab = settings?.prayer?.effectiveMadhab

    val ordered = remember(madhab) { madhab?.let { Reciters.orderedFor(it) } ?: Reciters.ALL }

    // Checked once, in the background, as the screen opens. Null means "not yet asked".
    var reachable by remember { mutableStateOf(emptyMap<String, Boolean>()) }
    LaunchedEffect(ordered) {
        ordered.forEach { reciter ->
            val ok = recitation.isReachable(reciter)
            reachable = reachable + (reciter.id to ok)
        }
    }

    HubPageFrame(
        title = t("Reciter"),
        subtitle = t("Ḥafṣ ʿan ʿĀṣim · downloaded per sūra"),
        onBack = onBack,
        footer = {
            Text(
                text = t("Recitation is fetched once and then plays with no network at all. ") +
                    t("A whole sūra at 128 kbps is roughly a megabyte a minute."),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        },
    ) {
        var lastTradition: Reciter.Tradition? = null
        ordered.forEach { reciter ->
            if (reciter.tradition != lastTradition) {
                SectionLabel(
                    text = t(reciter.tradition.label),
                    modifier = Modifier.padding(top = if (lastTradition == null) 0.dp else 20.dp, bottom = 4.dp),
                )
                lastTradition = reciter.tradition
            }

            val available = reachable[reciter.id]
            ChoiceRow(
                title = reciter.name,
                subtitle = reciter.arabicName + " · " + when (available) {
                    null -> t("checking…")
                    true -> t("%s kbps", reciter.kbps)
                    false -> t("not reachable")
                },
                selected = reciter.id == chosen,
                // A reciter the host does not have cannot be chosen, because choosing them would
                // only produce a download that fails.
                onSelect = {
                    if (available != false) scope.launch { store.setReciterId(reciter.id) }
                },
            )
            Hairline()
        }
    }
}
