package dev.yusr.ui.hub

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
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
import dev.yusr.data.quran.Tajweed
import dev.yusr.ui.isArabic
import dev.yusr.ui.reciterName
import dev.yusr.ui.t
import dev.yusr.ui.Hairline
import dev.yusr.ui.SectionLabel
import dev.yusr.ui.ThinProgress
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.theme.Amiri
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.QuranStyle
import dev.yusr.ui.theme.TajweedColours
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
 * Four pages behind one back gesture: the leaf you are reading, the index — sūrahs, juz and
 * ḥizbs — the list of reciters, and al-Mīzān on whichever ayah was held down.
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

    // The ayah the tafsīr was asked for, which outlives the page it is shown on: turning back to
    // the mushaf and holding the same word again should not have to fetch the passage twice.
    var explaining by remember { mutableStateOf<Pair<Int, Int>?>(null) }

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
            onOpenLegend = { page = ReaderPage.LEGEND },
            onExplain = { surah, ayah ->
                explaining = surah to ayah
                page = ReaderPage.TAFSIR
            },
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
        ReaderPage.LEGEND -> TajweedLegendScreen(onBack = { page = ReaderPage.READER })
        ReaderPage.TAFSIR -> {
            val (surah, ayah) = explaining ?: here
            TafsirScreen(
                surah = surah,
                ayah = ayah,
                onBack = { page = ReaderPage.READER },
            )
        }
    }
}

private enum class ReaderPage { READER, INDEX, RECITERS, TAFSIR, LEGEND }

