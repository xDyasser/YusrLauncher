package dev.yusr.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.yusr.domain.AsrMethod
import dev.yusr.domain.CalculationMethod
import dev.yusr.domain.GatePolicy
import dev.yusr.domain.HighLatitudeRule
import dev.yusr.domain.Madhab
import dev.yusr.domain.Prayer
import dev.yusr.domain.PrayerConfig
import dev.yusr.domain.PrayerWindowConfig
import dev.yusr.domain.Tasbih
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yusr_settings")

/** Whether the app follows the system's light/dark setting or is pinned to one of them. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * What language the app speaks.
 *
 * SYSTEM follows the phone, which is what almost everyone wants and nobody has to ask for. The
 * other two are for the case the phone cannot express: an Arabic reader whose phone is in
 * English, or the reverse — common enough among the people this app is for that leaving it to
 * the system alone would be leaving them out.
 *
 * Arabic brings the whole layout round with it. That is not a style: it is the direction the
 * language is read in, and a right-to-left reader looking at a left-aligned screen is being
 * asked to do the work the app should have done.
 */
enum class Language(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    ARABIC("ar"),
}

/** Which script the ayah at the gate is shown in. */
enum class AyahLanguage { ARABIC, ENGLISH, BOTH }

/** Where the coordinates came from, which is worth showing so a wrong timetable is explicable. */
enum class LocationSource { UNSET, MANUAL, DEVICE }

/**
 * The bookmark as it is actually on disk: [place] is null when nothing has ever been written.
 *
 * The wrapper exists so that a screen collecting the flow can tell "not read from disk yet" (no
 * value at all) from "read, and there is nothing there" ([place] null) — a bare nullable pair
 * collapses the two, and on a fresh install they call for different ayat.
 */
data class StoredBookmark(val place: Pair<Int, Int>?)

/** No widget chosen. Matches [android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID]. */
const val NO_WIDGET: Int = 0

/**
 * Everything about salah. Enforcement stays off until [configured] is true, because a launcher
 * that blocks the phone at the prayer times of latitude zero would be worse than useless.
 */
data class PrayerSettings(
    val enabled: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationSource: LocationSource = LocationSource.UNSET,
    val method: CalculationMethod = CalculationMethod.MWL,
    val asr: AsrMethod = AsrMethod.STANDARD,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED,
    val combineDhuhrAsr: Boolean = false,
    val combineMaghribIsha: Boolean = false,
    val windowBeforeMinutes: Int = 0,
    val windowAfterMinutes: Int = 20,
    val offsetMinutes: Map<Prayer, Int> = emptyMap(),
    val hijriOffsetDays: Int = 0,
    val ayahLanguage: AyahLanguage = AyahLanguage.BOTH,
    /** Whether to refresh times from the network. The solver runs either way. */
    val syncOverNetwork: Boolean = true,
    val quranSyncedAtMillis: Long = 0L,
    /**
     * The school, or null until it has been asked for.
     *
     * Null is a real state rather than a missing default: nothing about salah is computed before
     * the question is answered, in the same way and for the same reason that nothing is computed
     * before there is a location. A launcher that guessed a madhab and then silently timed
     * someone's asr by it would be worse than one that asked.
     */
    val madhab: Madhab? = null,
    /** Whether the preferred-time windows are shown alongside the prayer times. */
    val showFadila: Boolean = true,
) {
    val configured: Boolean get() = locationSource != LocationSource.UNSET

    /**
     * Whether the setup checklist should still put the madhab question in front of the user.
     *
     * Deliberately not part of [configured]. Someone who set this app up before the question
     * existed already has a working timetable, and treating their install as unconfigured would
     * turn salah enforcement off underneath them — a loosening they never asked for, which is the
     * one thing this app's whole design says must not happen quietly.
     */
    val needsMadhab: Boolean get() = madhab == null

    /**
     * The school to act on: the one chosen, or the nearest match to the settings already in
     * force. An existing install is read rather than interrogated.
     */
    val effectiveMadhab: Madhab
        get() = madhab ?: when {
            method == CalculationMethod.JAFARI || method == CalculationMethod.TEHRAN -> Madhab.JAFARI
            asr == AsrMethod.HANAFI -> Madhab.HANAFI
            else -> Madhab.SHAFII
        }

    /** True only when the times are both wanted and trustworthy. */
    val enforcing: Boolean get() = enabled && configured

    fun toPrayerConfig(): PrayerConfig = PrayerConfig(
        method = method,
        asr = asr,
        highLatitude = highLatitude,
        offsetMinutes = offsetMinutes,
    )

    fun toWindowConfig(): PrayerWindowConfig = PrayerWindowConfig(
        minutesBefore = windowBeforeMinutes,
        minutesAfter = windowAfterMinutes,
        combineDhuhrAsr = combineDhuhrAsr,
        combineMaghribIsha = combineMaghribIsha,
    )
}

