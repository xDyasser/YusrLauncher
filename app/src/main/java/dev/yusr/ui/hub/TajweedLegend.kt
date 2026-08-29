package dev.yusr.ui.hub

import dev.yusr.data.quran.Tajweed
import dev.yusr.ui.t

/** One colour on the page: the rules it stands for, what it is called, and what it asks for. */
internal data class LegendEntry(
    val rules: List<Tajweed.Rule>,
    val name: String,
    val gloss: String,
)

/**
 * The colours, in the order a reader meets the rules rather than the order of the palette: what
 * the nūn and the mīm do, then what belongs to a single letter, then the madds, which are the
 * only ones that are a length rather than a sound.
 *
 * Built on each call rather than held as a table, because the wording is translated and the
 * language can change under a running process.
 *
 * Every rule the engine knows appears here exactly once. `TajweedLegendTest` holds it to that, so
 * a rule added to [Tajweed] without a colour anybody can read fails the build.
 */
internal fun tajweedLegend(): List<LegendEntry> = listOf(
    LegendEntry(
        rules = listOf(
            Tajweed.Rule.GHUNNAH,
            Tajweed.Rule.IDGHAAM_GHUNNAH,
            Tajweed.Rule.IDGHAAM_SHAFAWI,
        ),
        name = t("Ghunnah — held in the nose"),
        gloss = t("A nūn or a mīm with a shadda, and a nūn at rest running into yāʾ, mīm, ") +
            t("wāw or another nūn: two counts through the nose."),
    ),
    LegendEntry(
        rules = listOf(
            Tajweed.Rule.IDGHAAM_NO_GHUNNAH,
            Tajweed.Rule.IDGHAAM_MUTAJAANISAIN,
            Tajweed.Rule.IDGHAAM_MUTAQAARIBAIN,
        ),
        name = t("Idghām — gone into the letter after it"),
        gloss = t("A letter not sounded on its own but doubled onto the next one, with no ") +
            t("ghunnah held: the nūn of مِن رَّبِّهِمۡ, the dāl of قَد تَّبَيَّنَ."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.IKHFA, Tajweed.Rule.IKHFA_SHAFAWI),
        name = t("Ikhfāʾ — hidden"),
        gloss = t("A nūn at rest or a tanwīn neither said plainly nor run into the letter ") +
            t("after it, and a mīm at rest before a bāʾ."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.IQLAB),
        name = t("Iqlāb — turned into a mīm"),
        gloss = t("A nūn at rest or a tanwīn read as a mīm before a bāʾ. The small mīm ") +
            t("written above it is the mushaf saying so."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.QALQALAH),
        name = t("Qalqalah — struck"),
        gloss = t("Qāf, ṭāʾ, bāʾ, jīm and dāl at rest: struck rather than leant on, with a ") +
            t("small echo after them."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.HAMZAT_WASL, Tajweed.Rule.LAM_SHAMSIYYAH),
        name = t("Not read where it stands"),
        gloss = t("The joining alif, read only when you begin on it, and the lām of ٱل that ") +
            t("the sun letter after it takes over."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.SILENT),
        name = t("Written and not read"),
        gloss = t("A letter the mushaf writes and the reading passes over."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.MADD_2),
        name = t("A madd written small — two counts"),
        gloss = t("The dagger alif, the small wāw of هُۥ, the small yāʾ of بِهِۦ: a letter of ") +
            t("madd written above the line, and read like one written on it."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.MADD_246),
        name = t("A madd stopped on — two, four or six"),
        gloss = t("A madd at the end of an ayah or one letter short of it, stretched by ") +
            t("stopping. Any of the three lengths, kept the same throughout."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.MADD_MUTTASIL, Tajweed.Rule.MADD_MUNFASIL),
        name = t("A madd meeting a hamza — four or five"),
        gloss = t("Muttaṣil where the hamza is in the same word, munfaṣil where it opens the ") +
            t("next one."),
    ),
    LegendEntry(
        rules = listOf(Tajweed.Rule.MADD_6),
        name = t("A madd of six counts"),
        gloss = t("A madd running into a letter at rest or one with a shadda, and the ") +
            t("letters that open a sūrah — alif lām mīm."),
    ),
)
