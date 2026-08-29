package dev.yusr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yusr.R
import dev.yusr.container
import dev.yusr.data.quran.Tajweed
import dev.yusr.data.settings.ThemeMode

// The night palette. Not a grey scale: the ground is a green-black and the text is a warm
// off-white, which is what stops a screen made entirely of text from reading as a terminal.
private val Night = Color(0xFF0D0F0E)
private val NightBright = Color(0xFFF2EEE4)
private val NightText = Color(0xFFE9E5DB)
private val NightMuted = Color(0xFF8B9088)
private val NightFaint = Color(0xFF6F746C)
private val NightEdge = Color(0xFF2A2E2B)

// The day palette, from the Arabic light mockup: unbleached paper rather than white.
private val Day = Color(0xFFF4F1E9)
private val DayBright = Color(0xFF171A16)
private val DayText = Color(0xFF23261F)
private val DayMuted = Color(0xFF5E6459)
private val DayFaint = Color(0xFF77796F)
private val DayEdge = Color(0xFFDAD5C7)

/**
 * The one accent in the app: old gold, on both grounds.
 *
 * It is spent on exactly two things — the prayer that is next, and the thing you are in the
 * middle of. Everywhere else the hierarchy is carried by weight and by how faint the grey is,
 * which is why a single warm colour still reads as an event when it appears.
 */
private val NightGold = Color(0xFFB99A5B)
private val DayGold = Color(0xFF8A6B2E)

private val NightScheme = darkColorScheme(
    primary = NightBright,
    onPrimary = Night,
    secondary = NightGold,
    onSecondary = Night,
    tertiary = NightMuted,
    background = Night,
    onBackground = NightText,
    surface = Night,
    onSurface = NightText,
    surfaceVariant = Night,
    onSurfaceVariant = NightMuted,
    outline = NightEdge,
    outlineVariant = NightFaint,
    error = NightBright,
    onError = Night,
)

private val DayScheme = lightColorScheme(
    primary = DayBright,
    onPrimary = Day,
    secondary = DayGold,
    onSecondary = Day,
    tertiary = DayMuted,
    background = Day,
    onBackground = DayText,
    surface = Day,
    onSurface = DayText,
    surfaceVariant = Day,
    onSurfaceVariant = DayMuted,
    outline = DayEdge,
    outlineVariant = DayFaint,
    error = DayBright,
    onError = Day,
)

/**
 * The page itself. Named rather than hard-coded so light mode is a scheme swap, not a hunt
 * through every screen for a black rectangle.
 */
val Backdrop: Color
    @Composable get() = MaterialTheme.colorScheme.background

/** The brightest text on the page: the clock, a heading, the ayah being read. */
val Bright: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/** Secondary text: captions, hints, the things you are not meant to read twice. */
val Faint: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Fainter still, but still text: timestamps, counts, the line under a heading. */
val Dim: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

/**
 * Borders, and borders only.
 *
 * This is an edge colour: on the near-black ground it is a line you can find, not text you can
 * read. Anything with words in it uses [Dim] at the faintest — a hint you cannot see is a hint
 * that is not there, and the dhikr to be typed at the gate was exactly that.
 */
val Fainter: Color
    @Composable get() = MaterialTheme.colorScheme.outline

/** The accent. Reach for it only for the next prayer and the thing in progress. */
val Gold: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

/** The one corner radius in the app. Everything that has an edge uses this and nothing else. */
val YusrShape = RoundedCornerShape(16.dp)

/**
 * The interface face. Latin and Arabic are cut as one family here, so a screen that mixes a
 * sūra name with an English subtitle keeps a single voice instead of two.
 */
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_extralight, FontWeight.ExtraLight),
    Font(R.font.ibm_plex_sans_arabic_light, FontWeight.Light),
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
)

