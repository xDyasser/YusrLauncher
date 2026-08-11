package dev.minimalist.data.quran

/**
 * The names of the sūrahs in Arabic, and the digits to go with them.
 *
 * These are held here rather than taken from the download because the reference has to read
 * correctly whether the full Qur'an was ever fetched or the bundled handful is standing in — and
 * because the name of a sūrah is not something that needs a network.
 */
object SurahNames {

    /** How many sūrahs there are, which is the bound on every loop over them. */
    const val COUNT = 114

    /** The Arabic name of [surah], or null if that is not a sūrah number. */
    fun arabic(surah: Int): String? = ARABIC.getOrNull(surah - 1)

    /** "Al-Fātiḥa" — the name set for a screen reading in English. */
    fun transliterated(surah: Int): String? = TRANSLITERATED.getOrNull(surah - 1)

    /** How many āyāt are in [surah]. Zero for a number that is not one. */
    fun ayahCount(surah: Int): Int = AYAH_COUNTS.getOrNull(surah - 1) ?: 0

    /**
     * The ayah after [surah]:[ayah] in mushaf order.
     *
     * It runs on into the next sūrah at the end of one, and al-Nās wraps back to al-Fātiḥa — so
     * there is no reference from which the next tap does nothing. A number that is not a sūrah
     * starts again at the opening rather than going further wrong.
     */
    fun next(surah: Int, ayah: Int): Pair<Int, Int> = when {
        surah !in 1..COUNT -> 1 to 1
        ayah < ayahCount(surah) -> surah to ayah + 1
        surah < COUNT -> surah + 1 to 1
        else -> 1 to 1
    }

    /**
     * The ayah before [surah]:[ayah] in mushaf order — the mirror of [next], and wrapping the same
     * way, so stepping backwards off the front of al-Fātiḥa lands on the last ayah of al-Nās.
     */
    fun previous(surah: Int, ayah: Int): Pair<Int, Int> = when {
        surah !in 1..COUNT -> 1 to 1
        ayah > 1 -> surah to ayah - 1
        surah > 1 -> (surah - 1) to ayahCount(surah - 1)
        else -> COUNT to ayahCount(COUNT)
    }

    /** Whether [surah] was revealed at Makkah rather than Madīnah. */
    fun isMakki(surah: Int): Boolean = surah in MAKKI

    /** "Sūra 18 · 110 āyāt · Makkī" — the line under the name in the reader. */
    fun subtitle(surah: Int): String {
        val place = if (isMakki(surah)) "Makkī" else "Madanī"
        return "Sūra $surah · ${ayahCount(surah)} āyāt · $place"
    }

    /** Every sūrah in order, for the index the reader opens onto. */
    fun all(): List<Int> = (1..COUNT).toList()

    /** "١٠٣" — the same number, in the digits the Arabic is set in. */
    fun arabicDigits(value: Int): String = value.toString()
        .map { character ->
            if (character in '0'..'9') EASTERN_DIGITS[character - '0'] else character
        }
        .joinToString("")

