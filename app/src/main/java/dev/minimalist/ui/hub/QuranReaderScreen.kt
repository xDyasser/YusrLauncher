package dev.minimalist.ui.hub

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.quran.Ayah
import dev.minimalist.data.quran.Basmala
import dev.minimalist.data.quran.RecitationStore
import dev.minimalist.data.quran.Reciter
import dev.minimalist.data.quran.Reciters
import dev.minimalist.data.quran.SurahNames
import dev.minimalist.data.settings.AyahLanguage
import dev.minimalist.domain.Hijri
import dev.minimalist.ui.isArabic
import dev.minimalist.ui.t
import dev.minimalist.ui.Hairline
import dev.minimalist.ui.SectionLabel
import dev.minimalist.ui.ThinProgress
import dev.minimalist.ui.noRippleClickable
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Gold
import dev.minimalist.ui.theme.QuranStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

    when (page) {
        ReaderPage.READER -> Reader(
            onBack = onBack,
            onOpenIndex = { page = ReaderPage.INDEX },
            onOpenReciters = { page = ReaderPage.RECITERS },
        )
        ReaderPage.INDEX -> SurahIndex(
            onPick = { number ->
                scope.launch { store.setBookmark(number, 1) }
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
    onBack: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenReciters: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { context.container.settingsStore }
    val quran = remember { context.container.quran }
    val recitation = remember { context.container.recitation }

    val settings by store.settings.collectAsState(initial = null)
    val bookmark by store.bookmark.collectAsState(initial = 1 to 1)
    val reciterId by store.reciterId.collectAsState(initial = null)
    val reciter = remember(reciterId) { Reciters.byId(reciterId) }

    val surah = bookmark.first
    val ayat by produceState(initialValue = emptyList<Ayah>(), surah) { value = quran.surah(surah) }

    // Downloading and playing are both about a sūrah rather than an ayah, so they live here and
    // the rows below only report what they are told.
    var download by remember(surah, reciter) { mutableStateOf<RecitationStore.Progress>(RecitationStore.Progress.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var playing by remember { mutableStateOf(false) }
    var reciting by remember(surah) { mutableIntStateOf(bookmark.second) }

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
                reciting += 1
                scope.launch { store.setBookmark(surah, reciting) }
            } else {
                playing = false
            }
        },
    )

    val downloaded = remember(surah, reciter, download) {
        reciter?.let { recitation.downloadedAyat(it, surah) } ?: 0
    }
    val total = SurahNames.ayahCount(surah)

    /**
     * Moves the bookmark, and with it the screen. Landing in another sūrah stops recitation:
     * the next sūrah's audio is a separate download and may not be on the phone at all, and a
     * player that is silently playing nothing is worse than one that has plainly stopped.
     */
    fun goTo(toSurah: Int, toAyah: Int) {
        if (toSurah == surah) {
            reciting = toAyah
        } else {
            playing = false
        }
        // Within a sūrah the line above has already moved the screen; across one, the bookmark is
        // what moves it, and `reciting` is re-read from the bookmark as the new sūrah loads.
        scope.launch { store.setBookmark(toSurah, toAyah) }
    }

    /** One sūrah forward or back, from its beginning — which is what a page turn means here. */
    fun turnSurah(step: Int) {
        val next = ((surah - 1 + step) + SurahNames.COUNT) % SurahNames.COUNT + 1
        goTo(next, 1)
    }

    HubPageFrame(
        // Named in whichever script the interface is in; the index below shows both, side by
        // side, whatever happens up here.
        title = (if (isArabic()) SurahNames.arabic(surah) else SurahNames.transliterated(surah))
            ?: t("Qur'an"),
        subtitle = SurahNames.subtitle(surah),
        onBack = onBack,
        // A new sūrah starts at its first ayah rather than at whatever line the last one was left
        // on. Coming back from the index to find al-Fātiḥa scrolled past its own end is the
        // change looking like it never happened.
        scrollKey = surah,
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
                    goTo(toSurah, toAyah)
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
    ) {
        if (ayat.isEmpty()) {
            Text(
                text = t("The Qur'an has not been downloaded yet. Settings → Prayer times and salah ") +
                    t("→ download the Qur'an fetches all 6,236 āyāt once, and then never again."),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            return@HubPageFrame
        }

        // Drawn across the whole text rather than around a single ayah, because a page turn is
        // about the page. It sits outside the vertical scroll's own gesture, so reading up and
        // down the sūrah and moving between sūrahs never contend for the same drag.
        SwipeBetweenSurahs(key = surah, onTurn = { step -> turnSurah(step) }) {
            // The basmala heads every sūrah but al-Tawba, and is counted as an ayah in none of
            // them except al-Fātiḥa — where it is the first, and so already in the list below.
            // Everywhere else it is printed here, once; the stored text of ayah 1 no longer
            // carries it, so there is no chance of reading it twice.
            if (Basmala.headingBelongsAbove(surah, ayah = 1)) {
                Text(
                    text = Basmala.ARABIC,
                    style = QuranStyle,
                    color = Faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                )
            }

            val language = settings?.prayer?.ayahLanguage ?: AyahLanguage.BOTH
            ayat.forEach { ayah ->
                AyahRow(
                    ayah = ayah,
                    language = language,
                    marked = ayah.ayah == reciting,
                    onMark = { goTo(surah, ayah.ayah) },
                )
            }
        }
    }
}

/**
 * A horizontal drag across the text, turning to the sūrah either side of this one.
 *
 * Which way is forward follows the interface rather than the page: on an English screen the next
 * sūrah is pulled in from the right, and on an Arabic one — where everything else is already
 * mirrored, and where the mushaf itself is bound the other way round — from the left.
 */
@Composable
private fun SwipeBetweenSurahs(
    key: Any?,
    onTurn: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(key, rtl) {
                var dragged = 0f
                val threshold = SWIPE_THRESHOLD.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val forward = if (rtl) dragged > threshold else dragged < -threshold
                        val back = if (rtl) dragged < -threshold else dragged > threshold
                        if (forward) onTurn(1) else if (back) onTurn(-1)
                        dragged = 0f
                    },
                    onDragCancel = { dragged = 0f },
                    onHorizontalDrag = { _, amount -> dragged += amount },
                )
            },
    ) {
        content()
    }
}

/** Far enough that a thumb drifting sideways while scrolling does not turn the page. */
private val SWIPE_THRESHOLD = 72.dp

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

/** All hundred and fourteen, with the ones whose recitation is already on the phone marked. */
@Composable
private fun SurahIndex(onPick: (Int) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    val bookmark by store.bookmark.collectAsState(initial = 1 to 1)

    HubPageFrame(title = t("Sūras"), subtitle = "114", onBack = onBack) {
        SurahNames.all().forEach { number ->
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
                        color = if (number == bookmark.first) {
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
                    text = reciter.tradition.label,
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
