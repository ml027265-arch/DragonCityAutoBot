package com.dragoncity.autobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Demonstration mode intercepts taps on an accessibility overlay, records a
 * screenshot before each tap and forwards the tap through dispatchGesture.
 * Replay is bounded, sequential and requires visual confirmation.
 */
class BotAccessibilityService : AccessibilityService() {
 companion object { var active: BotAccessibilityService? = null; private set }
 private val handler = Handler(Looper.getMainLooper())
 private lateinit var capture: ScreenCapture
 private lateinit var store: BattleDemonstration
 private val matcher = ScreenMatcher()
 private var window: WindowManager? = null
 private var overlay: View? = null
 private var training = false
 private var playing = false
 private var stepIndex = 0
 private var startedAt = 0L
 private var lastPackage: String? = null
 private var awaiting = false

 override fun onServiceConnected() {
  super.onServiceConnected()
  active = this
  capture = ScreenCapture(this)
  store = BattleDemonstration(this)
  window = getSystemService(WINDOW_SERVICE) as WindowManager
 }
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  val pkg = event?.packageName?.toString()
  if (pkg != null && pkg != packageName) {
   lastPackage = pkg
   if (playing && pkg != selectedPackage()) stopAll("Jogo saiu de primeiro plano")
  }
 }
 override fun onInterrupt() { stopAll("Serviço interrompido") }
 override fun onDestroy() { stopAll(null); active = null; super.onDestroy() }
 private fun selectedPackage() = getSharedPreferences("bot", MODE_PRIVATE).getString("package", null)
 private fun inGame() = selectedPackage() != null && selectedPackage() == lastPackage
 private fun toast(s: String) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show() }
 private fun removeOverlay() {
  overlay?.let { try { window?.removeView(it) } catch (_: Exception) {} }
  overlay = null
 }
 fun stopAll(reason: String? = null) {
  training = false; playing = false; awaiting = false
  handler.removeCallbacksAndMessages(null)
  removeOverlay()
  if (reason != null) toast(reason)
 }
 fun beginTraining() {
  stopAll()
  if (!inGame()) { toast("Abra primeiro o jogo selecionado e volte ao app"); return }
  store.clear()
  training = true
  addOverlay()
  toast("Ensinar: toque até 12 vezes. Toque PARAR no topo para salvar.")
 }
 private fun addOverlay() {
  if (!training || overlay != null) return
  val view = object : View(this) {
   val paint = Paint(Paint.ANTI_ALIAS_FLAG)
   override fun onDraw(canvas: Canvas) {
    paint.color = Color.argb(215,25,25,25)
    canvas.drawRect(0f,0f,width.toFloat(),72f,paint)
    paint.color = Color.WHITE; paint.textSize = 35f
    canvas.drawText("ENSINAR • ${store.load().size}/12     PARAR",18f,48f,paint)
   }
   override fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.action == MotionEvent.ACTION_UP) {
     if (event.y < 88f) { stopAll("Demonstração salva"); return true }
     if (!awaiting) recordTap(event.x.toInt(),event.y.toInt())
    }
    return true
   }
  }
  val params = WindowManager.LayoutParams(
   WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
   WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
   android.graphics.PixelFormat.TRANSLUCENT
  ).apply { gravity = Gravity.TOP or Gravity.START }
  try { window?.addView(view,params); overlay = view } catch (e: Exception) { stopAll("Falha ao abrir painel") }
 }
 private fun recordTap(x: Int,y: Int) {
  if (!training || awaiting) return
  awaiting = true
  removeOverlay()
  handler.postDelayed({
   if (!training) return@postDelayed
   capture.capture { bitmap ->
    if (!training) { bitmap?.recycle(); return@capture }
    if (bitmap == null) { stopAll("Captura bloqueada pelo Android/jogo"); return@capture }
    val ok = try { store.add(bitmap,x,y) } catch (_: Exception) { false }
    bitmap.recycle()
    if (!ok) { stopAll("Limite de 12 ações ou falha de gravação"); return@capture }
    tap(x,y) {
     handler.postDelayed({
      awaiting = false
      if (training) addOverlay()
     },450)
    }
   }
  },150)
 }
 private fun tap(x:Int,y:Int,done:()->Unit) {
  val path=Path().apply { moveTo(x.toFloat(),y.toFloat()) }
  val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,75)).build()
  val sent=dispatchGesture(gesture,object: GestureResultCallback() {
   override fun onCompleted(gestureDescription:GestureDescription?) { handler.post { done() } }
   override fun onCancelled(gestureDescription:GestureDescription?) { handler.post { stopAll("Toque cancelado") } }
  },handler)
  if (!sent) stopAll("Não foi possível executar toque")
 }
 fun beginReplay() {
  stopAll()
  if (!inGame()) { toast("Abra primeiro o jogo selecionado e volte ao app"); return }
  if (store.load().isEmpty()) { toast("Ensine uma batalha primeiro"); return }
  playing=true; stepIndex=0; startedAt=SystemClock.elapsedRealtime()
  toast("Executar: no máximo 12 ações, com confirmação visual")
  handler.postDelayed({ replayStep() },450)
 }
 private fun replayStep() {
  if (!playing) return
  if (!inGame()) { stopAll("Jogo não está ativo"); return }
  if (SystemClock.elapsedRealtime()-startedAt > 90000L) { stopAll("Tempo máximo excedido"); return }
  val steps=store.load()
  if (stepIndex >= steps.size) { stopAll("Sequência concluída"); return }
  val step=steps[stepIndex]
  capture.capture { bitmap ->
   if (!playing) { bitmap?.recycle(); return@capture }
   if (bitmap==null) { stopAll("Captura indisponível"); return@capture }
   val template=store.template(step)
   if (template==null) { bitmap.recycle(); stopAll("Modelo visual ausente"); return@capture }
   val target=try { matcher.find(bitmap,template,TaskType.BATTLE,0.93) } catch (_:Exception) { null }
   template.recycle()
   if (target==null) { bitmap.recycle(); stopAll("Tela diferente da demonstração: toque não executado"); return@capture }
   val x=(step.nx*bitmap.width).toInt()
   val y=(step.ny*bitmap.height).toInt()
   bitmap.recycle()
   // Require the learned target to remain near the recorded normalized position.
   if (kotlin.math.abs(target.x-x)>120 || kotlin.math.abs(target.y-y)>120) {
    stopAll("Alvo visual deslocado: execução interrompida"); return@capture
   }
   tap(x,y) {
    stepIndex++
    handler.postDelayed({ replayStep() },1700)
   }
  }
 }
}
