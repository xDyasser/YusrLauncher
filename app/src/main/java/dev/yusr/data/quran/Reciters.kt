package dev.yusr.data.quran

import dev.yusr.domain.Madhab

/**
 * Who is reciting.
 *
 * Recitation is the one thing in this app that is a matter of taste rather than of ruling: people
 * learn the Qur'an in one voice and hear every other one as slightly wrong. So this is a list to
 * choose from rather than a default to accept, and it spans both traditions — a Shia user handed
 * only the Ḥaramayn reciters, or a Sunni user handed only Parhizgar, has been handed the wrong
 * app.
 *
 * Every recording here is Ḥafṣ ʿan ʿĀṣim, which is what the bundled text is. A Warsh or Qālūn
 * recitation would not track the words on the screen, so offering one would be a bug rather than
 * a feature.
 */
data class Reciter(
    val id: String,
    val name: String,
    val arabicName: String,
    val tradition: Tradition,
    /** Roughly how much a whole Qur'an would take, to warn before a download. */
    val kbps: Int,
) {
    enum class Tradition(val label: String) {
        SUNNI("Sunnī"),
        SHIA("Shīʿī"),
    }

    /**
     * Where one ayah lives.
     *
     * The files are per ayah rather than per sūrah, which is what makes the reader able to
     * follow along a line at a time and to resume in the middle of al-Baqara. A "sūrah download"
     * is simply every ayah of it fetched in turn.
     */
    fun url(surah: Int, ayah: Int): String =
        "$HOST/$id/${pad(surah)}${pad(ayah)}.mp3"

    private fun pad(value: Int): String = value.toString().padStart(3, '0')

    companion object {
        const val HOST = "https://everyayah.com/data"
    }
}

object Reciters {

    /**
     * The list offered in the picker.
     *
     * These identifiers are folder names on the audio host, and the app cannot prove any of them
     * exists without asking — so it asks. The picker checks each one before offering it and says
     * plainly when a reciter cannot be reached, rather than letting a download fail silently
     * halfway through al-Baqara.
     */
    val ALL = listOf(
        Reciter("Minshawy_Murattal_128kbps", "Muḥammad Ṣiddīq al-Minshāwī", "محمد صديق المنشاوي", Reciter.Tradition.SUNNI, 128),
        Reciter("Abdul_Basit_Murattal_192kbps", "ʿAbd al-Bāsiṭ ʿAbd al-Ṣamad", "عبد الباسط عبد الصمد", Reciter.Tradition.SUNNI, 192),
        Reciter("Husary_128kbps", "Maḥmūd Khalīl al-Ḥuṣarī", "محمود خليل الحصري", Reciter.Tradition.SUNNI, 128),
        Reciter("Abdurrahmaan_As-Sudais_192kbps", "ʿAbd al-Raḥmān al-Sudais", "عبد الرحمن السديس", Reciter.Tradition.SUNNI, 192),
        Reciter("Alafasy_128kbps", "Mishārī Rāshid al-ʿAfāsī", "مشاري راشد العفاسي", Reciter.Tradition.SUNNI, 128),
        Reciter("Abu_Bakr_Ash-Shaatree_128kbps", "Abū Bakr al-Shāṭirī", "أبو بكر الشاطري", Reciter.Tradition.SUNNI, 128),
        Reciter("Hani_Rifai_192kbps", "Hānī al-Rifāʿī", "هاني الرفاعي", Reciter.Tradition.SUNNI, 192),
        Reciter("Mohammad_al_Tablaway_128kbps", "Muḥammad al-Ṭablāwī", "محمد الطبلاوي", Reciter.Tradition.SUNNI, 128),
        Reciter("Parhizgar_48kbps", "Shahriyār Parhīzgār", "شهريار برهيزكار", Reciter.Tradition.SHIA, 48),
        Reciter("Karim_Mansoori_40kbps", "Karīm Manṣūrī", "كريم منصوري", Reciter.Tradition.SHIA, 40),
        Reciter("Sahl_Yassin_128kbps", "Sahl Yāsīn", "سهل ياسين", Reciter.Tradition.SHIA, 128),
        Reciter("Ahmed_Neana_128kbps", "Aḥmad Nuʿayna", "أحمد نعينع", Reciter.Tradition.SUNNI, 128),
    )

    fun byId(id: String?): Reciter? = ALL.firstOrNull { it.id == id }

    /**
     * Who to put at the top of the list for someone who follows [madhab].
     *
     * A preference, not a filter: the whole list stays visible underneath, because plenty of
     * people listen across the line and nobody should have to change their madhab to hear
     * al-Ḥuṣarī.
     */
    fun suggestedFor(madhab: Madhab): Reciter.Tradition = when (madhab.branch) {
        Madhab.Branch.SHIA -> Reciter.Tradition.SHIA
        Madhab.Branch.SUNNI -> Reciter.Tradition.SUNNI
    }

    /** The list with [madhab]'s own tradition first, each group still labelled. */
    fun orderedFor(madhab: Madhab): List<Reciter> {
        val first = suggestedFor(madhab)
        return ALL.sortedBy { if (it.tradition == first) 0 else 1 }
    }
}
