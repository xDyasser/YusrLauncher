package dev.yusr.ui.hub

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yusr.container
import dev.yusr.data.quran.Basmala
import dev.yusr.data.quran.MushafLayout
import dev.yusr.data.quran.MushafPage
import dev.yusr.data.quran.RecitationStore
import dev.yusr.data.quran.Reciter
import dev.yusr.data.quran.Reciters
import dev.yusr.data.quran.SurahNames
import dev.yusr.ui.isArabic
import dev.yusr.ui.reciterName
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.ThinProgress
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The mushaf.
 *
 * Six hundred and four pages, turning right to left, each holding what the printed page holds:
 * the same āyāt, broken across the same fifteen lines, ending on the same words. That is the whole
 * of the idea. Somebody who has read a page a hundred times knows its shape — where the sūrah
 * turns over, which line the sajda falls on — and a reader that reflows the text into a scroll
 * throws all of that away and hands back a search box in exchange.
 *
 * Three pages behind one back gesture: the leaf you are reading, the index — sūrahs, juz and
 * ḥizbs — and the list of reciters.
 *
 * Recitation plays from the phone, never streamed. A sūrah is fetched once, ayah by ayah, and
 * then belongs to you — which is the same bargain the rest of this app makes with the network,
 * and the only one that survives a masjid basement.
 */