@Composable
private fun Reader(
    place: Pair<Int, Int>,
    onGoTo: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenReciters: () -> Unit,
    onOpenLegend: () -> Unit,
    onExplain: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val recitation = remember { context.container.recitation }
    val mushaf = remember { context.container.mushaf }

    val reciterId by store.reciterId.collectAsState(initial = null)
    val reciter = remember(reciterId) { Reciters.byId(reciterId) }

    // Off unless asked for, and asked for in the settings rather than here: a mushaf that
    // repainted itself the first time somebody opened it would be answering a question nobody
    // put to it.
    val settings by store.settings.collectAsState(initial = null)
    val tajweed = settings?.tajweedColours == true


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
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        // The reader gets a thinner top than the rest of the hub: every line of chrome here is a
        // line of the Qur'an made smaller, because the page below is fitted to whatever room is
        // left over.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CHROME),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("‹ Devotions"),
                style = MaterialTheme.typography.bodyMedium,
                color = Fainter,
                modifier = Modifier.noRippleClickable(onClick = onBack).padding(vertical = 4.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Only where there is something to explain. With the colours off the page is
                // black on cream and a key to it would be a key to nothing.
                if (tajweed) {
                    Text(
                        text = "ℹ️",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .noRippleClickable(onClick = onOpenLegend)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = t("Index"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.noRippleClickable(onClick = onOpenIndex).padding(8.dp),
                )
            }
        }

        // The mushaf itself, which opens the way a mushaf opens — from the right — whatever
        // language the interface is in. A book has a spine before it has a locale.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = LEAF),
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
                    tajweed = tajweed,
                    onExplain = onExplain,
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = CHROME)) {
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
    tajweed: Boolean,
    onExplain: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val mushaf = remember { context.container.mushaf }

    // Null is "not set yet" and a page with no lines is "not on the phone" — the two look nothing
    // alike to whoever is holding it, and telling them apart is what keeps the download notice
    // from flashing up on a page that is only a moment from having its text.
    var page by remember(number, tajweed) { mutableStateOf<MushafPage?>(null) }
    var missing by remember(number, tajweed) { mutableStateOf(false) }
    LaunchedEffect(number, tajweed) {
        val set = mushaf.page(number, tajweed)
        page = set
        missing = set == null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val set = page
        if (set == null) {
            if (missing) NotDownloadedYet()
            return@Column
        }

        // The leaf is given more of the screen than the chrome around it, because every dp of
        // it is a line of the Qur'an set larger. What the page has of its own — its heading, its
        // rules, its number — is set back in by the difference, and stays in line with the app.
        Column(modifier = Modifier.padding(horizontal = CHROME - LEAF)) {
            PageHeader(surah = set.surah, juz = layout.juzOf(number))
            Hairline()
        }

        MushafLines(
            page = set,
            marked = marked,
            onMark = onMark,
            onExplain = onExplain,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp),
        )

        Column(modifier = Modifier.padding(horizontal = CHROME - LEAF)) {
            Hairline()
            PageFooter(number = number, layout = layout, marked = marked)
        }
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
 * both margins without running off one — and left to itself the width answers first on most pages
 * and answers small, so the letters are drawn a little narrower until the page's longest line
 * fits and the height is what decides. [Setting] is where that arithmetic lives, and why the
 * words are set tighter than the face would space them and the letters narrower than it draws
 * them.
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
    onExplain: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val height = constraints.maxHeight
        val width = constraints.maxWidth

        // Worked out once per page and per shape of screen. Measuring fifteen lines is not free,
        // and it must not happen again on the frame that turns the page.
        val style = remember(page.number, height, width, density) {
            val at = with(density) { MEASURE_AT.toPx() }
            // Every line is measured with the face's own word spaces in it and then set tight,
            // the way it will be drawn, so the page is fitted to the width it actually needs.
            val trim = at * Setting.TRIM
            val widest = page.lines
                .filterIsInstance<MushafPage.Line.Text>()
                .maxOfOrNull { line ->
                    val text = line.words.joinToString(" ") { it.text }
                    val measured = measurer.measure(
                        text = AnnotatedString(text),
                        style = QuranStyle.copy(fontSize = MEASURE_AT),
                        maxLines = 1,
                        softWrap = false,
                    ).size.width
                    measured - trim * (line.words.size - 1)
                }?.coerceAtLeast(1f) ?: 1f

            val perLine = height.toFloat() / page.lines.size
            // How narrow the letters have to be drawn for the longest line to fit at the size the
            // height allows, and then the size itself, which is that one unless the letters ran
            // out of room to give first.
            val squeeze = Setting.squeeze(
                perLine = perLine,
                widest = widest,
                width = width.toFloat(),
                at = at,
            )
            // Bounded at both ends against a leaf measured before it has any room: a size of
            // nothing draws an empty page, and an unbounded one draws a single enormous word.
            val fitted = Setting.size(
                perLine = perLine,
                widest = widest,
                width = width.toFloat(),
                at = at,
                squeeze = squeeze,
            )
            val size = with(density) { fitted.toSp() }.value.coerceIn(MIN_SIZE, MAX_SIZE).sp
            QuranStyle.copy(
                fontSize = size,
                lineHeight = size * Setting.LINE_SPACING,
                textGeometricTransform = TextGeometricTransform(scaleX = squeeze),
            )
        }

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
                        width = width,
                        measurer = measurer,
                        marked = marked,
                        onMark = onMark,
                        onExplain = onExplain,
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
            // Not revelation, and not in the face kept for it — that one has no Latin in it at
            // all, and the band reads "Sūrat al-Baqara" when the interface is in English.
            // Drawn at its own width: the condensing is what buys the page's letters their size,
            // and a heading of three words has no line to fit.
            style = style.copy(
                fontFamily = Amiri,
                fontSize = style.fontSize * BAND_SIZE,
                textGeometricTransform = null,
            ),
            color = Gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * One line of the text: the words, spaced out to both margins, each one belonging to an ayah.
 *
 * The line is set as one run rather than as a row of words. A word set on its own is measured to
 * its own advance width and drawn clipped to it, and in this face — where a final letter sweeps
 * back under the word before it and the marks stand well clear of the letters they belong to —
 * that shaves the ends off words and the tops off ḥarakāt. Set as one run the shaping is the
 * font's own, the same as the printed page.
 *
 * Which leaves justification, the reason for the row of words in the first place: the words are
 * set tight — a hair between them rather than the space the face would give them, which is what
 * a printed mushaf does and what leaves the letters the room to be large — and the width that
 * comes back is shared out between those spaces again, equally. That is what the printer does
 * with a line of Arabic.
 *
 * The colours of tajwīd are painted over the run rather than put on the letters, for the reason
 * [Bands] gives at length: a colour on a letter cuts the line at that letter, and a cut line is
 * reshaped in pieces, which costs the letter its ḥaraka.
 *
 * Tapped, it marks the ayah the touch fell in; held, it opens al-Mīzān on the passage that ayah
 * is inside. Which ayah that is comes from the text layout rather than from a tap target of its
 * own, since a line is one view now.
 */
@Composable
private fun TextLine(
    line: MushafPage.Line.Text,
    style: TextStyle,
    width: Int,
    measurer: TextMeasurer,
    marked: Pair<Int, Int>?,
    onMark: (Int, Int) -> Unit,
    onExplain: (Int, Int) -> Unit,
) {
    val colours = TajweedColours
    val ink = MaterialTheme.colorScheme.primary
    // The ayah numbers and the rubʿ marks are the printer's marks rather than the revelation, and
    // are set back a shade so the words read as the words.
    val printersMark = Gold
    val wash = Gold.copy(alpha = MARK_TINT)
    val density = LocalDensity.current

    val set = remember(line, style, width, colours, printersMark, ink, density) {
        setLine(line, style, width, measurer, colours, printersMark, ink, density)
    }
    // The mark goes on last and on its own. It moves as the reader taps about the page, and the
    // line under it — measured twice, coloured and justified — should not have to be worked out
    // again to put a wash behind six words of it.
    //
    // Painted rather than set as a background on the words, and for the same reason the colours
    // of tajwīd are painted: the platform fills a background with the very paint it is about to
    // draw the letters with, shader and all, so a background asked for in gold comes out in
    // whatever the gradient is passing through — which on a coloured page is the ink itself, a
    // grey slab over the ayah you are trying to read.
    val stripe = remember(set, marked) { set.stripe(marked) }

    // Keyed on the line rather than on the mark: the mark moves as the reader taps about the
    // page and moves no letter on it, and a layout thrown away as it moves is a tap unanswered.
    var layout by remember(set) { mutableStateOf<TextLayoutResult?>(null) }
    val ayahAt: (Offset) -> Pair<Int, Int>? = { at ->
        layout?.let { result ->
            val offset = result.getOffsetForPosition(at)
            // The last word beginning at or before the touch: which is the word it landed in, or
            // the one it belongs to if it landed in the space after it.
            set.words.lastOrNull { it.at <= offset }?.let { it.surah to it.ayah }
        }
    }

    Text(
        text = set.text,
        style = style,
        color = ink,
        maxLines = 1,
        softWrap = false,
        // Nothing on this line is ever cut: the size it is set at was fitted to the page, and a
        // letter that reaches past its own width is this face drawing Arabic rather than an
        // overflow.
        overflow = TextOverflow.Visible,
        textAlign = if (line.centred) TextAlign.Center else TextAlign.Start,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val here = stripe ?: return@drawBehind
                drawRect(
                    color = wash,
                    topLeft = Offset(here.start, 0f),
                    size = Size(here.endInclusive - here.start, size.height),
                )
            }
            .pointerInput(set) {
                detectTapGestures(
                    onLongPress = { at -> ayahAt(at)?.let { onExplain(it.first, it.second) } },
                    onTap = { at -> ayahAt(at)?.let { onMark(it.first, it.second) } },
                )
            },
    )
}

