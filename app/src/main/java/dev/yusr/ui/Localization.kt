package dev.yusr.ui

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import dev.yusr.data.settings.Language
import dev.yusr.domain.AppTier
import java.util.Locale

/**
 * The app in Arabic, if that is what was asked for.
 *
 * Strings are keyed on their own English text rather than on invented names. That is a deliberate
 * trade: it gives up the compiler's help — a typo is a missing translation rather than a build
 * failure — and buys back the thing that matters more here, which is that every call site still
 * reads as the sentence it prints. `t("go and pray. that is the whole idea.")` says what will be
 * on the screen; `stringResource(R.string.block_salah_body)` does not, and a screen whose whole
 * argument is its wording is one where that matters.
 *
 * A missing translation falls through to the English. That is the right failure: an app half
 * translated is still usable, and an app that crashes or shows a resource name is not.
 *
 * The direction is not handled here at all. Arabic is a right-to-left locale, the manifest allows
 * mirroring, and Compose reads the layout direction off the configuration — so the screens turn
 * round on their own, and every `start`/`end` in the app becomes the other edge. The few places
 * that must not mirror are the ones already pinned with an absolute `Right`: Qur'anic text is set
 * flush right whatever language the interface is in.
 *
 * [t] is not a composable, on purpose. Choosing a language hands it to the system, which recreates
 * every activity with the new configuration, so nothing here has to be watched for changes — and a
 * plain function can be called from the places that also have to speak: a `when` that picks a
 * headline, a notification built in a service, a helper that names the days of the week.
 */
fun t(english: String): String =
    if (Locale.getDefault().language == ARABIC) ArabicStrings.TABLE[english] ?: english else english

/**
 * The same, with the values filled in. Positional, so a translation may put them in another order,
 * and forgiving: a format that does not match its arguments prints the English rather than
 * throwing, because a crash on a settings screen is worse than a sentence in the wrong language.
 */
fun t(english: String, vararg args: Any?): String =
    runCatching { String.format(t(english), *args) }.getOrDefault(english)

/**
 * Hands the choice to the system, which then owns it: it survives a restart, it shows up in
 * Android's own per-app language screen, and it recreates whatever is on screen.
 *
 * [Language.SYSTEM] clears it rather than pinning the phone's current language, so that someone
 * who later switches their phone to Arabic gets an Arabic launcher without coming back here.
 */
fun applyLanguage(context: Context, language: Language) {
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    val wanted = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    // Setting the same value again would recreate the activity for nothing, and on the home
    // screen that is a visible flash on every settings visit.
    if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
}

/** What the app is speaking right now, whoever decided it. */
fun isArabic(): Boolean = Locale.getDefault().language == ARABIC

/**
 * What a tier is called where there is room for the word — the line under an app's name, the
 * description of a change waiting out its cooldown.
 *
 * The enum's own spelling is not it. `AppTier.FAVORITE.name.lowercase()` is "favorite" in every
 * language on earth, and the four pills used to be that string cut to four letters, which gives
 * "favo" and "allo" in English and four letters of nothing in Arabic.
 */
fun tierName(tier: AppTier): String = t(
    when (tier) {
        AppTier.FAVORITE -> "favourite"
        AppTier.ALLOWED -> "allowed"
        AppTier.GATED -> "gated"
        AppTier.BLOCKED -> "blocked"
    },
)

/** The same four, short enough that all of them fit across one row of pills. */
fun tierPill(tier: AppTier): String = t(
    when (tier) {
        AppTier.FAVORITE -> "fav"
        AppTier.ALLOWED -> "open"
        AppTier.GATED -> "gate"
        AppTier.BLOCKED -> "block"
    },
)

private const val ARABIC = "ar"

/** The Arabic of the interface, in the language the app was written in as the key. */
internal object ArabicStrings {
    val TABLE: Map<String, String> = buildMap {
        putAll(HOME)
        putAll(HUB)
        putAll(GATE_AND_BLOCK)
        putAll(SETTINGS)
        putAll(SETUP)
        putAll(DOMAIN)
        putAll(UNITS)
        putAll(PENDING)
        putAll(PROSE)
    }
}

/**
 * Durations, which are text too.
 *
 * "45m" is not a number with a symbol after it, it is English — the m is the first letter of
 * "minutes", and on an Arabic screen it is a Latin letter sitting in the middle of a right-to-left
 * line saying nothing. The digits stay as they are, because the rest of the app sets numbers that
 * way; only the letter changes.
 */
private val UNITS = mapOf(
    "%sh" to "%s س",
    "%sm" to "%s د",
    "%ss" to "%s ث",
    "%sh %sm" to "%s س %s د",
    "%sm %ss" to "%s د %s ث",
)

/**
 * What a change waiting out its cooldown is called on the pending-changes screen.
 *
 * These are written when the change is queued rather than when it is shown, because that is when
 * the words exist — the row in the database keeps a sentence, not a sentence and its arguments.
 * A cooldown is minutes, so the only way to see one of these in the wrong language is to change
 * language while a change is waiting, and then it says what it said when you asked for it.
 *
 * The arrow points the way the language runs: → in English, ← in Arabic, the same as everywhere
 * else in the app that names a path through the settings.
 */
