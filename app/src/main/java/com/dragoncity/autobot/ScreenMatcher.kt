package com.dragoncity.autobot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/** Restricted local search. Never matches arbitrary controls elsewhere on screen. */
class ScreenMatcher {
    fun find(screen: Bitmap, template: Bitmap, type: TaskType, threshold: Double = 0.93,
        expectedX: Int = screen.width / 2, expectedY: Int = screen.height / 2,
        scale: Double = 1.0, offsetX: Double = 0.5, offsetY: Double = 0.5): Target? {
        if (screen.isRecycled || template.isRecycled || template.width < 8 || template.height < 8 ||
            scale !in 0.5..2.0 || threshold !in 0.0..1.0) return null
        val w = (template.width * scale).toInt()
        val h = (template.height * scale).toInt()
        if (w > screen.width || h > screen.height || w < 8 || h < 8) return null
        val resized = Bitmap.createScaledBitmap(template, w, h, true)
        try {
            // Uniform templates are unsafe: too many unrelated regions match them.
            val colors = IntArray(64) { i -> resized.getPixel((i%8)*(w-1)/7, (i/8)*(h-1)/7) }
            val brightness = colors.map { (Color.red(it)+Color.green(it)+Color.blue(it))/3.0 }
            val mean = brightness.average()
            if (brightness.sumOf { (it-mean)*(it-mean) } / 64 < 80) return null
            val radius = (64 * scale).toInt()
            val originX = (expectedX - w*offsetX).toInt()
            val originY = (expectedY - h*offsetY).toInt()
            val minX = (originX-radius).coerceAtLeast(0)
            val maxX = (originX+radius).coerceAtMost(screen.width-w)
            val minY = (originY-radius).coerceAtLeast(0)
            val maxY = (originY+radius).coerceAtMost(screen.height-h)
            if (minX > maxX || minY > maxY) return null
            fun score(x: Int, y: Int): Double {
                var distance = 0L
                for (i in colors.indices) {
                    val a = screen.getPixel(x+(i%8)*(w-1)/7, y+(i/8)*(h-1)/7)
                    val b = colors[i]
                    distance += abs(Color.red(a)-Color.red(b)) + abs(Color.green(a)-Color.green(b)) + abs(Color.blue(a)-Color.blue(b))
                }
                return 1.0-distance/(64*765.0)
            }
            var best = -1.0; var bx = minX; var by = minY
            val candidates = mutableListOf<Pair<Int, Int>>()
            for (y in minY..maxY) for (x in minX..maxX) {
                val s = score(x,y)
                if (s > best) { best=s; bx=x; by=y }
                if (s >= threshold) candidates.add(x to y)
            }
            if (best < threshold) return null
            // A second comparably strong target outside the local peak makes the tap ambiguous.
            if (candidates.any { (x,y) -> abs(x-bx)>w/3 || abs(y-by)>h/3 }) return null
            return Target(type, (bx+w*offsetX).toInt(), (by+h*offsetY).toInt(), best)
        } finally { if (resized !== template) resized.recycle() }
    }
}
