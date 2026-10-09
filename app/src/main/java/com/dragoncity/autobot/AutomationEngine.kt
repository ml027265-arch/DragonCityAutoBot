package com.dragoncity.autobot

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Guarded state machine. Deliberately never spends currency or initiates battles
 * without a dedicated action classifier and user opt-in.
 */
class AutomationEngine(private val matcher: ScreenMatcher = ScreenMatcher(), private val now: () -> Long = SystemClock::elapsedRealtime) {
 var state: BotState = BotState.STOPPED
  private set
 private var lastAction = 0L
 private var repeated = 0
 private var lastTarget: Target? = null
 fun start() { state = BotState.SEARCHING; repeated = 0; lastTarget = null; lastAction = -100_000L }
 fun stop() { state = BotState.STOPPED }
 fun pause() { if (state != BotState.STOPPED) state = BotState.PAUSED }
 fun next(screen: Bitmap, templates: Map<TaskType, Bitmap>, config: AutomationConfig): Target? {
  if (state != BotState.SEARCHING) return null
  if (config.intervalMs < 1500 || config.confidenceThreshold !in 0.93..1.0) { pause(); return null }
  if (now() - lastAction < config.intervalMs) return null
  val allowed = buildList {
   if (config.gold) add(TaskType.GOLD)
   if (config.food) add(TaskType.FOOD)
   if (config.rewards) add(TaskType.REWARD)
   // Battles require a separate explicit decision/verification implementation.
  }
  val candidate = allowed.mapNotNull { type ->
   templates[type]?.let { matcher.find(screen, it, type, config.confidenceThreshold) }
  }.maxByOrNull { it.confidence } ?: return null
  if (lastTarget?.type == candidate.type &&
      kotlin.math.abs((lastTarget?.x ?: 0)-candidate.x) < 20 &&
      kotlin.math.abs((lastTarget?.y ?: 0)-candidate.y) < 20) repeated++ else repeated = 0
  if (repeated >= 3) { state = BotState.PAUSED; return null }
  lastTarget = candidate
  lastAction = now()
  state = BotState.VERIFYING
  return candidate
 }
 fun verifyChanged(changed: Boolean) {
  if (state != BotState.VERIFYING) return
  state = if (changed) BotState.SEARCHING else BotState.PAUSED
 }
}
