package com.dragoncity.autobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
import android.widget.TextView
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
    private var phase = ""
    val visiblePanel: String? get() = overlay?.takeIf { it.isAttachedToWindow }?.contentDescription?.toString()

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
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // Toasts, the overlay and Samsung system panels can emit events without replacing the game.
                val token = generation
                handler.postDelayed({ if (current(token) && !inGame()) stopAll("Jogo saiu de primeiro plano") }, 300)
            }
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
    private fun accessibleRisk(x: Int? = null, y: Int? = null): Boolean {
        val root = activeRoot() ?: return true
        var risk = false
        var count = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 20 || count++ > 400) return
            val text = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}"
            if (SafetyPolicy.purchaseConfirmation(text)) risk = true
            if (x != null && y != null) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                // A root/surface containing the whole screen has no trustworthy local label geometry.
                val localized = expectedWidth <= 0 || expectedHeight <= 0 ||
                    bounds.width().toLong()*bounds.height() < expectedWidth.toLong()*expectedHeight*0.6
                if (localized && SafetyPolicy.labelNearTap(text, bounds.left, bounds.top, bounds.right, bounds.bottom, x, y, 96)) risk=true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { visit(child, depth + 1) } finally { @Suppress("DEPRECATION") child.recycle() }
            }
        }
        try { visit(root, 0) } finally { @Suppress("DEPRECATION") root.recycle() }
        return risk
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
        recognizedText = null
        trainingBefore?.recycle(); trainingBefore = null
        handler.removeCallbacksAndMessages(null); removeOverlay()
        if (reason != null) {
            getSharedPreferences("bot", MODE_PRIVATE).edit().putString("status", reason).apply()
            toast(reason)
            if (::window.isInitialized) showStoppedPanel(reason)
        }
    }
    private fun showStoppedPanel(reason: String) {
        val message = "ENSINO/EXECUÇÃO PARADOS: $reason\nToque aqui para fechar. Veja o último status no app."
        val panel = TextView(this).apply {
            text = message; contentDescription = message; setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(90, 30, 30)); textSize=14f; setPadding(16, 8, 16, 8)
            setOnClickListener { removeOverlay() }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START }
        try { window.addView(panel, params); overlay=panel } catch (_: RuntimeException) { }
    }
    private fun setPhase(message: String) {
        phase=message
        getSharedPreferences("bot", MODE_PRIVATE).edit().putString("status", message).apply()
        removeOverlay(); addOverlay()
    }
    private fun current(token: Long) = token == generation && (training || playing)
    private fun begin(teach: Boolean): Boolean {
        stopAll(); sessionPackage = selectedPackage()
        if (!inGame()) { toast("O jogo selecionado precisa estar em primeiro plano"); return false }
        training = teach; playing = !teach
        phase = ""
        expectedWidth = 0; expectedHeight = 0; lastActionX = -1; lastActionY = -1; lastPre = null
        lastTrainingTap = 0
        guard = ReplayGuard(SystemClock::elapsedRealtime)
        val token = generation
        handler.postDelayed({ if (current(token)) stopAll("Limite de 90 segundos atingido") }, 90_000)
        return true
    }
    fun beginTraining() {
        if (!begin(true)) return
        store.clear(); setPhase("Aguardando toque")
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
        val label = if (training) "ENSINAR ${store.load().size}/12 • $phase • PARAR" else "EXECUTAR ${stepIndex+1}/${steps.size} • PARAR"
        val view = object : View(this) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                paint.color = Color.argb(235, 25, 25, 25)
                canvas.drawRect(0f, 0f, width.toFloat(), bandHeight.toFloat(), paint)
                paint.color = Color.WHITE; paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
                val measured = paint.measureText(label)
                if (measured > width-24) paint.textSize *= (width-24).coerceAtLeast(1) / measured
                canvas.drawText(label, 12f, bandHeight * 0.65f, paint)
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
        view.contentDescription=label
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
            if (training && !awaiting) WindowManager.LayoutParams.MATCH_PARENT else bandHeight,
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
            if (bitmap == null) { handler.removeCallbacks(timeout); stopAll("Captura indisponível: ${capture.lastFailure ?: "imagem protegida"}"); return@capture }
            if (expectedWidth == 0) { expectedWidth = bitmap.width; expectedHeight = bitmap.height }
            if (bitmap.width != expectedWidth || bitmap.height != expectedHeight || !inGame()) {
                bitmap.recycle(); handler.removeCallbacks(timeout); stopAll("Orientação, resolução ou foco mudou"); return@capture
            }
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnCompleteListener { task ->
                        handler.removeCallbacks(timeout)
                        if (!current(token)) { bitmap.recycle(); return@addOnCompleteListener }
                        if (!task.isSuccessful) { bitmap.recycle(); stopAll("OCR falhou; nenhum toque (${task.exception?.javaClass?.simpleName})"); return@addOnCompleteListener }
                        if (!inGame()) { bitmap.recycle(); stopAll("Jogo não está ativo após a captura"); return@addOnCompleteListener }
                        if (SafetyPolicy.purchaseConfirmation(task.result.text) || accessibleRisk()) {
                            bitmap.recycle(); stopAll("Confirmação de compra/gasto detectada: nenhum toque"); return@addOnCompleteListener
                        }
                        recognizedText = task.result
                        done(bitmap)
                    }
            } catch (_: Exception) { handler.removeCallbacks(timeout); bitmap.recycle(); stopAll("Falha no OCR") }
        }
    }
    private var recognizedText: com.google.mlkit.vision.text.Text? = null
    private fun targetRisk(x: Int, y: Int): Boolean {
        val text = recognizedText ?: return true
        return text.textBlocks.any { block -> block.lines.any { line -> line.elements.any { element ->
            val bounds = element.boundingBox
            SafetyPolicy.spendingRisk(element.text) && (bounds == null ||
                SafetyPolicy.labelNearTap(element.text, bounds.left, bounds.top, bounds.right, bounds.bottom, x, y, 96))
        } } } || accessibleRisk(x, y)
    }
    private fun tap(x: Int, y: Int, token: Long, done: () -> Unit) {
        if (!current(token)) return
        if (!inGame() ||
            !SafetyPolicy.validPoint(x.toDouble()/expectedWidth, y.toDouble()/expectedHeight)) {
            stopAll("Toque fora da área segura ou foco perdido"); return
        }
        if (targetRisk(x, y)) { stopAll("Controle de compra/gasto próximo ao toque: ação bloqueada"); return }
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
        awaiting = true; lastTrainingTap = SystemClock.elapsedRealtime(); setPhase("Capturando e verificando")
        handler.postDelayed({ safeFrame(token) { before ->
            if (!SafetyPolicy.validPoint(x.toDouble()/before.width, y.toDouble()/before.height)) {
                before.recycle(); stopAll("Área de toque não permitida"); return@safeFrame
            }
            // No bitmap ownership crosses a cancelled delayed task.
            val preserved = before.copy(Bitmap.Config.ARGB_8888, false)
            trainingBefore = preserved
            before.recycle()
            tap(x, y, token) {
                setPhase("Verificando resultado")
                handler.postDelayed({ safeFrame(token) { after ->
                    val ok = try { store.add(preserved, after, x, y) } catch (_: Exception) { false }
                    after.recycle(); preserved.recycle()
                    trainingBefore = null
                    if (!ok) { stopAll("Toque não salvo: tela sem mudança suficiente, limite ou falha de gravação"); return@safeFrame }
                    awaiting = false
                    if (store.load().size >= 12) stopAll("12 ações salvas; revise no app") else setPhase("Toque salvo; aguardando próximo")
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