private val PENDING = mapOf(
    "%s → %s" to "%s ← %s",
    "%s daily limit → %s" to "حدّ %s اليومي ← %s",
    "%s daily opens → %s" to "فتحات %s اليومية ← %s",
    "base wait → %s" to "الانتظار الأساسي ← %s",
    "escalation → %s per open" to "التصاعد ← %s لكل فتحة",
    "reason length → %s" to "طول السبب ← %s",
    "session length → %s" to "طول الجلسة ← %s",
    "cooldown → %s" to "مهلة الترخية ← %s",
    "bypasses → %s per week" to "الاستثناءات ← %s أسبوعيًّا",
    "salah pause → %s before, %s after" to "وقفة الصلاة ← %s قبلها و%s بعدها",
    "turn off “%s”" to "إيقاف ”%s“",
    "delete “%s”" to "حذف ”%s“",
    "%s opens during salah" to "%s يُفتح أثناء الصلاة",
    "%s opens when another app sends you to it" to "%s يُفتح حين يرسلك إليه تطبيق آخر",
    "unlimited" to "بلا حدّ",
)

/**
 * Words that come out of the domain rather than off a screen — the school you follow, the day the
 * fasting calendar has found, the point of the compass. They are keyed the same way as everything
 * else so that the screen printing them does not have to know where they came from.
 */
private val DOMAIN = mapOf(
    "Ḥanafī" to "حنفي",
    "Shāfiʿī" to "شافعي",
    "Mālikī" to "مالكي",
    "Ḥanbalī" to "حنبلي",
    "Jaʿfarī" to "جعفري",
    "Zaydī" to "زيدي",
    "Sunnī" to "سنّي",
    "Shīʿī" to "شيعي",
    "Later ʿasr" to "العصر متأخّرًا",
    "Earlier ʿasr" to "العصر مبكّرًا",
    "Maghrib after sunset delay" to "المغرب بعد تأخّر الغروب",
    "Ramaḍān" to "رمضان",
    "ʿĀshūrāʾ" to "عاشوراء",
    "ʿArafah" to "عرفة",
    "Monday" to "الإثنين",
    "Thursday" to "الخميس",
    "White days · %s %s" to "الأيام البيض · %s %s",
    "ʿĪd al-Fiṭr · no fasting" to "عيد الفطر · لا صوم",
    "ʿĪd al-Aḍḥā · no fasting" to "عيد الأضحى · لا صوم",
    "Days of tashrīq · no fasting" to "أيام التشريق · لا صوم",
    "N" to "ش",
    "NNE" to "ش ش شرق",
    "NE" to "ش شرق",
    "ENE" to "شرق ش شرق",
    "E" to "شرق",
    "ESE" to "شرق ج شرق",
    "SE" to "ج شرق",
    "SSE" to "ج ج شرق",
    "S" to "ج",
    "SSW" to "ج ج غرب",
    "SW" to "ج غرب",
    "WSW" to "غرب ج غرب",
    "W" to "غرب",
    "WNW" to "غرب ش غرب",
    "NW" to "ش غرب",
    "NNW" to "ش ش غرب",
)

private val HOME = mapOf(
    // Not "المفاتيح". That is the plural of "key", which is not what a hub is in either language,
    // and in an app that ships Mafātīḥ al-Jinān it is the name of one of the books on the shelf.
    // "الأوراد" is the word for the portions of worship a person keeps to daily — the Qur'an read,
    // the dhikr counted, the duʿāʾ said, the fast marked — which is exactly what is behind this
    // door and nothing that is not.
    "Devotions" to "الأوراد",
    "All apps" to "كل التطبيقات",
    "All apps · %s" to "كل التطبيقات · %s",
    "Dhikr" to "ذِكر",
    "done" to "تمّ",
    "no favourites yet" to "لا مفضّلات بعد",
    "tap for the next ayah →" to "← المس للآية التالية",
    "setup unfinished — hold the clock" to "الإعداد لم يكتمل — المس الساعة مطوّلًا",
    "%s in %s" to "%s بعد %s",
    "faḍīla %s" to "الفضيلة %s",
    "faḍīla %s · %s" to "الفضيلة %s · %s",
    "Fajr" to "الفجر",
    "Sunrise" to "الشروق",
    "Dhuhr" to "الظهر",
    "ʿAsr" to "العصر",
    "Maghrib" to "المغرب",
    "ʿIshāʾ" to "العشاء",
    "type to find" to "اكتب للبحث",
    "at least %s letters" to "%s أحرف على الأقل",
    "nothing matches" to "لا نتائج",
    "blocked" to "محجوب",
    "gated" to "خلف البوّابة",
    "That app has no launch screen." to "هذا التطبيق بلا شاشة تُفتح.",
    " · off" to " · مُعطّل",
    "%s sets · %s today" to "%s دورة · %s اليوم",
)

