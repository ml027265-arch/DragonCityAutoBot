package com.dragoncity.autobot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object VisualContext {
    // Ignore the upper status/control band; compare the rest of the whole screen.
    fun sample(bitmap: Bitmap): IntArray = IntArray(32 * 24) { i ->
        val x = ((i % 32 + 0.5) / 32 * bitmap.width).toInt().coerceAtMost(bitmap.width - 1)
        val y = ((0.12 + (i / 32 + 0.5) / 24 * 0.86) * bitmap.height).toInt().coerceAtMost(bitmap.height - 1)
        bitmap.getPixel(x, y)
    }
    fun similarity(a: IntArray, b: IntArray): Double {
        if (a.size != 768 || b.size != a.size) return 0.0
        var distance = 0L
        for (i in a.indices) {
            distance += abs(Color.red(a[i]) - Color.red(b[i])) +
                abs(Color.green(a[i]) - Color.green(b[i])) + abs(Color.blue(a[i]) - Color.blue(b[i]))
        }
        return 1.0 - distance / (a.size * 765.0)
    }
}
