package dev.yusr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppRuleEntity::class,
        UsageSessionEntity::class,
        OverrideLogEntity::class,
        NotificationDigestEntity::class,
        BlackoutWindowEntity::class,
        PendingChangeEntity::class,
        PrayerDayEntity::class,
        QuranAyahEntity::class,
        DhikrDayEntity::class,
        FastingDayEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class YusrDatabase : RoomDatabase() {

    abstract fun appRuleDao(): AppRuleDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun overrideLogDao(): OverrideLogDao
    abstract fun notificationDigestDao(): NotificationDigestDao
    abstract fun blackoutWindowDao(): BlackoutWindowDao
    abstract fun pendingChangeDao(): PendingChangeDao
    abstract fun prayerDayDao(): PrayerDayDao
    abstract fun quranAyahDao(): QuranAyahDao
    abstract fun dhikrDayDao(): DhikrDayDao
    abstract fun fastingDayDao(): FastingDayDao

    companion object {

        /**
         * Adds the prayer layer. Written out rather than destroyed and rebuilt, because by the
         * time anyone installs this the rule table holds decisions that took a sitting to make.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_rules ADD COLUMN prayerExempt INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS prayer_days (
                        date TEXT NOT NULL PRIMARY KEY,
                        fajr INTEGER NOT NULL,
                        sunrise INTEGER NOT NULL,
                        dhuhr INTEGER NOT NULL,
                        asr INTEGER NOT NULL,
                        maghrib INTEGER NOT NULL,
                        isha INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        fetchedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quran_ayat (
                        id INTEGER NOT NULL PRIMARY KEY,
                        surah INTEGER NOT NULL,
                        ayah INTEGER NOT NULL,
                        surahName TEXT NOT NULL,
                        arabic TEXT NOT NULL,
                        english TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quran_ayat_surah ON quran_ayat (surah)")
            }
        }

        /** Adds the browser handoff exemption. One column, no data to move. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_rules ADD COLUMN openableByHandoff INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** Adds the two things the hub keeps: the day's tasbīḥ, and the days fasted. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dhikr_days (
                        date TEXT NOT NULL PRIMARY KEY,
                        count INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fasting_days (
                        date TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Empties the mushaf, so it is fetched again from the edition the app now reads.
         *
         * The old source folded the basmala into the first ayah of the sūrahs it heads, and its
         * translation was not a Shīʿī one. Neither is something that can be patched in place with
         * string surgery on rows already written — the text itself is the wrong text. So the table
         * is cleared and the download runs again, from a corpus that numbers the book the way the
         * mushaf does.
         *
         * Only the Qur'an goes. Rules, budgets and history are untouched, and the bundled āyāt
         * stand in at the gate until the download lands — the reader says plainly that the text
         * is not there yet rather than showing a part of it.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("DELETE FROM quran_ayat")
        }

        /** The same, for a book stored by the version that shipped as 5. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("DELETE FROM quran_ayat")
        }

        /**
         * Marks which sessions arrived by handoff, so the budget can leave them out.
         *
         * The rows already on the phone default to 0 — charged, as they were when they were
         * written. Yesterday's totals do not move under anyone.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE usage_sessions ADD COLUMN wasHandoff INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        @Volatile
        private var instance: YusrDatabase? = null

        fun get(context: Context): YusrDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                YusrDatabase::class.java,
                "yusr.db",
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
            )
                .build()
                .also { instance = it }
        }
    }
}