/** A line ready to draw, and where each word of which ayah begins — in it, and on the page. */
private data class SetLine(val text: AnnotatedString, val words: List<PlacedWord>) {

    /**
     * How far across the line the ayah at [at] runs, from its first letter to its last, spaces
     * and all — or nothing, if none of it is on this line.
     */
    fun stripe(at: Pair<Int, Int>?): ClosedFloatingPointRange<Float>? {
        if (at == null) return null
        val here = words.filter { it.surah == at.first && it.ayah == at.second && it.to > it.from }
        if (here.isEmpty()) return null
        return here.minOf { it.from }..here.maxOf { it.to }
    }
}

/**
 * One word of the line: where it starts in the text, how long it is, the ayah it belongs to, and
 * where its letters stand on the line once the line has been set.
 */
private data class PlacedWord(
    val at: Int,
    val length: Int,
    val surah: Int,
    val ayah: Int,
    val from: Float,
    val to: Float,
)

/**
 * The line, coloured and justified.
 *
 * Three passes over it, and each one needs the one before. The words are strung together and
 * measured, which says what the line has left over once it is set tight; the line is justified
 * and measured again, which says where on it every letter has ended up; and the colours — and
 * the places the wash behind a marked ayah is drawn from — are laid across those measurements.
 * The second measurement is the price of colouring a line without cutting it, and it is paid
 * once per line rather than once per frame: everything here is remembered against the line, its
 * size and the width of the page.
 */