private val HUB = mapOf(
    "Prayer times" to "أوقات الصلاة",
    "Prayer" to "الصلاة",
    "Qur'an" to "القرآن",
    "Tasbīḥ" to "التسبيح",
    "Adhkār" to "الأذكار",
    "Duʿāʾ" to "الدعاء",
    "Fasting" to "الصيام",
    "Madhab" to "المذهب",
    "Madhab & calculation" to "المذهب والحساب",
    "Change" to "تغيير",
    "not set" to "غير محدّد",
    "not set up" to "لم يُضبط",
    "Not set" to "غير محدّد",
    "morning and evening" to "الصباح والمساء",
    "supplications" to "أدعية",
    "%s today" to "%s اليوم",
    "%s this month" to "%s هذا الشهر",
    "%s · today" to "%s · اليوم",
    "%s in this part" to "%s في هذا الباب",
    // Ḥiṣn's parts. Mafātīḥ's are the book's own Arabic already and pass through untouched.
    "Morning" to "الصباح",
    "After fajr" to "بعد الفجر",
    "Evening" to "المساء",
    "After ʿaṣr" to "بعد العصر",
    "After prayer" to "بعد الصلاة",
    "Taʿqībāt" to "التعقيبات",
    "Going out" to "الخروج",
    "Leaving, travelling, returning" to "الخروج والسفر والعودة",
    "%s texts" to "%s نصًّا",
    "‹ Devotions" to "الأوراد ›",
    "‹ %s" to "%s ›",
    "Selected" to "المختار",
    "Qibla" to "القبلة",
    "set a location to find the qibla" to "حدّد موقعك لتظهر القبلة",
    "no compass · north is up" to "لا بوصلة · الشمال إلى أعلى",
    "offline compass" to "بوصلة بلا شبكة",
    "qibla" to "القبلة",
    "5 today" to "خمس اليوم",
    "5 today · faḍīla shown" to "خمس اليوم · مع أوقات الفضيلة",
    "Computed on this phone · your location never leaves it" to
        "يُحسب في هذا الهاتف · موقعك لا يغادره",
    "No location set, so there is no timetable to show." to
        "لا موقع محدّد، فلا جدول يُعرض.",
    "faḍīla to %s" to "الفضيلة إلى %s",
    "Sunrise · fajr ends" to "الشروق · نهاية وقت الفجر",
    "Midnight · ʿishāʾ ends" to "منتصف الليل · نهاية وقت العشاء",
    "Last third · qiyām al-layl" to "الثلث الأخير · قيام الليل",
    "midnight %s · last third %s" to "منتصف الليل %s · الثلث الأخير %s",
    "%s from %s" to "%s من %s",
    "in %s" to "بعد %s",
    "%s ago" to "منذ %s",
    "now" to "الآن",
    // ---- madhab and calculation
    "School" to "المذهب",
    "Convention" to "الاصطلاح",
    "Method" to "طريقة الحساب",
    "High-latitude rule" to "قاعدة خطوط العرض العالية",
    "Manual offsets" to "تعديلات يدوية",
    "Show faḍīla windows" to "إظهار أوقات الفضيلة",
    "Combine prayers" to "الجمع بين الصلاتين",
    "Both pairs" to "الجمعان",
    "Dhuhr & ʿasr" to "الظهر والعصر",
    "Maghrib & ʿishāʾ" to "المغرب والعشاء",
    "Location" to "الموقع",
    "On" to "مُفعّل",
    "Off" to "مُعطّل",
    "None" to "بلا",
    "Second shadow" to "المثل الثاني",
    "First shadow" to "المثل الأول",
    "Middle of night" to "منتصف الليل",
    "Seventh of night" to "سُبع الليل",
    "Angle-based" to "بحسب الزاوية",
    "Muslim World League" to "رابطة العالم الإسلامي",
    "Islamic Society of North America" to "الجمعية الإسلامية لأمريكا الشمالية",
    "Egyptian General Authority" to "الهيئة المصرية العامة للمساحة",
    "Umm al-Qurā" to "أم القرى",
    "University of Karachi" to "جامعة كراتشي",
    "Jaʿfarī · Leva, Qum" to "جعفري · معهد ليوا، قم",
    "University of Tehran" to "جامعة طهران",
    // ---- Qur'an
    "All sūras" to "كل السور",
    "Sūras" to "السور",
    "Reciter" to "القارئ",
    "Bookmarked" to "علامة",
    "choose a reciter" to "اختر قارئًا",
    "Play" to "تشغيل",
    "Pause" to "إيقاف",
    "download" to "تنزيل",
    "failed · try again" to "أخفق · أعد المحاولة",
    "checking…" to "يُتحقّق…",
    "not reachable" to "غير متاح",
    "%s kbps" to "%s كيلوبت/ث",
    "Ḥafṣ · %s" to "حفص · %s",
    "downloading %s/%s · stop" to "يُنزَّل %s/%s · أوقف",
    "resume · %s/%s" to "استأنف · %s/%s",
    "Ḥafṣ ʿan ʿĀṣim · downloaded per sūra" to "حفص عن عاصم · يُنزَّل سورةً سورة",
    "Recitation is fetched once and then plays with no network at all. " to
        "تُنزَّل التلاوة مرّة ثم تُشغَّل بلا شبكة البتّة. ",
    "A whole sūra at 128 kbps is roughly a megabyte a minute." to
        "السورة كاملة بجودة ١٢٨ كيلوبت/ث نحو ميغابايت لكل دقيقة.",
    // ---- tasbih
    "back one" to "تراجع",
    "reset" to "تصفير",
    "tap anywhere" to "المس أيّ موضع",
    "of %s" to "من %s",
    // ---- supplications
    "Set a madhab to choose a collection." to "حدّد مذهبك ليُختار الكتاب.",
    "read the other book" to "اقرأ الكتاب الآخر",
    "back to your school's book" to "عُد إلى كتاب مذهبك",
    "show the translation" to "أظهر الترجمة",
    "hide the translation" to "أخفِ الترجمة",
    "end" to "تمّ النص",
    "very long" to "طويل جدًّا",
    "long" to "طويل",
    "medium" to "متوسط",
    "short" to "قصير",
    // ---- fasting
    "nothing due today" to "لا صوم اليوم",
    "%s kept this month" to "%s صُمن هذا الشهر",
    "Suhūr ends" to "نهاية السحور",
    "Ifṭār" to "الإفطار",
    "The next three weeks" to "الأسابيع الثلاثة القادمة",
    "kept" to "صُمْت",
    "mark" to "سجّل",
    "due" to "مندوب",
)

