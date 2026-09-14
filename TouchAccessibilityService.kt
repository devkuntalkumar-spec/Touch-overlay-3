package com.touchoverlay.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Legitimate, user-enabled use of Android's public AccessibilityService gesture APIs.
 *
 * Android does not let an ordinary app inject touch events into another app's window
 * (that would be a serious security hole). The only sanctioned way for an app to send
 * synthetic input system-wide is [AccessibilityService.dispatchGesture], and only after
 * the user has explicitly turned the service on in Settings > Accessibility.
 *
 * This service does not read, analyze, or react to what's on screen — it does not
 * override [onAccessibilityEvent] for any content inspection. It exists purely so the
 * floating overlay's buttons and joystick (see OverlayService) can ask it to reproduce
 * a tap or a short drag at specific screen coordinates.
 *
 * Known platform limitation: dispatchGesture cannot hold an indefinite "finger down"
 * state independent of further calls, and it will not work on windows/apps that opt
 * out of accessibility interaction (e.g. some secured or DRM-protected screens). This
 * is intentional Android security behavior and is not something this app attempts to
 * bypass.
 */
class TouchAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TouchAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally unused: this service never inspects screen content.
    }

    override fun onInterrupt() {
        // No-op.
    }

    /** Dispatches a single tap gesture at the given absolute screen coordinates. */
    fun dispatchTap(x: Float, y: Float, durationMs: Long = 60L) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Dispatches one short drag segment from [startX],[startY] to [endX],[endY].
     * The joystick calls this repeatedly, once per touch-move sample, to
     * approximate a continuous drag as a fast sequence of short strokes — the
     * closest legitimate approximation available through the public gesture API,
     * which has no raw down/move/up injection entry point for third-party apps.
     */
    fun dispatchStroke(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 40L) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
