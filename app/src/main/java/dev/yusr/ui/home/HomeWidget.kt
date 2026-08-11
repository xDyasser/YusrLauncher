package dev.yusr.ui.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * One app widget on the home screen.
 *
 * The launcher shows a single line about the next prayer, which is enough for most people and
 * not enough for anyone who already has a salah app they trust. Rather than reimplement that
 * app's timetable, adhan and iqama badly, the home screen can host the widget it already
 * publishes — and since a widget is a widget, the choice is left open: any of them will do.
 *
 * Hosting one means being an [AppWidgetHost], which is three things: an id allocated per widget,
 * a binding the user consents to, and a listener that must run only while the screen is up.
 */
object HomeWidget {

    /**
     * Any constant will do, as long as it never changes: ids allocated under one host id are
     * not visible under another, and a change would orphan the user's chosen widget.
     */
    const val HOST_ID = 0x4D49

    fun host(context: Context): AppWidgetHost = AppWidgetHost(context.applicationContext, HOST_ID)

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    fun info(context: Context, widgetId: Int): AppWidgetProviderInfo? =
        runCatching { manager(context).getAppWidgetInfo(widgetId) }.getOrNull()

    /**
     * Builds the hosted view, sized to the space it has been given so the widget lays itself out
     * for the width it actually gets rather than the one it guessed. Null when it cannot be
     * built at all — see [size] for why that is not hypothetical.
     *
     * Nothing here is allowed to throw. This view goes on the home screen, and a home screen
     * that crashes is a phone with no way back into the app that caused it.
     */
    fun createView(
        context: Context,
        host: AppWidgetHost,
        widgetId: Int,
        info: AppWidgetProviderInfo,
        widthDp: Int,
        heightDp: Int,
    ): AppWidgetHostView? = runCatching {
        host.createView(context, widgetId, info).apply {
            setAppWidget(widgetId, info)
            size(this, widthDp, heightDp)
        }
    }.getOrElse {
        Log.w(TAG, "Widget $widgetId would not build", it)
        null
    }

    /**
     * Tells the widget how much room it has.
     *
     * The bundle must be one of ours and it must be mutable: the framework writes the four sizes
     * into whatever it is handed. Passing `Bundle.EMPTY` throws, because that instance is shared
     * and immutable — which took down the home screen for anyone who had chosen a widget.
     */
    fun size(view: AppWidgetHostView, widthDp: Int, heightDp: Int) {
        runCatching {
            view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
        }
    }

    /** One widget offered by one app, named the way the person choosing it would name it. */
    data class Choice(
        val provider: AppWidgetProviderInfo,
        val appLabel: String,
        val widgetLabel: String,
    )

    /**
     * Every widget published on the phone, sorted by the app that publishes it.
     *
     * This list used to be the system's job: `ACTION_APPWIDGET_PICK` opens a picker that both
     * chooses and binds. But that picker lives in AOSP's Settings, and several manufacturers —
     * HyperOS among them — do not ship it, so the intent resolves to nothing and launching it
     * throws. Enumerating the providers ourselves works on every phone and lets the list look
     * like the rest of the app.
     */
    fun providers(context: Context): List<Choice> = runCatching {
        val packages = context.packageManager
        manager(context).installedProviders
            .map { info ->
                Choice(
                    provider = info,
                    appLabel = appLabel(context, info.provider.packageName),
                    widgetLabel = info.loadLabel(packages)?.trim().orEmpty()
                        .ifEmpty { info.provider.shortClassName.substringAfterLast('.') },
                )
            }
            .sortedWith(
                compareBy({ it.appLabel.lowercase() }, { it.widgetLabel.lowercase() }),
            )
    }.getOrDefault(emptyList())

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val packages = context.packageManager
        packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** What happened when we tried to bind [Choice] to a freshly allocated id. */
    sealed interface Binding {
        /** Bound and ready to draw. */
        data class Bound(val widgetId: Int) : Binding

        /** The system wants the user to agree first; [intent] is the dialog that asks. */
        data class NeedsConsent(val widgetId: Int, val intent: Intent) : Binding

        /** No way to bind on this phone. The id has already been handed back. */
        data object Impossible : Binding
    }

    /**
     * Claims an id for [choice] and binds it if we are allowed to.
     *
     * Binding needs a signature-level permission no ordinary app holds, so the usual answer is
     * [Binding.NeedsConsent]: a one-tap system dialog that grants this launcher the right to bind
     * widgets. After the first yes, later picks bind outright.
     */
    fun bind(context: Context, host: AppWidgetHost, choice: Choice): Binding {
        val widgetId = host.allocateAppWidgetId()
        val allowed = runCatching {
            manager(context).bindAppWidgetIdIfAllowed(widgetId, choice.provider.provider)
        }.getOrDefault(false)
        if (allowed) return Binding.Bound(widgetId)

        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, choice.provider.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, choice.provider.profile)
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.w(TAG, "No activity will ask for permission to bind a widget")
            release(host, widgetId)
            return Binding.Impossible
        }
        return Binding.NeedsConsent(widgetId, intent)
    }

    /**
     * Runs the widget's own configuration screen, if it has one. Returns false when there is
     * nothing to configure and the widget is ready as it stands.
     */
    fun configure(
        activity: Activity,
        host: AppWidgetHost,
        widgetId: Int,
        requestCode: Int,
    ): Boolean {
        val info = info(activity, widgetId) ?: return false
        if (info.configure == null) return false
        return runCatching {
            host.startAppWidgetConfigureActivityForResult(activity, widgetId, 0, requestCode, null)
            true
        }.getOrElse {
            // Some widgets declare a configuration screen we are not allowed to start. Better a
            // widget with its defaults than no widget.
            Log.w(TAG, "The widget's configuration screen would not open", it)
            false
        }
    }

    /**
     * A note left on disk while a widget is being drawn, and taken down once it has survived
     * being on screen.
     *
     * Everything this file does is guarded, but the widget's own views are not ours and run code
     * we cannot wrap. If one of them brings the process down, the home screen dies — and a home
     * screen that dies on every launch is a phone with no way of reaching the setting that would
     * turn the widget off. So the note outlives the crash: finding it still there at startup is
     * proof the last attempt did not end well, and the widget is dropped rather than drawn again.
     */
    fun markDrawing(context: Context, drawing: Boolean) {
        runCatching {
            context.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
                .edit()
                // Written through rather than queued: the whole point is to survive a crash that
                // may be milliseconds away.
                .putBoolean(DRAWING_KEY, drawing)
                .commit()
        }
    }

    /** True when the last attempt to draw a widget never reported back. */
    fun crashedWhileDrawing(context: Context): Boolean = runCatching {
        context.getSharedPreferences(SAFETY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(DRAWING_KEY, false)
    }.getOrDefault(false)

    /** Hands the id back, so an abandoned pick does not leak a binding. */
    fun release(host: AppWidgetHost, widgetId: Int) {
        runCatching { host.deleteAppWidgetId(widgetId) }
    }

    private const val TAG = "HomeWidget"
    private const val SAFETY_PREFS = "home-widget-safety"
    private const val DRAWING_KEY = "drawing"
}
