package com.dragoncity.autobot

import android.os.SystemClock

/** Conservative scheduler: no automation while the target app is not foreground. */
class TaskScheduler {
 private var lastRun = mutableMapOf<TaskType, Long>()
 fun due(type: TaskType, minIntervalMs: Long): Boolean =
  SystemClock.elapsedRealtime() - (lastRun[type] ?: 0L) >= minIntervalMs
 fun mark(type: TaskType) { lastRun[type] = SystemClock.elapsedRealtime() }
 fun clear() { lastRun.clear() }
}