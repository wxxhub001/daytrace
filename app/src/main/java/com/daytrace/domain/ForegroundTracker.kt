package com.daytrace.domain

/**
 * 增量回放 UsageEvents，维护"当前前台应用 + 本次连续使用时长"。
 * 与 UsageAnalyzer 的严格口径一致：短暂离开（< mergeGapMs）仍算同一次使用。
 */
class ForegroundTracker(
    private val mergeGapMs: Long = 3_000L,
    private val ignoredPackages: Set<String> = DEFAULT_IGNORED,
) {

    data class Snapshot(val packageName: String, val sessionStartTs: Long, val usedMs: Long)

    private var pkg: String? = null
    private var sessionStart = 0L
    private var accumulated = 0L
    private var segmentStart = 0L
    private var paused = false
    private var pausedAt = 0L

    fun consume(events: List<RawEvent>) {
        if (events.isEmpty()) return
        for (e in events.sortedBy { it.ts }) {
            if (e.pkg in ignoredPackages) continue
            when (e.type) {
                EventType.RESUMED -> onResume(e.pkg, e.ts)
                EventType.PAUSED, EventType.STOPPED -> onPause(e.pkg, e.ts)
                EventType.SCREEN_NON_INTERACTIVE, EventType.KEYGUARD_SHOWN, EventType.DEVICE_SHUTDOWN ->
                    closeSegment(e.ts)
                else -> Unit
            }
        }
    }

    private fun onResume(p: String, ts: Long) {
        if (pkg == p) {
            if (paused) {
                if (ts - pausedAt <= mergeGapMs) {
                    paused = false
                    segmentStart = ts
                } else {
                    pkg = p
                    sessionStart = ts
                    accumulated = 0L
                    segmentStart = ts
                    paused = false
                }
            }
            return
        }
        closeSegment(ts)
        pkg = p
        sessionStart = ts
        accumulated = 0L
        segmentStart = ts
        paused = false
    }

    private fun onPause(p: String, ts: Long) {
        if (pkg != p || paused) return
        closeSegment(ts)
    }

    private fun closeSegment(ts: Long) {
        if (pkg != null && !paused) {
            accumulated += (ts - segmentStart).coerceAtLeast(0L)
        }
        paused = true
        pausedAt = ts
    }

    fun snapshot(now: Long): Snapshot? {
        val current = pkg ?: return null
        if (paused) return null
        val used = accumulated + (now - segmentStart).coerceAtLeast(0L)
        return Snapshot(current, sessionStart, used)
    }

    fun reset() {
        pkg = null
        sessionStart = 0L
        accumulated = 0L
        segmentStart = 0L
        paused = false
        pausedAt = 0L
    }

    companion object {
        val DEFAULT_IGNORED: Set<String> = setOf(
            "com.android.systemui",
            "android",
            "com.android.launcher",
            "com.android.launcher3",
        )
    }
}