private fun setLine(
    line: MushafPage.Line.Text,
    style: TextStyle,
    width: Int,
    measurer: TextMeasurer,
    colours: Map<Tajweed.Rule, Color>,
    printersMark: Color,
    ink: Color,
    density: Density,
): SetLine {
    val plain = StringBuilder()
    val starts = IntArray(line.words.size)
    line.words.forEachIndexed { index, word ->
        if (plain.isNotEmpty()) plain.append(' ')
        starts[index] = plain.length
        plain.append(word.text)
    }
    val text = plain.toString()

    // Measured the way it will be drawn, alignment and all, so that where the letters land in the
    // measurement is where they land on the page.
    val aligned = style.copy(textAlign = if (line.centred) TextAlign.Center else TextAlign.Start)

    // What the line has left over once its words are set tight, shared out between its spaces. A
    // centred line — the last of a sūrah, and the framed opening pages — keeps its own width.
    // The answer is negative on the line the page was fitted to, which is what sets that line
    // tight; a space is a space, and narrowing one moves no letter off its own.
    val gaps = line.words.size - 1
    val spacing = if (line.centred || gaps <= 0) {
        0f
    } else {
        val measured = measurer.measure(
            text = AnnotatedString(text),
            style = aligned,
            maxLines = 1,
            softWrap = false,
        ).size.width
        // The face's own word space is drawn narrower along with the letters, so what comes off
        // it is narrower too — the measurement it is taken away from was made condensed.
        val squeeze = style.textGeometricTransform?.scaleX ?: 1f
        Setting.spacing(
            natural = measured.toFloat(),
            gaps = gaps,
            width = width.toFloat(),
            trim = with(density) { style.fontSize.toPx() } * Setting.TRIM * squeeze,
        )
    }

    val justified = buildAnnotatedString {
        append(text)
        if (spacing != 0f) {
            val extra = with(density) { spacing.toSp() }
            text.forEachIndexed { at, character ->
                if (character == ' ') addStyle(SpanStyle(letterSpacing = extra), at, at + 1)
            }
        }
    }

    val laid = measurer.measure(
        text = justified,
        style = aligned,
        overflow = TextOverflow.Visible,
        softWrap = false,
        maxLines = 1,
        constraints = Constraints(maxWidth = width),
        layoutDirection = LayoutDirection.Rtl,
    )

    // Every character's box in one pass. Asked for one at a time each answer walks the line from
    // its edge, and a page is fifteen lines with a few dozen coloured letters on each of them.
    val boxes = FloatArray(text.length * 4)
    if (text.isNotEmpty()) {
        laid.multiParagraph.fillBoundingBoxes(TextRange(0, text.length), boxes, 0)
    }

    // Where each word of the line has ended up, which is what the wash behind a marked ayah is
    // drawn from. Taken here rather than when the mark moves: the line is measured once and the
    // reader taps about the page all evening.
    val placed = line.words.mapIndexed { index, word ->
        val at = starts[index]
        val (from, to) = extent(boxes, at, at + word.text.length)
        PlacedWord(
            at = at,
            length = word.text.length,
            surah = word.surah,
            ayah = word.ayah,
            from = from,
            to = to,
        )
    }

    val bands = mutableListOf<Bands.Band<Color>>()
    line.words.forEachIndexed { index, word ->
        val at = starts[index]
        if (word.kind != MushafPage.Word.Kind.TEXT) {
            band(boxes, text, at, at + word.text.length, printersMark)?.let { bands += it }
        } else {
            // The letters a rule falls on are the only ones that take a colour. Everything else
            // on the line stays the colour the page is set in, which is what keeps a coloured
            // mushaf a mushaf with colours in it rather than a chart.
            word.tajweed.forEach { span ->
                val colour = colours[span.rule] ?: return@forEach
                band(boxes, text, at + span.start, at + span.end, colour)?.let { bands += it }
            }
        }
    }

    val stops = Bands.stops(bands, width.toFloat(), ink)
    if (stops.isEmpty()) return SetLine(text = justified, words = placed)

    return SetLine(
        text = buildAnnotatedString {
            append(justified)
            addStyle(
                SpanStyle(brush = Brush.horizontalGradient(*stops.toTypedArray())),
                0,
                text.length,
            )
        },
        words = placed,
    )
}

/**
 * Where on the line one coloured letter stands.
 *
 * The letter is measured with whatever marks it carries, because a ḥaraka is drawn above its
 * letter and within its width: colouring one and not the other would leave a red letter under a
 * black fatḥa, which is not how a mushaf is printed.
 *
 * A letter that measures no width at all is half of a ligature — the alif of لا, which the face
 * draws as one shape with the lām — so the band is widened backwards until it has a width to
 * cover. Colouring a shape that is two letters is the one thing this cannot do better: the
 * alternative is colouring nothing.
 */
