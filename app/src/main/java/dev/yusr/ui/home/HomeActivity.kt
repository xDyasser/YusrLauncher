package dev.yusr.ui.home

import android.Manifest
import android.appwidget.AppWidgetHost
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.lifecycleScope
import dev.yusr.container
import dev.yusr.data.db.AppRuleEntity
import dev.yusr.data.prayer.PrayerToday
import dev.yusr.data.quran.Ayah
import dev.yusr.data.quran.SurahNames
import dev.yusr.data.settings.AyahLanguage
import dev.yusr.data.settings.NO_WIDGET
import dev.yusr.domain.FavoriteOrder
import dev.yusr.domain.Hijri
import dev.yusr.domain.Prayer
import dev.yusr.domain.PrayerEntry
import dev.yusr.domain.PrayerWindow
import dev.yusr.service.GuardService
import dev.yusr.ui.AppLauncher
import dev.yusr.ui.Hairline
import dev.yusr.ui.PauseRing
import dev.yusr.ui.hub.HubActivity
import dev.yusr.ui.hub.HubScreen
import dev.yusr.ui.noRippleClickable
import dev.yusr.ui.isArabic
import dev.yusr.ui.t
import dev.yusr.ui.search.SearchActivity
import dev.yusr.ui.settings.SettingsActivity
import dev.yusr.ui.theme.Dim
import dev.yusr.ui.theme.Faint
import dev.yusr.ui.theme.Fainter
import dev.yusr.ui.theme.Gold
import dev.yusr.ui.theme.Backdrop
import dev.yusr.ui.theme.YusrTheme
import dev.yusr.ui.theme.QuranQuoteStyle
import dev.yusr.util.DayClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home screen: the time, where you are in the day's prayers, an ayah, and a handful of names.
 *
 * The order of those is the argument. The clock is the largest thing on the screen because it is
 * the thing you unlocked the phone to see; the prayer strip is next because it is the thing you
 * did not unlock the phone to see and should anyway; the apps come last, small, and set as words.
 * There are no icons, no wallpaper, and no swipe that lands you in a grid of everything you own.
 *
 * The one exception is a single widget, off by default and chosen by hand — almost always a salah
 * app's, which knows things about the prayer that a calculated timetable does not.
 */
class HomeActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Held by the activity rather than the composition: a widget host must be listening for
     * exactly as long as its views are on screen, which is the activity's started window.
     */
    private val widgetHost by lazy { HomeWidget.host(this) }

    /** Set when safe mode has decided the widget does not get drawn this time round. */
    private var widgetSuppressed = false

    override fun onStart() {
        super.onStart()
        runCatching { widgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { widgetHost.stopListening() }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this the guard service's ongoing notification is hidden, and with it the only
        // visible sign that enforcement is running.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        GuardService.start(this)
        lifecycleScope.launch { container.repository.syncCatalog() }

        // Safe mode. If the last launch went down while drawing the widget, it does not get a
        // second attempt — otherwise the home screen crashes on every launch, and the setting
        // that would fix it is only reachable from the home screen.
        if (HomeWidget.crashedWhileDrawing(this)) {
            HomeWidget.markDrawing(this, false)
            // Suppressed for this launch as well as forgotten, because forgetting is a write to
            // disk and the composition below would otherwise read the old value and try again.
            widgetSuppressed = true
            lifecycleScope.launch { container.settingsStore.setHomeWidgetId(NO_WIDGET) }
        }

        setContent {
            YusrTheme {
                HomeScreen(
                    widgetHost = widgetHost,
                    allowWidget = !widgetSuppressed,
                    onOpenDrawer = { startActivity(Intent(this, SearchActivity::class.java)) },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenSetup = { startActivity(SettingsActivity.setupIntent(this)) },
                    onOpenHub = { startActivity(Intent(this, HubActivity::class.java)) },
                    onOpenTasbih = { startActivity(HubActivity.intent(this, HubScreen.TASBIH)) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    widgetHost: AppWidgetHost,
    allowWidget: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenHub: () -> Unit,
    onOpenTasbih: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { context.container.repository }
    val store = remember { context.container.settingsStore }
    val quran = remember { context.container.quran }
    val devotions = remember { context.container.devotions }

    val favorites by repository.favorites.collectAsState(initial = emptyList())
    val allRules by repository.allRules.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = null)
    val beads by devotions.observeTodayCount().collectAsState(initial = 0)
    val now by rememberTicker()

    // The one time this launcher opens a screen you did not ask for: the first launch after
    // install. Everything the app needs — being the home screen, usage access, drawing over other
    // apps — can only be granted by hand, on a checklist that otherwise lives behind a long press
    // on the clock, and a first-time user has not been told that gesture. The flag is written
    // before the screen opens, so this happens once whatever is done with it.
    LaunchedEffect(settings?.setupPrompted) {
        val current = settings ?: return@LaunchedEffect
        if (!current.setupPrompted) {
            store.setSetupPrompted(true)
            if (!current.onboardingComplete) onOpenSetup()
        }
    }

    val prayerRepository = remember { context.container.prayerRepository }
    val prayerSettings = settings?.prayer
    // Recomputed each minute, which is as often as any of these can change.
    val today by produceState<PrayerToday?>(null, now / 60_000, prayerSettings) {
        value = prayerRepository.today(now)
    }
    val activeWindow by produceState<PrayerWindow?>(null, now / 60_000, prayerSettings) {
        value = prayerRepository.activeWindow(now)
    }

    // The ayah on the home screen is the one the reader is bookmarked at, and tapping it moves the
    // bookmark on by one. Not a random verse: the home screen and the mushaf are the same place in
    // the book, so reading a line here is reading a line of the Qur'an in order, and the reader
    // opens where the home screen left off rather than somewhere unrelated.
    //
    // It changes only when it is tapped. A verse that changed itself would be scenery; one that
    // waits is something you finished reading.
    //
    // Before anything has been read there is no bookmark to be at, and the ayah is the one the
    // launcher is named for — or the opening of the book itself, once the book is on the device.
    // Either way the first tap writes a bookmark and it is the ordinary logic from then on.
    val bookmark by store.storedBookmark.collectAsState(initial = null)
    // Only the never-read case reads this, and only to pick which opening it is; it is here so
    // that a download finishing while the home screen is up turns the ayah over with it.
    val quranDownloaded by quran.downloaded.collectAsState(initial = null)
    val ayah by produceState<Ayah?>(null, bookmark, quranDownloaded) {
        val place = bookmark?.place
        value = when {
            bookmark == null -> null // Not read off disk yet; nothing to show rather than a guess.
            place == null -> quran.opening()
            else -> quran.at(place.first, place.second)
        }
    }

    // Rearranging the names is a mode, entered by hand and left the same way. Nothing moves
    // while it is off, so a thumb resting on the list cannot quietly reorder the home screen.
    var arranging by remember { mutableStateOf(false) }
    val canArrange = activeWindow == null && favorites.size > 1
    // Two names became one while the mode was open, or salah began: there is nothing left to
    // arrange, so the mode closes itself rather than stranding a "done" with no list under it.
    LaunchedEffect(canArrange) { if (!canArrange) arranging = false }

    // Home is the floor: there is nothing behind it to go back to. Arranging is the one thing
    // here that back can dismiss, because it is a mode rather than a screen.
    BackHandler(enabled = true) { arranging = false }

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            // A long press anywhere still opens the drawer, as it always has. The word at the
            // foot of the screen is the discoverable way in; this is the fast one.
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = { },
                onLongClick = onOpenDrawer,
            )
            .systemBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 22.dp, bottom = 8.dp),
    ) {
        // The clock sits centred, and the way into arranging sits in the corner beside it — the
        // only mark on the screen that is not a word you can read at a glance, and the only one
        // that has to be looked for.
        Box(modifier = Modifier.fillMaxWidth()) {
            ClockBlock(
                nowMillis = now,
                hijriOffsetDays = prayerSettings?.hijriOffsetDays ?: 0,
                onLongPress = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
            if (canArrange) {
                ArrangeToggle(
                    arranging = arranging,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) { arranging = !arranging }
            }
        }

        // A chosen widget speaks for the prayer instead of the strip; it almost always has more
        // to say. The strip comes back the moment the widget goes.
        val widgetId = if (allowWidget) settings?.homeWidgetId ?: NO_WIDGET else NO_WIDGET
        if (widgetId != NO_WIDGET) {
            HomeWidgetSlot(
                host = widgetHost,
                widgetId = widgetId,
                heightDp = (settings?.homeWidgetHeightDp ?: 132).dp,
                modifier = Modifier.padding(top = 20.dp),
            )
        } else {
            today?.let { PrayerStrip(it, modifier = Modifier.padding(top = 22.dp)) }
        }

        val shown = ayah
        if (shown != null) {
            AyahCard(
                ayah = shown,
                language = prayerSettings?.ayahLanguage ?: AyahLanguage.BOTH,
                // Stepping from the ayah on the screen rather than from the bookmark it was asked
                // for: when the Qur'an has not been downloaded those two are not the same
                // reference, and a tap has to move on from what was read.
                onRead = {
                    val (nextSurah, nextAyah) = SurahNames.next(shown.surah, shown.ayah)
                    scope.launch { store.setBookmark(nextSurah, nextAyah) }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        // The names take whatever room is left and scroll inside it. Twenty favourites is a bad
        // idea, but a home screen that hides the twentieth one is a worse one.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            val window = activeWindow
            if (window != null) {
                // Nothing to tap during salah. That is the whole point of the window, and a list
                // of names that all refuse is worse than no list. What there is instead is the
                // name of the prayer and how much of the stop is left, which is the one question
                // a closed phone in your hand raises.
                // Centred, unlike everything else on this screen. The names below are a list and
                // a list is read down its left edge; this is one thing on an otherwise empty
                // screen, and a thing on its own sitting against the margin looks like the top of
                // a list that failed to load.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = prayerName(window),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    PauseRing(
                        window = window,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                FavoriteList(
                    favorites = favorites,
                    arranging = arranging,
                    onOpen = { packageName -> scope.launch { AppLauncher.open(context, packageName) } },
                    onReorder = { order -> scope.launch { repository.reorderFavorites(order) } },
                )
            }
        }

        // Three words at the foot of the screen, in the same order every time: what is behind the
        // phone, what is on it, and what you are counting. They stay put whatever the list above
        // them does, because a long press on a full screen of names is a press on a name.
        HomeBar(
            appCount = allRules.size,
            beads = beads,
            onOpenHub = onOpenHub,
            onOpenDrawer = onOpenDrawer,
            onOpenTasbih = onOpenTasbih,
        )

        // Once setup is done the hint goes too. A finished home screen is the clock, the prayer,
        // the ayah and the names.
        //
        // It is the way back to the checklist as well as the notice that there is one. It used to
        // name the gesture instead — "hold the clock" — which is a line of instructions where a
        // door would do, and left the one screen a half-set-up launcher needs two steps and a
        // piece of remembered trivia away. The gesture is still worth knowing, so the checklist
        // itself says it, on the screen you are already on when you learn what it is for.
        if (settings?.onboardingComplete == false) {
            Text(
                text = t("setup unfinished — tap to finish"),
                style = MaterialTheme.typography.labelSmall,
                color = Gold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .noRippleClickable(onClick = onOpenSetup)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/**
 * The time, and under it both dates on one line.
 *
 * The Hijri one comes first and is set in the reading colour; the Gregorian follows it, dimmer,
 * after a dot. That is the whole hierarchy — the Islamic date is the one this app is organised
 * around — and it costs one line instead of two, which is a line the names below can have.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClockBlock(
    nowMillis: Long,
    hijriOffsetDays: Int,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = remember(nowMillis / 60_000) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        DayClock.localDateTime(nowMillis).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
    // In Arabic the date is set in Arabic — month name and numerals both. A Hijri date
    // transliterated into Latin letters on an Arabic screen is the app talking to itself.
    val arabic = isArabic()
    val hijri = remember(nowMillis / 3_600_000, hijriOffsetDays, arabic) {
        Hijri.of(DayClock.localDateTime(nowMillis).toLocalDate(), hijriOffsetDays)
            ?.let { if (arabic) it.arabic else it.display }
    }
    // Short, because it is now sharing a line: the weekday and the day, without the year, which
    // is the part of a Gregorian date nobody has ever needed off a home screen.
    val date = remember(nowMillis / 3_600_000) {
        DayClock.localDateTime(nowMillis)
            .format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { },
            onLongClick = onLongPress,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hijri != null) {
                Text(
                    text = hijri,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(text = "·", style = MaterialTheme.typography.bodyMedium, color = Fainter)
            }
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
        }
    }
}

/**
 * The five prayers across the width of the screen, with the next one in gold.
 *
 * Under it, two lines: how long until that prayer, and how long the *current* prayer's preferred
 * time has left. The second is the one that changes behaviour — "ʿasr in 2 h" is information,
 * "dhuhr's faḍīla ends in 42 m" is a reason to put the phone down now.
 */
@Composable
private fun PrayerStrip(today: PrayerToday, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Three columns rather than five where the prayers are joined, because that is how
            // many times the day is actually broken up for someone who prays them that way.
            today.entries.forEach { entry ->
                val isNext = entry.covers(today.next.prayer) && !today.next.tomorrow
                val minute = entry.minuteOfDay
                val passed = minute <= today.minuteOfDay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = prayerName(entry),
                        style = MaterialTheme.typography.bodySmall,
                        // Next in gold, already prayed in the mid grey, still to come the faintest
                        // — so the strip reads as a position in the day rather than a table.
                        color = when {
                            isNext -> Gold
                            passed -> Faint
                            else -> Dim
                        },
                    )
                    Text(
                        text = DayClock.clock(minute),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNext) Gold else Faint,
                    )
                }
            }
        }
        Hairline()

        // Both on one line, held apart rather than stacked. They answer the same question from two
        // ends — how long until the next prayer, how long the one you are in stays preferred — and
        // reading them together is what makes the second one mean anything.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("%s in %s", t(prayerName(today.next.prayer)), DayClock.formatMinutes(today.next.minutesAway)),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
            )
            today.fadilaRemaining?.let { left ->
                val ends = today.endOfFadila(today.current)
                Text(
                    // Shorter than it was, because it is now sharing a line with the countdown:
                    // the prayer is named in the strip directly above, so it is not named twice.
                    text = ends?.let { t("faḍīla %s · %s", DayClock.formatMinutes(left), DayClock.clock(it)) }
                        ?: t("faḍīla %s", DayClock.formatMinutes(left)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Dim,
                    maxLines = 1,
                )
            }
        }

        // The night, on its own line under the day. Neither of these is a prayer time, which is
        // why they are the faintest thing in the block — but ʿishāʾ runs out at the first and
        // qiyām begins at the second, and neither is anywhere on a wall clock.
        Text(
            text = t(
                "midnight %s · last third %s",
                DayClock.clock(today.night.midnightMinuteOfDay),
                DayClock.clock(today.night.lastThirdMinuteOfDay),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = Fainter,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * An ayah, with the reference under it and an invitation to move on.
 *
 * Tapping it moves to the next one, and moves the reader's bookmark with it. That is the only
 * counter in this app that runs the right way: every other number here goes up as you spend more of
 * your life on the phone, and this one goes up as you read.
 */
@Composable
private fun AyahCard(
    ayah: Ayah,
    language: AyahLanguage,
    onRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .noRippleClickable(onClick = onRead)
            .padding(vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (language != AyahLanguage.ENGLISH) {
            Text(
                text = ayah.arabic,
                style = QuranQuoteStyle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Right,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val english = ayah.english
        if (language != AyahLanguage.ARABIC && english != null) {
            Text(
                text = english,
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = ayah.reference(language),
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            Text(
                // Says what the tap does rather than what you are supposed to have done, which is
                // the version somebody looking for a way to move the verse on can actually find.
                text = t("tap for the next ayah →"),
                style = MaterialTheme.typography.labelSmall,
                color = Fainter,
            )
        }
        Hairline(modifier = Modifier.padding(top = 4.dp))
    }
}

/** Hub on the left, the drawer in the middle, the day's count on the right. */
@Composable
private fun HomeBar(
    appCount: Int,
    beads: Int,
    onOpenHub: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenTasbih: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("Devotions"),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.noRippleClickable(onClick = onOpenHub).padding(end = 12.dp),
            )
            Text(
                text = if (appCount > 0) t("All apps · %s", appCount) else t("All apps"),
                style = MaterialTheme.typography.bodyMedium,
                color = Faint,
                modifier = Modifier.noRippleClickable(onClick = onOpenDrawer).padding(horizontal = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.noRippleClickable(onClick = onOpenTasbih).padding(start = 12.dp),
            ) {
                Text(text = t("Dhikr"), style = MaterialTheme.typography.bodyMedium, color = Faint)
                Text(
                    text = beads.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Gold,
                )
            }
        }
    }
}

/**
 * The chosen widget, drawn at the width the home screen actually has.
 *
 * Nothing is shown if the widget cannot be resolved — the app behind it has been uninstalled, or
 * the binding was lost. A gap is better than an error box on a screen whose whole argument is
 * that there is nothing on it.
 */
@Composable
private fun HomeWidgetSlot(
    host: AppWidgetHost,
    widgetId: Int,
    heightDp: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The home screen keeps 26dp of margin on each side, and the widget lays out for the width
    // it is told about rather than the one it is given.
    val widthDp = (LocalConfiguration.current.screenWidthDp - 52).coerceAtLeast(1)
    val height = heightDp.value.toInt()

    val view = remember(widgetId, widthDp, height) {
        HomeWidget.markDrawing(context, true)
        HomeWidget.info(context, widgetId)?.let { info ->
            HomeWidget.createView(context, host, widgetId, info, widthDp, height)
        }
    }

    // Long enough for the widget to have been measured, laid out and drawn. Surviving that is
    // what the note is asking about; anything later is the widget's own business.
    LaunchedEffect(view) {
        delay(2_000)
        HomeWidget.markDrawing(context, false)
    }

    if (view == null) {
        // The widget is gone, or refuses to be built. Forget it rather than leave a hole here on
        // every launch — and this is the only route back for someone whose chosen widget has
        // started misbehaving, since the home screen is how they reach settings at all.
        val store = remember { context.container.settingsStore }
        LaunchedEffect(widgetId) { store.setHomeWidgetId(NO_WIDGET) }
        return
    }

    key(widgetId) {
        AndroidView(
            modifier = modifier.fillMaxWidth().height(heightDp),
            // Built once, above, where a failure can be caught. Handing back the same view is
            // safe as long as it is not still parented from a previous pass.
            factory = { (view.parent as? ViewGroup)?.removeView(view); view },
            update = { hosted -> HomeWidget.size(hosted, widthDp, height) },
        )
    }
}

/**
 * What to call a line of the timetable: the prayer, or the pair when two are prayed together.
 *
 * The joined names are single strings rather than two names and an ampersand, because that is
 * what they are called — *al-ẓuhrayn*, "الظهر والعصر" — and because an Arabic screen building
 * "الظهر & العصر" out of parts would get the conjunction wrong.
 */
internal fun prayerName(entry: PrayerEntry): String = when {
    entry.with == null -> t(prayerName(entry.prayer))
    entry.prayer == Prayer.DHUHR -> t("Dhuhr & ʿasr")
    entry.prayer == Prayer.MAGHRIB -> t("Maghrib & ʿishāʾ")
    else -> t(prayerName(entry.prayer))
}

/**
 * The same, for a window the phone has actually stopped for.
 *
 * [PrayerWindow.label] builds one out of the enum's own spelling, which is English wherever it is
 * printed — so the block screen headed an Arabic phone with "maghrib and isha" until this existed.
 */
internal fun prayerName(window: PrayerWindow): String = when {
    window.through == null -> prayerName(window.prayer)
    window.prayer == Prayer.DHUHR -> t("Dhuhr & ʿasr")
    window.prayer == Prayer.MAGHRIB -> t("Maghrib & ʿishāʾ")
    else -> prayerName(window.prayer)
}

/** t("Fajr"), t("ʿAsr"), t("ʿIshāʾ") — set the way they are said, not the way the enum spells them. */
internal fun prayerName(prayer: Prayer): String = when (prayer) {
    Prayer.FAJR -> t("Fajr")
    Prayer.SUNRISE -> t("Sunrise")
    Prayer.DHUHR -> t("Dhuhr")
    Prayer.ASR -> t("ʿAsr")
    Prayer.MAGHRIB -> t("Maghrib")
    Prayer.ISHA -> t("ʿIshāʾ")
}

/**
 * The way in and out of arranging, in the corner beside the clock.
 *
 * Off, it is three faint lines and no word — findable if you go looking, invisible if you are
 * not. On, it says *done*, because a mode you can enter by accident must be obvious to leave.
 */
@Composable
private fun ArrangeToggle(arranging: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Text(
        text = if (arranging) t("done") else "≡",
        style = MaterialTheme.typography.labelSmall,
        color = if (arranging) Gold else Fainter,
        modifier = modifier
            .noRippleClickable(onClick = onToggle)
            // A mark this quiet still has to be hittable, so the touch target is padded well
            // past the ink — out to the screen's edge on the right, where nothing else is.
            .padding(start = 24.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
    )
}

/** How far the list creeps per frame while a dragged name is held against one of its edges. */
private val AUTO_SCROLL_STEP = 6.dp

/**
 * The smallest gap kept between the left edge of the screen and the chevrons, so the block never
 * runs into the margin even when the longest name is nearly as wide as the screen.
 */
private val FAVORITE_MIN_INSET = 16.dp

/** The mark in front of every favourite. Measured as well as drawn, so it is written once. */
private const val FAVORITE_CHEVRON = "\u203a"

/**
 * The names, in the order the user put them in, each behind a chevron.
 *
 * Ordinarily a tap opens and nothing else happens. While [arranging] every row grows a handle at
 * its right edge, and the row is split between two jobs: the name is still the scroller's, the
 * handle is the finger's. Nothing has to be inferred from how long a press lasted or how far it
 * travelled, which is the whole reason for the mode.
 *
 * A name dragged against the top or bottom edge walks the list past under it, so a favourite can
 * be moved further than the screen is tall without the finger leaving the glass.
 *
 * The names all start at the same x, and that x is worked out from the longest of them: the
 * chevron and the widest name are treated as one block, and the block is centred on the screen.
 * The list therefore sits under the clock whether it holds one short name or ten long ones,
 * without any name being centred individually — they stay a left-aligned column.
 */
@Composable
private fun FavoriteList(
    favorites: List<AppRuleEntity>,
    arranging: Boolean,
    onOpen: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    // The names follow the finger from this local copy; the stored order is written on drop. It
    // is keyed on the favourites themselves, so a tier change elsewhere still lands here.
    var order by remember(favorites) { mutableStateOf(favorites) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // Every name is one line of the same size, so one row's height measures them all, and the
    // row a finger is over is arithmetic rather than a hit test.
    var rowHeight by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()
    // -1 creeps towards the top of the list, 1 towards the bottom, 0 stands still.
    var creep by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val stepPx = with(density) { AUTO_SCROLL_STEP.toPx() }

    // The block's own width, laid out before anything is drawn: the chevron, the gap after it, and
    // the longest name there is. Measured off the favourites rather than the working order, so a
    // drag that shuffles the same names does not re-measure them, and off the label alone, since
    // the handle appears and disappears with arrange mode and must not shift the names when it
    // does.
    val gap = 12.dp
    val nameStyle = MaterialTheme.typography.titleMedium
    val chevronStyle = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val chevronWidth = remember(textMeasurer, chevronStyle) {
        textMeasurer.measure(FAVORITE_CHEVRON, chevronStyle, maxLines = 1).size.width
    }
    val widestName = remember(textMeasurer, nameStyle, favorites) {
        favorites.maxOfOrNull { textMeasurer.measure(it.label, nameStyle, maxLines = 1).size.width }
            ?: 0
    }
    // Half of what is left over once the block has taken its width, so the block is centred. A
    // name too wide to centre is simply pinned at the margin and ellipsised where it runs out.
    val inset = with(density) {
        val blockWidth = chevronWidth + gap.toPx() + widestName
        ((viewportWidth - blockWidth) / 2f).toDp().coerceAtLeast(FAVORITE_MIN_INSET)
    }

    /**
     * Puts the held name in the row its offset now reaches, and decides whether the list should
     * be creeping under it. Called on every finger move and on every scrolled pixel, since both
     * change where the name sits.
     */
    fun follow() {
        val from = order.indexOfFirst { it.packageName == dragging }
        // A name that has stopped being a favourite mid-drag is no longer in the list.
        if (from < 0) {
            creep = 0
            return
        }
        val to = FavoriteOrder.landingIndex(
            from = from,
            dragPx = dragOffset,
            rowHeightPx = rowHeight.toFloat(),
            size = order.size,
        )
        if (to != from) {
            order = FavoriteOrder.move(order, from, to)
            // The name has moved a row in the layout, so the same finger position is now that
            // much less of a displacement.
            dragOffset -= (to - from) * rowHeight
        }
        // Where the name is drawn, measured from the top of the visible list. Rows are uniform,
        // so its resting place is its index times a row.
        val landed = order.indexOfFirst { it.packageName == dragging }
        creep = FavoriteOrder.creepDirection(
            topPx = landed * rowHeight + dragOffset - scrollState.value,
            rowHeightPx = rowHeight.toFloat(),
            viewportPx = viewportHeight.toFloat(),
        )
    }

    // One step per frame for as long as the name is held in a margin, and each scrolled pixel is
    // added to the offset so the name stays under the finger while the list moves beneath it.
    //
    // The whole drag runs inside a single scroll session, and the direction is read off state
    // each frame rather than restarting the loop: taking and releasing the scroller once a frame
    // is itself a stutter, which is what made the first version of this feel heavy.
    LaunchedEffect(dragging) {
        if (dragging == null) return@LaunchedEffect
        scrollState.scroll {
            // Cancelled when the finger lifts, at the frame wait below.
            while (true) {
                withFrameNanos { }
                val direction = creep
                if (direction != 0) {
                    val consumed = scrollBy(stepPx * direction)
                    // Zero means the list has run out at this end, and the finger is just
                    // resting in the margin. Nothing to do but wait for the next frame.
                    if (consumed != 0f) {
                        dragOffset += consumed
                        follow()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Measured outside the scroller, so this is the height of the window onto the list
            // rather than the height of the names themselves.
            .onSizeChanged {
                viewportHeight = it.height
                viewportWidth = it.width
            }
            // Scrollable in both modes, and by the same gesture in both: arranging takes the
            // handles for dragging and leaves everything else to the scroller.
            .verticalScroll(scrollState),
    ) {
        if (order.isEmpty()) {
            Text(
                text = t("no favourites yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
            )
            return@Column
        }
        order.forEach { rule ->
            // Identity, not position. Without this a row is whichever app happens to be in that
            // slot, so the first swap hands this one a different app, and everything hanging off
            // the row — the drag in progress above all — is torn down and rebuilt underneath the
            // finger. Keyed, the row moves with its app and the gesture survives the reorder.
            key(rule.packageName) {
                val packageName = rule.packageName
                val held = dragging == packageName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Drawn over its neighbours, and offset rather than laid out again, so
                        // the held name passes across the others instead of shoving them aside
                        // twice.
                        .zIndex(if (held) 1f else 0f)
                        .graphicsLayer { translationY = if (held) dragOffset else 0f }
                        .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                        // Nothing opens while the list is being arranged: a tap here is a finger
                        // that meant for the handle and missed it.
                        .noRippleClickable(enabled = !arranging) { onOpen(packageName) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // Every row is set in by the same measured amount, so the names stay a
                    // left-aligned column and the column as a whole sits under the centred clock.
                    // Less the row's own spacing, which lands between this and the chevron.
                    Spacer(modifier = Modifier.width((inset - gap).coerceAtLeast(0.dp)))
                    // The chevron is indent as much as ornament.
                    Text(
                        text = FAVORITE_CHEVRON,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Fainter,
                    )
                    Text(
                        text = rule.label,
                        style = MaterialTheme.typography.titleMedium,
                        // While one name is held the rest step back, so it is obvious which moves.
                        color = if (dragging == null || held) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            Dim
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // The name itself is left to the scroller, and only this hands the row to the
                    // finger. One side of the row moves the list, the other moves the name, and
                    // neither has to guess which was meant.
                    if (arranging) {
                        Text(
                            text = "≡",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (held) Gold else Faint,
                            modifier = Modifier
                                .pointerInput(packageName) {
                                    try {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragging = packageName
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                follow()
                                            },
                                            onDragEnd = {
                                                dragging = null
                                                dragOffset = 0f
                                                creep = 0
                                                onReorder(order.map { it.packageName })
                                            },
                                            onDragCancel = {
                                                dragging = null
                                                dragOffset = 0f
                                                creep = 0
                                                // Nothing was decided, so the stored order
                                                // stands.
                                                order = favorites
                                            },
                                        )
                                    } finally {
                                        // Taken down mid-drag by something other than the finger.
                                        // Neither callback above runs in that case, so without
                                        // this the name would be left stranded out of its row,
                                        // drawn over its neighbour and never put back.
                                        if (dragging == packageName) {
                                            dragging = null
                                            dragOffset = 0f
                                            creep = 0
                                        }
                                    }
                                }
                                // Padding rather than size, so the target is wide without the
                                // mark growing: it reaches the screen's edge and back to the name.
                                .padding(start = 28.dp, end = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberTicker(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(10_000)
    }
}