@Composable
fun QuranReaderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    // Deliberately the scope of this screen rather than of the index below it. Picking a place
    // writes the bookmark and leaves the index in the same breath, and a write launched from the
    // index's own scope is cancelled as that page goes — which is a tap that appears to do
    // nothing. This scope outlives all three pages, so the write lands.
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf(ReaderPage.READER) }
    BackHandler(enabled = page != ReaderPage.READER) { page = ReaderPage.READER }

    // Where the reader is, held above all three pages, and still an ayah rather than a page.
    //
    // The book is paged now, but a bookmark is a place in the *text*: it is set at the gate, on
    // the home screen and by recitation, none of which know or care what a page is. The page
    // being read is worked out from it. Keeping it the other way round would mean the gate could
    // only say "page 293", and 293 is not a thing anybody was reading.
    //
    // The bookmark on disk is the record of this rather than the thing that drives it. Moving by
    // writing to DataStore and waiting to read the write back meant a page turn did not land on
    // the frame it was asked for: the page you were leaving stayed on the screen until the store
    // came back, which is the pause that made turning a page feel like the app had stopped to
    // think about it. The move happens here, at once; the write follows.
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
        ReaderPage.INDEX -> MushafIndex(
            current = here,
            onPick = { surah, ayah ->
                goTo(surah, ayah)
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
    val mushaf = remember { context.container.mushaf }

    val reciterId by store.reciterId.collectAsState(initial = null)
    val reciter = remember(reciterId) { Reciters.byId(reciterId) }

    // The layout is read off the disk once and then never again, so the reader waits for it here
    // rather than each leaf waiting for it separately.
    val layout by produceState<MushafLayout?>(initialValue = null) { value = mushaf.layout() }
    val plan = layout

    // Which leaf the bookmark falls on. Null until the layout is in, which is a frame or two.
    val open = remember(plan, place) { plan?.pageOf(place.first, place.second) }
    if (plan == null || open == null) {
        Box(modifier = Modifier.fillMaxSize().background(Backdrop))
        return
    }

    // One leaf per printed page, all six hundred and four of them.
    val pager = rememberPagerState(initialPage = open - 1) { MushafLayout.PAGES }
    val page = pager.currentPage + 1

    // The sūrah named at the head of the page, and the ayah recitation is at. Read off the pager
    // rather than the bookmark so the header changes over as the new leaf takes the screen, which
    // is the moment the page has turned.
    val surah = remember(plan, page) { plan.firstAyahOn(page)?.surah ?: place.first }

    // Downloading and playing are both about a sūrah rather than a page, so they live here and
    // the leaves below only report what they are told.
    var download by remember(surah, reciter) { mutableStateOf<RecitationStore.Progress>(RecitationStore.Progress.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var playing by remember { mutableStateOf(false) }

    // The bookmark follows the paper, once it has come to rest — but only when it has to. A page
    // turned to gets the bookmark moved to its first ayah; a page you are already reading an ayah
    // of is left alone, which is what keeps recitation from being dragged back to the top of the
    // page every time it turns one.
    val current by rememberUpdatedState(place)
    LaunchedEffect(pager, plan) {
        snapshotFlow { pager.settledPage }.collect { settled ->
            val number = settled + 1
            if (plan.pageOf(current.first, current.second) == number) return@collect
            val first = plan.firstAyahOn(number) ?: return@collect
            onGoTo(first.surah, first.ayah)
        }
    }

    // And the paper follows anything that moves the reader without touching it — a bookmark set
    // at the gate, an ayah tapped, recitation running off the bottom of the page. Animated rather
    // than snapped, so a page that turns itself turns rather than being swapped.
    LaunchedEffect(open) {
        if (pager.currentPage != open - 1) pager.animateScrollToPage(open - 1)
    }

    // Recitation belongs to the sūrah it was started in. Reaching a new one stops it: the next
    // sūrah's audio is a separate download and may not be on the phone at all, and a player that
    // is silently playing nothing is worse than one that has plainly stopped.
    LaunchedEffect(surah) { playing = false }

    PlayCurrentAyah(
        reciter = reciter,
        recitation = recitation,
        surah = place.first,
        ayah = place.second,
        playing = playing,
        onFinishedAyah = {
            // Runs on to the end of the sūrah, and stops rather than wrapping to the next one:
            // where to go after al-Kahf is a decision, not a default. Moving the bookmark is what
            // turns the page under it when the ayah it lands on is printed on the next one.
            if (place.second < SurahNames.ayahCount(place.first)) {
                onGoTo(place.first, place.second + 1)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        // The reader gets a thinner top than the rest of the hub: every line of chrome here is a
        // line of the Qur'an made smaller, because the page below is fitted to whatever room is
        // left over.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("‹ Devotions"),
                style = MaterialTheme.typography.bodyMedium,
                color = Fainter,
                modifier = Modifier.noRippleClickable(onClick = onBack).padding(vertical = 4.dp),
            )
            Text(
                text = t("Index"),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.noRippleClickable(onClick = onOpenIndex).padding(8.dp),
            )
        }

        // The mushaf itself, which opens the way a mushaf opens — from the right — whatever
        // language the interface is in. A book has a spine before it has a locale.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                // The leaf either side is composed and its text fetched before it is ever dragged
                // into view. This is the whole of what makes the turn look like paper rather than
                // a load: by the time the edge of the next page appears, it is already written.
                beyondViewportPageCount = 1,
                // A gutter between the leaves, so mid-drag the two read as two pages rather than
                // one column of text sliding over another.
                pageSpacing = 20.dp,
                key = { it },
            ) { index ->
                Leaf(
                    number = index + 1,
                    layout = plan,
                    // Only the leaf being read carries the mark; the pages either side of it are
                    // pages you have not arrived at yet.
                    marked = if (index + 1 == plan.pageOf(place.first, place.second)) place else null,
                    onMark = onGoTo,
                )
            }
        }

        Hairline()
        ReaderFooter(
            reciter = reciter,
            playing = playing,
            complete = reciter != null && downloaded >= total && total > 0,
            downloaded = downloaded,
            total = total,
            download = download,
            marked = place,
            onOpenReciters = onOpenReciters,
            onTogglePlay = { playing = !playing },
            // The chevrons turn the leaf. A swipe is the natural way to do it and the way it is
            // mostly done, but a page you can only reach by dragging is a page somebody holding
            // the phone one-handed, or reading it through TalkBack, cannot reach at all.
            onTurn = { step ->
                val to = (page - 1 + step).coerceIn(0, MushafLayout.PAGES - 1)
                scope.launch { pager.animateScrollToPage(to) }
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
    }
}

/**
 * One leaf of the mushaf: the printed page, with its heading, its fifteen lines and its number.
 *
 * The text is fetched by the leaf rather than by the reader around it, which is what lets the
 * pager have the next page ready before the drag that asks for it — and what keeps the page you
 * are leaving whole and on the screen while it slides off, instead of blanking as the number
 * under it changes.
 */
@Composable
private fun Leaf(
    number: Int,
    layout: MushafLayout,
    marked: Pair<Int, Int>?,
    onMark: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val mushaf = remember { context.container.mushaf }

    // Null is "not set yet" and a page with no lines is "not on the phone" — the two look nothing
    // alike to whoever is holding it, and telling them apart is what keeps the download notice
    // from flashing up on a page that is only a moment from having its text.
    var page by remember(number) { mutableStateOf<MushafPage?>(null) }
    var missing by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        val set = mushaf.page(number)
        page = set
        missing = set == null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val set = page
        if (set == null) {
            if (missing) NotDownloadedYet()
            return@Column
        }

        PageHeader(surah = set.surah, juz = layout.juzOf(number))
        Hairline()

        MushafLines(
            page = set,
            marked = marked,
            onMark = onMark,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp),
        )

        Hairline()
        PageFooter(number = number, layout = layout, marked = marked)
    }
}

/** The sūrah on one side and the juz on the other, the way the printed page heads itself. */
@Composable
private fun PageHeader(surah: Int, juz: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = SurahNames.arabic(surah).orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = Faint,
        )
        Text(
            text = t("Juzʾ %s", SurahNames.arabicDigits(juz)),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
        )
    }
}