    private val EASTERN_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    private val ARABIC = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال",
        "التوبة", "يونس", "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء",
        "الكهف", "مريم", "طه", "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء",
        "النمل", "القصص", "العنكبوت", "الروم", "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر",
        "يس", "الصافات", "ص", "الزمر", "غافر", "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية",
        "الأحقاف", "محمد", "الفتح", "الحجرات", "ق", "الذاريات", "الطور", "النجم", "القمر",
        "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة", "الصف", "الجمعة",
        "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ",
        "النازعات", "عبس", "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق",
        "الأعلى", "الغاشية", "الفجر", "البلد", "الشمس", "الليل", "الضحى", "الشرح", "التين",
        "العلق", "القدر", "البينة", "الزلزلة", "العاديات", "القارعة", "التكاثر", "العصر",
        "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر", "المسد",
        "الإخلاص", "الفلق", "الناس",
    )

    private val TRANSLITERATED = listOf(
        "Al-Fātiḥa", "Al-Baqara", "Āl ʿImrān", "An-Nisāʾ", "Al-Māʾida",
        "Al-Anʿām", "Al-Aʿrāf", "Al-Anfāl", "At-Tawba", "Yūnus",
        "Hūd", "Yūsuf", "Ar-Raʿd", "Ibrāhīm", "Al-Ḥijr",
        "An-Naḥl", "Al-Isrāʾ", "Al-Kahf", "Maryam", "Ṭā Hā",
        "Al-Anbiyāʾ", "Al-Ḥajj", "Al-Muʾminūn", "An-Nūr", "Al-Furqān",
        "Ash-Shuʿarāʾ", "An-Naml", "Al-Qaṣaṣ", "Al-ʿAnkabūt", "Ar-Rūm",
        "Luqmān", "As-Sajda", "Al-Aḥzāb", "Sabaʾ", "Fāṭir",
        "Yā Sīn", "Aṣ-Ṣāffāt", "Ṣād", "Az-Zumar", "Ghāfir",
        "Fuṣṣilat", "Ash-Shūrā", "Az-Zukhruf", "Ad-Dukhān", "Al-Jāthiya",
        "Al-Aḥqāf", "Muḥammad", "Al-Fatḥ", "Al-Ḥujurāt", "Qāf",
        "Adh-Dhāriyāt", "Aṭ-Ṭūr", "An-Najm", "Al-Qamar", "Ar-Raḥmān",
        "Al-Wāqiʿa", "Al-Ḥadīd", "Al-Mujādila", "Al-Ḥashr", "Al-Mumtaḥana",
        "Aṣ-Ṣaff", "Al-Jumuʿa", "Al-Munāfiqūn", "At-Taghābun", "Aṭ-Ṭalāq",
        "At-Taḥrīm", "Al-Mulk", "Al-Qalam", "Al-Ḥāqqa", "Al-Maʿārij",
        "Nūḥ", "Al-Jinn", "Al-Muzzammil", "Al-Muddaththir", "Al-Qiyāma",
        "Al-Insān", "Al-Mursalāt", "An-Nabaʾ", "An-Nāziʿāt", "ʿAbasa",
        "At-Takwīr", "Al-Infiṭār", "Al-Muṭaffifīn", "Al-Inshiqāq", "Al-Burūj",
        "Aṭ-Ṭāriq", "Al-Aʿlā", "Al-Ghāshiya", "Al-Fajr", "Al-Balad",
        "Ash-Shams", "Al-Layl", "Aḍ-Ḍuḥā", "Ash-Sharḥ", "At-Tīn",
        "Al-ʿAlaq", "Al-Qadr", "Al-Bayyina", "Az-Zalzala", "Al-ʿĀdiyāt",
        "Al-Qāriʿa", "At-Takāthur", "Al-ʿAṣr", "Al-Humaza", "Al-Fīl",
        "Quraysh", "Al-Māʿūn", "Al-Kawthar", "Al-Kāfirūn", "An-Naṣr",
        "Al-Masad", "Al-Ikhlāṣ", "Al-Falaq", "An-Nās",
    )

    /** The number of āyāt in each sūrah, in the Ḥafṣ counting — 6,236 in all. */
    private val AYAH_COUNTS = listOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128,
        111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30,
        73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29,
        18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18,
        12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19,
        5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4,
        5, 6,
    )

    /** The sūrahs revealed at Makkah. The rest are Madanī. */
    private val MAKKI = setOf(
        1, 6, 7, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 23, 25,
        26, 27, 28, 29, 30, 31, 32, 34, 35, 36, 37, 38, 39, 40, 41, 42,
        43, 44, 45, 46, 50, 51, 52, 53, 54, 56, 67, 68, 69, 70, 71, 72,
        73, 74, 75, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
        90, 91, 92, 93, 94, 95, 96, 97, 100, 101, 102, 103, 104, 105, 106, 107,
        108, 109, 111, 112, 113, 114,
    )
}
