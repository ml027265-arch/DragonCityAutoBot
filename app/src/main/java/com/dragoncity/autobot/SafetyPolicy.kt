package com.dragoncity.autobot

import java.text.Normalizer

/** Fail closed on known purchase language. OCR cannot prove that an action is free. */
object SafetyPolicy {
    private val blocked = Regex("\\b(buy|purchase|pay|payment|checkout|shop|shops|store|stores|offer|offers|sale|gem|gems|diamond|diamonds|comprar|compra|compras|pagar|pagamento|gastar|gasto|loja|lojas|oferta|ofertas|promocao|gema|gemas|diamante|diamantes|reais|dollars|euros)\\b|[€$£]|r\\$")
    private val confirmation = Regex("\\b(buy|purchase|pay|payment|checkout|spend|comprar|compra|compras|pagar|pagamento|gastar|gasto)\\b|\\b(usar|use)\\b.{0,60}\\b(gemas?|gems?|diamantes?|diamonds?|moedas?|coins?|ouro|gold)\\b")
    private fun normalized(text: String) = Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    fun spendingRisk(text: String): Boolean {
        return blocked.containsMatchIn(normalized(text))
    }
    /** Explicit purchase/spending prompts block the whole screen. Passive navigation labels do not. */
    fun purchaseConfirmation(text: String) = confirmation.containsMatchIn(normalized(text))
    fun labelNearTap(text: String, left: Int, top: Int, right: Int, bottom: Int, x: Int, y: Int, radius: Int): Boolean =
        spendingRisk(text) && right > left && bottom > top &&
            x >= left-radius && x <= right+radius && y >= top-radius && y <= bottom+radius
    fun validPoint(x: Double, y: Double) = x.isFinite() && y.isFinite() && x in 0.02..0.98 && y in 0.12..0.98
    fun sameGeometry(w: Int, h: Int, learnedW: Int, learnedH: Int): Boolean =
        w > 0 && h > 0 && learnedW > 0 && learnedH > 0 &&
        kotlin.math.abs(w.toDouble() / h - learnedW.toDouble() / learnedH) < 0.025
}

/** One action at a time; never retry a gesture that may already have reached the game. */
class ReplayGuard(private val now: () -> Long) {
    private var start = now()
    private var last = Long.MIN_VALUE
    private var actions = 0
    private var pending = false
    var stopped = false
        private set
    fun stop() { stopped = true; pending = false }
    fun reserve(): Boolean {
        if (stopped || pending || actions >= 12 || now() - start >= 90_000) { stop(); return false }
        if (last != Long.MIN_VALUE && now() - last < 1_500) return false
        pending = true; actions++; last = now()
        return true
    }
    fun verified(changed: Boolean): Boolean {
        if (!pending || !changed) { stop(); return false }
        pending = false
        return true
    }
}