/**
 * A naskh in the Būlāq tradition, for Arabic that is *about* the Qur'an rather than the Qur'an:
 * the sūrah's name in its band, and anywhere else the interface wants the older letterforms.
 *
 * It is not the face the text itself is set in. Amiri draws its vowel marks at a fixed height
 * above the baseline instead of anchoring them to the letter beneath, which ordinary Arabic
 * survives and the Uthmani orthography does not — a word like أَنۡعَمۡتَ comes out with its
 * ḥarakāt strung along above the line rather than sitting on their letters.
 */
val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

/**
 * The face for revelation, and for nothing else.
 *
 * This is the face of the printed Madani mushaf — the one the King Fahd Complex cut for the
 * Uthmani text and gave away, and the one the page layout this app draws from was measured on.
 * It carries an anchor for every mark on every letter it can follow, which is the whole
 * difference between a mushaf and Arabic with vowel marks scattered over it: the sukūn sits in
 * the notch of the letter it stops, the shadda and the vowel stack in the order they are read,
 * and a tall stack over a tooth does not collide with the line above.
 *
 * Two of its habits shape the code that uses it. Its Arabic-Indic digits are drawn as ayah
 * numbers already inside their medallion, so a page sets the number alone and never adds a ۝ of
 * its own; and it predates the codepoints Unicode gave the open tanwīn, which is why the text is
 * put through [dev.yusr.data.quran.UthmaniText.printed] before it is set.
 *
 * Redistributed under the licence the font carries: free to use and copy, not to be modified —
 * so it is committed exactly as the Complex published it, byte for byte.
 */
val UthmanicHafs = FontFamily(
    Font(R.font.uthmanic_hafs, FontWeight.Normal),
)

/**
 * Qur'anic text: generous leading, because the marks of this orthography stack a long way above
 * the line and hang below it. Measured over the whole book, the tallest ayah of the mushaf inks
 * 1.22 em over the baseline and 0.61 em under it, so a line needs about 1.85 of its own size
 * before two of them can touch.
 */
val QuranStyle = TextStyle(
    fontFamily = UthmanicHafs,
    fontWeight = FontWeight.Normal,
    fontSize = 25.sp,
    lineHeight = 46.sp,
)

/** The same, at the size an ayah is quoted rather than read. */
val QuranQuoteStyle = TextStyle(
    fontFamily = UthmanicHafs,
    fontWeight = FontWeight.Normal,
    fontSize = 21.sp,
    lineHeight = 42.sp,
)

/**
 * Arabic that is read at length but is not the Qur'an: the supplications of Mafātīḥ al-Jinān and
 * of Ḥiṣn al-Muslim.
 *
 * They are set in the naskh rather than in the mushaf's own face, which is the honest thing —
 * al-Qummī's book is not revelation — and also the practical one, since that face carries the
 * letters and marks of the Uthmani orthography and little else, not the punctuation a printed
 * duʿāʾ is set with.
 */
val SupplicationStyle = TextStyle(
    fontFamily = Amiri,
    fontWeight = FontWeight.Normal,
    fontSize = 25.sp,
    lineHeight = 51.sp,
)

/**
 * One family, a handful of sizes. The display sizes are set very light and very tight and the
 * labels small and widely tracked; the distance between those two extremes is doing all the work
 * that colour usually does.
 */
private val YusrTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 80.sp,
        lineHeight = 76.sp,
        letterSpacing = (-3.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 46.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.9).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Light,
        fontSize = 22.sp,
        lineHeight = 31.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Light,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 2.0.sp,
    ),
)

/** Follows the system by default; the setting only exists for people who want it pinned. */
@Composable
fun YusrTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val store = remember { context.container.settingsStore }
    val settings by store.settings.collectAsState(initial = null)
    val systemDark = isSystemInDarkTheme()

    val dark = when (settings?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = if (dark) NightScheme else DayScheme,
        typography = YusrTypography,
        content = content,
    )
}

