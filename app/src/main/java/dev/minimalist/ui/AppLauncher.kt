package dev.minimalist.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.minimalist.container
import dev.minimalist.domain.GateDecision
import dev.minimalist.service.SessionGovernor
import dev.minimalist.ui.block.BlockActivity
import dev.minimalist.ui.gate.GateActivity

/**
 * The only route from a tap to an app. Every entry point in the UI goes through here, so the
 * launcher can never open something the guard service would immediately close.
 */
object AppLauncher {

    suspend fun open(context: Context, packageName: String) {
        when (val decision = context.container.repository.decide(packageName)) {
            is GateDecision.Allow -> launchDirect(context, packageName)

            is GateDecision.RequireFriction ->
                context.startActivity(GateActivity.newIntent(context, packageName))

            is GateDecision.Refuse -> context.startActivity(
                BlockActivity.newIntent(context, packageName, decision.reason, decision.bypassesRemaining),
            )
        }
    }

    /** Bypasses the decision entirely; only the gate and the bypass flow may call this. */
    fun launchDirect(context: Context, packageName: String) {
        val intent: Intent? = context.container.catalog.launchIntentFor(packageName)
        if (intent == null) {
            Toast.makeText(context, t("That app has no launch screen."), Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Grants a bounded session and opens the app. */
    fun grantAndLaunch(context: Context, packageName: String, sessionMinutes: Int, wasBypass: Boolean) {
        SessionGovernor.grant(packageName, sessionMinutes, wasBypass)
        launchDirect(context, packageName)
    }
}
