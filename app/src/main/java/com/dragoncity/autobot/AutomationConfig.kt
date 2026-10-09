package com.dragoncity.autobot

data class AutomationConfig(
 val gold: Boolean = true,
 val food: Boolean = true,
 val battles: Boolean = false,
 val rewards: Boolean = true,
 val intervalMs: Long = 2200L,
 val confidenceThreshold: Double = 0.96
)
enum class TaskType { GOLD, FOOD, BATTLE, REWARD }
enum class BotState { STOPPED, SEARCHING, VERIFYING, PAUSED, ERROR }
data class Target(val type: TaskType, val x: Int, val y: Int, val confidence: Double)
