package com.powerbutton.pro

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("button_settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 30, 28, 24) }
        val scroll = ScrollView(this).also { it.addView(root) }
        root.addView(TextView(this).apply { text = "Power Button PRO"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "Короткое нажатие — блокировка экрана. Долгое нажатие — системное меню питания."
            textSize = 16f; setPadding(0, 16, 0, 18)
        })

        fun heading(s: String) { root.addView(TextView(this).apply { text = s; textSize = 18f; setPadding(0, 14, 0, 4) }) }
        fun action(label: String, run: () -> Unit) { root.addView(Button(this).apply { text = label; setOnClickListener { run() } }) }
        fun seek(title: String, key: String, min: Int, max: Int, default: Int, suffix: String) {
            heading(title)
            val label = TextView(this)
            val bar = SeekBar(this).apply { this.max = max - min; progress = (prefs.getInt(key, default) - min).coerceIn(0, max-min) }
            fun show() { label.text = "${bar.progress + min}$suffix" }
            show()
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { show() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { prefs.edit().putInt(key, bar.progress + min).apply() }
            })
            root.addView(label); root.addView(bar)
        }
        seek("Размер кнопки", "size", 40, 100, 58, " dp")
        seek("Прозрачность", "opacity", 20, 100, 88, "%")
        seek("Длительность долгого нажатия", "long_press_ms", 500, 1500, 650, " мс")

        heading("Цвет кнопки")
        val colors = listOf("Графитовый" to 0xFF202124.toInt(), "Синий" to 0xFF1565C0.toInt(),
            "Красный" to 0xFFC62828.toInt(), "Зелёный" to 0xFF2E7D32.toInt(),
            "Фиолетовый" to 0xFF6A1B9A.toInt(), "Оранжевый" to 0xFFEF6C00.toInt())
        colors.forEach { (name, color) ->
            action((if (prefs.getInt("color", colors[0].second) == color) "✓ " else "") + name) {
                prefs.edit().putInt("color", color).apply()
                refreshService()
                recreate()
            }
        }

        heading("Значок")
        val icons = listOf("⏻", "⏼", "⏽", "●")
        icons.forEachIndexed { index, symbol ->
            action((if (prefs.getInt("icon_index", 0) == index) "✓ " else "") + symbol) {
                prefs.edit().putInt("icon_index", index).apply(); refreshService(); recreate()
            }
        }

        heading("Дополнительные функции")
        val vibration = android.widget.CheckBox(this).apply {
            text = "Вибрация при нажатии"; isChecked = prefs.getBoolean("vibration", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("vibration", checked).apply() }
        }
        root.addView(vibration)
        val snap = android.widget.CheckBox(this).apply {
            text = "Прилипание к ближайшему краю"; isChecked = prefs.getBoolean("snap_edge", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("snap_edge", checked).apply() }
        }
        root.addView(snap)

        action("Применить настройки") { refreshService() }
        action("Скрыть кнопку на 30 секунд") {
            startService(Intent(this, FloatingButtonService::class.java).setAction(FloatingButtonService.ACTION_HIDE_TEMP))
        }
        action("1. Разрешить отображение поверх окон") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        action("2. Разрешить блокировку экрана") {
            val c = ComponentName(this, LockAdminReceiver::class.java)
            val d = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!d.isAdminActive(c)) startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, c)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Нужно для блокировки экрана коротким нажатием."))
        }
        action("3. Включить службу доступности") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        action("Запустить плавающую кнопку") {
            if (Settings.canDrawOverlays(this)) {
                val i = Intent(this, FloatingButtonService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            } else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        action("Остановить плавающую кнопку") { stopService(Intent(this, FloatingButtonService::class.java)) }
        setContentView(scroll)
    }

    private fun refreshService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        stopService(Intent(this, FloatingButtonService::class.java))
        val i = Intent(this, FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
}