/**
 * The page's number at the foot, in the digits the page is set in — and, when an ayah has been
 * tapped, which ayah that was.
 *
 * The reference goes here rather than on the page because the page is the page: a mushaf has a
 * number at the bottom and nothing else, and anything the reader wants to be told belongs in the
 * margin the app has added rather than in the one the printer left.
 */
@Composable
private fun PageFooter(number: Int, layout: MushafLayout, marked: Pair<Int, Int>?) {
    val quarter = layout.quarterOf(number)
    val hizb = layout.hizbOf(number)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = SurahNames.arabicDigits(number),
            style = MaterialTheme.typography.bodySmall,
            color = Faint,
        )
        // Where the reader is, when that is on this page. It sits between the page's number and
        // its ḥizb rather than in place of either, because both of those are things the printed
        // page says and neither should have to move over for a bookmark.
        if (marked != null) {
            Text(
                text = "${SurahNames.arabic(marked.first).orEmpty()} " +
                    "${SurahNames.arabicDigits(marked.first)}:${SurahNames.arabicDigits(marked.second)}",
                style = MaterialTheme.typography.bodySmall,
                color = Gold,
            )
        }
        Text(
            text = t("Ḥizb %s%s", SurahNames.arabicDigits(hizb), QUARTERS[quarter]),
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
        )
    }
}

/** Nothing, a quarter, a half, three quarters — how far into the ḥizb the page is. */
private val QUARTERS = listOf("", " ¼", " ½", " ¾")

/**
 * The fifteen lines, fitted to the leaf.
 *
 * A printed page does not scroll and neither does this one: the text is set at whatever size puts
 * every line of the page on the screen at once, height and width both, and that is the size it is
 * read at. Fitting to the height alone is not enough — the longest line of al-Baqara has to reach
 * both margins without running off one — so the size is the smaller of the two answers.
 *
 * Lines are justified by spacing the words out to the margins, which is how the room left over at
 * the end of a line is taken up in print. The last line of a sūrah, and every line of the two
 * framed pages at the front, is centred instead, because that is what the page does.
 */
@Composable
private fun MushafLines(
    page: MushafPage,
    marked: Pair<Int, Int>?,
    onMark: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val height = constraints.maxHeight
        val width = constraints.maxWidth

        // Worked out once per page and per shape of screen. Measuring fifteen lines is not free,
        // and it must not happen again on the frame that turns the page.
        val size = remember(page.number, height, width) {
            val perLine = height.toFloat() / page.lines.size
            // A line's box is taller than its letters: the rest is the room above and below that
            // keeps a page of Arabic from setting solid.
            val byHeight = with(density) { (perLine * LINE_FILL).toSp() }

            val widest = page.lines
                .filterIsInstance<MushafPage.Line.Text>()
                .maxOfOrNull { line ->
                    val text = line.words.joinToString(" ") { it.text }
                    measurer.measure(
                        text = AnnotatedString(text),
                        style = QuranStyle.copy(fontSize = MEASURE_AT),
                        maxLines = 1,
                        softWrap = false,
                    ).size.width
                }?.coerceAtLeast(1) ?: 1

            val byWidth = MEASURE_AT * (width.toFloat() / widest.toFloat())
            // Bounded at both ends against a leaf measured before it has any room: a size of
            // nothing draws an empty page, and an unbounded one draws a single enormous word.
            minOf(byHeight.value, byWidth.value).coerceIn(MIN_SIZE, MAX_SIZE).sp
        }

        val style = QuranStyle.copy(fontSize = size, lineHeight = size * LINE_SPACING)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            page.lines.forEach { line ->
                when (line) {
                    is MushafPage.Line.Heading -> SurahBand(surah = line.surah, style = style)
                    is MushafPage.Line.Basmala -> Text(
                        text = Basmala.ARABIC,
                        style = style,
                        color = Faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    is MushafPage.Line.Text -> TextLine(
                        line = line,
                        style = style,
                        marked = marked,
                        onMark = onMark,
                    )
                }
            }
        }
    }
}

