package com.dragoncity.autobot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import java.util.concurrent.Executor

/**
 * Requires user-enabled AccessibilityService and canTakeScreenshot=true.
 * FLAG_SECURE windows may not be capturable.
 */
class ScreenCapture(private val service: AccessibilityService) {
 fun capture(onResult: (Bitmap?) -> Unit) {
  if (Build.VERSION.SDK_INT < 30) { onResult(null); return }
  service.takeScreenshot(Display.DEFAULT_DISPLAY, Executor { action ->
   Handler(Looper.getMainLooper()).post(action)
  }, object : AccessibilityService.TakeScreenshotCallback {
   override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
    val buffer = result.hardwareBuffer
    try {
     val bitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
     onResult(bitmap?.copy(Bitmap.Config.ARGB_8888, false))
    } finally { buffer.close() }
   }
   override fun onFailure(errorCode: Int) { onResult(null) }
  })
 }
}