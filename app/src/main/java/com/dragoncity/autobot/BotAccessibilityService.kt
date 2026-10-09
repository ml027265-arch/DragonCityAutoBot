package com.dragoncity.autobot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class BotAccessibilityService : AccessibilityService() {
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
 override fun onInterrupt() {}
}