private fun band(
    boxes: FloatArray,
    text: String,
    from: Int,
    to: Int,
    paint: Color,
): Bands.Band<Color>? {
    if (from !in text.indices) return null
    var end = to.coerceIn(from + 1, text.length)
    while (end < text.length && text[end].isMark()) end++

    var head = from
    var (left, right) = extent(boxes, head, end)
    while (right - left <= 0.5f && head > 0) {
        head--
        val wider = extent(boxes, head, end)
        left = wider.first
        right = wider.second
    }
    return if (right > left) Bands.Band(left, right, paint) else null
}

/**
 * Where a stretch of the line stands: the left edge of the leftmost letter between [from] and
 * [to] and the right edge of the rightmost, or nothing at all when they have no width between
 * them.
 */
private fun extent(boxes: FloatArray, from: Int, to: Int): Pair<Float, Float> {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    for (i in maxOf(from, 0) until minOf(to, boxes.size / 4)) {
        left = minOf(left, boxes[i * 4])
        right = maxOf(right, boxes[i * 4 + 2])
    }
    return if (right > left) left to right else 0f to 0f
}

/** A vowel, a shadda, a small mīm: everything the mushaf writes above and below its letters. */
private fun Char.isMark(): Boolean =
    Character.getType(this) == Character.NON_SPACING_MARK.toInt()

/** Said once, on a page that has no text because the book is not on the phone yet. */
@Composable
private fun NotDownloadedYet() {
    Text(
        text = t("The Qur'an has not been downloaded yet. Settings → Prayer times and salah ") +
            t("→ download the Qur'an fetches all 6,236 āyāt once, and then never again."),
        style = MaterialTheme.typography.bodyMedium,
        color = Dim,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CHROME - LEAF)
            .padding(top = 20.dp),
    )
}

/** The size lines are measured at before being scaled to fit; nothing is ever drawn at it. */
private val MEASURE_AT = 40.sp

/** The range a fitted line may end up in, in sp. Neither end is expected to be reached. */
private const val MIN_SIZE = 8f
private const val MAX_SIZE = 48f

/** The margin the reader's own chrome keeps, and the narrower one the leaf keeps. */
private val CHROME = 18.dp
private val LEAF = 4.dp

/** The band behind a sūrah's name, and the size its letters are set at within it. */
private const val BAND_TINT = 0.10f
private const val BAND_SIZE = 0.62f

/**
 * The wash behind the ayah that has been tapped.
 *
 * A tenth of the gold and no more. This is a finger held under a line, not a highlighter pen:
 * whatever is under it has to be as readable as the rest of the page, because it is the part of
 * the page being read.
 */
private const val MARK_TINT = 0.10f

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
        // The second line is where the tafsīr is said out loud. A long press is not a gesture
        // anybody guesses at, and the index is the one screen in the reader with room to say so
        // without taking a line off the page.
        subtitle = t("114 sūras · 30 juzʾ · 60 ḥizb") + " · " + t("hold an ayah for its tafsīr"),
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
 * What the colours on the page mean.
 *
 * A coloured mushaf is only useful to somebody who can read the colours, and the convention is
 * not one convention: every printer has their own, and a reader who learned green for ghunnah in
 * one mushaf will find it standing for something else in the next. So the page says what it is
 * doing, once, on a page of its own — reached from the mushaf and only when the colours are
 * turned on, since with them off there is nothing here to explain.
 *
 * The list is grouped by colour rather than by rule, because that is the question a reader
 * actually has: not "what is idghām mutajānisayn" but "why is this letter purple". Rules that
 * share a colour share a line.
 */
@Composable
private fun TajweedLegendScreen(onBack: () -> Unit) {
    val colours = TajweedColours
    HubPageFrame(
        title = t("The colours"),
        subtitle = t("what each colour on the page is saying"),
        onBack = onBack,
        backLabel = t("‹ Mushaf"),
        footer = {
            Text(
                text = t("The rules are worked out on the phone from the text itself. Where ") +
                    t("the mushaf does not say outright which rule applies, nothing is ") +
                    t("coloured: a wrong colour on a letter of the Qur'an is worse than none."),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        },
    ) {
        tajweedLegend().forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(10.dp)
                        .background(colours[entry.rules.first()] ?: Faint, CircleShape),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = entry.gloss,
                        style = MaterialTheme.typography.bodySmall,
                        color = Faint,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
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
