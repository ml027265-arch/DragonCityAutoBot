package com.dragoncity.autobot

import java.text.Normalizer

/** Fail closed on known purchase language. OCR cannot prove that an action is free. */
object SafetyPolicy {
    private val blocked = Regex("\\b(buy|purchase|pay|payment|checkout|shop|store|offer|sale|gem|gems|diamond|diamonds|comprar|compra|compras|pagar|pagamento|gastar|gasto|loja|oferta|promocao|gema|gemas|diamante|diamantes|reais|dollars|euros)\\b|[€$£]|r\\$")
    fun spendingRisk(text: String): Boolean {
        val normalized = Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return blocked.containsMatchIn(normalized)
    }
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