private val GATE_AND_BLOCK = mapOf(
    "Kahf pause" to "وقفة الكهف",
    "the wait starts once both are done" to "يبدأ العدّ متى تمّ الأمران",
    "go on then" to "ادخل إذن",
    "or put the phone down" to "أو ضَعِ الهاتف",
    "once" to "مرّة واحدة",
    "%s times" to "%s مرّات",
    "open for %s min" to "يُفتح %s دقيقة",
    "type: %s" to "اكتب: %s",
    "why, in at least %s characters" to "لماذا، في %s حرفًا على الأقل",
    "You have opened it %s today, for %s." to "فتحته %s اليوم، بمقدار %s.",
    "You have opened it %s today." to "فتحته %s اليوم.",
    "%s is in %s." to "%s بعد %s.",
    "time is up" to "انتهى الوقت",
    "salah" to "الصلاة",
    "not right now" to "ليس الآن",
    "that was the last one" to "كان ذلك آخرها",
    "budget spent" to "نفد النصيب",
    "back to home" to "العودة إلى الشاشة الرئيسة",
    "use one" to "استعمل واحدة",
    "spend a bypass" to "أنفِق استثناء",
    "no bypasses left this week." to "لا استثناءات باقية هذا الأسبوع.",
    "this will be logged. say what the emergency is." to
        "سيُسجَّل هذا. اذكر ما الطارئ.",
    "emergency bypass · %s left this week" to "استثناء طارئ · بقي %s هذا الأسبوع",
    "go and pray. that is the whole idea." to "قُم فصلِّ. هذا كل المقصود.",
    "it resets tomorrow. that is the whole idea." to "يعود غدًا. هذا كل المقصود.",
    "the phone is closed for salah." to "الهاتف مغلق للصلاة.",
    "the phone is closed for salah. it opens again in %s." to
        "الهاتف مغلق للصلاة. يُفتح بعد %s.",
    "a blackout window is in force. only favourites open until it ends." to
        "نافذة إظلام سارية. لا يُفتح إلا المفضّل حتى تنتهي.",
    "your session on %s has ended." to "انتهت جلستك في %s.",
    "%s is blocked outright. no countdown will open it." to
        "%s محجوب تمامًا. لا عدّ يفتحه.",
    "you have used every open you allowed yourself on %s today." to
        "استنفدت كل مرّات الفتح التي سمحت بها لنفسك في %s اليوم.",
    "you have spent today's minutes on %s." to "أنفقت دقائق اليوم في %s.",
)

