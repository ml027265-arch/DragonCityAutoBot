package com.dragoncity.autobot

import android.os.SystemClock

/** Conservative scheduler: no automation while the target app is not foreground. */
class TaskScheduler(private val now: () -> Long = SystemClock::elapsedRealtime) {
 private var lastRun = mutableMapOf<TaskType, Long>()
 fun due(type: TaskType, minIntervalMs: Long): Boolean =
  minIntervalMs >= 1500 && (lastRun[type]?.let { now() - it >= minIntervalMs } ?: true)
 fun mark(type: TaskType) { lastRun[type] = now() }
 fun clear() { lastRun.clear() }
}
