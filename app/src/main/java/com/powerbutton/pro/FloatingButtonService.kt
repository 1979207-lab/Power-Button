package com.powerbutton.pro

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class FloatingButtonService : Service() {
    companion object { const val ACTION_HIDE_TEMP = "com.powerbutton.pro.HIDE_TEMP" }

    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var dragged = false
    private var hidden = false
    private val showAgain = Runnable { hidden = false; addBubble() }

    override fun onCreate() {
        super.onCreate()
        val channel = "power_button"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(android.app.NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channel, "Power Button PRO", android.app.NotificationManager.IMPORTANCE_LOW))
            startForeground(41, Notification.Builder(this, channel)
                .setContentTitle("Power Button PRO активна")
                .setSmallIcon(android.R.drawable.ic_lock_power_off).build())
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("button_settings", Context.MODE_PRIVATE)
        val size = prefs.getInt("size", 58).coerceIn(40, 100)
        lp = WindowManager.LayoutParams(size, size,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("position_x", 30)
            y = prefs.getInt("position_y", 220)
        }
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_TEMP) {
            removeBubble()
            hidden = true
            handler.removeCallbacks(showAgain)
            handler.postDelayed(showAgain, 30_000L)
        } else if (!hidden && bubble == null) {
            addBubble()
        }
        return START_STICKY
    }

    private fun addBubble() {
        if (hidden || bubble != null) return
        val prefs = getSharedPreferences("button_settings", Context.MODE_PRIVATE)
        val size = prefs.getInt("size", 58).coerceIn(40, 100)
        lp.width = size
        lp.height = size
        val color = prefs.getInt("color", 0xFF202124.toInt())
        val opacity = prefs.getInt("opacity", 88).coerceIn(20, 100) / 100f
        val glyph = listOf("⏻", "⏼", "⏽", "●").getOrElse(prefs.getInt("icon_index", 0)) { "⏻" }

        val view = TextView(this).apply {
            text = glyph
            textSize = (size * 0.5f).coerceIn(22f, 50f)
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2, 0x66FFFFFF)
            }
            alpha = opacity
        }

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = lp.x
                    originY = lp.y
                    dragged = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    // Ignore finger jitter. A drag starts only after a deliberate movement.
                    val threshold = (16f * resources.displayMetrics.density).toInt().coerceAtLeast(16)
                    if (!dragged && abs(dx) + abs(dy) > threshold) dragged = true
                    if (dragged) {
                        lp.x = originX + dx
                        lp.y = originY + dy
                        try {
                            wm.updateViewLayout(view, lp)
                        } catch (_: Exception) {
                            // Keep the touch handler alive; the service will recreate the overlay if needed.
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        savePosition(prefs)
                    } else {
                        if (prefs.getBoolean("vibration", true)) vibrateSafely()
                        val duration = event.eventTime - event.downTime
                        try {
                            if (duration >= prefs.getInt("long_press_ms", 650)) {
                                val opened = PowerAccessibilityService.showPowerMenu()
                                if (!opened) {
                                    Toast.makeText(this, "Не удалось открыть меню питания. Проверьте службу доступности Power Button PRO.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                lockScreen()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Не удалось выполнить действие: ${e.localizedMessage ?: "ошибка"}", Toast.LENGTH_LONG).show()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragged) savePosition(prefs)
                    true
                }

                else -> true
            }
        }

        try {
            wm.addView(view, lp)
            bubble = view
        } catch (e: Exception) {
            bubble = null
            Toast.makeText(this, "Не удалось показать кнопку поверх окон. Проверьте разрешение.", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePosition(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean("snap_edge", true)) {
            val metrics = resources.displayMetrics
            val maxX = (metrics.widthPixels - lp.width).coerceAtLeast(0)
            lp.x = if (lp.x + lp.width / 2 < metrics.widthPixels / 2) 0 else maxX
            lp.y = lp.y.coerceIn(0, (metrics.heightPixels - lp.height).coerceAtLeast(0))
            bubble?.let { view ->
                try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
            }
        }
        prefs.edit().putInt("position_x", lp.x).putInt("position_y", lp.y).apply()
    }

    private fun removeBubble() {
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble = null
    }

    @Suppress("DEPRECATION")
    private fun vibrateSafely() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(35)
            }
        } catch (_: Exception) {}
    }

    private fun lockScreen() {
        val component = ComponentName(this, LockAdminReceiver::class.java)
        val policy = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!policy.isAdminActive(component)) {
            Toast.makeText(this, "Блокировка не выполнена: активируйте администратора устройства в приложении.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            policy.lockNow()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Нет разрешения на блокировку экрана: ${e.localizedMessage ?: "проверьте администратора устройства"}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
