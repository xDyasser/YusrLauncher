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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yusr.R
import dev.yusr.container
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
 * The face for revelation, and for nothing else.
 *
 * Amiri is a naskh in the Būlāq tradition, which is what a mushaf looks like. Setting an ayah in
 * the same sans as the rest of the interface is legible and wrong; this is the one place in the
 * app where the reader is meant to slow down, and the letterforms should say so before the words
 * do.
 */
val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

/** Qur'anic text: generous leading, because vowel marks sit above and below the line. */
val QuranStyle = TextStyle(
    fontFamily = Amiri,
    fontWeight = FontWeight.Normal,
    fontSize = 25.sp,
    lineHeight = 51.sp,
)

/** The same, at the size an ayah is quoted rather than read. */
val QuranQuoteStyle = TextStyle(
    fontFamily = Amiri,
    fontWeight = FontWeight.Normal,
    fontSize = 21.sp,
    lineHeight = 41.sp,
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
