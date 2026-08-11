package dev.yusr.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules WHERE favoriteOrder IS NOT NULL ORDER BY favoriteOrder")
    fun observeFavorites(): Flow<List<AppRuleEntity>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<AppRuleEntity>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun get(packageName: String): AppRuleEntity?

    @Query("SELECT packageName FROM app_rules WHERE tier = 'BLOCKED'")
    suspend fun blockedPackages(): List<String>

    @Upsert
    suspend fun upsert(rule: AppRuleEntity)

    @Upsert
    suspend fun upsertAll(rules: List<AppRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(rules: List<AppRuleEntity>)

    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM app_rules")
    suspend fun count(): Int

    @Query("SELECT MAX(favoriteOrder) FROM app_rules")
    suspend fun maxFavoriteOrder(): Int?

    @Query("UPDATE app_rules SET favoriteOrder = :order WHERE packageName = :packageName")
    suspend fun setFavoriteOrder(packageName: String, order: Int?)
}

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE endMillis IS NULL ORDER BY startMillis DESC LIMIT 1")
    suspend fun openSession(): UsageSessionEntity?

    @Query("SELECT * FROM usage_sessions WHERE startMillis >= :since OR endMillis IS NULL")
    suspend fun since(since: Long): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions WHERE startMillis >= :since OR endMillis IS NULL")
    fun observeSince(since: Long): Flow<List<UsageSessionEntity>>

    @Insert
    suspend fun insert(session: UsageSessionEntity): Long

    @Query("UPDATE usage_sessions SET endMillis = :endMillis WHERE id = :id")
    suspend fun close(id: Long, endMillis: Long)

    @Query("UPDATE usage_sessions SET endMillis = :endMillis WHERE endMillis IS NULL")
    suspend fun closeAllOpen(endMillis: Long)

    @Query("DELETE FROM usage_sessions WHERE endMillis IS NOT NULL AND endMillis < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface OverrideLogDao {
    @Query("SELECT * FROM override_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<OverrideLogEntity>>

    @Query("SELECT COUNT(*) FROM override_log WHERE kind = 'EMERGENCY_BYPASS' AND timestamp >= :since")
    suspend fun bypassCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM override_log WHERE kind = 'EMERGENCY_BYPASS' AND timestamp >= :since")
    fun observeBypassCountSince(since: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: OverrideLogEntity)

    @Query("DELETE FROM override_log WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface NotificationDigestDao {
    @Insert
    suspend fun insert(entry: NotificationDigestEntity)

    @Query("SELECT * FROM notification_digest WHERE delivered = 0 ORDER BY timestamp")
    suspend fun undelivered(): List<NotificationDigestEntity>

    @Query("SELECT * FROM notification_digest WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun observeSince(since: Long): Flow<List<NotificationDigestEntity>>

    @Query("UPDATE notification_digest SET delivered = 1 WHERE delivered = 0")
    suspend fun markAllDelivered()

    @Query("DELETE FROM notification_digest WHERE timestamp < :before")
    suspend fun pruneBefore(before: Long)
}

@Dao
interface BlackoutWindowDao {
    @Query("SELECT * FROM blackout_windows ORDER BY startMinuteOfDay")
    fun observeAll(): Flow<List<BlackoutWindowEntity>>

    @Query("SELECT * FROM blackout_windows")
    suspend fun getAll(): List<BlackoutWindowEntity>

    @Query("SELECT * FROM blackout_windows WHERE id = :id")
    suspend fun get(id: Long): BlackoutWindowEntity?

    @Upsert
    suspend fun upsert(window: BlackoutWindowEntity): Long

    @Delete
    suspend fun delete(window: BlackoutWindowEntity)

    @Query("DELETE FROM blackout_windows WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PrayerDayDao {
    @Query("SELECT * FROM prayer_days WHERE date = :date")
    suspend fun get(date: String): PrayerDayEntity?

    @Upsert
    suspend fun upsertAll(days: List<PrayerDayEntity>)

    @Query("DELETE FROM prayer_days WHERE date < :before")
    suspend fun pruneBefore(before: String)

    /** A move makes every cached row wrong, so they go rather than quietly mislead. */
    @Query("DELETE FROM prayer_days")
    suspend fun clear()
}

@Dao
interface QuranAyahDao {
    @Query("SELECT COUNT(*) FROM quran_ayat")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM quran_ayat")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM quran_ayat ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): QuranAyahEntity?

    @Query("SELECT * FROM quran_ayat WHERE surah = :surah ORDER BY ayah")
    suspend fun surah(surah: Int): List<QuranAyahEntity>

    @Query("SELECT * FROM quran_ayat WHERE surah = :surah AND ayah = :ayah")
    suspend fun at(surah: Int, ayah: Int): QuranAyahEntity?

    @Upsert
    suspend fun upsertAll(ayat: List<QuranAyahEntity>)

    @Query("DELETE FROM quran_ayat")
    suspend fun clear()
}

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY applyAtMillis")
    fun observeAll(): Flow<List<PendingChangeEntity>>

    @Query("SELECT * FROM pending_changes WHERE applyAtMillis <= :now ORDER BY applyAtMillis")
    suspend fun due(now: Long): List<PendingChangeEntity>

    @Query("SELECT MIN(applyAtMillis) FROM pending_changes")
    suspend fun earliestApplyAt(): Long?

    @Insert
    suspend fun insert(change: PendingChangeEntity): Long

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DhikrDayDao {
    @Query("SELECT count FROM dhikr_days WHERE date = :date")
    fun observeCount(date: String): Flow<Int?>

    @Query("SELECT count FROM dhikr_days WHERE date = :date")
    suspend fun count(date: String): Int?

    @Upsert
    suspend fun upsert(day: DhikrDayEntity)

    /** Everything older than [date], which is how the counter forgets last week. */
    @Query("DELETE FROM dhikr_days WHERE date < :date")
    suspend fun deleteBefore(date: String)
}

@Dao
interface FastingDayDao {
    @Query("SELECT * FROM fasting_days ORDER BY date DESC")
    fun observeAll(): Flow<List<FastingDayEntity>>

    @Query("SELECT * FROM fasting_days WHERE date >= :from ORDER BY date")
    suspend fun since(from: String): List<FastingDayEntity>

    @Query("SELECT COUNT(*) FROM fasting_days WHERE date >= :from")
    fun observeCountSince(from: String): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM fasting_days WHERE date = :date)")
    fun observeMarked(date: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(day: FastingDayEntity)

    @Query("DELETE FROM fasting_days WHERE date = :date")
    suspend fun delete(date: String)
}