private val SETTINGS = mapOf(
    "Settings" to "الإعدادات",
    "Usage" to "الاستعمال",
    "Apps and limits" to "التطبيقات والحدود",
    "Blackout windows" to "نوافذ الإظلام",
    "Prayer times and salah" to "أوقات الصلاة والوقفة",
    "Friction" to "المقاومة",
    "Appearance and navigation" to "المظهر والتنقّل",
    "Home widget" to "أداة الشاشة الرئيسة",
    "Pending changes" to "تغييرات معلّقة",
    "Setup and permissions" to "الإعداد والأذونات",
    "Decide app by app" to "قرّر تطبيقًا تطبيقًا",
    "Language" to "اللغة",
    "LANGUAGE" to "اللغة",
    "THEME" to "السِّمة",
    "NAVIGATION" to "التنقّل",
    "system" to "كالنظام",
    "english" to "الإنجليزية",
    "arabic" to "العربية",
    "dark" to "داكنة",
    "light" to "فاتحة",
    "the app follows the phone unless you tell it otherwise. arabic brings the " to
        "يتبع التطبيق الهاتف ما لم تختر غير ذلك. والعربية تُدير ",
    "whole layout round with it." to "الشاشة كلها معها.",
    "what the app still needs to do its job" to "ما يحتاجه التطبيق ليقوم بعمله",
    "sort the whole list out now, while nothing has to wait" to
        "افرغ من القائمة كلها الآن، ما دام لا شيء ينتظر",
    "favourites, gated apps, daily budgets" to "المفضّلة، وما خلف البوّابة، وأنصبة اليوم",
    "hours when only favourites open" to "ساعات لا يُفتح فيها إلا المفضّل",
    "set a location to work out the times" to "حدّد موقعًا لتُحسب الأوقات",
    "times shown, nothing blocked yet" to "الأوقات تُعرض، ولا حجب بعد",
    "how long the countdown is, how long the cooldown is" to
        "طول العدّ التنازلي، وطول مهلة التراخي",
    "light or dark, and how you get around" to "فاتحة أو داكنة، وكيف تتنقّل",
    "nothing under the clock but the next prayer" to "لا شيء تحت الساعة سوى الصلاة التالية",
    "one widget under the clock" to "أداة واحدة تحت الساعة",
    "nothing waiting" to "لا شيء ينتظر",
    "%s waiting out the cooldown" to "%s تنتظر انقضاء المهلة",
    "where the day actually went" to "أين ذهب اليوم حقًّا",
    "setup is unfinished — the limits are not fully enforced yet" to
        "الإعداد لم يكتمل — الحدود لم تُفرَض بعد كاملةً",
    "nothing waits yet — the cooldown starts once you lock the rules in." to
        "لا شيء ينتظر بعد — تبدأ المهلة متى أحكمت القواعد.",
    "changes that loosen the rules wait out the cooldown before they take effect." to
        "التغييرات التي تُرخي القواعد تنتظر انقضاء المهلة قبل أن تسري.",
    "tap one to cancel it" to "المس واحدًا لإلغائه",
    "nothing waiting." to "لا شيء ينتظر.",
    "due — applies at the next check" to "حان — يسري عند الفحص التالي",
    "changes are applied by a background check that runs about every fifteen minutes." to
        "تسري التغييرات بفحص في الخلفية يجري كل خمس عشرة دقيقة تقريبًا.",
    "on the phone today, across %s opens" to "على الهاتف اليوم، في %s فتحة",
    "%s over the last seven days" to "%s خلال الأيام السبعة الماضية",
    "%s emergency bypasses left this week" to "بقي %s من الاستثناءات الطارئة هذا الأسبوع",
    "TODAY, BY APP" to "اليوم، بحسب التطبيق",
    "%s apps · anything you installed yourself starts gated" to
        "%s تطبيقًا · كل ما ثبّتّه بنفسك يبدأ خلف البوّابة",
    "%s opens/day" to "%s فتحة/يوم",
    "%s still opens while a prayer window is in force." to "%s يظلّ يُفتح ووقت الصلاة قائم.",
    "stricter takes effect at once; looser waits %s minutes" to
        "التشديد يسري فورًا؛ والترخية تنتظر %s دقيقة",
    "%s chars" to "%s حرفًا",
    "the phone stops for salah · %s" to "الهاتف يقف للصلاة · %s",
    "nothing here waits while you are still setting up. %s" to
        "لا شيء هنا ينتظر ما دمت في الإعداد. %s",
    "%s on the home screen · %s open freely · %s gated · %s blocked." to
        "%s على الشاشة الرئيسة · %s تُفتح بحرّية · %s خلف البوّابة · %s محجوبة.",
    "nothing recorded yet." to "لا شيء مسجّل بعد.",
    "only time recorded while the enforcement service was running is counted." to
        "لا يُحتسب إلا ما سُجّل والخدمة تعمل.",
    "filter" to "تصفية",
    "back" to "رجوع",
    "none" to "بلا",
    "done." to "تمّ.",
    "applied." to "سرى.",
    "in force now." to "سارٍ الآن.",
    "TIER" to "الدرجة",
    // The four tiers, twice: once as the word, once short enough for a row of four pills.
    "favourite" to "مفضّل",
    "allowed" to "مباح",
    "fav" to "مفضّل",
    "open" to "مباح",
    "gate" to "بوّابة",
    "block" to "محجوب",
    "daily minutes" to "دقائق اليوم",
    "daily opens" to "فتحات اليوم",
    "DURING SALAH" to "أثناء الصلاة",
    "closed" to "مغلق",
    "opens" to "يُفتح",
    "WHEN ANOTHER APP OPENS IT" to "حين يفتحه تطبيق آخر",
    "no windows yet." to "لا نوافذ بعد.",
    "tap a window to remove it" to "المس نافذة لحذفها",
    "ADD" to "إضافة",
    "every day" to "كل يوم",
    "weekdays" to "أيام العمل",
    "sleep" to "النوم",
    "work" to "العمل",
    "evening" to "المساء",
    "mon" to "الإثنين",
    "tue" to "الثلاثاء",
    "wed" to "الأربعاء",
    "thu" to "الخميس",
    "fri" to "الجمعة",
    "sat" to "السبت",
    "sun" to "الأحد",
    "base wait" to "الانتظار الأساسي",
    "added per open today" to "يُضاف لكل فتحة اليوم",
    "reason length" to "طول السبب",
    "session length" to "طول الجلسة",
    "cooldown on loosening" to "مهلة الترخية",
    "emergency bypasses per week" to "الاستثناءات الطارئة أسبوعيًّا",
    "LOCATION" to "الموقع",
    "use my location" to "استعمل موقعي",
    "location set." to "ضُبط الموقع.",
    "CALCULATION" to "الحساب",
    "ASR" to "العصر",
    "standard" to "المشهور",
    "hanafi" to "حنفي",
    "WHEN THE PHONE STOPS" to "متى يتوقّف الهاتف",
    "stop pausing for salah" to "أوقف الوقفة للصلاة",
    "pause for salah" to "قِف للصلاة",
    "minutes before the adhan" to "دقائق قبل الأذان",
    "minutes after" to "دقائق بعده",
    "COMBINING" to "الجمع",
    "separate" to "مفرّقة",
    "combined" to "مجموعة",
    "THE AYAH AT THE GATE" to "الآية عند البوّابة",
    "both" to "كلاهما",
    "download the qur'an" to "نزّل القرآن",
    "download again" to "نزّله مرّة أخرى",
    "downloading — it carries on in the background." to "يُنزَّل — ويستمر في الخلفية.",
    "CORRECTIONS" to "التصحيحات",
    "HIJRI DATE" to "التاريخ الهجري",
    "NETWORK" to "الشبكة",
    "compute only" to "حساب فقط",
    "sync too" to "ومزامنة",
    "no times yet." to "لا أوقات بعد.",
    "latitude" to "خط العرض",
    "longitude" to "خط الطول",
    "set coordinates" to "اضبط الإحداثيات",
    "nothing chosen" to "لم يُختر شيء",
    "choose a widget" to "اختر أداة",
    "choose a different one" to "اختر غيرها",
    "HEIGHT" to "الارتفاع",
    "remove it" to "احذفها",
    "never mind" to "دَعْ ذلك",
    "no app on this phone publishes a widget." to "لا تطبيق في هذا الهاتف ينشر أداة.",
    "A STRIP OF OUR OWN · OFF" to "شريط من عندنا · مُعطّل",
    "hide the strip" to "أخفِ الشريط",
    "show the strip" to "أظهر الشريط",
    "open accessibility settings" to "افتح إعدادات إمكانية الوصول",
)

