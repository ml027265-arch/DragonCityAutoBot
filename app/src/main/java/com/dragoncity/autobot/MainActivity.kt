package com.dragoncity.autobot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var store: BattleDemonstration
    private var launchPending = false
    private val prefs by lazy { getSharedPreferences("bot", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = BattleDemonstration(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        layout.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            view.setPadding(24+bars.left, 24+bars.top, 24+bars.right, 24+bars.bottom); insets
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply { this.text=text; textSize=size }.also { layout.addView(it) }
        fun button(text: String, click: () -> Unit) = Button(this).apply {
            this.text=text; setOnClickListener { click() }
        }.also { layout.addView(it) }
        label("DragonCity AutoBot", 24f)
        label("Batalhas experimentais, sem root. Ensine até 12 toques e revise cada ação antes do replay. O app compara telas e interrompe diante de diferenças. Não escolhe ataques nem garante vitória.")
        label("Compras: bloqueio por texto OCR/acessibilidade e revisão manual. Ícones ou textos não reconhecidos podem escapar. Demonstre somente controles gratuitos de batalha.")
        status = label("")
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString() }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                apps.map { "${it.loadLabel(packageManager)} (${it.activityInfo.packageName})" })
            val selected = apps.indexOfFirst { it.activityInfo.packageName == prefs.getString("package", null) }
            if (selected >= 0) setSelection(selected)
        }
        layout.addView(spinner)
        button("Selecionar jogo") {
            val i = spinner.selectedItemPosition
            if (i in apps.indices) {
                cancelPending(); BotAccessibilityService.active?.stopAll()
                val pkg = apps[i].activityInfo.packageName
                if (prefs.getString("package", null) != pkg) store.clear()
                prefs.edit().putString("package", pkg).apply(); refreshStatus()
            }
        }
        button("Autorizar acessibilidade") {
            AlertDialog.Builder(this).setTitle("Permitir captura e gestos")
                .setMessage("Este serviço pode ler a tela e executar toques no jogo selecionado, apenas após ENSINAR ou EXECUTAR. As imagens ficam no armazenamento privado. Você pode parar pelo painel sobre o jogo ou desativar o serviço nas configurações.")
                .setPositiveButton("Abrir configurações") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                .setNegativeButton("Cancelar", null).show()
        }
        button("Abrir jogo selecionado") { gameIntent()?.let { startActivity(it) } ?: message("Selecione o jogo primeiro") }
        button("ENSINAR batalha") {
            AlertDialog.Builder(this).setTitle("Nova demonstração")
                .setMessage("A gravação anterior será substituída. Demonstre apenas ações gratuitas de batalha. Compras detectadas serão bloqueadas.")
                .setPositiveButton("Ensinar") { _, _ -> startMode(true) }.setNegativeButton("Cancelar", null).show()
        }
        button("Revisar ações ensinadas") { review(0) }
        button("EXECUTAR batalha revisada") { startMode(false) }
        button("PARAR automação") { cancelPending(); BotAccessibilityService.active?.stopAll("Parado pelo usuário"); refreshStatus() }
        button("Apagar demonstração") {
            AlertDialog.Builder(this).setMessage("Apagar a demonstração e todas as imagens locais?")
                .setPositiveButton("Apagar") { _, _ -> cancelPending(); BotAccessibilityService.active?.stopAll(); store.clear(); refreshStatus() }
                .setNegativeButton("Cancelar", null).show()
        }
        setContentView(ScrollView(this).apply { addView(layout) })
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun gameIntent() = prefs.getString("package", null)?.let { packageManager.getLaunchIntentForPackage(it) }
    private fun startMode(teach: Boolean) {
        if (launchPending) return
        val service = BotAccessibilityService.active ?: run { message("Ative a acessibilidade primeiro"); return }
        val intent = gameIntent() ?: run { message("Selecione o jogo primeiro"); return }
        if (!teach && (store.load().isEmpty() || store.load().any { !it.approved })) {
            message("Ensine e revise todas as ações primeiro"); return
        }
        launchPending = true
        try { startActivity(intent) } catch (_: RuntimeException) { launchPending=false; message("Não foi possível abrir o jogo"); return }
        handler.postDelayed({
            launchPending = false
            if (BotAccessibilityService.active === service) {
                if (teach) service.beginTraining() else service.beginReplay()
            }
        }, 2_200)
    }
    private fun cancelPending() { handler.removeCallbacksAndMessages(null); launchPending=false }
    override fun onResume() { super.onResume(); if (::status.isInitialized) { cancelPending(); refreshStatus() } }
    override fun onDestroy() { cancelPending(); super.onDestroy() }
    private fun refreshStatus() {
        val steps = store.load()
        status.text = "Acessibilidade: ${if (BotAccessibilityService.active != null) "ativa" else "desativada"}\n"+
            "Demonstração: ${steps.size} ações, ${steps.count { it.approved }} revisadas\nÚltimo status: " + prefs.getString("status", "Parado")
    }
    private fun review(index: Int) {
        val steps = store.load()
        if (steps.isEmpty()) { message("Nenhuma demonstração válida"); return }
        if (index >= steps.size) { refreshStatus(); message("Revisão concluída"); return }
        val step = steps[index]
        val source = store.preview(step) ?: run { message("Imagem ausente"); return }
        val bitmap = source.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        source.recycle()
        android.graphics.Canvas(bitmap).drawCircle((step.nx*bitmap.width).toFloat(), (step.ny*bitmap.height).toFloat(), 24f,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=android.graphics.Color.RED; style=android.graphics.Paint.Style.STROKE; strokeWidth=6f
            })
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        content.addView(TextView(this).apply {
            text="Ação ${index+1}/${steps.size}: posição ${(step.nx*100).toInt()}%, ${(step.ny*100).toInt()}%. Confirme somente se este controle não compra, não gasta recursos e não confirma uma compra."
        })
        content.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true; minimumHeight=250 })
        AlertDialog.Builder(this).setTitle("Revisar ação gratuita").setView(content)
            .setPositiveButton("Confirmar ação gratuita") { _, _ ->
                if (store.approve(index)) review(index+1) else message("Demonstração mudou; revise novamente")
            }.setNegativeButton("Manter bloqueada", null).create().apply {
                setOnDismissListener { bitmap.recycle(); refreshStatus() }; show()
            }
    }
}
