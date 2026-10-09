package com.dragoncity.autobot

import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    @Test fun passiveStoreLabelsDoNotBlockTheEntireScreen() {
        assertFalse(SafetyPolicy.purchaseConfirmation("BATALHA LOJA OFERTAS GEMAS 65"))
        assertTrue(SafetyPolicy.spendingRisk("LOJA")); assertTrue(SafetyPolicy.spendingRisk("OFERTAS"))
    }
    @Test fun explicitSpendingPromptAlwaysBlocks() {
        listOf("Comprar", "Deseja gastar 5 gemas?", "BUY GEMS $5", "Use 3 gems", "Usar 30 moedas?").forEach {
            assertTrue(it, SafetyPolicy.purchaseConfirmation(it))
        }
    }
    @Test fun shopLabelBlocksOnlyNearbyTap() {
        assertTrue(SafetyPolicy.labelNearTap("LOJA",800,400,900,440,850,380,96))
        assertFalse(SafetyPolicy.labelNearTap("LOJA",800,400,900,440,200,380,96))
        assertFalse(SafetyPolicy.labelNearTap("BATALHA",800,400,900,440,850,380,96))
    }
    @Test fun blocksPurchaseLanguageAndAccents() {
        listOf("Comprar", "GASTAR GEMAS", "Oferta especial", "Promoção", "Buy now", "Purchase", "R$ 9,99", "$5", "€3", "PAY", "Diamantes").forEach {
            assertTrue(it, SafetyPolicy.spendingRisk(it))
        }
    }
    @Test fun acceptsOrdinaryBattleLabels() {
        listOf("Atacar", "Defender", "Vitória", "Attack", "Trocar dragão", "Turno 2").forEach {
            assertFalse(it, SafetyPolicy.spendingRisk(it))
        }
    }
    @Test fun rejectsUnsafeAndNonFiniteCoordinates() {
        assertFalse(SafetyPolicy.validPoint(Double.NaN, 0.5))
        assertFalse(SafetyPolicy.validPoint(0.5, Double.POSITIVE_INFINITY))
        assertFalse(SafetyPolicy.validPoint(-0.1, 0.5))
        assertFalse(SafetyPolicy.validPoint(0.5, 0.08))
        assertFalse(SafetyPolicy.validPoint(0.999, 0.5))
        assertTrue(SafetyPolicy.validPoint(0.5, 0.5))
    }
    @Test fun geometryAllowsScalingButRejectsRotation() {
        assertTrue(SafetyPolicy.sameGeometry(1080, 2400, 540, 1200))
        assertFalse(SafetyPolicy.sameGeometry(2400, 1080, 1080, 2400))
        assertFalse(SafetyPolicy.sameGeometry(0, 1080, 1080, 2400))
    }
    @Test fun duplicatePendingGestureStopsSession() {
        var time = 0L; val guard = ReplayGuard { time }
        assertTrue(guard.reserve()); time += 2000
        assertFalse(guard.reserve()); assertTrue(guard.stopped)
    }
    @Test fun unchangedScreenStopsWithoutRetry() {
        val guard = ReplayGuard { 0L }
        assertTrue(guard.reserve()); assertFalse(guard.verified(false)); assertFalse(guard.reserve())
    }
    @Test fun limitsReplayToTwelveActions() {
        var time = 0L; val guard = ReplayGuard { time }
        repeat(12) { assertTrue(guard.reserve()); assertTrue(guard.verified(true)); time += 2000 }
        assertFalse(guard.reserve())
    }
    @Test fun enforcesCooldownWithoutDispatch() {
        var time = 0L; val guard = ReplayGuard { time }
        assertTrue(guard.reserve()); assertTrue(guard.verified(true))
        time = 1499; assertFalse(guard.reserve()); assertFalse(guard.stopped)
        time = 1500; assertTrue(guard.reserve())
    }
    @Test fun stopsAtNinetySeconds() {
        var time = 0L; val guard = ReplayGuard { time }
        time = 90000; assertFalse(guard.reserve()); assertTrue(guard.stopped)
    }
    @Test fun cancellationIsPermanent() {
        val guard = ReplayGuard { 0L }; guard.stop(); assertFalse(guard.reserve())
    }
    @Test fun verificationWithoutAnActionStops() {
        val guard = ReplayGuard { 0L }; assertFalse(guard.verified(true)); assertTrue(guard.stopped)
    }
    @Test fun schedulerHasIndependentCooldowns() {
        var time = 0L; val scheduler = TaskScheduler { time }
        assertTrue(scheduler.due(TaskType.BATTLE, 2000)); scheduler.mark(TaskType.BATTLE)
        assertFalse(scheduler.due(TaskType.BATTLE, 2000)); assertTrue(scheduler.due(TaskType.FOOD, 2000))
        time=2000; assertTrue(scheduler.due(TaskType.BATTLE, 2000)); assertFalse(scheduler.due(TaskType.BATTLE, -1))
        scheduler.clear(); assertTrue(scheduler.due(TaskType.BATTLE, 2000))
    }
}