private val SETUP = mapOf(
    "Setup" to "الإعداد",
    "the first three are what make the limits real" to "الثلاثة الأولى هي ما يجعل الحدود حقيقية",
    "WHICH SCHOOL DO YOU FOLLOW?" to "أيّ مذهب تتبع؟",
    "this sets the ʿasr rule, the calculation method and whether the prayers " to
        "يضبط هذا وقت العصر، وطريقة الحساب، والجمع بين الصلاتين من عدمه. ",
    "are combined. all three stay editable afterwards." to "وتبقى الثلاثة قابلة للتغيير بعدُ.",
    "be the home screen" to "كُن الشاشة الرئيسة",
    "otherwise the old launcher is one press away." to "وإلا فالمشغّل القديم على بُعد ضغطة.",
    "open home settings" to "افتح إعدادات الشاشة الرئيسة",
    "usage access" to "الوصول إلى الاستعمال",
    "lets the app see which app is in front, which is how limits are enforced." to
        "يتيح للتطبيق معرفة ما هو في المقدّمة، وبه تُفرَض الحدود.",
    "grant usage access" to "امنح الوصول إلى الاستعمال",
    "display over other apps" to "الظهور فوق التطبيقات",
    "without it the block screen cannot appear on top of the app you just opened." to
        "بدونه لا تستطيع شاشة الحجب أن تظهر فوق ما فتحتَه للتو.",
    "grant display over apps" to "امنح الظهور فوق التطبيقات",
    "notification access" to "الوصول إلى الإشعارات",
    "used only to hold back notifications from gated apps. calls and alarms always ring." to
        "لا يُستعمل إلا لحبس إشعارات ما خلف البوّابة. والمكالمات والمنبّهات ترنّ دائمًا.",
    "grant notification access" to "امنح الوصول إلى الإشعارات",
    "device admin" to "مدير الجهاز",
    "makes uninstalling take deliberate steps rather than a long press." to
        "يجعل الحذف يحتاج خطوات مقصودة لا ضغطة مطوّلة.",
    "activate device admin" to "فعّل مدير الجهاز",
    "battery: no restrictions" to "البطارية: بلا قيود",
    "allow unrestricted battery" to "اسمح باستعمال البطارية بلا قيد",
    "granted. on Xiaomi/HyperOS also turn on Autostart and lock the app in recents — " to
        "مُنح. وعلى شاومي/هايبر أو إس فعّل كذلك التشغيل التلقائي وثبّت التطبيق في الأخيرة — ",
    "no API can report those, so this line cannot check them for you." to
        "فلا واجهة برمجية تُخبر عنهما، ولا يستطيع هذا السطر التحقّق منهما نيابةً عنك.",
    "on Xiaomi/HyperOS also turn on Autostart and lock the app in recents, or the " to
        "على شاومي/هايبر أو إس فعّل كذلك التشغيل التلقائي وثبّت التطبيق في الأخيرة، وإلا ",
    "enforcement service will be killed while you sleep." to "قُتلت خدمة الفرض وأنت نائم.",
    "DEVICE OWNER · OPTIONAL" to "مالك الجهاز · اختياري",
    "active. blocked apps are suspended by the system itself." to
        "مُفعّل. النظام نفسه يُعلّق التطبيقات المحجوبة.",
    "hide the command" to "أخفِ الأمر",
    "how to enable it" to "كيف يُفعَّل",
    "YOUR RULES · LOCKED" to "قواعدك · محكَمة",
    "YOUR RULES · STILL OPEN" to "قواعدك · ما زالت مفتوحة",
    "loosening a rule now waits out the cooldown." to "ترخية قاعدة الآن تنتظر انقضاء المهلة.",
    "decide app by app" to "قرّر تطبيقًا تطبيقًا",
    "start enforcing" to "ابدأ الفرض",
    "you can come back here whenever something stops working." to
        "لك أن تعود إلى هنا كلما تعطّل شيء.",
    "DECIDE ONCE" to "قرّر مرّة واحدة",
    "yes, lock it in" to "نعم، أحكِمها",
    "not yet" to "ليس بعد",
    "lock these rules in" to "أحكِم هذه القواعد",
    "Keeps Yusr Launcher from being removed on impulse." to "يمنع حذف التطبيق في لحظة اندفاع.",
)

/**
 * The paragraphs. This app explains itself more than most, and a screen whose reasoning is in one
 * language and whose buttons are in another explains nothing — so the prose is here too, in the
 * fragments the source concatenates it from.
 */
