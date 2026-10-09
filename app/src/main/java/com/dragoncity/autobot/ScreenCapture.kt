package com.dragoncity.autobot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import java.util.concurrent.Executor

/** Caller owns the software bitmap; hardware wrappers and buffers are released here. */
class ScreenCapture(private val service: AccessibilityService) {
    var lastFailure: String? = null
        private set
    private val handler = Handler(Looper.getMainLooper())
    fun capture(onResult: (Bitmap?) -> Unit) {
        lastFailure = null
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, Executor { handler.post(it) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        var wrapper: Bitmap? = null
                        val copy = try {
                            wrapper = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            wrapper?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (e: Exception) { lastFailure = "Imagem inválida (${e.javaClass.simpleName})"; null }
                        finally { wrapper?.recycle(); buffer.close() }
                        onResult(copy)
                    }
                    override fun onFailure(errorCode: Int) {
                        lastFailure = "Android recusou a captura (código $errorCode)"; onResult(null)
                    }
                })
        } catch (e: RuntimeException) { lastFailure = "Captura recusada (${e.javaClass.simpleName})"; onResult(null) }
    }
}
