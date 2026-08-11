package dev.minimalist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import dev.minimalist.data.quran.Ayah
import dev.minimalist.data.settings.AyahLanguage
import androidx.compose.ui.graphics.Color
import dev.minimalist.ui.theme.Dim
import dev.minimalist.ui.theme.Faint
import dev.minimalist.ui.theme.Fainter
import dev.minimalist.ui.theme.Gold
import dev.minimalist.domain.AppTier
import dev.minimalist.ui.theme.Backdrop
import dev.minimalist.ui.theme.MinimalShape
import dev.minimalist.ui.theme.QuranQuoteStyle

/** No ripple anywhere in this app: visual feedback is one more thing to look at. */
@Composable
fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * A plain page: a small title that stays put, and content that scrolls under it. The title being
 * fixed matters on the long screens — the app list is taller than any phone.
 */
@Composable
fun MinimalPage(
    title: String,
    subtitle: String? = null,
    scrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding()
            .padding(horizontal = 26.dp),
    ) {
        Column(modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Faint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            content()
        }
    }
}

/**
 * The rule that separates one band of a screen from the next.
 *
 * A hairline at a tenth of the text colour rather than a box: it has to be findable when you look
 * for the edge of a section and invisible when you are reading across it.
 */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)),
    )
}

/** A small, widely tracked heading: "SCHOOL", "QIBLA", "KAHF PAUSE". */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color ?: Dim,
        modifier = modifier,
    )
}

/** The way back up, as a word rather than an arrow in a bar: t("‹ Hub"). */
@Composable
fun BackLine(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = "‹ $label",
        style = MaterialTheme.typography.bodyMedium,
        color = Dim,
        modifier = modifier
            .noRippleClickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

/**
 * A progress bar that is one pixel tall.
 *
 * Used for a countdown at the gate and for a download in the reader. It is a hairline that has
 * been partly filled in, which is as much weight as a progress indicator earns on a screen made
 * of text.
 */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(Gold),
        )
    }
}

/** An outlined text button. The only interactive shape in the app. */
@Composable
fun MinimalButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = if (enabled) MaterialTheme.colorScheme.onBackground else Faint,
        modifier = modifier
            .fillMaxWidth()
            .clip(MinimalShape)
            .border(1.dp, if (enabled) Faint else Fainter, MinimalShape)
            .noRippleClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
    )
}

/** A single tappable line of text, the app's main list idiom. */
@Composable
fun MinimalRow(
    label: String,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MinimalShape)
            .let { if (onClick != null) it.noRippleClickable(onClick = onClick) else it }
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (detail != null) {
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = Faint)
        }
    }
}

/** The four tiers as four boxes. The chosen one is the only one drawn in full strength. */
@Composable
fun TierPicker(
    selected: AppTier,
    modifier: Modifier = Modifier,
    onSelect: (AppTier) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTier.entries.forEach { tier ->
            val active = tier == selected
            val pill = RoundedCornerShape(12.dp)
            Text(
                text = tier.name.lowercase(Locale.getDefault()).take(4),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (active) MaterialTheme.colorScheme.onBackground else Faint,
                modifier = Modifier
                    .weight(1f)
                    .clip(pill)
                    .border(1.dp, if (active) Faint else Fainter, pill)
                    .noRippleClickable { onSelect(tier) }
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/**
 * A row of pills where exactly one is chosen. The same idiom as [TierPicker], for the settings
 * that are a short closed list — calculation method, madhhab, language.
 */
@Composable
fun PillPicker(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            val pill = RoundedCornerShape(12.dp)
            Text(
                text = option,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (active) MaterialTheme.colorScheme.onBackground else Faint,
                modifier = Modifier
                    .weight(1f)
                    .clip(pill)
                    .border(1.dp, if (active) Faint else Fainter, pill)
                    .noRippleClickable { onSelect(index) }
                    .padding(vertical = 11.dp),
            )
        }
    }
}

/**
 * An ayah, in whichever of the two languages the user asked for.
 *
 * The Arabic is set right-aligned and larger, with room between the lines — the script needs it,
 * and this is the one place in the app where the text is meant to be dwelt on rather than
 * scanned.
 */
@Composable
fun AyahBlock(
    ayah: Ayah,
    language: AyahLanguage,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (language != AyahLanguage.ENGLISH) {
            Text(
                text = ayah.arabic,
                // Amiri, not the interface face. This is the one place in the app where the
                // letterforms are meant to say "slow down" before the words do.
                style = QuranQuoteStyle,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val english = ayah.english
        if (language != AyahLanguage.ARABIC && english != null) {
            Text(
                text = english,
                style = MaterialTheme.typography.bodyMedium,
                color = if (language == AyahLanguage.ENGLISH) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    Faint
                },
                modifier = Modifier.padding(top = if (language == AyahLanguage.BOTH) 14.dp else 0.dp),
            )
        }
        val arabicOnly = language == AyahLanguage.ARABIC
        Text(
            text = ayah.reference(language).let {
                // Lowercasing is for the transliterated names; Arabic has no case to fold.
                if (arabicOnly) it else it.lowercase(Locale.getDefault())
            },
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            textAlign = if (arabicOnly) TextAlign.Right else TextAlign.Left,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
fun CenteredMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Faint,
            textAlign = TextAlign.Center,
        )
    }
}
