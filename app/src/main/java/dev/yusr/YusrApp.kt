package dev.yusr

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.yusr.admin.PolicyManager
import dev.yusr.data.AppCatalog
import dev.yusr.data.YusrRepository
import dev.yusr.data.RuleMutator
import dev.yusr.data.DevotionRepository
import dev.yusr.data.prayer.PrayerRepository
import dev.yusr.data.quran.QuranSource
import dev.yusr.data.quran.RecitationStore
import dev.yusr.data.quran.Supplications
import dev.yusr.data.settings.SettingsStore
import dev.yusr.ui.applyLanguage
import dev.yusr.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class YusrApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        WorkScheduler.scheduleRecurring(this)
        restoreLanguage()
    }

    /**
     * The system owns the app's language once it has been set, and normally that is the end of it.
     * This is for the case where it does not: a restore onto a new phone brings the settings back
     * but not the system's per-app locale, and the launcher would come up in English for someone
     * who had chosen Arabic. The stored choice is the one this app made a promise about, so it is
     * the one that wins.
     */
    private fun restoreLanguage() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val language = runCatching { container.settingsStore.settings.first().language }.getOrNull()
            if (language != null) applyLanguage(this@YusrApp, language)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GUARD,
                getString(R.string.guard_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.guard_channel_description)
                setShowBadge(false)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DIGEST,
                getString(R.string.digest_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.digest_channel_description)
            },
        )
    }

    companion object {
        const val CHANNEL_GUARD = "guard"
        const val CHANNEL_DIGEST = "digest"
    }
}

/** Hand-rolled dependency graph. One app, one graph, no framework needed. */
class AppContainer(context: Context) {
    val settingsStore: SettingsStore = SettingsStore(context)
    val catalog: AppCatalog = AppCatalog(context)
    val prayerRepository: PrayerRepository = PrayerRepository(context, settingsStore)
    val quran: QuranSource = QuranSource(context)
    val recitation: RecitationStore = RecitationStore(context)
    val supplications: Supplications = Supplications(context)
    val devotions: DevotionRepository = DevotionRepository(context)
    val repository: YusrRepository =
        YusrRepository(context, settingsStore, catalog, prayerRepository)
    val ruleMutator: RuleMutator = RuleMutator(repository, settingsStore)
    val policyManager: PolicyManager = PolicyManager(context)
}

val Context.container: AppContainer
    get() = (applicationContext as YusrApp).container
