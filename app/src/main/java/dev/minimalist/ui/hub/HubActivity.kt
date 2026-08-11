package dev.minimalist.ui.hub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.minimalist.container
import dev.minimalist.data.prayer.PrayerToday
import dev.minimalist.data.quran.SurahNames
import dev.minimalist.data.settings.LocationSource
import dev.minimalist.domain.FastingCalendar
import dev.minimalist.domain.Qibla
import dev.minimalist.ui.isArabic
import dev.minimalist.ui.t
import dev.minimalist.ui.Hairline
import dev.minimalist.ui.SectionLabel
import dev.minimalist.ui.noRippleClickable
import dev.minimalist.ui.settings.SettingsActivity
import dev.minimalist.ui.theme.Backdrop
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Fainter
import dev.minimalist.ui.theme.Gold
import dev.minimalist.ui.theme.MinimalTheme
import dev.minimalist.util.DayClock
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Everything the phone can do that is not an app.
 *
 * The hub exists because these things do not belong on the home screen and do not belong in
 * settings either. The home screen is for the time and the way out; settings is for decisions you
 * make once. This is for the things you come back to — the direction to pray in, where you were in
 * the mushaf, what you are counting today.
 *
 * The Qibla is at the centre of it, at the size of a thing you hold up rather than a thing you
 * read, because it is the only one you might need in a hurry in a room you have never been in.
 */
class HubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opening = intent?.getStringExtra(EXTRA_SCREEN)
            ?.let { name -> runCatching { HubScreen.valueOf(name) }.getOrNull() }
            ?: HubScreen.HUB

        setContent {
            MinimalTheme {
                // One activity, several pages. They share the prayer settings and the reciter and
                // are never more than a step apart, so a back stack of activities would be
                // machinery around what is really one screen with sections.
                var screen by remember { mutableStateOf(opening) }
                // Back from a page returns to the hub; back from the hub leaves.
                BackHandler(enabled = screen != HubScreen.HUB) { screen = HubScreen.HUB }

                when (screen) {
                    HubScreen.HUB -> HubPage(
                        onOpen = { screen = it },
                        onOpenSettings = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                    )
                    HubScreen.PRAYER -> PrayerTimesScreen(onBack = { screen = HubScreen.HUB })
                    HubScreen.MADHAB -> MadhabScreen(onBack = { screen = HubScreen.HUB })
                    HubScreen.QURAN -> QuranReaderScreen(onBack = { screen = HubScreen.HUB })
                    HubScreen.TASBIH -> TasbihScreen(onBack = { screen = HubScreen.HUB })
                    HubScreen.ADHKAR -> SupplicationScreen(
                        dua = false,
                        onBack = { screen = HubScreen.HUB },
                    )
                    HubScreen.DUA -> SupplicationScreen(
                        dua = true,
                        onBack = { screen = HubScreen.HUB },
                    )
                    HubScreen.FASTING -> FastingScreen(onBack = { screen = HubScreen.HUB })
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SCREEN = "screen"

        /** Opens the hub straight onto one of its pages, which is how the home bar reaches them. */
        fun intent(context: Context, screen: HubScreen): Intent =
            Intent(context, HubActivity::class.java).putExtra(EXTRA_SCREEN, screen.name)
    }
}

enum class HubScreen { HUB, PRAYER, MADHAB, QURAN, TASBIH, ADHKAR, DUA, FASTING }

@Composable
private fun HubPage(onOpen: (HubScreen) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    val prayerRepository = remember { context.container.prayerRepository }
    val devotions = remember { context.container.devotions }
    val supplications = remember { context.container.supplications }

    val settings by store.settings.collectAsState(initial = null)
    val prayer = settings?.prayer
    val beads by devotions.observeTodayCount().collectAsState(initial = 0)
    val fastedThisMonth by devotions.observeFastedThisMonth().collectAsState(initial = 0)
    val bookmark by store.bookmark.collectAsState(initial = 1 to 1)

    val today by produceState<PrayerToday?>(null, prayer) { value = prayerRepository.today() }
    val heading = rememberHeading()

    val madhab = prayer?.effectiveMadhab
    val book by produceState<String?>(null, madhab) {
        value = madhab?.let {
            supplications.book(it.collection)?.let { book ->
                if (isArabic()) book.arabicTitle.ifBlank { book.title } else book.title
            }
        }
    }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = t("Hub"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            today?.let {
                Text(
                    text = t("%s in %s", t(prayerLabel(it)), DayClock.formatMinutes(it.next.minutesAway)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll),
        ) {
            QiblaBlock(
                prayerConfigured = prayer?.locationSource != LocationSource.UNSET && prayer != null,
                latitude = prayer?.latitude ?: 0.0,
                longitude = prayer?.longitude ?: 0.0,
                heading = heading,
                onSetLocation = onOpenSettings,
                modifier = Modifier.padding(top = 22.dp),
            )

            // Six things, in two columns, each with a line saying where it stands. A grid of
            // names with nothing under them would be a launcher inside a launcher.
            val fasting = remember(prayer?.hijriOffsetDays) {
                FastingCalendar.classify(LocalDate.now(), prayer?.hijriOffsetDays ?: 0)
            }
            HubGrid(
                tiles = listOf(
                    HubTile(t("Prayer times"), prayerSubtitle(today, prayer?.showFadila == true), HubScreen.PRAYER),
                    HubTile(
                        t("Qur'an"),
                        (if (isArabic()) {
                            SurahNames.arabic(bookmark.first)
                        } else {
                            SurahNames.transliterated(bookmark.first)
                        })
                            ?.let { "$it ${bookmark.first}:${bookmark.second}" }
                            ?: "Al-Fātiḥa 1:1",
                        HubScreen.QURAN,
                    ),
                    HubTile(t("Tasbīḥ"), t("%s today", beads), HubScreen.TASBIH),
                    HubTile(t("Adhkār"), book ?: t("morning and evening"), HubScreen.ADHKAR),
                    HubTile(t("Duʿāʾ"), book ?: t("supplications"), HubScreen.DUA),
                    HubTile(
                        t("Fasting"),
                        if (fasting.isFast) t("%s · today", t(fasting.label)) else t("%s this month", fastedThisMonth),
                        HubScreen.FASTING,
                    ),
                ),
                onOpen = onOpen,
                modifier = Modifier.padding(top = 26.dp),
            )
        }

        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = t("Madhab"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = prayer?.let { settingsMadhabLine(it.effectiveMadhab.label, it.method.name) }
                        ?: t("not set"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
            Text(
                text = t("Change"),
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
                modifier = Modifier
                    .noRippleClickable { onOpen(HubScreen.MADHAB) }
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
    }
}

private data class HubTile(val title: String, val subtitle: String, val screen: HubScreen)

/**
 * The six tiles, as a grid drawn entirely out of the gaps between them.
 *
 * There are no boxes here: the cells sit one pixel apart over a faint ground, so the lines you see
 * are the background showing through. It is the cheapest possible grid and the only one that does
 * not add a second rectangle language to a screen made of text.
 */
@Composable
private fun HubGrid(
    tiles: List<HubTile>,
    onOpen: (HubScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEach { tile ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Backdrop)
                            .noRippleClickable { onOpen(tile.screen) }
                            .padding(horizontal = 14.dp, vertical = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = tile.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = tile.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Dim,
                        )
                    }
                }
                // An odd row would otherwise stretch its one tile across the width and stop
                // looking like part of the same grid.
                if (row.size == 1) Box(modifier = Modifier.weight(1f).background(Backdrop))
            }
        }
    }
}

/** The dial, the bearing, and how far away it is. */
@Composable
private fun QiblaBlock(
    prayerConfigured: Boolean,
    latitude: Double,
    longitude: Double,
    heading: Float?,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!prayerConfigured) {
            // A qibla computed for latitude zero points at nothing, so it is not drawn at all.
            Text(
                text = t("set a location to find the qibla"),
                style = MaterialTheme.typography.bodyMedium,
                color = Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.noRippleClickable(onClick = onSetLocation).padding(vertical = 40.dp),
            )
            return@Column
        }

        val bearing = remember(latitude, longitude) { Qibla.bearing(latitude, longitude) }
        val distance = remember(latitude, longitude) { Qibla.distanceKm(latitude, longitude) }

        QiblaDial(
            bearing = bearing,
            heading = heading,
            ring = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
            innerRing = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
            needle = Gold,
            tick = Dim,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionLabel(t("Qibla"))
            Text(
                text = "${bearing.roundToInt()}° ${Qibla.compassPoint(bearing)}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = t("%,d km to Makkah · %s").format(
                    distance.roundToInt(),
                    // A dial that cannot turn is still worth showing, but only if it says so —
                    // otherwise it looks like a compass that has stuck.
                    if (heading == null) t("no compass · north is up") else t("offline compass"),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun prayerLabel(today: PrayerToday): String =
    dev.minimalist.ui.home.prayerName(today.next.prayer)

private fun prayerSubtitle(today: PrayerToday?, fadila: Boolean): String = when {
    today == null -> t("not set up")
    fadila -> t("5 today · faḍīla shown")
    else -> t("5 today")
}

private fun settingsMadhabLine(madhab: String, method: String): String =
    "$madhab · ${method.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}"

/** Shared by every page in the hub: a way back, and a title. */
@Composable
internal fun HubPageFrame(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    /**
     * Changes when the page under this frame becomes a different page, so that stepping from a
     * long list into one entry starts at the top of it rather than wherever the list was left.
     */
    scrollKey: Any? = null,
    /** What the way out is called. A page inside a page goes back one step, not all the way. */
    backLabel: String = t("‹ Hub"),
    trailing: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scroll = key(scrollKey) { rememberScrollState() }
    HubPageChrome(
        title = title,
        onBack = onBack,
        subtitle = subtitle,
        backLabel = backLabel,
        trailing = trailing,
        footer = footer,
    ) { modifier ->
        Column(modifier = modifier.verticalScroll(scroll).padding(top = 20.dp)) {
            content()
        }
    }
}

/**
 * The same frame, over a list that composes only what is on the screen.
 *
 * A page whose body is a few dozen rows can be laid out in one go; a page whose body is the two
 * hundred and eighty-six āyāt of al-Baqara cannot, and measuring all of them at once is a visible
 * stall on the frame that swaps one sūrah for the next. Pages that long take this frame instead.
 */
@Composable
internal fun HubPageListFrame(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    scrollKey: Any? = null,
    backLabel: String = t("‹ Hub"),
    trailing: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val scroll = key(scrollKey) { rememberLazyListState() }
    HubPageChrome(
        title = title,
        onBack = onBack,
        subtitle = subtitle,
        backLabel = backLabel,
        trailing = trailing,
        footer = footer,
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            state = scroll,
            contentPadding = PaddingValues(top = 20.dp),
            content = content,
        )
    }
}

/**
 * The frame with nothing in the middle: the way back, the title, and the footer, around whatever
 * [body] makes of the space between them. [body] is handed the modifier for that space, so a page
 * whose middle is neither a column nor a list — one that pages sideways, say — can still be a
 * page of the hub.
 */
@Composable
internal fun HubPageChrome(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    backLabel: String = t("‹ Hub"),
    trailing: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
    body: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Text(
            text = backLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = Fainter,
            modifier = Modifier.noRippleClickable(onClick = onBack).padding(vertical = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
            }
            trailing?.invoke()
        }

        body(Modifier.fillMaxWidth().weight(1f))

        if (footer != null) {
            Hairline()
            Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
                footer()
            }
        }
    }
}

/** A row of a list where exactly one thing is chosen, marked by a gold bar down its left edge. */
@Composable
internal fun ChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .noRippleClickable(onClick = onSelect)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f).height(IntrinsicSize.Min)) {
            // The bar is drawn in the row's own left margin rather than beside the text, so the
            // names stay in one column whether they are chosen or not.
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (selected) Gold else Color.Transparent),
            )
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Dim,
                    )
                }
            }
        }
        if (selected) {
            Text(
                text = t("Selected"),
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
            )
        }
    }
}

/** A plain "label — value" line, which is most of what a settings list is. */
@Composable
internal fun DetailRow(
    label: String,
    value: String,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.noRippleClickable(onClick = onClick) else it }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) Gold else Faint,
        )
    }
}
