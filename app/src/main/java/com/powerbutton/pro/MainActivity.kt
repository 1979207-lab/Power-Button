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
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("button_settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 30, 28, 24)
        }
        val scroll = ScrollView(this)
        scroll.addView(root)
        root.addView(TextView(this).apply { text = "Power Button PRO"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "Короткое нажатие — блокировка экрана. Долгое нажатие — системное меню питания. Настройте внешний вид кнопки ниже."
            textSize = 16f
            setPadding(0, 16, 0, 18)
        })

        fun addButton(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        root.addView(TextView(this).apply { text = "Размер кнопки"; textSize = 18f })
        val sizeLabel = TextView(this)
        val sizeSeek = SeekBar(this).apply {
            max = 60
            progress = (prefs.getInt("size", 58) - 40).coerceIn(0, 60)
        }
        fun updateSizeLabel() { sizeLabel.text = "${sizeSeek.progress + 40} dp" }
        updateSizeLabel()
        sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateSizeLabel() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("size", sizeSeek.progress + 40).apply()
            }
        })
        root.addView(sizeLabel)
        root.addView(sizeSeek)

        root.addView(TextView(this).apply { text = "Прозрачность"; textSize = 18f; setPadding(0, 14, 0, 0) })
        val opacityLabel = TextView(this)
        val opacitySeek = SeekBar(this).apply {
            max = 80
            progress = (prefs.getInt("opacity", 88) - 20).coerceIn(0, 80)
        }
        fun updateOpacityLabel() { opacityLabel.text = "${opacitySeek.progress + 20}%" }
        updateOpacityLabel()
        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updateOpacityLabel() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("opacity", opacitySeek.progress + 20).apply()
            }
        })
        root.addView(opacityLabel)
        root.addView(opacitySeek)

        root.addView(TextView(this).apply { text = "Цвет кнопки"; textSize = 18f; setPadding(0, 14, 0, 6) })
        val colors = listOf(
            "Графитовый" to 0xFF202124.toInt(),
            "Синий" to 0xFF1565C0.toInt(),
            "Красный" to 0xFFC62828.toInt(),
            "Зелёный" to 0xFF2E7D32.toInt(),
            "Фиолетовый" to 0xFF6A1B9A.toInt(),
            "Оранжевый" to 0xFFEF6C00.toInt()
        )
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colors.forEach { (name, color) ->
            colorRow.addView(Button(this).apply {
                text = if (prefs.getInt("color", 0xFF202124.toInt()) == color) "✓ $name" else name
                setOnClickListener {
                    prefs.edit().putInt("color", color).apply()
                    for (i in 0 until colorRow.childCount) {
                        val b = colorRow.getChildAt(i) as Button
                        b.text = colors[i].first
                    }
                    text = "✓ $name"
                }
            })
        }
        root.addView(colorRow)

        addButton("Применить настройки к плавающей кнопке") {
            if (Settings.canDrawOverlays(this)) {
                stopService(Intent(this, FloatingButtonService::class.java))
                val service = Intent(this, FloatingButtonService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        addButton("1. Разрешить отображение поверх окон") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        addButton("2. Разрешить блокировку экрана") {
            val c = ComponentName(this, LockAdminReceiver::class.java)
            val d = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (!d.isAdminActive(c)) startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, c).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Нужно для блокировки экрана коротким нажатием."))
        }
        addButton("3. Включить службу доступности") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        addButton("Запустить плавающую кнопку") {
            if (Settings.canDrawOverlays(this)) {
                val i = Intent(this, FloatingButtonService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            }
        }
        addButton("Остановить плавающую кнопку") { stopService(Intent(this, FloatingButtonService::class.java)) }
        setContentView(scroll)
    }
}
