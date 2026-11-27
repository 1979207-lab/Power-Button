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
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=1; setPadding(28,36,28,24) }
  root.addView(TextView(this).apply { text="Power Button PRO"; textSize=26f })
  root.addView(TextView(this).apply { text="Короткое нажатие — блокировка экрана. Долгое нажатие — системное меню питания. Для работы предоставьте разрешения Android."; textSize=16f; setPadding(0,20,0,20) })
  fun add(label:String, action:()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
  add("1. Разрешить отображение поверх окон") { if(!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
  add("2. Разрешить блокировку экрана") { val c=ComponentName(this,LockAdminReceiver::class.java); val d=getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; if(!d.isAdminActive(c)) startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,c).putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Нужно для блокировки экрана коротким нажатием.")) }
  add("3. Включить службу доступности") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
  add("Запустить плавающую кнопку") { if(Settings.canDrawOverlays(this)) { val i=Intent(this,FloatingButtonService::class.java); if(Build.VERSION.SDK_INT>=26) startForegroundService(i) else startService(i) } }
  add("Остановить плавающую кнопку") { stopService(Intent(this,FloatingButtonService::class.java)) }
  setContentView(root)
 }
}