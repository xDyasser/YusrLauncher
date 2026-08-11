package dev.minimalist.domain

/**
 * The school whose practice the app should assume.
 *
 * This is deliberately one setting rather than five. Asked separately, "which asr school?",
 * "which calculation method?", "combine dhuhr and asr?" and "which book of supplications?" are
 * four questions almost nobody can answer; asked as "which madhab do you follow?" they are one
 * question everybody can. Each of them is still reachable on its own afterwards — picking a
 * school sets the defaults, it does not lock anything.
 */
enum class Madhab(
    val label: String,
    val arabicLabel: String,
    /** The one-line note under the name, which is the only thing distinguishing four of these. */
    val note: String,
    val asr: AsrMethod,
    val method: CalculationMethod,
    val branch: Branch,
    /** Whether dhuhr with asr and maghrib with isha are combined out of the box. */
    val combinesByDefault: Boolean,
) {
    HANAFI("Ḥanafī", "حنفي", "Later ʿasr", AsrMethod.HANAFI, CalculationMethod.MWL, Branch.SUNNI, false),
    SHAFII("Shāfiʿī", "شافعي", "Earlier ʿasr", AsrMethod.STANDARD, CalculationMethod.MWL, Branch.SUNNI, false),
    MALIKI("Mālikī", "مالكي", "Earlier ʿasr", AsrMethod.STANDARD, CalculationMethod.MWL, Branch.SUNNI, false),
    HANBALI("Ḥanbalī", "حنبلي", "Earlier ʿasr", AsrMethod.STANDARD, CalculationMethod.MWL, Branch.SUNNI, false),
    JAFARI(
        "Jaʿfarī",
        "جعفري",
        "Maghrib after sunset delay",
        AsrMethod.STANDARD,
        CalculationMethod.JAFARI,
        Branch.SHIA,
        combinesByDefault = true,
    ),
    ZAYDI("Zaydī", "زيدي", "Earlier ʿasr", AsrMethod.STANDARD, CalculationMethod.MWL, Branch.SHIA, false),
    ;

    /** Sunnī or Shīʿī, which is what the madhab screen prints beside the name. */
    enum class Branch(val label: String) {
        SUNNI("Sunnī"),
        SHIA("Shīʿī"),
    }

    /**
     * Which collection of supplications the adhkār and duʿāʾ screens read from.
     *
     * Two books ship in the app, and this is the only thing that chooses between them. It is a
     * default rather than a restriction: both remain readable from the duʿāʾ screen whatever is
     * set here, because nobody is served by an app that hides a supplication from them.
     */
    val collection: SupplicationCollection
        get() = if (branch == Branch.SHIA) {
            SupplicationCollection.MAFATIH
        } else {
            SupplicationCollection.HISN
        }

    /** The calculation defaults this school implies, as a config the solver can take straight. */
    fun defaultConfig(): PrayerConfig = PrayerConfig(method = method, asr = asr)
}

/** The two books of supplications bundled in the app. */
enum class SupplicationCollection(val title: String, val arabicTitle: String, val attribution: String) {
    MAFATIH(
        "Mafātīḥ al-Jinān",
        "مفاتيح الجنان",
        "Shaykh ʿAbbās al-Qummī · a selection",
    ),
    HISN(
        "Ḥiṣn al-Muslim",
        "حصن المسلم",
        "Saʿīd b. ʿAlī al-Qaḥṭānī · a selection",
    ),
}