/** The sūrah's name in its band across the page, which is how a mushaf announces one. */
@Composable
private fun SurahBand(surah: Int, style: TextStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Gold.copy(alpha = BAND_TINT))
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = t("Sūrat %s", SurahNames.arabic(surah).orEmpty()),
            style = style.copy(fontSize = style.fontSize * BAND_SIZE),
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One line of the text: the words, spaced out to both margins, each one belonging to an ayah.
 *
 * A word is its own tap target rather than the line being one, because an ayah is what somebody
 * means when they touch the page, and an ayah runs across the middle of lines.
 */
@Composable
private fun TextLine(
    line: MushafPage.Line.Text,
    style: TextStyle,
    marked: Pair<Int, Int>?,
    onMark: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.centred) {
            Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.words.forEach { word ->
            val here = marked?.first == word.surah && marked.second == word.ayah
            Text(
                text = word.text,
                style = style,
                color = when (word.kind) {
                    // The ayah numbers and the rubʿ marks are the printer's marks rather than the
                    // revelation, and are set back a shade so the words read as the words.
                    MushafPage.Word.Kind.TEXT -> MaterialTheme.colorScheme.primary
                    else -> Gold
                },
                modifier = Modifier
                    .background(if (here) Gold.copy(alpha = MARK_TINT) else Color.Transparent)
                    .noRippleClickable { onMark(word.surah, word.ayah) },
            )
        }
    }
}

/** Said once, on a page that has no text because the book is not on the phone yet. */
@Composable
private fun NotDownloadedYet() {
    Text(
        text = t("The Qur'an has not been downloaded yet. Settings → Prayer times and salah ") +
            t("→ download the Qur'an fetches all 6,236 āyāt once, and then never again."),
        style = MaterialTheme.typography.bodyMedium,
        color = Dim,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    )
}

/** How much of a line's box the letters themselves take up. */
private const val LINE_FILL = 0.58f

/** The room a line is given, as a multiple of its letters — Arabic needs a generous leading. */
private const val LINE_SPACING = 1.32f

/** The size lines are measured at before being scaled to fit; nothing is ever drawn at it. */
private val MEASURE_AT = 40.sp

/** The range a fitted line may end up in, in sp. Neither end is expected to be reached. */
private const val MIN_SIZE = 8f
private const val MAX_SIZE = 48f

/** The band behind a sūrah's name, and the wash behind a tapped ayah. */
private const val BAND_TINT = 0.10f
private const val BAND_SIZE = 0.62f
private const val MARK_TINT = 0.16f