private val PROSE = mapOf(
    "%,d km to Makkah · %s" to "%,d كم إلى مكة · %s",
    // The digest notification, which is built in a worker rather than on a screen and is none the
    // less the app talking to the person holding the phone.
    "%s notifications held back" to "%s إشعارًا مُحتجزًا",
    "The supplications as printed. The titles are written here, not the book's own." to
        "الأدعية كما هي مطبوعة. والعناوين مكتوبة هنا، لا من الكتاب.",
    "The Arabic edition, its own headings and its own notes. Footnotes are not here." to
        "من الطبعة العربية، بعناوين الكتاب نفسه وتعليقاته. والحواشي ليست هنا.",
    "Shaykh ʿAbbās al-Qummī · a selection" to "الشيخ عبّاس القمّي · مختارات",
    "Saʿīd b. ʿAlī al-Qaḥṭānī · a selection" to "سعيد بن علي القحطاني · مختارات",
    "A short selection of the best known of the book's supplications, not the whole of it. " +
        "Add more to assets/supplications.json from an edition you trust and they will appear here." to
        "مختارات قصيرة من أشهر أدعية الكتاب، لا الكتاب كلّه. زِد عليها في " +
        "assets/supplications.json من طبعة تثق بها فتظهر هنا.",
    "%.3f, %.3f · typed" to "%.3f، %.3f · مكتوب",
    "%.3f, %.3f · from the phone" to "%.3f، %.3f · من الهاتف",
    "/day" to "/يوم",
    "Offsets, location and the salah pause are set under Settings → Prayer times. " to
        "التعديلات والموقع والوقفة للصلاة تُضبط في الإعدادات ← أوقات الصلاة. ",
    "Changing the school here rewrites the method, the ʿasr rule and the combining to " to
        "وتغيير المذهب هنا يعيد كتابة طريقة الحساب ووقت العصر والجمع بما ",
    "match it; anything you change afterwards stays as you left it." to
        "يوافقه؛ وما غيّرته بعد ذلك يبقى كما تركته.",
    "The Qur'an has not been downloaded yet. Settings → Prayer times and salah " to
        "لم يُنزَّل القرآن بعد. الإعدادات ← أوقات الصلاة والوقفة ",
    "→ download the Qur'an fetches all 6,236 āyāt once, and then never again." to
        "← تنزيل القرآن يجلب الآيات الستة آلاف ومئتين وستًّا والثلاثين مرّةً واحدة، ثم لا يعود.",
    "closed during a prayer window, like everything else." to
        "مغلق وقت الصلاة، كسائر التطبيقات.",
    "a link, a sign-in page or a web app opens straight into %s. " to
        "الرابط وصفحة الدخول وتطبيق الوِب تفتح %s مباشرةً. ",
    "going to it yourself still " to "والذهاب إليه بنفسك يظلّ ",
    "costs the gate." to "يكلّفك البوّابة.",
    "the gate stands whichever way you arrive. on a browser that means every " to
        "البوّابة قائمة من أيّ طريق أتيت. وفي المتصفّح يعني ذلك أن كل ",
    "web-backed app on the phone waits behind it too." to
        "تطبيق يقوم على الوِب في الهاتف ينتظر خلفها كذلك.",
    "the phone's own navigation is what this launcher uses — the swipe from " to
        "يستعمل هذا المشغّل تنقّل الهاتف نفسه — السحب من ",
    "the edge for back, the swipe up for home, the one you already know. " to
        "الحافة للرجوع، والسحب لأعلى للرئيسة، وهو ما تعرفه أصلًا. ",
    "nothing here replaces it, and there is no bar of our own at the bottom." to
        "لا شيء هنا يحلّ محلّه، ولا شريط لنا في الأسفل.",
    "if your phone is set to gesture navigation and it feels wrong here, it " to
        "إن كان هاتفك على تنقّل الإيماءات ورأيته لا يستقيم هنا، فإعداد ",
    "is the system setting that decides it: Settings → System → Navigation mode." to
        "النظام هو الذي يقرّره: الإعدادات ← النظام ← وضع التنقّل.",
    "only for phones where the system navigation has been hidden or cannot be " to
        "للهواتف التي أُخفي فيها تنقّل النظام أو تعذّر ",
    "reached: a thin strip along the bottom edge — swipe up for home, tap or " to
        "الوصول إليه: شريط رفيع على الحافة السفلى — اسحب لأعلى للرئيسة، والمس أو ",
    "swipe right for back, hold or swipe left for recents. it needs an " to
        "اسحب يمينًا للرجوع، واضغط مطوّلًا أو اسحب يسارًا للأخيرة. ويحتاج ",
    "accessibility service, because nothing else may press those. leave it off " to
        "خدمة إمكانية وصول، إذ لا شيء غيرها يملك ضغط تلك. فدعه مُعطّلًا ",
    "unless you need it." to "ما لم تحتج إليه.",
    "the strip needs the Yusr Launcher navigation accessibility service " to
        "يحتاج الشريط إلى خدمة تنقّل يُسر في إمكانية الوصول ",
    "switched on before it can appear." to "أن تُفعَّل قبل أن يظهر.",
    "adding one is instant; removing one waits out the cooldown" to
        "الإضافة فورية؛ والحذف ينتظر انقضاء المهلة",
    "location refused — type the coordinates instead." to
        "رُفض الموقع — فاكتب الإحداثيات بدلًا من ذلك.",
    "set a location and the rest follows" to "حدّد موقعًا ويتبعه الباقي",
    "the school and authority you follow. jafari is the shia calculation, " to
        "المذهب والجهة التي تتبعها. والجعفري حساب الشيعة، ",
    "with maghrib after sunset rather than at it." to "بالمغرب بعد الغروب لا عنده.",
    "during a prayer window nothing opens but calls and whatever you have " to
        "وقت الصلاة لا يُفتح إلا الاتّصال وما ",
    "marked as opening during salah. favourites included." to
        "علّمته بأنه يُفتح أثناءها. والمفضّلة منها.",
    "a location comes first — the times cannot be worked out without one." to
        "الموقع أوّلًا — فلا تُحسب الأوقات بدونه.",
    "one window for dhuhr and asr, one for maghrib and isha — two pauses a " to
        "وقت واحد للظهر والعصر، وآخر للمغرب والعشاء — وقفتان في ",
    "day rather than four." to "اليوم بدل أربع.",
    "something gated." to "شيئًا خلف البوّابة.",
    "%s āyāt on the phone. shown at random when you try to open " to
        "%s آية في الهاتف. تُعرض واحدة منها حين تحاول فتح ",
    "a bundled handful is in use. downloading the qur'an replaces it with all " to
        "يُستعمل الآن نزر مُضمَّن. وتنزيل القرآن يستبدل به الآيات ",
    "6,236, and then the network is never needed again." to
        "الستة آلاف ومئتين وستًّا والثلاثين، ثم لا تُحتاج الشبكة أبدًا.",
    "these make the times right rather than weaker, so they take effect at once." to
        "هذه تُصحّح الأوقات لا تُضعفها، فتسري فورًا.",
    "the calendar is calculated, not sighted, so it can sit a day either side " to
        "التقويم محسوب لا مرئيّ، فقد يسبق أو يتأخّر يومًا عن ",
    "of the announcement where you are." to "الإعلان في بلدك.",
    "with sync off, the times are computed on this phone and nothing is ever " to
        "بإيقاف المزامنة تُحسب الأوقات في هذا الهاتف ولا يُجلب ",
    "fetched. everything still works." to "شيء أبدًا. ويبقى كل شيء عاملًا.",
    "shown only — nothing is being blocked for salah yet." to
        "تُعرض فحسب — ولا حجب للصلاة بعد.",
    "queued — it takes effect at %s." to "في الانتظار — ويسري عند %s.",
    "no fix — location may be off, or type the coordinates instead." to
        "لا تحديد — قد يكون الموقع مُعطّلًا، أو اكتب الإحداثيات.",
    "location set from the device." to "ضُبط الموقع من الجهاز.",
    "muslim world league" to "رابطة العالم الإسلامي",
    "isna" to "أمريكا الشمالية",
    "egyptian authority" to "الهيئة المصرية",
    "umm al-qura" to "أم القرى",
    "karachi" to "كراتشي",
    "jafari (qum)" to "جعفري (قم)",
    "tehran" to "طهران",
    "mwl" to "الرابطة",
    "egypt" to "مصر",
    "makkah" to "مكة",
    "jafari" to "جعفري",
    "one widget sits under the clock, in place of the line about the next " to
        "تجلس أداة واحدة تحت الساعة، مكان سطر ",
    "prayer. a salah app's widget is what this is for, but everything the phone " to
        "الصلاة التالية. وأداة تطبيق الصلاة هي المقصودة، لكن كل ما ينشره ",
    "publishes is listed." to "الهاتف مذكور هنا.",
    "how much of the home screen it may take. the names below it keep " to
        "كم يجوز لها أن تأخذ من الشاشة. والأسماء تحتها تأخذ ",
    "whatever is left." to "ما بقي.",
    "the app that published this widget is not answering — it may have " to
        "التطبيق الذي نشر هذه الأداة لا يجيب — ولعلّه ",
    "been uninstalled. choose another one, or remove it." to
        "حُذف. فاختر غيرها، أو احذفها.",
    "this phone will not let the launcher hold a widget — the screen that grants it is missing. " to
        "لا يسمح هذا الهاتف للمشغّل بحمل أداة — فالشاشة التي تمنح ذلك غير موجودة. ",
    "nothing to be done here; the next-prayer line stays." to
        "لا شيء يُفعل هنا؛ ويبقى سطر الصلاة التالية.",
    "the rules are locked. changes that loosen them now wait out the cooldown." to
        "القواعد محكَمة. وما يُرخيها الآن ينتظر انقضاء المهلة.",
    "after this, loosening any rule waits out the cooldown before it " to
        "بعد هذا، ترخية أيّ قاعدة تنتظر انقضاء المهلة قبل أن ",
    "takes effect. there is no way back to this screen." to
        "تسري. ولا طريق يعود بك إلى هذه الشاشة.",
    " the mark in the home screen's top corner reorders them." to
        " والعلامة في ركن الشاشة الرئيسة تعيد ترتيبها.",
    "not set. this is the strongest tier: the OS refuses to open blocked apps, " to
        "غير مضبوط. وهذه أقوى الدرجات: يرفض النظام فتح المحجوب، ",
    "and the app cannot be uninstalled without ADB or a factory reset." to
        "ولا يُحذف التطبيق إلا بـ ADB أو إعادة ضبط المصنع.",
    "with the phone connected over USB and no other accounts on the device:" to
        "والهاتف موصول عبر USB ولا حسابات أخرى على الجهاز:",
    "removing it later needs adb, or a factory reset. read docs/SETUP.md first." to
        "وإزالته بعدُ تحتاج adb أو إعادة ضبط المصنع. اقرأ docs/SETUP.md أوّلًا.",
    "go through the app list and decide each one. while the rules are unlocked " to
        "امضِ في قائمة التطبيقات وقرّر في كل واحد. وما دامت القواعد مفتوحة ",
    "every change applies immediately — no waiting." to
        "يسري كل تغيير فورًا — بلا انتظار.",
)
