package com.dragoncity.autobot

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight multiscale pixel matcher for local templates. This is not ML.
 * It does not know the current Dragon City UI until templates are supplied.
 */
class ScreenMatcher {
 fun find(screen: Bitmap, template: Bitmap, type: TaskType, threshold: Double = 0.90): Target? {
  if (screen.isRecycled || template.isRecycled || template.width < 8 || template.height < 8) return null
  var best = 0.0
  var point: Target? = null
  val scales = doubleArrayOf(0.75, 1.0, 1.25)
  for (scale in scales) {
   val w = (template.width * scale).toInt()
   val h = (template.height * scale).toInt()
   if (w > screen.width || h > screen.height || w < 8 || h < 8) continue
   val resized = Bitmap.createScaledBitmap(template, w, h, true)
   try {
    val stride = max(6, minOf(w, h) / 5)
    val offsets = intArrayOf(0, w / 4, w / 2, 3 * w / 4, w - 1)
    for (y in 0..(screen.height - h) step stride) {
     for (x in 0..(screen.width - w) step stride) {
      var distance = 0.0
      var samples = 0
      for (py in offsets) for (px in offsets) {
       val a = screen.getPixel(x + px, y + (py.toDouble() * h / w).toInt().coerceIn(0,h-1))
       val b = resized.getPixel(px, (py.toDouble() * h / w).toInt().coerceIn(0,h-1))
       distance += abs(Color.red(a)-Color.red(b)) + abs(Color.green(a)-Color.green(b)) + abs(Color.blue(a)-Color.blue(b))
       samples++
      }
      val score = 1.0 - distance / (samples * 765.0)
      if (score > best) { best = score; point = Target(type, x+w/2, y+h/2, score) }
     }
    }
   } finally { if (resized !== template) resized.recycle() }
  }
  return point?.takeIf { it.confidence >= threshold }
 }
}