/** The reciter, the transport, and — when the sūrah is not on the phone yet — the download. */
@Composable
private fun ReaderFooter(
    reciter: Reciter?,
    playing: Boolean,
    complete: Boolean,
    downloaded: Int,
    total: Int,
    download: RecitationStore.Progress,
    marked: Pair<Int, Int>,
    onOpenReciters: () -> Unit,
    onTogglePlay: () -> Unit,
    onTurn: (Int) -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (download as? RecitationStore.Progress.Running)?.let {
            ThinProgress(fraction = it.fraction)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reciter?.let { t("Ḥafṣ · %s", reciterName(it)) } ?: t("choose a reciter"),
                style = MaterialTheme.typography.bodySmall,
                color = if (reciter == null) Gold else Dim,
                modifier = Modifier.noRippleClickable(onClick = onOpenReciters).weight(1f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pointing the way the book runs: the chevron on the left goes forward, because
                // forward in a mushaf is leftward.
                Text(
                    text = "‹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Faint,
                    modifier = Modifier.noRippleClickable { onTurn(1) }.padding(6.dp),
                )
                if (complete) {
                    Text(
                        // Recitation starts at the ayah the bookmark is on and turns the pages as
                        // it goes, so it is worth saying which ayah that is before it starts.
                        text = if (playing) {
                            t("Pause")
                        } else {
                            t("Play %s", SurahNames.arabicDigits(marked.second))
                        },
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
                    modifier = Modifier.noRippleClickable { onTurn(-1) }.padding(6.dp),
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

/** Which of the three ways into the book the index is showing. */
private enum class IndexTab(val label: String) {
    SURAH("Sūras"),
    JUZ("Juzʾ"),
    HIZB("Ḥizb"),
}

/**
 * The three ways a mushaf is opened at a place: by sūrah, by juz, by ḥizb.
 *
 * All three land on an ayah rather than on a page, because that is what the bookmark is — and
 * because "the start of juz 15" is a thing somebody means, where "page 281" is a thing they only
 * arrive at.
 */
@Composable
private fun MushafIndex(current: Pair<Int, Int>, onPick: (Int, Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val mushaf = remember { context.container.mushaf }
    val layout by produceState<MushafLayout?>(initialValue = null) { value = mushaf.layout() }

    var tab by remember { mutableStateOf(IndexTab.SURAH) }
    val plan = layout

    HubPageListFrame(
        title = t("Index"),
        subtitle = t("114 sūras · 30 juzʾ · 60 ḥizb"),
        onBack = onBack,
        scrollKey = tab,
    ) {
        item(key = "tabs") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                IndexTab.entries.forEach { entry ->
                    Text(
                        text = t(entry.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry == tab) Gold else Faint,
                        modifier = Modifier
                            .noRippleClickable { tab = entry }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }

        when (tab) {
            IndexTab.SURAH -> items(SurahNames.all(), key = { "s$it" }) { number ->
                IndexRow(
                    number = number,
                    name = SurahNames.transliterated(number).orEmpty(),
                    arabic = SurahNames.arabic(number).orEmpty(),
                    // A sūrah is the one being read when the bookmark is inside it, which is not
                    // the same question as which page is open.
                    marked = number == current.first,
                    onPick = { onPick(number, 1) },
                )
            }

            IndexTab.JUZ -> items((1..MushafLayout.JUZ).toList(), key = { "j$it" }) { number ->
                val start = plan?.startOfJuz(number)
                IndexRow(
                    number = number,
                    name = t("Juzʾ %s", number),
                    arabic = start?.let { opening(it.surah, it.ayah) }.orEmpty(),
                    marked = plan != null && number == plan.juzOf(plan.pageOf(current.first, current.second)),
                    onPick = { start?.let { onPick(it.surah, it.ayah) } },
                )
            }

            IndexTab.HIZB -> items((1..MushafLayout.HIZB).toList(), key = { "h$it" }) { number ->
                val start = plan?.startOfHizb(number)
                IndexRow(
                    number = number,
                    name = t("Ḥizb %s", number),
                    arabic = start?.let { opening(it.surah, it.ayah) }.orEmpty(),
                    marked = plan != null && number == plan.hizbOf(plan.pageOf(current.first, current.second)),
                    onPick = { start?.let { onPick(it.surah, it.ayah) } },
                )
            }
        }
    }
}

/** "البقرة ٢:١٤٢" — where a juz or a ḥizb opens, said the way the page says it. */
private fun opening(surah: Int, ayah: Int): String =
    "${SurahNames.arabic(surah).orEmpty()} ${SurahNames.arabicDigits(surah)}:${SurahNames.arabicDigits(ayah)}"

/** One row of the index, whichever of the three lists it is in. */
@Composable
private fun IndexRow(
    number: Int,
    name: String,
    arabic: String,
    marked: Boolean,
    onPick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onPick)
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
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (marked) Gold else MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = arabic,
                style = MaterialTheme.typography.bodyLarge,
                color = Faint,
            )
        }
        Hairline()
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
            val state = when (available) {
                null -> t("checking…")
                true -> t("%s kbps", reciter.kbps)
                false -> t("not reachable")
            }
            ChoiceRow(
                title = reciterName(reciter),
                // Under an English name the Arabic one is worth having: it is the spelling the
                // recordings are catalogued under everywhere else. Under the Arabic name the
                // transliteration is nothing — the same name a second time, in letters the reader
                // did not ask for — so in Arabic the line is the bitrate alone.
                subtitle = if (isArabic()) state else reciter.arabicName + " · " + state,
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
