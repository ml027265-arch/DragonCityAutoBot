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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

/** No root. Every asynchronous operation belongs to one cancellable session. */
class BotAccessibilityService : AccessibilityService() {
    companion object {
        private var reference = java.lang.ref.WeakReference<BotAccessibilityService>(null)
        val active: BotAccessibilityService? get() = reference.get()
    }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private lateinit var capture: ScreenCapture
    private lateinit var store: BattleDemonstration
    private val matcher = ScreenMatcher()
    private lateinit var window: WindowManager
    private var overlay: View? = null
    private var training = false
    private var playing = false
    private var awaiting = false
    private var generation = 0L
    private var stepIndex = 0
    private var steps = emptyList<BattleStep>()
    private var guard = ReplayGuard(SystemClock::elapsedRealtime)
    private var lastTrainingTap = 0L
    private var expectedWidth = 0
    private var expectedHeight = 0
    private var lastActionX = -1
    private var lastActionY = -1
    private var lastPre: IntArray? = null
    private var sessionPackage: String? = null
    private var trainingBefore: Bitmap? = null
    private var notice: Toast? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        reference = java.lang.ref.WeakReference(this)
        capture = ScreenCapture(this)
        store = BattleDemonstration(this)
        window = getSystemService(WINDOW_SERVICE) as WindowManager
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (training || playing) {
            val pkg = event?.packageName?.toString()
            if (pkg != null && pkg != packageName && pkg != sessionPackage &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) stopAll("Jogo saiu de primeiro plano")
        }
    }
    override fun onInterrupt() { stopAll("Serviço interrompido") }
    override fun onDestroy() {
        stopAll(null); reference.clear(); recognizer.close(); worker.shutdown(); super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (training || playing) stopAll("Configuração da tela mudou")
    }
    private fun selectedPackage() = getSharedPreferences("bot", MODE_PRIVATE).getString("package", null)
    private fun activeRoot(): AccessibilityNodeInfo? = try { rootInActiveWindow } catch (_: RuntimeException) { null }
    private fun inGame(): Boolean {
        val root = activeRoot() ?: return false
        return try { sessionPackage != null && sessionPackage == selectedPackage() && root.packageName?.toString() == sessionPackage }
        finally { @Suppress("DEPRECATION") root.recycle() }
    }
    private fun accessibleText(): String {
        val root = activeRoot() ?: return ""
        val text = StringBuilder()
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 20 || count++ > 400) return
            text.append(node.text ?: "").append(' ').append(node.contentDescription ?: "").append(' ')
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
        }
        try { visit(root, 0) } finally { @Suppress("DEPRECATION") root.recycle() }
        return text.toString()
    }
    private fun toast(message: String) {
        notice?.cancel()
        notice = Toast.makeText(this, message, Toast.LENGTH_LONG).also { it.show() }
    }
    private fun removeOverlay() {
        overlay?.let { try { window.removeView(it) } catch (_: IllegalArgumentException) { } }
        overlay = null
    }
    fun stopAll(reason: String? = null) {
        notice?.cancel(); notice = null
        generation++; training = false; playing = false; awaiting = false; guard.stop()
        trainingBefore?.recycle(); trainingBefore = null
        handler.removeCallbacksAndMessages(null); removeOverlay()
        if (reason != null) {
            getSharedPreferences("bot", MODE_PRIVATE).edit().putString("status", reason).apply()
            toast(reason)
        }
    }
    private fun current(token: Long) = token == generation && (training || playing)
    private fun begin(teach: Boolean): Boolean {
        stopAll(); sessionPackage = selectedPackage()
        if (!inGame()) { toast("O jogo selecionado precisa estar em primeiro plano"); return false }
        training = teach; playing = !teach
        expectedWidth = 0; expectedHeight = 0; lastActionX = -1; lastActionY = -1; lastPre = null
        lastTrainingTap = 0
        guard = ReplayGuard(SystemClock::elapsedRealtime)
        val token = generation
        handler.postDelayed({ if (current(token)) stopAll("Limite de 90 segundos atingido") }, 90_000)
        return true
    }
    fun beginTraining() {
        if (!begin(true)) return
        store.clear(); addOverlay()
        toast("Ensinar: até 12 toques. Depois revise as ações no app.")
    }
    fun beginReplay() {
        val loaded = store.load()
        if (loaded.isEmpty() || loaded.any { !it.approved }) {
            toast("Ensine e revise todas as ações antes de executar"); return
        }
        if (!begin(false)) return
        steps = loaded; stepIndex = 0; addOverlay()
        handler.postDelayed({ replayStep(generation) }, 500)
    }
    private fun addOverlay() {
        if ((!training && !playing) || overlay != null) return
        val bandHeight = minOf((48 * resources.displayMetrics.density).toInt(), (resources.displayMetrics.heightPixels*0.10).toInt())
        val view = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                paint.color = Color.argb(235, 25, 25, 25)
                canvas.drawRect(0f, 0f, width.toFloat(), bandHeight.toFloat(), paint)
                paint.color = Color.WHITE; paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
                canvas.drawText(if (training) "ENSINAR ${store.load().size}/12 • PARAR" else "EXECUTAR ${stepIndex+1}/${steps.size} • PARAR",
                    12f, bandHeight * 0.65f, paint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    if (event.y < bandHeight) stopAll("Parado pelo usuário")
                    else if (training && !awaiting) {
                        val origin = IntArray(2); getLocationOnScreen(origin)
                        recordTap(event.x.toInt() + origin[0], event.y.toInt() + origin[1], generation)
                    }
                    performClick()
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            if (training) WindowManager.LayoutParams.MATCH_PARENT else bandHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        try { window.addView(view, params); overlay = view }
        catch (_: Exception) { stopAll("Falha ao abrir painel") }
    }
    /** Capture + OCR must complete before any gesture. Failure or timeout halts the session. */
    private fun safeFrame(token: Long, done: (Bitmap) -> Unit) {
        if (!current(token)) return
        if (!inGame()) { stopAll("Jogo não está ativo"); return }
        val timeout = Runnable { if (current(token)) stopAll("Captura/OCR excedeu 8 segundos") }
        handler.postDelayed(timeout, 8_000)
        capture.capture { bitmap ->
            if (!current(token)) { bitmap?.recycle(); return@capture }
            if (bitmap == null) { handler.removeCallbacks(timeout); stopAll("Captura indisponível ou protegida"); return@capture }
            if (expectedWidth == 0) { expectedWidth = bitmap.width; expectedHeight = bitmap.height }
            if (bitmap.width != expectedWidth || bitmap.height != expectedHeight || !inGame()) {
                bitmap.recycle(); handler.removeCallbacks(timeout); stopAll("Orientação, resolução ou foco mudou"); return@capture
            }
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnCompleteListener { task ->
                        handler.removeCallbacks(timeout)
                        if (!current(token)) { bitmap.recycle(); return@addOnCompleteListener }
                        if (!task.isSuccessful || !inGame() ||
                            SafetyPolicy.spendingRisk(task.result?.text.orEmpty() + " " + accessibleText())) {
                            bitmap.recycle(); stopAll("Texto de compra/gasto, foco ou OCR inseguro: nenhum toque"); return@addOnCompleteListener
                        }
                        done(bitmap)
                    }
            } catch (_: Exception) { handler.removeCallbacks(timeout); bitmap.recycle(); stopAll("Falha no OCR") }
        }
    }
    private fun tap(x: Int, y: Int, token: Long, done: () -> Unit) {
        if (!current(token)) return
        if (!inGame() || SafetyPolicy.spendingRisk(accessibleText()) ||
            !SafetyPolicy.validPoint(x.toDouble()/expectedWidth, y.toDouble()/expectedHeight)) {
            stopAll("Toque fora da área segura ou foco perdido"); return
        }
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 75)).build()
        val timeout = Runnable { if (current(token)) stopAll("Toque sem confirmação; não será repetido") }
        handler.postDelayed(timeout, 3_000)
        val sent = try { dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                handler.removeCallbacks(timeout); if (current(token)) done()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                handler.removeCallbacks(timeout); if (current(token)) stopAll("Toque cancelado; não será repetido")
            }
        }, handler) } catch (_: RuntimeException) { false }
        if (!sent) { handler.removeCallbacks(timeout); stopAll("Android recusou o gesto") }
    }
    private fun recordTap(x: Int, y: Int, token: Long) {
        if (!current(token) || awaiting || SystemClock.elapsedRealtime()-lastTrainingTap < 1_500) return
        awaiting = true; lastTrainingTap = SystemClock.elapsedRealtime(); removeOverlay()
        handler.postDelayed({ safeFrame(token) { before ->
            if (!SafetyPolicy.validPoint(x.toDouble()/before.width, y.toDouble()/before.height)) {
                before.recycle(); stopAll("Área de toque não permitida"); return@safeFrame
            }
            // No bitmap ownership crosses a cancelled delayed task.
            val preserved = before.copy(Bitmap.Config.ARGB_8888, false)
            trainingBefore = preserved
            before.recycle()
            tap(x, y, token) {
                handler.postDelayed({ safeFrame(token) { after ->
                    val ok = try { store.add(preserved, after, x, y) } catch (_: Exception) { false }
                    after.recycle(); preserved.recycle()
                    trainingBefore = null
                    if (!ok) { stopAll("Tela não mudou, limite ou gravação falhou"); return@safeFrame }
                    awaiting = false
                    if (store.load().size >= 12) stopAll("12 ações salvas; revise no app") else addOverlay()
                } }, 1_700)
            }
        } }, 250)
    }
    private fun replayStep(token: Long) {
        if (!current(token)) return
        if (stepIndex >= steps.size) { stopAll("Sequência verificada concluída; resultado da batalha não classificado"); return }
        val step = steps[stepIndex]
        safeFrame(token) { bitmap ->
            val pre = VisualContext.sample(bitmap)
            if (!SafetyPolicy.sameGeometry(bitmap.width, bitmap.height, step.width, step.height) ||
                VisualContext.similarity(pre, step.before) < 0.94) {
                bitmap.recycle(); stopAll("Tela desconhecida ou geometria incompatível"); return@safeFrame
            }
            val scale = bitmap.width.toDouble()/step.width
            val template = store.template(step)
            if (template == null) { bitmap.recycle(); stopAll("Modelo visual ausente"); return@safeFrame }
            val searchTimeout = Runnable { if (current(token)) stopAll("Reconhecimento visual excedeu 8 segundos") }
            handler.postDelayed(searchTimeout, 8_000)
            worker.execute {
                val target = try { matcher.find(bitmap, template, TaskType.BATTLE, 0.96,
                    (step.nx*bitmap.width).toInt(), (step.ny*bitmap.height).toInt(), scale, step.offsetX, step.offsetY) }
                catch (_: Exception) { null }
                finally { template.recycle(); bitmap.recycle() }
                handler.post {
                    handler.removeCallbacks(searchTimeout)
                    if (!current(token)) return@post
                    if (target == null) { stopAll("Alvo desconhecido, uniforme ou ambíguo"); return@post }
                    if (lastPre != null && kotlin.math.abs(lastActionX-target.x)<24 && kotlin.math.abs(lastActionY-target.y)<24 &&
                        VisualContext.similarity(lastPre!!, pre)>0.985) {
                        stopAll("Ação repetida na mesma tela bloqueada"); return@post
                    }
                    if (!guard.reserve()) { stopAll("Limite ou ação duplicada"); return@post }
                    lastPre = pre; lastActionX = target.x; lastActionY = target.y
                    tap(target.x, target.y, token) {
                        handler.postDelayed({ safeFrame(token) { after ->
                            val pixels = VisualContext.sample(after); after.recycle()
                            val changed = VisualContext.similarity(pre, pixels)<0.985
                            if (!guard.verified(changed) || VisualContext.similarity(pixels, step.after)<0.94) {
                                stopAll("Resultado inesperado; toque não será repetido"); return@safeFrame
                            }
                            stepIndex++; overlay?.invalidate()
                            handler.postDelayed({ replayStep(token) }, 400)
                        } }, 1_700)
                    }
                }
            }
        }
    }
}