/**
 * The colours the mushaf uses when tajwīd is turned on.
 *
 * The rest of this app spends one accent on two things and leaves everything else to grey. A
 * colour mushaf cannot work that way — a reader has to tell a madd from a ghunnah at a glance —
 * so this is the one palette in the app, and it is built the way the printed colour masaahif
 * build theirs: by family rather than by rule. The madds run warm, from the six counts of a
 * lāzim down to the two of a hidden one; everything nasal is green; what is assimilated without
 * a ghunnah is violet; qalqalah is blue; and a letter that is written but not read is grey,
 * which is the page saying "there is nothing here to say".
 *
 * Two sets, because a colour that reads on a green-black ground is a stain on unbleached paper.
 */
private val NightTajweed: Map<Tajweed.Rule, Color> = mapOf(
    Tajweed.Rule.MADD_6 to Color(0xFFD06A5A),
    Tajweed.Rule.MADD_MUTTASIL to Color(0xFFD98E4A),
    Tajweed.Rule.MADD_MUNFASIL to Color(0xFFD98E4A),
    Tajweed.Rule.MADD_246 to Color(0xFFC9A15C),
    Tajweed.Rule.MADD_2 to Color(0xFF9AAE72),
    Tajweed.Rule.GHUNNAH to Color(0xFF6FA97A),
    Tajweed.Rule.IDGHAAM_GHUNNAH to Color(0xFF6FA97A),
    Tajweed.Rule.IDGHAAM_SHAFAWI to Color(0xFF6FA97A),
    Tajweed.Rule.IKHFA to Color(0xFF5FA39A),
    Tajweed.Rule.IKHFA_SHAFAWI to Color(0xFF5FA39A),
    Tajweed.Rule.IQLAB to Color(0xFF5E93A8),
    Tajweed.Rule.QALQALAH to Color(0xFF6E8FC7),
    Tajweed.Rule.IDGHAAM_NO_GHUNNAH to Color(0xFFA088C0),
    Tajweed.Rule.IDGHAAM_MUTAJAANISAIN to Color(0xFFA088C0),
    Tajweed.Rule.IDGHAAM_MUTAQAARIBAIN to Color(0xFFA088C0),
    Tajweed.Rule.HAMZAT_WASL to Color(0xFF7E8379),
    Tajweed.Rule.LAM_SHAMSIYYAH to Color(0xFF7E8379),
    Tajweed.Rule.SILENT to Color(0xFF6B6F66),
)

private val DayTajweed: Map<Tajweed.Rule, Color> = mapOf(
    Tajweed.Rule.MADD_6 to Color(0xFF9E3B2C),
    Tajweed.Rule.MADD_MUTTASIL to Color(0xFFA9631E),
    Tajweed.Rule.MADD_MUNFASIL to Color(0xFFA9631E),
    Tajweed.Rule.MADD_246 to Color(0xFF8A6B2E),
    Tajweed.Rule.MADD_2 to Color(0xFF5F7238),
    Tajweed.Rule.GHUNNAH to Color(0xFF3D6B48),
    Tajweed.Rule.IDGHAAM_GHUNNAH to Color(0xFF3D6B48),
    Tajweed.Rule.IDGHAAM_SHAFAWI to Color(0xFF3D6B48),
    Tajweed.Rule.IKHFA to Color(0xFF2F6B65),
    Tajweed.Rule.IKHFA_SHAFAWI to Color(0xFF2F6B65),
    Tajweed.Rule.IQLAB to Color(0xFF35637A),
    Tajweed.Rule.QALQALAH to Color(0xFF3A5A9B),
    Tajweed.Rule.IDGHAAM_NO_GHUNNAH to Color(0xFF6B4E93),
    Tajweed.Rule.IDGHAAM_MUTAJAANISAIN to Color(0xFF6B4E93),
    Tajweed.Rule.IDGHAAM_MUTAQAARIBAIN to Color(0xFF6B4E93),
    Tajweed.Rule.HAMZAT_WASL to Color(0xFF6B6F66),
    Tajweed.Rule.LAM_SHAMSIYYAH to Color(0xFF6B6F66),
    Tajweed.Rule.SILENT to Color(0xFF8A8D84),
)

/** The set that suits the ground the page is being drawn on. */
val TajweedColours: Map<Tajweed.Rule, Color>
    @Composable get() =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) NightTajweed else DayTajweed
