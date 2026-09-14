package com.touchoverlay.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.touchoverlay.app.data.LayoutRepository
import com.touchoverlay.app.model.ButtonConfig
import com.touchoverlay.app.model.JoystickConfig
import com.touchoverlay.app.model.LayoutConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Foreground service that owns every floating overlay window: the small control
 * panel (move / open editor / stop), the four generic buttons, and the joystick.
 *
 * Design note on touch pass-through: rather than one full-screen overlay window,
 * each control gets its own small WRAP_CONTENT window with FLAG_NOT_TOUCH_MODAL.
 * That means only the area actually covered by a control intercepts touches —
 * everywhere else on screen passes straight through to the app underneath, which
 * is what lets the overlay "float" above another app without blocking it.
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "touch_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.touchoverlay.app.action.STOP_OVERLAY"

        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var repository: LayoutRepository
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val addedViews = mutableListOf<View>()

    private var screenWidthPx = 0
    private var screenHeightPx = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = LayoutRepository(applicationContext)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidthPx = metrics.widthPixels
        screenHeightPx = metrics.heightPixels

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            val layout = repository.getLayout()
            buildOverlay(layout)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        removeAllViews()
        serviceJob.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.stop_overlay), stopPending)
            .setOngoing(true)
            .build()
    }

    // ---------------------------------------------------------------------
    // Overlay construction helpers
    // ---------------------------------------------------------------------

    private fun buildOverlay(layout: LayoutConfig) {
        addControlPanel()
        layout.buttons.forEach { addButtonView(it) }
        addJoystickView(layout.joystick)
    }

    private fun baseLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    // ----- Control panel: move handle / open editor / stop -----

    private fun addControlPanel() {
        val params = baseLayoutParams()
        params.x = screenWidthPx - dp(160f)
        params.y = dp(24f)

        val panel = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC1A1A1A"))
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val moveHandle = makeIconButton(android.R.drawable.ic_menu_sort_by_size, R.string.cd_move_panel)
        val settingsBtn = makeIconButton(android.R.drawable.ic_menu_manage, R.string.cd_open_editor)
        val closeBtn = makeIconButton(android.R.drawable.ic_menu_close_clear_cancel, R.string.cd_stop_overlay)

        settingsBtn.setOnClickListener {
            val i = Intent(this@OverlayService, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_EDITOR, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(i)
        }
        closeBtn.setOnClickListener { stopSelf() }

        var dragStartX = 0f
        var dragStartY = 0f
        var startParamsX = 0
        var startParamsY = 0
        moveHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    startParamsX = params.x
                    startParamsY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startParamsX + (event.rawX - dragStartX)).toInt()
                    params.y = (startParamsY + (event.rawY - dragStartY)).toInt()
                    windowManager.updateViewLayout(panel, params)
                    true
                }
                else -> false
            }
        }

        row.addView(moveHandle)
        row.addView(settingsBtn)
        row.addView(closeBtn)
        panel.addView(row)

        windowManager.addView(panel, params)
        addedViews.add(panel)
    }

    private fun makeIconButton(iconRes: Int, cdRes: Int): ImageButton = ImageButton(this).apply {
        setImageResource(iconRes)
        background = null
        contentDescription = getString(cdRes)
        setColorFilter(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
    }

    // ----- Generic buttons (A / B / C / D) -----

    private fun addButtonView(config: ButtonConfig) {
        val params = baseLayoutParams()
        val sizePx = dp(config.sizeDp)
        params.x = (config.xPercent * screenWidthPx - sizePx / 2f).toInt()
        params.y = (config.yPercent * screenHeightPx - sizePx / 2f).toInt()

        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#3378C2F5"))
            setStroke(dp(2f), Color.parseColor("#78C2F5"))
        }
        val label = TextView(this).apply {
            text = config.label
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = circle
            alpha = config.alpha
            addView(
                label,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            )
        }

        attachButtonTouch(container, params, config)

        windowManager.addView(container, params)
        addedViews.add(container)
    }

    private fun attachButtonTouch(view: View, params: WindowManager.LayoutParams, config: ButtonConfig) {
        var isEditMode = false
        var downX = 0f
        var downY = 0f
        var startParamsX = 0
        var startParamsY = 0
        var downTime = 0L
        val longPressThresholdMs = 450L

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startParamsX = params.x
                    startParamsY = params.y
                    downTime = System.currentTimeMillis()
                    isEditMode = false
                    v.postDelayed({
                        val heldLongEnough = System.currentTimeMillis() - downTime >= longPressThresholdMs
                        if (heldLongEnough) {
                            isEditMode = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            Toast.makeText(this, R.string.edit_mode_hint, Toast.LENGTH_SHORT).show()
                        }
                    }, longPressThresholdMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isEditMode) {
                        params.x = (startParamsX + (event.rawX - downX)).toInt()
                        params.y = (startParamsY + (event.rawY - downY)).toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isEditMode) {
                        persistButtonPosition(config.id, params)
                        isEditMode = false
                    } else {
                        val moved = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                        if (moved < 20) {
                            performButtonPress(v, params)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun performButtonPress(v: View, params: WindowManager.LayoutParams) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val cx = params.x + v.width / 2f
        val cy = params.y + v.height / 2f
        val service = TouchAccessibilityService.instance
        if (service != null) {
            service.dispatchTap(cx, cy)
        } else {
            Toast.makeText(this, R.string.accessibility_not_enabled_warning, Toast.LENGTH_SHORT).show()
        }
    }

    private fun persistButtonPosition(id: String, params: WindowManager.LayoutParams) {
        serviceScope.launch {
            val current = repository.getLayout()
            val updatedButtons = current.buttons.map { button ->
                if (button.id == id) {
                    val sizePx = dp(button.sizeDp)
                    button.copy(
                        xPercent = (params.x + sizePx / 2f) / screenWidthPx.toFloat(),
                        yPercent = (params.y + sizePx / 2f) / screenHeightPx.toFloat()
                    )
                } else button
            }.toMutableList()
            repository.saveLayout(LayoutConfig(updatedButtons, current.joystick))
        }
    }

    // ----- Joystick -----

    private fun addJoystickView(config: JoystickConfig) {
        val params = baseLayoutParams()
        val sizePx = dp(config.sizeDp)
        params.x = (config.xPercent * screenWidthPx - sizePx / 2f).toInt()
        params.y = (config.yPercent * screenHeightPx - sizePx / 2f).toInt()

        val baseDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#33FFFFFF"))
            setStroke(dp(2f), Color.WHITE)
        }
        val base = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = baseDrawable
            alpha = config.alpha
        }
        val knobDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#AA78C2F5"))
        }
        val knobSize = (sizePx * 0.45f).toInt()
        val knob = View(this).apply {
            background = knobDrawable
            layoutParams = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER)
        }
        base.addView(knob)

        attachJoystickTouch(base, knob, params, sizePx)

        windowManager.addView(base, params)
        addedViews.add(base)
    }

    private fun attachJoystickTouch(
        base: View,
        knob: View,
        params: WindowManager.LayoutParams,
        sizePx: Int
    ) {
        var isEditMode = false
        var downX = 0f
        var downY = 0f
        var startParamsX = 0
        var startParamsY = 0
        var downTime = 0L
        val longPressThresholdMs = 450L
        val maxRadius = sizePx / 2f
        var centerScreenX = 0f
        var centerScreenY = 0f
        var lastStrokeX = 0f
        var lastStrokeY = 0f

        base.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startParamsX = params.x
                    startParamsY = params.y
                    downTime = System.currentTimeMillis()
                    isEditMode = false
                    centerScreenX = params.x + sizePx / 2f
                    centerScreenY = params.y + sizePx / 2f
                    lastStrokeX = centerScreenX
                    lastStrokeY = centerScreenY
                    v.postDelayed({
                        val heldLongEnough = System.currentTimeMillis() - downTime >= longPressThresholdMs
                        if (heldLongEnough) {
                            isEditMode = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            Toast.makeText(this, R.string.edit_mode_hint, Toast.LENGTH_SHORT).show()
                        }
                    }, longPressThresholdMs)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (isEditMode) {
                        params.x = (startParamsX + dx).toInt()
                        params.y = (startParamsY + dy).toInt()
                        windowManager.updateViewLayout(v, params)
                    } else {
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val clamped = min(dist, maxRadius)
                        val angle = atan2(dy.toDouble(), dx.toDouble())
                        val knobX = (clamped * cos(angle)).toFloat()
                        val knobY = (clamped * sin(angle)).toFloat()
                        knob.translationX = knobX
                        knob.translationY = knobY

                        val targetX = centerScreenX + knobX
                        val targetY = centerScreenY + knobY
                        TouchAccessibilityService.instance?.dispatchStroke(lastStrokeX, lastStrokeY, targetX, targetY)
                        lastStrokeX = targetX
                        lastStrokeY = targetY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isEditMode) {
                        persistJoystickPosition(params, sizePx)
                    } else {
                        knob.translationX = 0f
                        knob.translationY = 0f
                        if (TouchAccessibilityService.instance == null) {
                            Toast.makeText(this, R.string.accessibility_not_enabled_warning, Toast.LENGTH_SHORT).show()
                        }
                    }
                    isEditMode = false
                    true
                }
                else -> false
            }
        }
    }

    private fun persistJoystickPosition(params: WindowManager.LayoutParams, sizePx: Int) {
        serviceScope.launch {
            val current = repository.getLayout()
            val updatedJoystick = current.joystick.copy(
                xPercent = (params.x + sizePx / 2f) / screenWidthPx.toFloat(),
                yPercent = (params.y + sizePx / 2f) / screenHeightPx.toFloat()
            )
            repository.saveLayout(LayoutConfig(current.buttons, updatedJoystick))
        }
    }

    private fun removeAllViews() {
        addedViews.forEach {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View already detached; nothing to do.
            }
        }
        addedViews.clear()
    }
}
