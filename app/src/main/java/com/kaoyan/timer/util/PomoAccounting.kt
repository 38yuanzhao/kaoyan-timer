package com.kaoyan.timer.util

import com.kaoyan.timer.model.FocusSegment
import com.kaoyan.timer.model.Pomo

/** Actual focus intervals accumulated by [endAt]; pause gaps are absent from tracked Pomodoros. */
fun Pomo.focusIntervalsUntil(endAt: Long): List<FocusSegment> {
    val tracked = focusSegments
    if (tracked == null) {
        return if (endAt > startAt) listOf(FocusSegment(startAt, endAt)) else emptyList()
    }
    val result = tracked.filter { it.endAt > it.startAt }.toMutableList()
    if (pausedAt == null) {
        val activeStart = activeFocusStartedAt ?: startAt
        if (endAt > activeStart) result += FocusSegment(activeStart, endAt)
    }
    return result
}

fun Pomo.focusedSecondsUntil(endAt: Long): Double =
    focusIntervalsUntil(endAt).sumOf { (it.endAt - it.startAt) / 1000.0 }

/** Freeze and persist the just-finished active focus interval, upgrading legacy state if needed. */
fun pausePomoAt(pomo: Pomo, freezeAt: Long): Pomo {
    if (pomo.pausedAt != null) return pomo
    if (pomo.phase != "focus" && pomo.phase != "overtime") {
        return pomo.copy(pausedAt = freezeAt)
    }
    val existing = pomo.focusSegments ?: emptyList()
    val activeStart = pomo.activeFocusStartedAt ?: pomo.startAt
    val segments = if (freezeAt > activeStart) {
        existing + FocusSegment(activeStart, freezeAt)
    } else {
        existing
    }
    return pomo.copy(
        pausedAt = freezeAt,
        focusSegments = segments,
        activeFocusStartedAt = null
    )
}

/** Resume from a new wall-clock segment while extending the deadline by the pause duration. */
fun resumePomoAt(pomo: Pomo, now: Long): Pomo {
    val pausedAt = pomo.pausedAt ?: return pomo
    val delta = (now - pausedAt).coerceAtLeast(0L)
    if (pomo.phase != "focus" && pomo.phase != "overtime") {
        return pomo.copy(
            startAt = pomo.startAt + delta,
            endsAt = pomo.endsAt + delta,
            pausedAt = null
        )
    }
    val migratedSegments = pomo.focusSegments ?: listOfNotNull(
        if (pausedAt > pomo.startAt) FocusSegment(pomo.startAt, pausedAt) else null
    )
    return pomo.copy(
        // startAt/endsAt remain the logical, pause-free interval used by progress UI.
        // Real wall-clock focus is retained separately in focusSegments.
        startAt = pomo.startAt + delta,
        endsAt = pomo.endsAt + delta,
        pausedAt = null,
        focusSegments = migratedSegments,
        activeFocusStartedAt = now
    )
}
