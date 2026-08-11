package dev.yusr.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.yusr.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Navigation that does not depend on the phone's own gesture bar.
 *
 * A launcher cannot press back or open recents by itself — [performGlobalAction] is the only API
 * that can, and it is only available to an accessibility service. So this is a thin strip along
 * the bottom edge that turns a gesture into one of the three things you actually need:
 *
 *   swipe up → home · tap or swipe right → back · hold or swipe left → recent apps
 *
 * It reads nothing. `canRetrieveWindowContent` is false and no event types are subscribed, so the
 * service sees gestures on its own strip and nothing else.
 */
class NavAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var strip: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            container.settingsStore.settings
                .map { it.navOverlayEnabled }
                .distinctUntilChanged()
                .collect { enabled -> if (enabled) showStrip() else hideStrip() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideStrip()
        scope.cancel()
        super.onDestroy()
    }

    private fun showStrip() {
        if (strip != null) return
        val windowManager = getSystemService(WindowManager::class.java) ?: return

        val view = NavStripView(
            context = this,
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onRecents = { performGlobalAction(GLOBAL_ACTION_RECENTS) },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.density * STRIP_HEIGHT_DP).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        runCatching { windowManager.addView(view, params) }
            .onSuccess { strip = view }
            .onFailure { Log.w(TAG, "Could not add the navigation strip", it) }
    }

    private fun hideStrip() {
        val view = strip ?: return
        strip = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(view) }
    }

    private companion object {
        const val TAG = "NavAccessibility"
        const val STRIP_HEIGHT_DP = 18f
    }
}

/**
 * Touch handling is written out by hand rather than handed to a GestureDetector: three outcomes,
 * no ambiguity about which one fired, and no listener signature to keep in step with the SDK.
 */
private class NavStripView(
    context: Context,
    private val onBack: () -> Unit,
    private val onHome: () -> Unit,
    private val onRecents: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val swipeThreshold = SWIPE_DP * density
    private val slop = SLOP_DP * density

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        color = if (night) Color.argb(70, 255, 255, 255) else Color.argb(50, 0, 0, 0)
    }

    private var downX = 0f
    private var downY = 0f
    private var handled = false

    private val longPress = Runnable {
        handled = true
        onRecents()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                handled = false
                postDelayed(longPress, LONG_PRESS_MILLIS)
            }

            MotionEvent.ACTION_MOVE -> {
                val moved = abs(event.x - downX) > slop || abs(event.y - downY) > slop
                if (moved) removeCallbacks(longPress)
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (!handled) act(event.x - downX, event.y - downY)
            }

            MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        return true
    }

    private fun act(dx: Float, dy: Float) {
        val vertical = abs(dy) > abs(dx)
        when {
            vertical && dy < -swipeThreshold -> onHome()
            !vertical && dx > swipeThreshold -> onBack()
            !vertical && dx < -swipeThreshold -> onRecents()
            abs(dx) < slop && abs(dy) < slop -> onBack()
        }
    }

    /** A short faint bar, so the strip can be found without being something to look at. */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = HANDLE_WIDTH_DP * density
        val barHeight = HANDLE_HEIGHT_DP * density
        val left = (width - barWidth) / 2f
        val top = (height - barHeight) / 2f
        canvas.drawRoundRect(left, top, left + barWidth, top + barHeight, barHeight, barHeight, handlePaint)
    }

    private companion object {
        const val SWIPE_DP = 24f
        const val SLOP_DP = 8f
        const val HANDLE_WIDTH_DP = 96f
        const val HANDLE_HEIGHT_DP = 3f
        const val LONG_PRESS_MILLIS = 400L
    }
}
