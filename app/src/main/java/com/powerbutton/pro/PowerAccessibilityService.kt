package com.powerbutton.pro
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class PowerAccessibilityService : AccessibilityService() {
 override fun onAccessibilityEvent(event:AccessibilityEvent?) {}
 override fun onInterrupt() {}
 override fun onServiceConnected() { instance=this }
 override fun onDestroy() { if(instance===this) instance=null; super.onDestroy() }
 companion object {
  @Volatile var instance:PowerAccessibilityService?=null
  fun showPowerMenu():Boolean = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false
 }
}