/** Everything the user can tune, and the cooldown that governs tuning it downward. */
data class AppSettings(
    val policy: GatePolicy = GatePolicy.DEFAULT,
    /** How long a loosening change sits in the pending queue before it takes effect. */
    val cooldownMinutes: Int = 30,
    val bypassesPerWeek: Int = 3,
    val bypassMinutes: Int = 10,
    val digestMorningHour: Int = 9,
    val digestEveningHour: Int = 18,
    val suppressNotifications: Boolean = true,
    /** When the last digest actually went out, so a missed slot is not skipped silently. */
    val lastDigestDeliveredAt: Long = 0L,
    val onboardingComplete: Boolean = false,
    val catalogSeeded: Boolean = false,
    /** Whether the browsers have been given the handoff exemption once, on an existing install. */
    val handoffSeeded: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.SYSTEM,
    /**
     * Until you lock the rules in, every change applies the moment you make it. This is the one
     * window in which you can sort out the whole app list without serving a cooldown per app.
     * It is a one-way door: once locked, loosening waits.
     */
    val rulesLocked: Boolean = false,
    /**
     * The accessibility overlay strip: swipe for home, tap for back, hold for recents.
     *
     * Off unless asked for. The phone already has navigation — gestures or the three buttons —
     * and it works inside this launcher like it does anywhere else. The strip exists only for
     * the case where that navigation has been hidden or is unreachable, and putting a second
     * bar under the system's own is exactly the clutter this app is meant to avoid.
     */
    val navOverlayEnabled: Boolean = false,
    /**
     * The app widget shown on the home screen, or [NO_WIDGET]. One, chosen by the user —
     * a prayer-time widget, usually, in place of the app's own one-line next-prayer note.
     */
    val homeWidgetId: Int = NO_WIDGET,
    /** How tall that widget is allowed to be, in dp. */
    val homeWidgetHeightDp: Int = 132,
    val prayer: PrayerSettings = PrayerSettings(),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_DELAY = intPreferencesKey("base_delay_seconds")
        val ESCALATION = intPreferencesKey("escalation_seconds")
        val MAX_DELAY = intPreferencesKey("max_delay_seconds")
        val MIN_REASON = intPreferencesKey("min_reason_length")
        val SESSION_MINUTES = intPreferencesKey("default_session_minutes")
        val COOLDOWN = intPreferencesKey("cooldown_minutes")
        val BYPASSES = intPreferencesKey("bypasses_per_week")
        val BYPASS_MINUTES = intPreferencesKey("bypass_minutes")
        val DIGEST_MORNING = intPreferencesKey("digest_morning_hour")
        val DIGEST_EVENING = intPreferencesKey("digest_evening_hour")
        val LAST_DIGEST = longPreferencesKey("last_digest_delivered_at")
        val SUPPRESS = booleanPreferencesKey("suppress_notifications")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val SEEDED = booleanPreferencesKey("catalog_seeded")
        val HANDOFF_SEEDED = booleanPreferencesKey("handoff_seeded")
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val RULES_LOCKED = booleanPreferencesKey("rules_locked")
        val NAV_OVERLAY = booleanPreferencesKey("nav_overlay_enabled")
        val HOME_WIDGET_ID = intPreferencesKey("home_widget_id")
        val HOME_WIDGET_HEIGHT = intPreferencesKey("home_widget_height_dp")

        val PRAYER_ENABLED = booleanPreferencesKey("prayer_enabled")
        val LATITUDE = doublePreferencesKey("prayer_latitude")
        val LONGITUDE = doublePreferencesKey("prayer_longitude")
        val LOCATION_SOURCE = stringPreferencesKey("prayer_location_source")
        val METHOD = stringPreferencesKey("prayer_method")
        val ASR = stringPreferencesKey("prayer_asr")
        val HIGH_LATITUDE = stringPreferencesKey("prayer_high_latitude")
        val COMBINE_DHUHR_ASR = booleanPreferencesKey("prayer_combine_dhuhr_asr")
        val COMBINE_MAGHRIB_ISHA = booleanPreferencesKey("prayer_combine_maghrib_isha")
        val WINDOW_BEFORE = intPreferencesKey("prayer_window_before")
        val WINDOW_AFTER = intPreferencesKey("prayer_window_after")
        val HIJRI_OFFSET = intPreferencesKey("hijri_offset_days")
        val AYAH_LANGUAGE = stringPreferencesKey("ayah_language")
        val PRAYER_SYNC = booleanPreferencesKey("prayer_sync_over_network")
        val QURAN_SYNCED_AT = longPreferencesKey("quran_synced_at")
        val MADHAB = stringPreferencesKey("madhab")
        val SHOW_FADILA = booleanPreferencesKey("show_fadila")

        val RECITER = stringPreferencesKey("reciter_id")
        val BOOKMARK_SURAH = intPreferencesKey("quran_bookmark_surah")
        val BOOKMARK_AYAH = intPreferencesKey("quran_bookmark_ayah")
        val TASBIH_CYCLE = stringPreferencesKey("tasbih_cycle")

        fun offsetKey(prayer: Prayer) = intPreferencesKey("prayer_offset_${prayer.name.lowercase()}")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            policy = GatePolicy(
                baseDelaySeconds = prefs[Keys.BASE_DELAY] ?: defaults.policy.baseDelaySeconds,
                escalationSecondsPerOpen = prefs[Keys.ESCALATION] ?: defaults.policy.escalationSecondsPerOpen,
                maxDelaySeconds = prefs[Keys.MAX_DELAY] ?: defaults.policy.maxDelaySeconds,
                minReasonLength = prefs[Keys.MIN_REASON] ?: defaults.policy.minReasonLength,
                defaultSessionMinutes = prefs[Keys.SESSION_MINUTES] ?: defaults.policy.defaultSessionMinutes,
            ),
            cooldownMinutes = prefs[Keys.COOLDOWN] ?: defaults.cooldownMinutes,
            bypassesPerWeek = prefs[Keys.BYPASSES] ?: defaults.bypassesPerWeek,
            bypassMinutes = prefs[Keys.BYPASS_MINUTES] ?: defaults.bypassMinutes,
            digestMorningHour = prefs[Keys.DIGEST_MORNING] ?: defaults.digestMorningHour,
            digestEveningHour = prefs[Keys.DIGEST_EVENING] ?: defaults.digestEveningHour,
            suppressNotifications = prefs[Keys.SUPPRESS] ?: defaults.suppressNotifications,
            lastDigestDeliveredAt = prefs[Keys.LAST_DIGEST] ?: defaults.lastDigestDeliveredAt,
            onboardingComplete = prefs[Keys.ONBOARDED] ?: defaults.onboardingComplete,
            catalogSeeded = prefs[Keys.SEEDED] ?: defaults.catalogSeeded,
            handoffSeeded = prefs[Keys.HANDOFF_SEEDED] ?: defaults.handoffSeeded,
            themeMode = prefs[Keys.THEME]?.let { stored ->
                runCatching { ThemeMode.valueOf(stored) }.getOrNull()
            } ?: defaults.themeMode,
            language = prefs[Keys.LANGUAGE]?.let { stored ->
                runCatching { Language.valueOf(stored) }.getOrNull()
            } ?: defaults.language,
            rulesLocked = prefs[Keys.RULES_LOCKED] ?: defaults.rulesLocked,
            navOverlayEnabled = prefs[Keys.NAV_OVERLAY] ?: defaults.navOverlayEnabled,
            homeWidgetId = prefs[Keys.HOME_WIDGET_ID] ?: defaults.homeWidgetId,
            homeWidgetHeightDp = prefs[Keys.HOME_WIDGET_HEIGHT] ?: defaults.homeWidgetHeightDp,
            prayer = readPrayer(prefs, defaults.prayer),
        )
    }

    private fun readPrayer(prefs: Preferences, defaults: PrayerSettings) = PrayerSettings(
        enabled = prefs[Keys.PRAYER_ENABLED] ?: defaults.enabled,
        latitude = prefs[Keys.LATITUDE] ?: defaults.latitude,
        longitude = prefs[Keys.LONGITUDE] ?: defaults.longitude,
        locationSource = prefs[Keys.LOCATION_SOURCE].toEnum(defaults.locationSource, LocationSource::valueOf),
        method = prefs[Keys.METHOD].toEnum(defaults.method, CalculationMethod::valueOf),
        asr = prefs[Keys.ASR].toEnum(defaults.asr, AsrMethod::valueOf),
        highLatitude = prefs[Keys.HIGH_LATITUDE].toEnum(defaults.highLatitude, HighLatitudeRule::valueOf),
        combineDhuhrAsr = prefs[Keys.COMBINE_DHUHR_ASR] ?: defaults.combineDhuhrAsr,
        combineMaghribIsha = prefs[Keys.COMBINE_MAGHRIB_ISHA] ?: defaults.combineMaghribIsha,
        windowBeforeMinutes = prefs[Keys.WINDOW_BEFORE] ?: defaults.windowBeforeMinutes,
        windowAfterMinutes = prefs[Keys.WINDOW_AFTER] ?: defaults.windowAfterMinutes,
        offsetMinutes = Prayer.entries
            .mapNotNull { prayer -> prefs[Keys.offsetKey(prayer)]?.let { prayer to it } }
            .toMap(),
        hijriOffsetDays = prefs[Keys.HIJRI_OFFSET] ?: defaults.hijriOffsetDays,
        ayahLanguage = prefs[Keys.AYAH_LANGUAGE].toEnum(defaults.ayahLanguage, AyahLanguage::valueOf),
        syncOverNetwork = prefs[Keys.PRAYER_SYNC] ?: defaults.syncOverNetwork,
        quranSyncedAtMillis = prefs[Keys.QURAN_SYNCED_AT] ?: defaults.quranSyncedAtMillis,
        madhab = prefs[Keys.MADHAB]?.let { runCatching { Madhab.valueOf(it) }.getOrNull() },
        showFadila = prefs[Keys.SHOW_FADILA] ?: defaults.showFadila,
    )

    /** A stored name that no longer parses falls back to the default rather than crashing. */
    private fun <T> String?.toEnum(fallback: T, parse: (String) -> T): T =
        this?.let { runCatching { parse(it) }.getOrNull() } ?: fallback

    suspend fun current(): AppSettings = settings.first()

    suspend fun setBaseDelay(seconds: Int) = putInt(Keys.BASE_DELAY, seconds)

    suspend fun setEscalation(seconds: Int) = putInt(Keys.ESCALATION, seconds)

    suspend fun setMaxDelay(seconds: Int) = putInt(Keys.MAX_DELAY, seconds)

    suspend fun setMinReasonLength(chars: Int) = putInt(Keys.MIN_REASON, chars)

    suspend fun setDefaultSessionMinutes(minutes: Int) = putInt(Keys.SESSION_MINUTES, minutes)

    suspend fun setCooldownMinutes(minutes: Int) = putInt(Keys.COOLDOWN, minutes)

    suspend fun setBypassesPerWeek(count: Int) = putInt(Keys.BYPASSES, count)

    suspend fun setBypassMinutes(minutes: Int) = putInt(Keys.BYPASS_MINUTES, minutes)

    suspend fun setDigestHours(morning: Int, evening: Int) {
        context.dataStore.edit {
            it[Keys.DIGEST_MORNING] = morning
            it[Keys.DIGEST_EVENING] = evening
        }
    }

    suspend fun setSuppressNotifications(enabled: Boolean) = putBoolean(Keys.SUPPRESS, enabled)

    suspend fun setLastDigestDeliveredAt(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_DIGEST] = millis }
    }

    suspend fun setOnboardingComplete(complete: Boolean) = putBoolean(Keys.ONBOARDED, complete)

    suspend fun setCatalogSeeded(seeded: Boolean) = putBoolean(Keys.SEEDED, seeded)

    suspend fun setHandoffSeeded(seeded: Boolean) = putBoolean(Keys.HANDOFF_SEEDED, seeded)

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    /**
     * Stored here as well as handed to the system, because the system's own per-app language is
     * not readable before the first activity exists and this app has services and notifications
     * that speak before any of that.
     */
    suspend fun setLanguage(language: Language) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    /** One-way on purpose: there is no setter that unlocks the rules again. */
    suspend fun lockRules() = putBoolean(Keys.RULES_LOCKED, true)

    suspend fun setNavOverlayEnabled(enabled: Boolean) = putBoolean(Keys.NAV_OVERLAY, enabled)

    suspend fun setHomeWidgetId(id: Int) = putInt(Keys.HOME_WIDGET_ID, id)

    suspend fun setHomeWidgetHeightDp(dp: Int) = putInt(Keys.HOME_WIDGET_HEIGHT, dp.coerceIn(72, 320))

    // ---- prayer ------------------------------------------------------------------------

    suspend fun setPrayerEnabled(enabled: Boolean) = putBoolean(Keys.PRAYER_ENABLED, enabled)

    suspend fun setLocation(latitude: Double, longitude: Double, source: LocationSource) {
        context.dataStore.edit {
            it[Keys.LATITUDE] = latitude
            it[Keys.LONGITUDE] = longitude
            it[Keys.LOCATION_SOURCE] = source.name
        }
    }

    suspend fun setMethod(method: CalculationMethod) = putString(Keys.METHOD, method.name)

    suspend fun setAsrMethod(asr: AsrMethod) = putString(Keys.ASR, asr.name)

    suspend fun setHighLatitudeRule(rule: HighLatitudeRule) = putString(Keys.HIGH_LATITUDE, rule.name)

    suspend fun setCombine(dhuhrAsr: Boolean, maghribIsha: Boolean) {
        context.dataStore.edit {
            it[Keys.COMBINE_DHUHR_ASR] = dhuhrAsr
            it[Keys.COMBINE_MAGHRIB_ISHA] = maghribIsha
        }
    }

    suspend fun setWindowMinutes(before: Int, after: Int) {
        context.dataStore.edit {
            it[Keys.WINDOW_BEFORE] = before.coerceAtLeast(0)
            it[Keys.WINDOW_AFTER] = after.coerceAtLeast(0)
        }
    }

    suspend fun setPrayerOffset(prayer: Prayer, minutes: Int) = putInt(Keys.offsetKey(prayer), minutes)

    suspend fun setHijriOffsetDays(days: Int) = putInt(Keys.HIJRI_OFFSET, days.coerceIn(-2, 2))

    suspend fun setAyahLanguage(language: AyahLanguage) = putString(Keys.AYAH_LANGUAGE, language.name)

    suspend fun setPrayerSyncOverNetwork(enabled: Boolean) = putBoolean(Keys.PRAYER_SYNC, enabled)

    suspend fun setQuranSyncedAt(millis: Long) {
        context.dataStore.edit { it[Keys.QURAN_SYNCED_AT] = millis }
    }

    /**
     * Picking a school and taking its defaults in one write.
     *
     * The asr rule and the calculation method come with it, because they are what the school
     * *means* — someone answering "Ḥanafī" has already answered "which asr?" and should not be
     * asked again in different words. Anything set here stays editable underneath.
     */
    suspend fun setMadhab(madhab: Madhab, applyDefaults: Boolean = true) {
        context.dataStore.edit {
            it[Keys.MADHAB] = madhab.name
            if (applyDefaults) {
                it[Keys.ASR] = madhab.asr.name
                it[Keys.METHOD] = madhab.method.name
                it[Keys.COMBINE_DHUHR_ASR] = madhab.combinesByDefault
                it[Keys.COMBINE_MAGHRIB_ISHA] = madhab.combinesByDefault
            }
        }
    }

    suspend fun setShowFadila(show: Boolean) = putBoolean(Keys.SHOW_FADILA, show)

    // ---- the hub -----------------------------------------------------------------------

    val reciterId: Flow<String?> = context.dataStore.data.map { it[Keys.RECITER] }

    suspend fun setReciterId(id: String) = putString(Keys.RECITER, id)

    /**
     * Where the reader was left, or [StoredBookmark.place] null on a device that has never been
     * anywhere in the book.
     *
     * Never having read is not the same as being at the opening, and the home screen shows a
     * different ayah for each, so the two have to stay tellable apart this far down.
     */
    val storedBookmark: Flow<StoredBookmark> = context.dataStore.data.map { prefs ->
        val surah = prefs[Keys.BOOKMARK_SURAH]
        val ayah = prefs[Keys.BOOKMARK_AYAH]
        StoredBookmark(if (surah != null && ayah != null) surah to ayah else null)
    }

    /** Where the reader was left, as sūra and ayah. Defaults to the opening of al-Fātiḥa. */
    val bookmark: Flow<Pair<Int, Int>> = storedBookmark.map { it.place ?: (1 to 1) }

    suspend fun setBookmark(surah: Int, ayah: Int) {
        context.dataStore.edit {
            it[Keys.BOOKMARK_SURAH] = surah
            it[Keys.BOOKMARK_AYAH] = ayah
        }
    }

    val tasbihCycle: Flow<Tasbih.Cycle> = context.dataStore.data.map { prefs ->
        prefs[Keys.TASBIH_CYCLE]
            ?.let { runCatching { Tasbih.Cycle.valueOf(it) }.getOrNull() }
            ?: Tasbih.Cycle.THIRTY_THREE
    }

    suspend fun setTasbihCycle(cycle: Tasbih.Cycle) = putString(Keys.TASBIH_CYCLE, cycle.name)

    private suspend fun putInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun putString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }
}
