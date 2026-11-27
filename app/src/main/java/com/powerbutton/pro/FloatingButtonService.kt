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
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

class FloatingButtonService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private lateinit var lp: WindowManager.LayoutParams
    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var dragged = false

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
        val color = prefs.getInt("color", 0xFF202124.toInt())
        val opacity = prefs.getInt("opacity", 88).coerceIn(20, 100) / 100f
        val v = TextView(this).apply {
            text = "⏻"
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
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        lp = WindowManager.LayoutParams(size, size, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("position_x", 30)
            y = prefs.getInt("position_y", 220)
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    originX = lp.x; originY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) dragged = true
                    lp.x = originX + dx
                    lp.y = originY + dy
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        prefs.edit().putInt("position_x", lp.x).putInt("position_y", lp.y).apply()
                    } else if (e.eventTime - e.downTime >= 500) {
                        if (!PowerAccessibilityService.showPowerMenu()) Toast.makeText(this, "Включите службу доступности Power Button PRO", Toast.LENGTH_LONG).show()
                    } else lockScreen()
                    true
                }
                else -> true
            }
        }
        bubble = v
        wm.addView(v, lp)
    }

    private fun lockScreen() {
        val c = ComponentName(this, LockAdminReceiver::class.java)
        val d = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (d.isAdminActive(c)) d.lockNow()
        else Toast.makeText(this, "Активируйте администратора устройства в приложении", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        bubble?.let { wm.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
