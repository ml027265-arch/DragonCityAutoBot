package com.dragoncity.autobot

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.random.Random

/** Debug-only synthetic battle. Never opens a game or spends resources. */
class BattleFixtureActivity : Activity() {
    @Volatile var actionCount = 0
        private set
    var targetShift = 0
    @Volatile var lastTouchX = -1
        private set
    var showPurchase = false
    lateinit var surface: View
    private val colors = IntArray(96*96).also { values ->
        val random = Random(12)
        for (i in values.indices) values[i] = Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface = object : View(this) {
            private val paint = Paint()
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(if (actionCount%2==0) Color.rgb(16,36,70) else Color.rgb(90,140,100))
                paint.color = Color.WHITE; paint.textSize = 45f
                canvas.drawText(if (showPurchase) "BUY GEMS $5" else "ATTACK", 60f, height*0.30f, paint)
                val cx = width/2+targetShift; val cy=height/2
                for (y in 0 until 96) for (x in 0 until 96) {
                    paint.color=colors[y*96+x]
                    canvas.drawRect((cx-48+x).toFloat(),(cy-48+y).toFloat(),(cx-47+x).toFloat(),(cy-47+y).toFloat(),paint)
                }
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action==MotionEvent.ACTION_UP && kotlin.math.abs(event.x-(width/2+targetShift))<40 && kotlin.math.abs(event.y-height/2)<40) {
                    lastTouchX=event.x.toInt()
                    actionCount++; invalidate(); performClick()
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        setContentView(surface)
    }
    fun reset() { actionCount=0; lastTouchX=-1; targetShift=0; showPurchase=false; surface.invalidate() }
    fun target(): IntArray {
        val location=IntArray(2); surface.getLocationOnScreen(location)
        return intArrayOf(location[0]+surface.width/2+targetShift, location[1]+surface.height/2)
    }
}
